package id.aruthtale.arulume

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Riwayat pekerjaan (PRD FR-08).
 *
 * Disimpan sebagai satu berkas JSON di `filesDir/riwayat.json` — tanpa database,
 * tanpa jaringan. Isinya rujukan ke berkas hasil di cache (`cacheDir/hasil/`),
 * jadi entri bisa saja kehilangan berkasnya saat cache dipangkas; UI wajib
 * memeriksa `hasResult` sebelum membuka.
 *
 * Aturan: menghapus entri TIDAK menghapus foto di galeri (PRD FR-08).
 */
object Riwayat {
    private const val FILE_NAME = "riwayat.json"
    private const val MAX_ENTRIES = 60

    private fun file(ctx: Context) = File(ctx.filesDir, FILE_NAME)

    @Synchronized
    fun load(ctx: Context): List<JSONObject> {
        val f = file(ctx)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        } catch (t: Throwable) {
            // Berkas rusak jangan sampai mematikan aplikasi: mulai dari kosong.
            emptyList()
        }
    }

    @Synchronized
    private fun simpan(ctx: Context, items: List<JSONObject>) {
        try {
            val arr = JSONArray()
            items.forEach { arr.put(it) }
            file(ctx).writeText(arr.toString())
        } catch (t: Throwable) {
            // Gagal menulis riwayat tidak boleh menggagalkan pemrosesan foto.
        }
    }

    /** Tambah entri baru di depan, buang duplikat path, dan pangkas jumlahnya. */
    @Synchronized
    fun add(ctx: Context, entri: JSONObject) {
        val path = entri.optString("resultPath")
        val lama = load(ctx).filter { it.optString("resultPath") != path }
        simpan(ctx, (listOf(entri) + lama).take(MAX_ENTRIES))
    }

    /**
     * Entri riwayat untuk hasil video (Fase 2). Bentuknya sama dengan entri foto
     * plus `kind = "video"`, supaya layar Riwayat bisa membedakan keduanya tanpa
     * berkas riwayat terpisah.
     */
    @Synchronized
    fun tambahVideo(ctx: Context, data: com.getcapacitor.JSObject) {
        try {
            val entri = JSONObject()
                .put("id", data.optString("jobId"))
                .put("kind", "video")
                .put("createdAt", System.currentTimeMillis())
                .put("model", "video")
                .put("scale", 1)
                .put("srcWidth", data.optInt("srcWidth"))
                .put("srcHeight", data.optInt("srcHeight"))
                .put("outWidth", data.optInt("width"))
                .put("outHeight", data.optInt("height"))
                .put("durationMs", data.optLong("ms"))
                .put("videoDurationMs", data.optLong("durationMs"))
                .put("frames", data.optInt("frames"))
                .put("resultPath", data.optString("path"))
                .put("beforePreviewPath", data.optString("previewPath"))
                .put("afterPreviewPath", data.optString("previewPath"))
                .put("savedUri", "")
                .put("savedName", "")
            add(ctx, entri)
        } catch (t: Throwable) {
            Log.w("Arulume/Riwayat", "Gagal menambah riwayat video: ${t.message}")
        }
    }

    /** Tandai entri dengan `resultPath` ini sudah disimpan ke galeri. */
    @Synchronized
    fun tandaiTersimpan(ctx: Context, resultPath: String, uri: String, nama: String) {
        val items = load(ctx)
        // Path bisa datang sebagai /data/data/... atau /data/user/0/... untuk
        // berkas yang sama, jadi bandingkan nama berkasnya (nama hasil selalu
        // unik: Arulume_yyyyMMdd-HHmmss.jpg).
        val nama = File(resultPath).name
        var berubah = false
        items.forEach { e ->
            if (File(e.optString("resultPath")).name == nama) {
                e.put("savedUri", uri)
                e.put("savedName", nama)
                berubah = true
            }
        }
        if (berubah) simpan(ctx, items)
    }

    @Synchronized
    fun hapus(ctx: Context, id: String): Boolean {
        val items = load(ctx)
        val sisa = items.filter { it.optString("id") != id }
        if (sisa.size == items.size) return false
        simpan(ctx, sisa)
        return true
    }

    @Synchronized
    fun kosongkan(ctx: Context) {
        simpan(ctx, emptyList())
    }
}
