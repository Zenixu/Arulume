package id.aruthtale.arulume

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.getcapacitor.JSObject
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pelaksana job perbesar foto — dipisah dari plugin supaya pekerjaan tetap
 * hidup walau WebView/Activity sudah tidak ada (lihat [EnhancerService]).
 *
 * Aturan yang dijaga:
 * - Semua kerja berat di thread `enhancer-worker` (Docs/AGENTS.md #3).
 * - Tidak menyentuh berkas asli pengguna; hasil hanya di `cacheDir/hasil/`
 *   (Docs/AGENTS.md #4). Galeri hanya lewat `saveToGallery` saat user menekan Simpan.
 * - Inferensi di native (Docs/AGENTS.md #2).
 */
object JobRunner {

    private const val TAG = "Arulume/Job"

    /** Parameter satu pekerjaan; bisa dikirim lewat Intent ke layanan. */
    data class Params(
        val jobId: String,
        val uri: String,
        val modelId: String,
        val scale: Int,
        val useGpu: Boolean,
        val tileSize: Int,
        val maxOutputLongSide: Int,
        val quality: Int,
    ) {
        fun keIntent(i: Intent): Intent = i
            .putExtra("jobId", jobId)
            .putExtra("uri", uri)
            .putExtra("modelId", modelId)
            .putExtra("scale", scale)
            .putExtra("useGpu", useGpu)
            .putExtra("tileSize", tileSize)
            .putExtra("maxOutputLongSide", maxOutputLongSide)
            .putExtra("quality", quality)

        companion object {
            fun dariIntent(i: Intent?): Params? {
                if (i == null) return null
                val jobId = i.getStringExtra("jobId") ?: return null
                val uri = i.getStringExtra("uri") ?: return null
                val modelId = i.getStringExtra("modelId") ?: return null
                return Params(
                    jobId = jobId,
                    uri = uri,
                    modelId = modelId,
                    scale = i.getIntExtra("scale", 2).coerceIn(2, 4),
                    useGpu = i.getBooleanExtra("useGpu", false),
                    tileSize = i.getIntExtra("tileSize", 0),
                    maxOutputLongSide = i.getIntExtra(
                        "maxOutputLongSide",
                        ImagePipeline.MAX_OUTPUT_SIDE,
                    ),
                    quality = i.getIntExtra("quality", 95).coerceIn(60, 100),
                )
            }
        }
    }

    /** Keadaan terakhir — dibaca plugin saat WebView lahir ulang. */
    data class Status(
        val jobId: String,
        val stage: String,
        val percent: Int,
        val aktif: Boolean,
    )

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "enhancer-worker").apply { priority = Thread.NORM_PRIORITY }
    }

    private val pendengar = CopyOnWriteArrayList<(String, JSObject) -> Unit>()

    @Volatile
    private var jobAktif: Pair<String, AtomicBoolean>? = null

    @Volatile
    var status: Status? = null
        private set

    private val seq = AtomicInteger(0)

    fun jobIdBaru(): String = "job-${System.currentTimeMillis()}-${seq.incrementAndGet()}"

    fun sedangJalan(): String? = jobAktif?.first

    fun tambahPendengar(l: (String, JSObject) -> Unit) {
        pendengar.add(l)
    }

    fun hapusPendengar(l: (String, JSObject) -> Unit) {
        pendengar.remove(l)
    }

    fun mulai(ctx: Context, p: Params) {
        val batal = AtomicBoolean(false)
        jobAktif = p.jobId to batal
        status = Status(p.jobId, "decoding", 0, true)
        executor.execute { jalankan(ctx.applicationContext, p, batal) }
    }

    fun batalkan(jobId: String?): Boolean {
        val aktif = jobAktif ?: return false
        if (jobId != null && jobId != aktif.first) return false
        aktif.second.set(true)
        return true
    }

    // ------------------------------------------------------------- pelaksanaan

    private fun jalankan(ctx: Context, p: Params, batal: AtomicBoolean) {
        val t0 = SystemClock.elapsedRealtime()
        var handle = 0L
        var src: Bitmap? = null
        var result: Bitmap? = null
        val judul = "Memperbesar foto ${p.scale}×"
        try {
            ImagePipeline.pruneResults(ctx)
            lapor(p.jobId, "decoding", 3)
            perbaruiNotif(ctx, judul, "Membaca foto…", 3)

            val uri = Uri.parse(p.uri)
            val inputCap = (p.maxOutputLongSide / p.scale).coerceAtLeast(64)
            // Berkas hilang / bukan gambar melempar exception, bukan null —
            // ditangkap di sini supaya pesannya ramah, bukan teks ENOENT.
            src = try {
                ImagePipeline.decode(ctx, uri, inputCap)
            } catch (t: Throwable) {
                Log.w(TAG, "Dekode gagal: ${t.message}")
                null
            }
            if (src == null) {
                gagal(ctx, p.jobId, "E_DECODE_FAILED", "Foto tidak bisa dibaca")
                return
            }
            if (batal.get()) {
                gagal(ctx, p.jobId, "E_CANCELLED", "Dibatalkan")
                return
            }
            lapor(p.jobId, "decoding", 10)

            val beforePath = ImagePipeline.writePreview(ctx, src, 720)

            val dir = File(ctx.cacheDir, "models").apply { mkdirs() }
            val param = File(dir, "${p.modelId}.param")
            val bin = File(dir, "${p.modelId}.bin")
            copyAsset(ctx, "models/${p.modelId}.param", param)
            copyAsset(ctx, "models/${p.modelId}.bin", bin)

            val threads = maxOf(1, Runtime.getRuntime().availableProcessors() / 2)
            handle = EnhancerNative.nativeCreate(param.absolutePath, bin.absolutePath, threads, p.useGpu)
            if (handle == 0L) {
                gagal(ctx, p.jobId, "E_MODEL_MISSING", "Model gagal dimuat")
                return
            }
            lapor(p.jobId, "decoding", 15)
            perbaruiNotif(ctx, judul, "Menyiapkan mesin…", 15)

            val w = src.width
            val h = src.height
            // Pemotongan wajib di atas 512 px: satu lintasan 1024² mematikan proses.
            val tile = if (p.tileSize > 0) p.tileSize else if (maxOf(w, h) > 512) 256 else 0

            val px = ImagePipeline.toArgb(src)
            var persenTerakhir = 15

            val outPx = EnhancerNative.nativeUpscale(
                handle, px, w, h, tile, 16,
                EnhancerNative.TileProgress { selesai, total ->
                    if (batal.get()) return@TileProgress
                    val bagian = if (total > 0) selesai.toFloat() / total else 1f
                    // Inferensi menempati rentang 15–88%.
                    val persen = 15 + (bagian * 73).toInt()
                    if (persen - persenTerakhir >= 3) {
                        persenTerakhir = persen
                        lapor(p.jobId, "enhancing", persen)
                        perbaruiNotif(
                            ctx, judul,
                            "Memperbesar… $persen%  ($selesai/$total bagian)",
                            persen,
                        )
                    }
                },
            )
            if (outPx == null) {
                gagal(ctx, p.jobId, "E_OUT_OF_MEMORY", "Kehabisan memori saat memproses")
                return
            }
            Log.i(TAG, "Sumber ${w}x$h → hasil ${w * p.scale}x${h * p.scale}, tile=$tile")
            lapor(p.jobId, "enhancing", 88)

            if (batal.get()) {
                gagal(ctx, p.jobId, "E_CANCELLED", "Dibatalkan")
                return
            }

            perbaruiNotif(ctx, judul, "Menyiapkan hasil…", 92)
            result = ImagePipeline.fromArgb(outPx, w * p.scale, h * p.scale)
            lapor(p.jobId, "encoding", 93)

            val afterPath = ImagePipeline.writePreview(ctx, result, 1080)
            lapor(p.jobId, "saving", 96)
            perbaruiNotif(ctx, judul, "Menyimpan hasil…", 96)

            // PRD FR-06 (revisi): hasil TIDAK langsung masuk galeri. User melihat
            // dulu di layar Hasil, baru menekan Simpan → `saveToGallery`.
            val file = ImagePipeline.writeResult(ctx, result, p.quality)
            if (file == null) {
                gagal(ctx, p.jobId, "E_SAVE_FAILED", "Gagal menyiapkan hasil")
                return
            }

            lapor(p.jobId, "done", 100)
            val done = JSObject()
                .put("jobId", p.jobId)
                .put("resultPath", file.absolutePath)
                .put("outputUri", "")
                .put("outputWidth", result.width)
                .put("outputHeight", result.height)
                .put("durationMs", SystemClock.elapsedRealtime() - t0)
                .put("usedGpu", p.useGpu)
                .put("tileSizeUsed", tile)
                .put("beforePreviewPath", beforePath)
                .put("afterPreviewPath", afterPath)
            Log.i(TAG, "Job selesai: ${done.toString()}")

            // Riwayat (PRD FR-08): satu entri per hasil, disimpan di filesDir.
            Riwayat.add(
                ctx,
                JSONObject()
                    .put("id", p.jobId)
                    .put("createdAt", System.currentTimeMillis())
                    .put("model", p.modelId)
                    .put("scale", p.scale)
                    .put("srcWidth", w)
                    .put("srcHeight", h)
                    .put("outWidth", result.width)
                    .put("outHeight", result.height)
                    .put("durationMs", done.getLong("durationMs"))
                    .put("resultPath", file.absolutePath)
                    .put("beforePreviewPath", beforePath)
                    .put("afterPreviewPath", afterPath)
                    .put("savedUri", "")
                    .put("savedName", ""),
            )

            status = Status(p.jobId, "done", 100, false)
            kirim("completed", done)
            EnhancerService.selesai(
                ctx,
                "Foto selesai diperbesar",
                "Ketuk untuk melihat hasil ${result.width}×${result.height}.",
            )
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Job ${p.jobId} kehabisan memori", e)
            gagal(ctx, p.jobId, "E_OUT_OF_MEMORY", "Kehabisan memori saat memproses")
        } catch (t: Throwable) {
            Log.e(TAG, "Job ${p.jobId} gagal", t)
            gagal(ctx, p.jobId, "E_UNKNOWN", t.message ?: "Terjadi kesalahan")
        } finally {
            if (handle != 0L) {
                try {
                    EnhancerNative.nativeDestroy(handle)
                } catch (t: Throwable) {
                    Log.w(TAG, "Lepas model gagal: ${t.message}")
                }
            }
            src?.recycle()
            result?.recycle()
            if (jobAktif?.first == p.jobId) jobAktif = null
        }
    }

    private fun lapor(jobId: String, stage: String, percent: Int) {
        status = Status(jobId, stage, percent, true)
        kirim(
            "progress",
            JSObject().put("jobId", jobId).put("stage", stage).put("percent", percent),
        )
    }

    private fun gagal(ctx: Context, jobId: String, code: String, pesan: String) {
        Log.w(TAG, "Job $jobId gagal: $code — $pesan")
        status = Status(jobId, "failed", 0, false)
        kirim(
            "failed",
            JSObject()
                .put("jobId", jobId)
                .put("code", code)
                .put("message", pesan)
                .put("recoverable", code != "E_OUT_OF_MEMORY"),
        )
        val (judul, teks) = when (code) {
            "E_CANCELLED" -> "Proses dibatalkan" to "Foto tidak diubah."
            "E_OUT_OF_MEMORY" -> "Foto terlalu besar" to "Coba skala 2× atau foto lain."
            else -> "Gagal memperbesar foto" to pesan
        }
        EnhancerService.selesai(ctx, judul, teks)
    }

    private fun perbaruiNotif(ctx: Context, judul: String, teks: String, persen: Int) {
        ProsesNotif.perbarui(ctx, judul, teks, persen)
    }

    private fun kirim(event: String, data: JSObject) {
        pendengar.forEach { l ->
            try {
                l(event, data)
            } catch (t: Throwable) {
                Log.w(TAG, "Pendengar $event gagal: ${t.message}")
            }
        }
    }

    private fun copyAsset(ctx: Context, asset: String, target: File) {
        if (target.exists() && target.length() > 0) return
        ctx.assets.open(asset).use { input ->
            target.outputStream().use { out -> input.copyTo(out) }
        }
    }
}
