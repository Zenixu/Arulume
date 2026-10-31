package id.aruthtale.arulume

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Menyimpan hasil video ke galeri `Movies/Arulume` — hanya saat user menekan
 * Simpan (PRD FR-06/FR-26). Berkas asli pengguna tidak pernah disentuh.
 */
object VideoSaver {

    private const val TAG = "Arulume/VideoSaver"

    data class Saved(val uri: Uri, val nama: String)

    private fun namaBaru(): String =
        "Arulume_${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.mp4"

    fun simpan(ctx: Context, sumber: File): Saved? {
        if (!sumber.exists() || sumber.length() <= 0L) return null
        val nama = namaBaru()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                simpanScoped(ctx, sumber, nama)
            } else {
                simpanLama(ctx, sumber, nama)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Simpan video ke galeri gagal", t)
            null
        }
    }

    private fun simpanScoped(ctx: Context, sumber: File, nama: String): Saved? {
        val resolver = ctx.contentResolver
        val nilai = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, nama)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(
                MediaStore.Video.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_MOVIES}/Arulume",
            )
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val koleksi = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(koleksi, nilai) ?: return null
        resolver.openOutputStream(uri)?.use { keluar ->
            sumber.inputStream().use { masuk -> masuk.copyTo(keluar, 256 * 1024) }
        } ?: throw IllegalStateException("stream kosong")

        nilai.clear()
        nilai.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, nilai, null, null)
        return Saved(uri, nama)
    }

    @Suppress("DEPRECATION")
    private fun simpanLama(ctx: Context, sumber: File, nama: String): Saved? {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "Arulume",
        ).apply { mkdirs() }
        val tujuan = File(dir, nama)
        FileOutputStream(tujuan).use { keluar ->
            sumber.inputStream().use { masuk -> masuk.copyTo(keluar, 256 * 1024) }
        }
        MediaScannerConnection.scanFile(ctx, arrayOf(tujuan.absolutePath), arrayOf("video/mp4"), null)
        return Saved(Uri.fromFile(tujuan), nama)
    }
}
