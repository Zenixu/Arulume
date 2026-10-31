package id.aruthtale.arulume

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Pemeriksa & pengunduh pembaruan dari halaman rilis resmi (Docs/AGENTS.md #1,
 * pengecualian 2026-10-31).
 *
 * Batas yang disengaja:
 * - Hanya membaca `api.github.com/repos/Zenixu/Arulume/releases/latest`.
 * - Hanya mengunduh berkas `.apk` dari rilis itu, disimpan di cache aplikasi.
 * - Tidak ada data pengguna yang dikirim: permintaan hanya GET tanpa body.
 * - Tidak memakai pustaka jaringan apa pun; cukup `HttpURLConnection`.
 *
 * Semua fungsi di sini **memblokir** — panggil dari thread pekerja, jangan dari
 * thread UI atau thread plugin (Docs/AGENTS.md #3).
 */
object Pembaruan {

    private const val TAG = "Arulume/Pembaruan"
    private const val API = "https://api.github.com/repos/Zenixu/Arulume/releases/latest"
    private const val AGEN = "Arulume-Android"

    /** Ringkasan satu rilis. */
    data class Info(
        val versi: String,
        val catatan: String,
        val namaBerkas: String,
        val ukuran: Long,
        val urlUnduh: String,
    )

    /** Hasil pemeriksaan: versi terpasang vs versi terbaru di GitHub. */
    data class Hasil(
        val versiSekarang: String,
        val info: Info?,
        val adaBaru: Boolean,
        val galat: String? = null,
    )

    /** Versi aplikasi yang terpasang, mis. "0.1.0". */
    fun versiTerpasang(ctx: Context): String = try {
        @Suppress("DEPRECATION")
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"
    } catch (t: Throwable) {
        "0"
    }

    /** Membaca rilis terbaru dari GitHub. Mengembalikan galat sebagai teks, bukan exception. */
    fun cek(ctx: Context): Hasil {
        val sekarang = versiTerpasang(ctx)
        return try {
            val isi = get(API)
            val o = JSONObject(isi)
            val tag = o.optString("tag_name", "").trim()
            val catatan = o.optString("body", "").trim()
            val aset = o.optJSONArray("assets")
            var nama = ""
            var ukuran = 0L
            var url = ""
            if (aset != null) {
                for (i in 0 until aset.length()) {
                    val a = aset.getJSONObject(i)
                    val n = a.optString("name", "")
                    if (n.endsWith(".apk", ignoreCase = true)) {
                        nama = n
                        ukuran = a.optLong("size", 0L)
                        url = a.optString("browser_download_url", "")
                        break
                    }
                }
            }
            if (tag.isEmpty()) {
                return Hasil(sekarang, null, false, "Rilis terbaru tidak punya nomor versi.")
            }
            if (url.isEmpty()) {
                return Hasil(sekarang, null, false, "Rilis $tag belum menyertakan berkas APK.")
            }
            val info = Info(tag, catatan, nama, ukuran, url)
            Hasil(sekarang, info, lebihBaru(tag, sekarang))
        } catch (t: Throwable) {
            Log.w(TAG, "Cek pembaruan gagal: ${t.message}")
            Hasil(sekarang, null, false, pesanRamah(t))
        }
    }

    /**
     * Mengunduh APK rilis ke cache. [kemajuan] dipanggil dengan persen 0..100.
     * Berkas lama dihapus lebih dulu supaya tidak menumpuk.
     */
    fun unduh(ctx: Context, info: Info, kemajuan: (Int) -> Unit): File {
        val folder = File(ctx.cacheDir, "pembaruan").apply { mkdirs() }
        folder.listFiles()?.forEach { it.delete() }
        val tujuan = File(folder, info.namaBerkas.ifEmpty { "Arulume.apk" })
        val sambungan = (URL(info.urlUnduh).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", AGEN)
            setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            val kode = sambungan.responseCode
            if (kode !in 200..299) {
                throw IllegalStateException("Server menjawab $kode.")
            }
            val total = sambungan.contentLengthLong.takeIf { it > 0 } ?: info.ukuran
            var terunduh = 0L
            var terakhir = -1
            sambungan.inputStream.use { masuk ->
                FileOutputStream(tujuan).use { keluar ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = masuk.read(buf)
                        if (n <= 0) break
                        keluar.write(buf, 0, n)
                        terunduh += n
                        if (total > 0) {
                            val persen = ((terunduh * 100) / total).toInt().coerceIn(0, 100)
                            if (persen != terakhir) {
                                terakhir = persen
                                kemajuan(persen)
                            }
                        }
                    }
                }
            }
            if (tujuan.length() < 1024) {
                tujuan.delete()
                throw IllegalStateException("Berkas unduhan tidak utuh.")
            }
            kemajuan(100)
            return tujuan
        } finally {
            sambungan.disconnect()
        }
    }

    /**
     * Membuka pemasang Android untuk APK yang sudah diunduh. Bila pengguna belum
     * mengizinkan pemasangan dari Arulume, layar pengaturannya dibuka lebih dulu.
     */
    fun pasang(ctx: Context, apk: File) {
        val uri: Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !ctx.packageManager.canRequestPackageInstalls()
        ) {
            // Belum diizinkan: buka saklar "Instal aplikasi tak dikenal" dulu.
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    /** Membersihkan APK unduhan lama. */
    fun bersihkan(ctx: Context) {
        try {
            File(ctx.cacheDir, "pembaruan").listFiles()?.forEach { it.delete() }
        } catch (t: Throwable) {
            Log.w(TAG, "Bersihkan unduhan gagal: ${t.message}")
        }
    }

    // ---- internal ----

    private fun get(url: String): String {
        val sambungan = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", AGEN)
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            val kode = sambungan.responseCode
            if (kode == 404) throw IllegalStateException("Rilis belum tersedia.")
            if (kode !in 200..299) throw IllegalStateException("Server menjawab $kode.")
            return sambungan.inputStream.bufferedReader().use { it.readText() }
        } finally {
            sambungan.disconnect()
        }
    }

    private fun pesanRamah(t: Throwable): String = when {
        t is java.net.UnknownHostException -> "Tidak ada koneksi internet."
        t is java.net.SocketTimeoutException -> "Koneksi terlalu lambat. Coba lagi."
        else -> t.message ?: "Tidak bisa memeriksa pembaruan."
    }

    /** True bila [tagRilis] ("v0.1.1") lebih tinggi dari [terpasang] ("0.1.0"). */
    fun lebihBaru(tagRilis: String, terpasang: String): Boolean {
        val a = pecah(tagRilis)
        val b = pecah(terpasang)
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun pecah(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V")
            .split('.', '-', '+')
            .map { bagian -> bagian.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
}
