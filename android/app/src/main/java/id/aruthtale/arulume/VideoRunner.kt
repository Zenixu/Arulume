package id.aruthtale.arulume

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.getcapacitor.JSObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pelaksana job video (Fase 2) — dipisah dari plugin supaya pekerjaan tetap
 * hidup walau WebView/Activity sudah tidak ada (lihat [EnhancerService]).
 *
 * Tiga perilaku yang penting untuk pengguna:
 *  - **tetap jalan** saat aplikasi ditinggal (foreground service + wake lock),
 *  - **bisa dilanjutkan** kalau proses mati di tengah (lihat [VideoState]),
 *  - **tidak dobel**: hanya satu job video dalam satu waktu.
 *
 * Semua kerja berat ada di thread `enhancer-worker`; hasil hanya ditulis ke
 * `cacheDir/hasil-video/` (Docs/AGENTS.md #3 dan #4). Tidak ada jaringan.
 */
object VideoRunner {

    private const val TAG = "Arulume/VideoRun"

    /** Parameter satu pekerjaan video; bisa dikirim lewat Intent ke layanan. */
    data class Params(
        val jobId: String,
        val uri: String,
        val targetSisi: Int,
        val bitrate: Int,
        val tajam: Boolean,
    ) {
        fun keIntent(i: Intent): Intent = i
            .putExtra("jobId", jobId)
            .putExtra("uri", uri)
            .putExtra("target", targetSisi)
            .putExtra("bitrate", bitrate)
            .putExtra("tajam", tajam)

        companion object {
            fun dariIntent(i: Intent?): Params? {
                if (i == null) return null
                val jobId = i.getStringExtra("jobId") ?: return null
                val uri = i.getStringExtra("uri") ?: return null
                return Params(
                    jobId = jobId,
                    uri = uri,
                    targetSisi = i.getIntExtra("target", 1280).coerceIn(240, 2160),
                    bitrate = i.getIntExtra("bitrate", 0),
                    tajam = i.getBooleanExtra("tajam", true),
                )
            }

            fun dariState(s: VideoState.State): Params = Params(
                jobId = s.jobId,
                uri = s.uri,
                targetSisi = s.target,
                bitrate = s.bitrate,
                tajam = s.tajam,
            )
        }
    }

    /** Keadaan terakhir — dibaca plugin saat WebView lahir ulang. */
    data class Status(
        val jobId: String,
        val stage: String,
        val percent: Int,
        val aktif: Boolean,
        val frame: Int = 0,
        val totalFrame: Int = 0,
        val bisaLanjut: Boolean = false,
        val path: String = "",
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

    fun jobIdBaru(): String = "vid-${System.currentTimeMillis()}-${seq.incrementAndGet()}"

    fun sedangJalan(): String? = jobAktif?.first

    fun tambahPendengar(l: (String, JSObject) -> Unit) {
        pendengar.add(l)
    }

    fun hapusPendengar(l: (String, JSObject) -> Unit) {
        pendengar.remove(l)
    }

    /** Mulai job baru (membuang sisa job lama yang belum selesai). */
    fun mulai(ctx: Context, p: Params): Boolean {
        if (jobAktif != null) return false
        // Job lama yang tidak dilanjutkan tidak boleh menumpuk segmennya.
        VideoState.baca(ctx)?.let { lama ->
            VideoState.berkasSegmen(ctx, lama).forEach { runCatching { it.delete() } }
        }
        VideoState.hapus(ctx)
        VideoState.bersihkanSisa(ctx, null)
        return jalankan(ctx, p, null)
    }

    /** Lanjutkan job yang terhenti dari frame terakhir yang tercatat. */
    fun lanjutkan(ctx: Context): Boolean {
        if (jobAktif != null) return false
        val s = VideoState.baca(ctx) ?: return false
        if (s.selesai) return false
        return jalankan(ctx, Params.dariState(s), s)
    }

    /** Hentikan sementara: kerja berhenti, catatan + potongan tetap disimpan. */
    fun hentikan(): Boolean {
        val aktif = jobAktif ?: return false
        aktif.second.set(true)
        return true
    }

    fun batalkan(jobId: String?): Boolean {
        val aktif = jobAktif ?: return false
        if (jobId != null && jobId != aktif.first) return false
        aktif.second.set(true)
        return true
    }

    /** Status untuk UI: dari memori bila sedang jalan, atau dari catatan di disk. */
    fun statusTerkini(ctx: Context): Status? {
        status?.let { if (it.aktif) return it }
        val s = VideoState.baca(ctx) ?: return status
        if (s.selesai) return status
        return Status(
            jobId = s.jobId,
            stage = "terhenti",
            percent = s.persen,
            aktif = false,
            frame = s.frameTerakhir,
            totalFrame = s.totalFrame,
            bisaLanjut = true,
        )
    }

    // ------------------------------------------------------------- pelaksanaan

    private fun jalankan(ctx: Context, p: Params, awal: VideoState.State?): Boolean {
        val batal = AtomicBoolean(false)
        jobAktif = p.jobId to batal
        status = Status(
            jobId = p.jobId,
            stage = if (awal != null) "melanjutkan" else "menyiapkan",
            percent = awal?.persen ?: 0,
            aktif = true,
            frame = awal?.frameTerakhir ?: 0,
            totalFrame = awal?.totalFrame ?: 0,
        )
        executor.execute { jalankanJob(ctx.applicationContext, p, batal, awal) }
        return true
    }

    private fun jalankanJob(ctx: Context, p: Params, batal: AtomicBoolean, awal: VideoState.State?) {
        val cap = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val dir = File(ctx.cacheDir, "hasil-video").apply { mkdirs() }
        val keluar = File(dir, "Arulume_$cap.mp4")

        var persenTerakhir = -1
        var waktuTerakhir = 0L
        var totalFrame = awal?.totalFrame ?: 0
        var frameKini = awal?.frameTerakhir ?: 0
        var catatan = awal

        val metaSumber = runCatching { VideoProbe.meta(ctx, Uri.parse(p.uri)) }.getOrNull()

        try {
            val hasil = VideoJob.proses(
                ctx = ctx,
                uri = Uri.parse(p.uri),
                keluar = keluar,
                opsi = VideoJob.Opsi(
                    targetSisi = p.targetSisi,
                    bitrate = p.bitrate,
                    tajam = p.tajam,
                ),
                awal = awal,
                simpan = { s ->
                    catatan = s
                    VideoState.tulis(ctx, s)
                },
                progres = { frame, total ->
                    totalFrame = total
                    frameKini = frame
                    val persen = if (total > 0) (frame * 100 / total).coerceIn(0, 100) else 0
                    // Kirim ke WebView paling sering 1× per persen supaya jembatan
                    // tidak kebanjiran event (ratusan frame per klip).
                    if (persen != persenTerakhir) {
                        persenTerakhir = persen
                        status = Status(p.jobId, "memproses", persen, true, frame, total)
                        kirim(
                            "progress",
                            JSObject()
                                .put("jobId", p.jobId)
                                .put("kind", "video")
                                .put("stage", "memproses")
                                .put("percent", persen)
                                .put("frame", frame)
                                .put("totalFrame", total),
                        )
                        val now = SystemClock.elapsedRealtime()
                        if (now - waktuTerakhir > 900) {
                            waktuTerakhir = now
                            ProsesNotif.perbarui(
                                ctx,
                                "Memperjelas video",
                                "$persen% · frame $frame dari $total",
                                persen,
                            )
                        }
                    }
                },
                dibatalkan = { batal.get() },
            )

            status = Status(p.jobId, "selesai", 100, false, hasil.frame, hasil.frame, false, hasil.path)

            // Cuplikan untuk Riwayat/Beranda: satu bingkai dari hasil akhir.
            val previewPath = runCatching {
                VideoProbe.potongan(ctx, Uri.fromFile(File(hasil.path)), 720)?.let { bmp ->
                    val jalur = ImagePipeline.writePreview(ctx, bmp, 720)
                    bmp.recycle()
                    jalur
                }
            }.getOrNull().orEmpty()

            val data = JSObject()
                .put("jobId", p.jobId)
                .put("kind", "video")
                .put("path", hasil.path)
                .put("previewPath", previewPath)
                .put("srcWidth", metaSumber?.lebar ?: 0)
                .put("srcHeight", metaSumber?.tinggi ?: 0)
                .put("width", hasil.lebar)
                .put("height", hasil.tinggi)
                .put("frames", hasil.frame)
                .put("bytes", hasil.bytes)
                .put("durationMs", hasil.durasiMs)
                .put("ms", hasil.ms)
            kirim("videoDone", data)

            Riwayat.tambahVideo(ctx, data)

            val judul = "Video selesai diperjelas"
            val teks = "Ketuk untuk melihat hasil ${hasil.lebar}×${hasil.tinggi}."
            EnhancerService.selesai(ctx, judul, teks)
        } catch (t: Throwable) {
            val dibatalkan = batal.get() || t is InterruptedException
            runCatching { keluar.delete() }

            // Catatan kemajuan tetap disimpan supaya bisa dilanjutkan.
            val sisa = catatan ?: VideoState.baca(ctx)
            val bisaLanjut = sisa != null && !sisa.selesai
            val persen = sisa?.persen ?: 0
            status = Status(
                jobId = p.jobId,
                stage = if (dibatalkan) "terhenti" else "gagal",
                percent = persen,
                aktif = false,
                frame = sisa?.frameTerakhir ?: frameKini,
                totalFrame = sisa?.totalFrame ?: totalFrame,
                bisaLanjut = bisaLanjut,
            )
            Log.w(TAG, "Job video ${p.jobId} berhenti di ${persen}%: ${t.message}")
            kirim(
                "videoFailed",
                JSObject()
                    .put("jobId", p.jobId)
                    .put("kind", "video")
                    .put("code", if (dibatalkan) "E_DIBATALKAN" else "E_VIDEO_ENCODE")
                    .put("message", t.message ?: "Proses video berhenti")
                    .put("percent", persen)
                    .put("frame", sisa?.frameTerakhir ?: frameKini)
                    .put("totalFrame", sisa?.totalFrame ?: totalFrame)
                    .put("bisaLanjut", bisaLanjut),
            )
            if (dibatalkan) {
                ProsesNotif.selesai(
                    ctx,
                    "Ekstrak video dijeda",
                    "Terhenti di $persen%. Buka Arulume untuk melanjutkan.",
                )
            } else {
                EnhancerService.gagal(ctx, "Proses video berhenti", t.message ?: "Terjadi kesalahan")
            }
        } finally {
            jobAktif = null
        }
    }

    private fun kirim(nama: String, data: JSObject) {
        pendengar.forEach { l -> runCatching { l(nama, data) } }
    }
}
