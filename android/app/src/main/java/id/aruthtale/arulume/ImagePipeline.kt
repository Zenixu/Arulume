package id.aruthtale.arulume

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Baca/tulis gambar tanpa menyentuh berkas asli pengguna.
 *
 * Aturan yang dijaga di sini:
 * - `Docs/AGENTS.md` #4: tidak pernah menulis ke berkas sumber; semua hasil
 *   ditulis ke berkas baru di `Pictures/Arulume`.
 * - Orientasi EXIF diterapkan supaya foto kamera tidak miring.
 * - Dekode selalu memakai `inSampleSize` agar memori tidak meledak
 *   (lihat temuan benchmark: 1024² tanpa pemotongan sudah mematikan proses).
 */
object ImagePipeline {

    private const val TAG = "Arulume/Image"

    /** Batas sisi terpanjang yang aman di perangkat 3,5 GB (benchmark §5). */
    const val MAX_OUTPUT_SIDE = 2048

    data class Picked(
        val uri: Uri,
        val name: String,
        val mime: String,
        val sizeBytes: Long,
        val width: Int,
        val height: Int,
        val previewPath: String,
    )

    data class Saved(
        val uri: Uri,
        val width: Int,
        val height: Int,
    )

    fun queryMeta(ctx: Context, uri: Uri): Triple<String, String, Long> {
        var name = "foto"
        var size = 0L
        try {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni)
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Gagal membaca metadata: ${t.message}")
        }
        val mime = ctx.contentResolver.getType(uri) ?: "image/*"
        return Triple(name, mime, size)
    }

    /** Dimensi asli tanpa mendekode seluruh piksel. */
    fun bounds(ctx: Context, uri: Uri): Pair<Int, Int> {
        val opt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opt) }
        return opt.outWidth to opt.outHeight
    }

    fun decode(ctx: Context, uri: Uri, maxSide: Int): Bitmap? {
        // Berkas terpotong (salinan belum selesai / kartu rusak) tetap "berhasil"
        // didekode oleh BitmapFactory dengan sisa area abu-abu. Periksa penutup
        // berkas lebih dulu supaya user dapat pesan jelas, bukan hasil separuh.
        if (!berkasUtuh(ctx, uri)) {
            throw java.io.IOException("Foto sepertinya rusak atau belum selesai disalin")
        }
        val probe = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, probe) }
        val longest = maxOf(probe.outWidth, probe.outHeight)
        if (longest <= 0) return null

        var sample = 1
        // Pilih pangkat dua TERBESAR yang masih menyisakan sisi terpanjang >= maxSide;
        // syarat lama (`> maxSide`) membuat hasil bisa menyusut sampai seperempat
        // (mis. 540x1170 dengan batas 512 turun ke 135x293).
        while (longest / (sample * 2) >= maxSide) sample *= 2

        val opt = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opt)
        } ?: return null

        val rotated = applyExifRotation(ctx, uri, raw)

        // `inSampleSize` hanya pangkat dua, jadi sisi terpanjang masih bisa jauh di
        // bawah batas. Perkecil tepat ke `maxSide` supaya hasil akhir tidak ikut
        // menyusut (mis. 1080x1920 → 540x960 hanya dapat 1080x1920 lagi).
        val longestNow = maxOf(rotated.width, rotated.height)
        if (longestNow <= maxSide) return rotated
        val k = maxSide.toFloat() / longestNow
        val w = (rotated.width * k).toInt().coerceAtLeast(1)
        val h = (rotated.height * k).toInt().coerceAtLeast(1)
        val exact = Bitmap.createScaledBitmap(rotated, w, h, true)
        if (exact !== rotated) rotated.recycle()
        return exact
    }

    private fun applyExifRotation(ctx: Context, uri: Uri, bmp: Bitmap): Bitmap {
        val orientation = try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (t: Throwable) {
            Log.w(TAG, "EXIF tidak terbaca: ${t.message}")
            ExifInterface.ORIENTATION_NORMAL
        }

        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            else -> return bmp
        }
        val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        if (out !== bmp) bmp.recycle()
        return out
    }

    /** ARGB_8888 → array int, format yang dipakai JNI. */
    fun toArgb(bmp: Bitmap): IntArray {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        return px
    }

    fun fromArgb(px: IntArray, w: Int, h: Int): Bitmap =
        Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)

    /** Pratinjau untuk WebView; ditulis ke cache, bukan ke galeri. */
    fun writePreview(ctx: Context, bmp: Bitmap, maxSide: Int, quality: Int = 85): String {
        val longest = maxOf(bmp.width, bmp.height)
        val scaled = if (longest > maxSide) {
            val k = maxSide.toFloat() / longest
            Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * k).toInt().coerceAtLeast(1),
                (bmp.height * k).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            bmp
        }
        val dir = File(ctx.cacheDir, "enhancer").apply { mkdirs() }
        val f = File(dir, "pratinjau-${System.currentTimeMillis()}.jpg")
        FileOutputStream(f).use { scaled.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        if (scaled !== bmp) scaled.recycle()
        return f.absolutePath
    }

    /**
     * Format keluaran yang didukung. `quality` hanya berpengaruh pada JPEG/WebP.
     * Catatan jujur: engine native bekerja di RGB, jadi kanal alpha dari PNG/WebP
     * sumber diratakan ke putih sebelum diproses (lihat [ratakanAlpha]).
     */
    enum class OutFormat(
        val ext: String,
        val mime: String,
        val compress: Bitmap.CompressFormat,
    ) {
        JPEG("jpg", "image/jpeg", Bitmap.CompressFormat.JPEG),
        PNG("png", "image/png", Bitmap.CompressFormat.PNG),
        WEBP("webp", "image/webp", Bitmap.CompressFormat.WEBP);

        companion object {
            fun dari(nama: String?): OutFormat = when (nama?.lowercase(Locale.US)) {
                "png" -> PNG
                "webp" -> WEBP
                else -> JPEG
            }
        }
    }

    /**
     * Tempelkan gambar transparan di atas latar putih. Tanpa ini, area transparan
     * jadi hitam karena engine native memakai 3 kanal.
     */
    fun ratakanAlpha(bmp: Bitmap): Bitmap {
        if (!bmp.hasAlpha()) return bmp
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.WHITE)
        c.drawBitmap(bmp, 0f, 0f, null)
        // Sengaja tidak me-recycle `bmp`: pemanggil masih perlu bitmap sumber
        // untuk memasang kembali topeng alpha (lihat [pasangAlpha]).
        return out
    }

    /**
     * Kembalikan transparansi sumber ke hasil (TC-04). Engine native hanya RGB,
     * jadi alpha hilang saat inferensi; topeng diambil dari bitmap sumber lalu
     * diskalakan ke ukuran hasil. Hanya dipakai untuk PNG/WebP.
     */
    fun pasangAlpha(hasil: Bitmap, sumber: Bitmap): Bitmap {
        if (!sumber.hasAlpha()) return hasil
        val lebar = hasil.width
        val tinggi = hasil.height
        val skala = Bitmap.createScaledBitmap(sumber, lebar, tinggi, true)
        val px = toArgb(hasil)
        val mask = toArgb(skala)
        for (i in px.indices) {
            px[i] = (px[i] and 0x00FFFFFF) or (mask[i] and 0xFF000000.toInt())
        }
        if (skala != sumber) skala.recycle()
        return Bitmap.createBitmap(px, lebar, tinggi, Bitmap.Config.ARGB_8888)
    }

    private fun fileName(ext: String = "jpg"): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "Arulume_$stamp.$ext"
    }

    /**
     * Folder hasil sementara. Hasil TIDAK langsung masuk galeri (PRD FR-06 versi
     * 2026-10-31): user melihat dulu di layar Hasil, baru menekan Simpan.
     */
    fun resultsDir(ctx: Context): File = File(ctx.cacheDir, "hasil").apply { mkdirs() }

    /** Tulis hasil resolusi penuh ke cache; belum menyentuh galeri. */
    fun writeResult(
        ctx: Context,
        bmp: Bitmap,
        quality: Int,
        format: OutFormat = OutFormat.JPEG,
    ): File? = try {
        // Batch bisa menyelesaikan dua foto di detik yang sama; jangan menimpa
        // hasil sebelumnya — beri akhiran -2, -3, dst.
        var f = File(resultsDir(ctx), fileName(format.ext))
        var n = 1
        while (f.exists()) {
            n++
            f = File(resultsDir(ctx), fileName(format.ext).replace(".${format.ext}", "-$n.${format.ext}"))
        }
        // JPEG tidak punya kanal alpha: tempel ke putih dulu supaya area
        // transparan tidak jadi hitam.
        val siap = if (format == OutFormat.JPEG) ratakanAlpha(bmp.copy(Bitmap.Config.ARGB_8888, false)) else bmp
        FileOutputStream(f).use { siap.compress(format.compress, quality, it) }
        if (siap != bmp) siap.recycle()
        f
    } catch (t: Throwable) {
        Log.e(TAG, "Tulis hasil sementara gagal", t)
        null
    }

    /** Buang hasil lama supaya cache tidak menumpuk (sisakan `keep` terbaru). */
    fun pruneResults(ctx: Context, keep: Int = 3) {
        val files = resultsDir(ctx).listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(keep).forEach { runCatching { it.delete() } }
    }

    /**
     * Cek penanda akhir berkas: JPEG harus punya EOI (FF D9), PNG harus punya
     * chunk IEND. Selain itu dianggap utuh (mis. HEIC/WebP) karena tidak ada
     * penanda murah yang bisa diperiksa.
     *
     * Berkas dibaca penuh (maks 24 MB) karena `FileChannel.position()` pada
     * descriptor `content://` tidak selalu dihormati; foto normal jauh di bawah
     * batas itu dan biaya bacanya kecil dibanding inferensi.
     */
    private fun berkasUtuh(ctx: Context, uri: Uri): Boolean {
        val batas = 24 * 1024 * 1024
        val data = try {
            ctx.contentResolver.openInputStream(uri)?.use { ins ->
                val out = java.io.ByteArrayOutputStream(64 * 1024)
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val r = ins.read(buf)
                    if (r <= 0) break
                    out.write(buf, 0, r)
                    if (out.size() > batas) return true // terlalu besar: lewati pemeriksaan
                }
                out.toByteArray()
            } ?: return true
        } catch (t: Throwable) {
            Log.w(TAG, "Tidak bisa memeriksa berkas: ${t.message}")
            return true
        }
        if (data.size < 16) return true

        val jpeg = (data[0].toInt() and 0xFF) == 0xFF && (data[1].toInt() and 0xFF) == 0xD8
        val png = (data[0].toInt() and 0xFF) == 0x89 && data[1].toInt() == 0x50
        val awal = maxOf(0, data.size - 1024)
        fun ada(vararg pola: Int): Boolean {
            for (i in awal..(data.size - pola.size)) {
                var cocok = true
                for (j in pola.indices) {
                    if ((data[i + j].toInt() and 0xFF) != pola[j]) {
                        cocok = false
                        break
                    }
                }
                if (cocok) return true
            }
            return false
        }
        return when {
            jpeg -> ada(0xFF, 0xD9)
            png -> ada(0x49, 0x45, 0x4E, 0x44) // "IEND"
            else -> true
        }
    }

    fun decodeFile(path: String): Bitmap? = try {
        BitmapFactory.decodeFile(path)
    } catch (t: Throwable) {
        Log.e(TAG, "Baca berkas hasil gagal", t)
        null
    }

    /**
     * Simpan ke galeri `Pictures/Arulume`.
     * API 29+ memakai `IS_PENDING` supaya berkas tidak tampil setengah jadi.
     */
    fun saveToGallery(
        ctx: Context,
        bmp: Bitmap,
        quality: Int,
        format: OutFormat = OutFormat.JPEG,
    ): Saved? {
        val name = fileName(format.ext)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveScoped(ctx, bmp, name, quality, format)
            } else {
                saveLegacy(ctx, bmp, name, quality, format)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Simpan ke galeri gagal", t)
            null
        }
    }

    private fun saveScoped(
        ctx: Context,
        bmp: Bitmap,
        name: String,
        quality: Int,
        format: OutFormat,
    ): Saved? {
        val resolver = ctx.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, format.mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Arulume")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: return null
        resolver.openOutputStream(uri)?.use { out ->
            if (!bmp.compress(format.compress, quality, out)) {
                throw IllegalStateException("compress gagal")
            }
        } ?: throw IllegalStateException("stream kosong")

        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return Saved(uri, bmp.width, bmp.height)
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(
        ctx: Context,
        bmp: Bitmap,
        name: String,
        quality: Int,
        format: OutFormat,
    ): Saved? {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "Arulume",
        ).apply { mkdirs() }
        val f = File(dir, name)
        FileOutputStream(f).use { bmp.compress(format.compress, quality, it) }
        MediaScannerConnection.scanFile(ctx, arrayOf(f.absolutePath), arrayOf(format.mime), null)
        return Saved(Uri.fromFile(f), bmp.width, bmp.height)
    }
}
