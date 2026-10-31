package id.aruthtale.arulume

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Catatan kemajuan job video supaya pekerjaan yang terhenti (aplikasi ditutup,
 * proses dibunuh MIUI, HP restart) bisa **dilanjutkan**, bukan diulang dari nol.
 *
 * Isinya hanya parameter job + nomor frame terakhir + daftar segmen yang sudah
 * jadi. Ditulis ke `filesDir/video-job.json` setiap satu segmen selesai
 * (default tiap 2–3 detik video), jadi kehilangan maksimum satu segmen.
 *
 * Tidak ada token/sandi di sini — hanya path berkas milik aplikasi sendiri.
 */
object VideoState {

    private const val TAG = "Arulume/VideoState"
    private const val NAMA = "video-job.json"

    /** Satu potongan hasil encode; `frame` = jumlah frame di dalamnya. */
    data class Seg(val nama: String, val frame: Int)

    data class State(
        val jobId: String,
        val uri: String,
        val target: Int,
        val bitrate: Int,
        val tajam: Boolean,
        val outW: Int,
        val outH: Int,
        val fps: Int,
        val totalFrame: Int,
        val frameTerakhir: Int,
        val segmen: List<Seg>,
        val mulaiMs: Long,
    ) {
        val persen: Int
            get() = if (totalFrame > 0) (frameTerakhir * 100 / totalFrame).coerceIn(0, 100) else 0

        val selesai: Boolean get() = totalFrame > 0 && frameTerakhir >= totalFrame
    }

    private fun berkas(ctx: Context) = File(ctx.filesDir, NAMA)

    fun baca(ctx: Context): State? {
        val f = berkas(ctx)
        if (!f.exists()) return null
        return try {
            val o = JSONObject(f.readText())
            val arr = o.optJSONArray("segmen") ?: JSONArray()
            val seg = ArrayList<Seg>(arr.length())
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                seg.add(Seg(s.getString("nama"), s.optInt("frame")))
            }
            State(
                jobId = o.getString("jobId"),
                uri = o.getString("uri"),
                target = o.optInt("target", 1280),
                bitrate = o.optInt("bitrate", 0),
                tajam = o.optBoolean("tajam", true),
                outW = o.optInt("outW", 0),
                outH = o.optInt("outH", 0),
                fps = o.optInt("fps", 30),
                totalFrame = o.optInt("totalFrame", 0),
                frameTerakhir = o.optInt("frameTerakhir", 0),
                segmen = seg,
                mulaiMs = o.optLong("mulaiMs", System.currentTimeMillis()),
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Catatan job video rusak, diabaikan: ${t.message}")
            null
        }
    }

    fun tulis(ctx: Context, s: State) {
        try {
            val arr = JSONArray()
            s.segmen.forEach { seg ->
                arr.put(JSONObject().put("nama", seg.nama).put("frame", seg.frame))
            }
            val o = JSONObject()
                .put("jobId", s.jobId)
                .put("uri", s.uri)
                .put("target", s.target)
                .put("bitrate", s.bitrate)
                .put("tajam", s.tajam)
                .put("outW", s.outW)
                .put("outH", s.outH)
                .put("fps", s.fps)
                .put("totalFrame", s.totalFrame)
                .put("frameTerakhir", s.frameTerakhir)
                .put("segmen", arr)
                .put("mulaiMs", s.mulaiMs)
            val f = berkas(ctx)
            val tmp = File(f.parentFile, "$NAMA.tmp")
            tmp.writeText(o.toString())
            if (!tmp.renameTo(f)) {
                f.writeText(o.toString())
                tmp.delete()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Gagal menyimpan catatan job video: ${t.message}")
        }
    }

    fun hapus(ctx: Context) {
        runCatching { berkas(ctx).delete() }
    }

    /** Segmen yang masih ada di disk (nama saja disimpan di catatan). */
    fun berkasSegmen(ctx: Context, s: State): List<File> =
        s.segmen.map { File(dirSegmen(ctx), it.nama) }.filter { it.exists() }

    fun dirSegmen(ctx: Context): File =
        File(ctx.cacheDir, "segmen-video").apply { mkdirs() }

    /** Buang segmen yatim (job lama yang catatannya sudah hilang). */
    fun bersihkanSisa(ctx: Context, aktif: State?) {
        val dipakai = aktif?.segmen?.map { it.nama }?.toSet() ?: emptySet()
        val dir = dirSegmen(ctx)
        dir.listFiles()?.forEach { f ->
            if (f.name !in dipakai && f.name.startsWith("seg-")) {
                runCatching { f.delete() }
            }
        }
    }
}
