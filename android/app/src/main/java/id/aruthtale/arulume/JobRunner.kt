package id.aruthtale.arulume

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.getcapacitor.JSArray
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
 * Mendukung satu foto maupun batch (PRD FR-11): foto diproses **berurutan**,
 * satu kegagalan tidak menghentikan antrian, batal menghentikan seluruh antrian.
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
        val uris: List<String>,
        val modelId: String,
        val scale: Int,
        val useGpu: Boolean,
        val tileSize: Int,
        val maxOutputLongSide: Int,
        val quality: Int,
        val format: String,
    ) {
        val total: Int get() = uris.size

        fun keIntent(i: Intent): Intent = i
            .putExtra("jobId", jobId)
            .putStringArrayListExtra("uris", ArrayList(uris))
            .putExtra("modelId", modelId)
            .putExtra("scale", scale)
            .putExtra("useGpu", useGpu)
            .putExtra("tileSize", tileSize)
            .putExtra("maxOutputLongSide", maxOutputLongSide)
            .putExtra("quality", quality)
            .putExtra("format", format)

        companion object {
            fun dariIntent(i: Intent?): Params? {
                if (i == null) return null
                val jobId = i.getStringExtra("jobId") ?: return null
                val uris = i.getStringArrayListExtra("uris")?.filter { it.isNotBlank() }
                    ?: listOfNotNull(i.getStringExtra("uri"))
                if (uris.isEmpty()) return null
                val modelId = i.getStringExtra("modelId") ?: return null
                return Params(
                    jobId = jobId,
                    uris = uris,
                    modelId = modelId,
                    scale = i.getIntExtra("scale", 2).coerceIn(2, 4),
                    useGpu = i.getBooleanExtra("useGpu", false),
                    tileSize = i.getIntExtra("tileSize", 0),
                    maxOutputLongSide = i.getIntExtra(
                        "maxOutputLongSide",
                        ImagePipeline.MAX_OUTPUT_SIDE,
                    ),
                    quality = i.getIntExtra("quality", 95).coerceIn(60, 100),
                    format = i.getStringExtra("format") ?: "jpeg",
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
        val index: Int = 0,
        val total: Int = 1,
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
        status = Status(p.jobId, "decoding", 0, true, 0, p.total)
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
        val total = p.total
        val sukses = ArrayList<JSObject>(total)
        var gagal = 0
        try {
            for ((indeks, uriStr) in p.uris.withIndex()) {
                if (batal.get()) break
                val hasil = try {
                    prosesSatu(ctx, p, uriStr, batal, indeks, total)
                } catch (e: OutOfMemoryError) {
                    Log.e(TAG, "Foto ${indeks + 1} kehabisan memori", e)
                    null
                } catch (t: Throwable) {
                    Log.e(TAG, "Foto ${indeks + 1} gagal", t)
                    null
                }

                if (hasil == null) {
                    gagal++
                    if (total == 1) {
                        // Job tunggal: tetap pakai event `failed` seperti kontrak lama.
                        gagalSatu(ctx, p.jobId, "E_DECODE_FAILED", "Foto tidak bisa dibaca")
                        return
                    }
                    kirim(
                        "itemFailed",
                        JSObject()
                            .put("jobId", p.jobId)
                            .put("index", indeks)
                            .put("total", total)
                            .put("code", "E_ITEM_FAILED")
                            .put("message", "Foto ini gagal diproses"),
                    )
                } else {
                    sukses.add(hasil)
                    kirim(
                        "itemDone",
                        JSObject()
                            .put("jobId", p.jobId)
                            .put("index", indeks)
                            .put("total", total)
                            .put("entry", hasil),
                    )
                }
            }

            if (batal.get() && sukses.isEmpty()) {
                gagalSatu(ctx, p.jobId, "E_CANCELLED", "Dibatalkan")
                return
            }

            status = Status(p.jobId, "done", 100, false, total - 1, total)
            val ringkas = JSObject()
                .put("jobId", p.jobId)
                .put("total", total)
                .put("ok", sukses.size)
                .put("failed", gagal)
                .put("cancelled", batal.get())
            val arr = JSArray()
            sukses.forEach { arr.put(it) }
            ringkas.put("entries", arr)
            // Kompatibilitas job tunggal: layar Hasil membaca field di akar objek.
            sukses.firstOrNull()?.let { satu ->
                for (k in satu.keys()) ringkas.put(k, satu.get(k))
            }
            kirim("completed", ringkas)

            val judul = when {
                batal.get() -> "Proses dihentikan"
                total == 1 -> "Foto selesai diperbesar"
                gagal == 0 -> "$total foto selesai diperbesar"
                else -> "${sukses.size} dari $total foto selesai"
            }
            val teks = when {
                total == 1 && sukses.isNotEmpty() ->
                    "Ketuk untuk melihat hasil " +
                        "${sukses[0].optInt("outputWidth")}×${sukses[0].optInt("outputHeight")}."
                total == 1 -> "Foto tidak diubah."
                gagal == 0 -> "Ketuk untuk melihat hasil."
                else -> "$gagal foto gagal diproses."
            }
            EnhancerService.selesai(ctx, judul, teks)
        } catch (t: Throwable) {
            Log.e(TAG, "Job ${p.jobId} gagal total", t)
            gagalSatu(ctx, p.jobId, "E_UNKNOWN", t.message ?: "Terjadi kesalahan")
        } finally {
            if (jobAktif?.first == p.jobId) jobAktif = null
        }
    }

    /**
     * Memproses satu foto. Mengembalikan objek hasil (untuk `itemDone`) atau
     * `null` kalau foto ini gagal — antrian batch tetap lanjut.
     */
    private fun prosesSatu(
        ctx: Context,
        p: Params,
        uriStr: String,
        batal: AtomicBoolean,
        indeks: Int,
        total: Int,
    ): JSObject? {
        val t0 = SystemClock.elapsedRealtime()
        val fmt = ImagePipeline.OutFormat.dari(p.format)
        var handle = 0L
        var src: Bitmap? = null
        var result: Bitmap? = null
        // Notifikasi: satu bar progres untuk seluruh antrian.
        val judul = if (total > 1) {
            "Memperbesar ${indeks + 1} dari $total"
        } else {
            "Memperbesar foto ${p.scale}×"
        }

        fun persenTotal(dalamItem: Int): Int =
            if (total <= 1) dalamItem else ((indeks * 100 + dalamItem) / total).coerceIn(0, 100)

        fun laporItem(stage: String, dalamItem: Int) {
            val totalPersen = persenTotal(dalamItem)
            status = Status(p.jobId, stage, totalPersen, true, indeks, total)
            kirim(
                "progress",
                JSObject()
                    .put("jobId", p.jobId)
                    .put("stage", stage)
                    .put("percent", totalPersen)
                    .put("itemPercent", dalamItem)
                    .put("index", indeks)
                    .put("total", total),
            )
        }

        fun notif(teks: String, dalamItem: Int) {
            ProsesNotif.perbarui(ctx, judul, teks, persenTotal(dalamItem))
        }

        try {
            ImagePipeline.pruneResults(ctx, keep = maxOf(3, total))
            laporItem("decoding", 3)
            notif("Membaca foto…", 3)

            val uri = Uri.parse(uriStr)
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
                Log.w(TAG, "Foto ${indeks + 1} tidak bisa dibaca")
                return null
            }
            if (batal.get()) return null

            // PNG/WebP transparan: engine native bekerja di RGB, jadi alpha
            // diratakan ke putih lebih dulu supaya tidak jadi hitam. Bitmap asli
            // disimpan supaya topeng alpha bisa dipasang lagi di hasil (TC-04).
            val sumberAlpha = if (src.hasAlpha()) src else null
            src = ImagePipeline.ratakanAlpha(src)
            laporItem("decoding", 10)

            val beforePath = ImagePipeline.writePreview(ctx, src, 720)

            val dir = File(ctx.cacheDir, "models").apply { mkdirs() }
            val param = File(dir, "${p.modelId}.param")
            val bin = File(dir, "${p.modelId}.bin")
            copyAsset(ctx, "models/${p.modelId}.param", param)
            copyAsset(ctx, "models/${p.modelId}.bin", bin)

            val threads = maxOf(1, Runtime.getRuntime().availableProcessors() / 2)
            handle = EnhancerNative.nativeCreate(param.absolutePath, bin.absolutePath, threads, p.useGpu)
            if (handle == 0L) {
                Log.w(TAG, "Model gagal dimuat")
                return null
            }
            laporItem("decoding", 15)
            notif("Menyiapkan mesin…", 15)

            val w = src.width
            val h = src.height
            // Pemotongan wajib di atas 512 px: satu lintasan 1024² mematikan proses.
            var tile = if (p.tileSize > 0) p.tileSize else if (maxOf(w, h) > 512) 256 else 0

            val px = ImagePipeline.toArgb(src)
            var persenTerakhir = 15

            // Fallback bertahap (PRD FR-03): kalau tile pertama kehabisan memori,
            // coba tile lebih kecil sebelum menyerah.
            var outPx: IntArray? = null
            var percobaan = 0
            while (outPx == null && percobaan < 3) {
                if (batal.get()) return null
                outPx = EnhancerNative.nativeUpscale(
                    handle, px, w, h, tile, 16,
                    EnhancerNative.TileProgress { selesai, bagian ->
                        if (batal.get()) return@TileProgress
                        val frac = if (bagian > 0) selesai.toFloat() / bagian else 1f
                        // Inferensi menempati rentang 15–88% di dalam satu foto.
                        val dalamItem = 15 + (frac * 73).toInt()
                        if (dalamItem - persenTerakhir >= 3) {
                            persenTerakhir = dalamItem
                            laporItem("enhancing", dalamItem)
                            notif("Memperbesar… $dalamItem%  ($selesai/$bagian bagian)", dalamItem)
                        }
                    },
                )
                if (outPx == null) {
                    percobaan++
                    if (tile <= 96) break
                    tile = maxOf(96, tile / 2)
                    Log.w(TAG, "Tile kehabisan memori, coba tile=$tile (percobaan $percobaan)")
                    notif("Mencoba ulang dengan potongan lebih kecil…", 40)
                }
            }
            if (outPx == null) {
                Log.w(TAG, "Inferensi gagal setelah $percobaan percobaan")
                return null
            }
            Log.i(TAG, "Sumber ${w}x$h → hasil ${w * p.scale}x${h * p.scale}, tile=$tile")
            laporItem("enhancing", 88)

            if (batal.get()) return null

            notif("Menyiapkan hasil…", 92)
            result = ImagePipeline.fromArgb(outPx, w * p.scale, h * p.scale)
            if (sumberAlpha != null) {
                if (fmt != ImagePipeline.OutFormat.JPEG) {
                    result = ImagePipeline.pasangAlpha(result, sumberAlpha)
                }
                sumberAlpha.recycle()
            }
            laporItem("encoding", 93)

            val afterPath = ImagePipeline.writePreview(ctx, result, 1080)
            laporItem("saving", 96)
            notif("Menyimpan hasil…", 96)

            // PRD FR-06 (revisi): hasil TIDAK langsung masuk galeri. User melihat
            // dulu di layar Hasil, baru menekan Simpan → `saveToGallery`.
            val file = ImagePipeline.writeResult(ctx, result, p.quality, fmt)
            if (file == null) {
                Log.w(TAG, "Gagal menulis hasil")
                return null
            }

            val durationMs = SystemClock.elapsedRealtime() - t0
            val done = JSObject()
                .put("jobId", p.jobId)
                .put("resultPath", file.absolutePath)
                .put("outputUri", "")
                .put("outputWidth", result.width)
                .put("outputHeight", result.height)
                .put("durationMs", durationMs)
                .put("usedGpu", p.useGpu)
                .put("tileSizeUsed", tile)
                .put("format", fmt.ext)
                .put("beforePreviewPath", beforePath)
                .put("afterPreviewPath", afterPath)
            Log.i(TAG, "Foto ${indeks + 1}/$total selesai: ${done.toString()}")

            // Riwayat (PRD FR-08): satu entri per hasil, disimpan di filesDir.
            Riwayat.add(
                ctx,
                JSONObject()
                    .put("id", if (total > 1) "${p.jobId}-${indeks + 1}" else p.jobId)
                    .put("createdAt", System.currentTimeMillis())
                    .put("model", p.modelId)
                    .put("scale", p.scale)
                    .put("srcWidth", w)
                    .put("srcHeight", h)
                    .put("outWidth", result.width)
                    .put("outHeight", result.height)
                    .put("durationMs", durationMs)
                    .put("resultPath", file.absolutePath)
                    .put("beforePreviewPath", beforePath)
                    .put("afterPreviewPath", afterPath)
                    .put("savedUri", "")
                    .put("savedName", ""),
            )
            return done
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
        }
    }

    /** Kegagalan yang menghentikan seluruh job (tunggal atau antrian batal). */
    private fun gagalSatu(ctx: Context, jobId: String, code: String, pesan: String) {
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
