package id.aruthtale.arulume

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMuxer
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import java.io.File

/**
 * Pipeline video Fase 2: dekode → skala ke resolusi HD → penajaman → encode H.264
 * → penyambungan potongan + audio asli.
 *
 * Dua hal penting di sini:
 *
 * 1. **Klasik, bukan AI.** Model Real-ESRGAN butuh ~1,9 detik per petak 320×184
 *    di HP ini, artinya klip 5 detik bisa 1,5 jam. Jalur skala + penajam berjalan
 *    jauh lebih cepat dan sudah cukup untuk tujuan "tidak pecah di WA/IG".
 * 2. **Dipotong per beberapa detik** (lihat [VideoState]). MP4 baru punya indeks
 *    saat ditutup, jadi kalau proses mati di tengah, berkas tunggal akan rusak
 *    dan harus diulang dari nol. Dengan potongan, pekerjaan bisa dilanjutkan
 *    dari frame terakhir yang tercatat.
 */
object VideoJob {

    private const val TAG = "Arulume/VideoJob"

    data class Opsi(
        /** Sisi panjang keluaran, mis. 1280 (720p) atau 1920 (1080p). */
        val targetSisi: Int = 1280,
        /** Bitrate keluaran; 0 = hitung otomatis dari resolusi & fps. */
        val bitrate: Int = 0,
        /** Penajaman ringan setelah skala. */
        val tajam: Boolean = true,
        /** Diagnosa: simpan frame pertama yang sudah diskala ke berkas PNG. */
        val dumpFrame: Boolean = false,
    )

    data class Hasil(
        val path: String,
        val lebar: Int,
        val tinggi: Int,
        val durasiMs: Long,
        val frame: Int,
        val bytes: Long,
        val ms: Long,
        val fpsProses: Double,
    )

    /** Jumlah frame per potongan: makin besar resolusi, makin pendek potongan. */
    fun framePerSegmen(targetSisi: Int): Int = if (targetSisi >= 1800) 45 else 90

    /** Rasio bitrate untuk video HD yang tahan kompresi ulang WhatsApp/Instagram. */
    private fun bitrateOtomatis(w: Int, h: Int, fps: Int): Int {
        val bpp = 0.14 // ~7,8 Mbps untuk 1080p30, ~3,5 Mbps untuk 720p30
        val hitung = (w.toLong() * h * fps * bpp).toInt()
        return hitung.coerceIn(3_000_000, 12_000_000)
    }

    /**
     * Jalankan (atau lanjutkan) pemrosesan video.
     *
     * @param awal catatan job sebelumnya bila melanjutkan; `null` = mulai baru.
     * @param simpan dipanggil setiap potongan selesai, untuk menyimpan kemajuan.
     */
    fun proses(
        ctx: Context,
        uri: Uri,
        keluar: File,
        opsi: Opsi = Opsi(),
        awal: VideoState.State? = null,
        simpan: (VideoState.State) -> Unit = {},
        progres: (frame: Int, total: Int) -> Unit = { _, _ -> },
        dibatalkan: () -> Boolean = { false },
    ): Hasil {
        val mulai = SystemClock.elapsedRealtime()
        val stream = VideoProbe.FrameStream(ctx, uri)
        val info = stream.info

        val putar = info.rotation % 180 != 0
        val srcW = if (putar) info.height else info.width
        val srcH = if (putar) info.width else info.height

        // Sisi panjang keluaran tidak boleh lebih kecil dari sumber.
        val sisiTarget = maxOf(opsi.targetSisi, maxOf(srcW, srcH))
        val skala = sisiTarget.toFloat() / maxOf(srcW, srcH)
        val outW = if (awal != null && awal.outW > 0) awal.outW else ((srcW * skala).toInt() / 2) * 2
        val outH = if (awal != null && awal.outH > 0) awal.outH else ((srcH * skala).toInt() / 2) * 2

        val fps = if (awal != null && awal.fps > 0) awal.fps else info.fps.toInt().coerceIn(12, 60)
        val bitrate = if (opsi.bitrate > 0) opsi.bitrate else bitrateOtomatis(outW, outH, fps)
        val totalFrame = if (awal != null && awal.totalFrame > 0) awal.totalFrame else maxOf(1, info.totalFrame)
        val langkahUs = 1_000_000L / fps
        val perSegmen = framePerSegmen(sisiTarget)

        var catatan = awal ?: VideoState.State(
            jobId = "vid-${System.currentTimeMillis()}",
            uri = uri.toString(),
            target = opsi.targetSisi,
            bitrate = opsi.bitrate,
            tajam = opsi.tajam,
            outW = outW,
            outH = outH,
            fps = fps,
            totalFrame = totalFrame,
            frameTerakhir = 0,
            segmen = emptyList(),
            mulaiMs = System.currentTimeMillis(),
        )
        // Pastikan dimensi/fps tersimpan sejak awal, supaya resume memakai nilai sama.
        if (awal == null) simpan(catatan)

        val mulaiFrame = catatan.frameTerakhir
        val segmen = catatan.segmen.toMutableList()

        keluar.parentFile?.mkdirs()
        if (keluar.exists()) keluar.delete()

        var muxer: MediaMuxer? = null
        var encoder: VideoEncoder? = null
        var frameSegmenAwal = mulaiFrame
        var frameTerakhir = mulaiFrame
        var frameSelesai = mulaiFrame

        fun tutupSegmen() {
            val enc = encoder ?: return
            runCatching { enc.tutup() }
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            encoder = null
            muxer = null
        }

        try {
            if (mulaiFrame > 0) stream.lompatKe(mulaiFrame * langkahUs)
            var tDekode = 0L
            var tSkala = 0L
            var tEncode = 0L
            var n = 0

            while (true) {
                if (dibatalkan()) throw InterruptedException("Dibatalkan")
                val t0 = SystemClock.elapsedRealtime()
                val bmp = stream.berikutnya() ?: break
                val t1 = SystemClock.elapsedRealtime()

                val idx = Math.round(stream.ptsUs.toDouble() / langkahUs).toInt()
                if (idx < mulaiFrame) {
                    bmp.recycle()
                    continue
                }

                if (encoder == null) {
                    frameSegmenAwal = idx
                    val nama = "seg-${catatan.jobId}-%04d.mp4".format(segmen.size)
                    val berkasSeg = File(VideoState.dirSegmen(ctx), nama)
                    berkasSeg.parentFile?.mkdirs()
                    val mx = MediaMuxer(berkasSeg.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                    muxer = mx
                    encoder = VideoEncoder(outW, outH, fps, bitrate, mx)
                }

                val ukuran = siapkanFrame(bmp, outW, outH, opsi.tajam)
                val t2 = SystemClock.elapsedRealtime()
                if (opsi.dumpFrame && n == 0) {
                    val bmpUji = Bitmap.createBitmap(ukuran, outW, outH, Bitmap.Config.ARGB_8888)
                    File(keluar.parentFile, "job-frame0.png").outputStream().use {
                        bmpUji.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bmpUji.recycle()
                }
                bmp.recycle()

                encoder!!.tulis(ukuran, (idx - frameSegmenAwal) * langkahUs)
                val t3 = SystemClock.elapsedRealtime()
                tDekode += t1 - t0
                tSkala += t2 - t1
                tEncode += t3 - t2
                n++
                frameTerakhir = idx + 1

                // Potongan penuh → tutup dan catat kemajuan.
                if (frameTerakhir - frameSegmenAwal >= perSegmen) {
                    val nama = "seg-${catatan.jobId}-%04d.mp4".format(segmen.size)
                    tutupSegmen()
                    segmen.add(VideoState.Seg(nama, frameTerakhir - frameSegmenAwal))
                    frameSelesai = frameTerakhir
                    catatan = catatan.copy(frameTerakhir = frameSelesai, segmen = segmen.toList())
                    simpan(catatan)
                }

                progres(frameTerakhir, totalFrame)
            }

            if (n > 0) {
                val nama = "seg-${catatan.jobId}-%04d.mp4".format(segmen.size)
                tutupSegmen()
                segmen.add(VideoState.Seg(nama, frameTerakhir - frameSegmenAwal))
                frameSelesai = frameTerakhir
            }
            if (segmen.isEmpty()) throw IllegalStateException("Tidak ada frame yang bisa diproses")

            catatan = catatan.copy(frameTerakhir = maxOf(frameSelesai, frameTerakhir), segmen = segmen.toList())
            simpan(catatan)

            Log.i(
                TAG,
                "Rincian: dekode=${if (n > 0) tDekode / n else 0}ms skala=${if (n > 0) tSkala / n else 0}ms " +
                    "encode=${if (n > 0) tEncode / n else 0}ms per frame, ${segmen.size} potongan",
            )

            // Sambungkan potongan + audio asli.
            val berkasSegmen = VideoState.berkasSegmen(ctx, catatan)
            if (!VideoGabung.gabung(ctx, berkasSegmen, uri, keluar, fps)) {
                throw IllegalStateException("Gagal menyambung potongan video")
            }
        } catch (t: Throwable) {
            runCatching { encoder?.tutup() }
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { stream.close() }
            // Sengaja TIDAK menghapus catatan/segmen: biar bisa dilanjutkan.
            throw t
        }

        runCatching { stream.close() }

        // Bersihkan potongan & catatan setelah berhasil.
        segmen.forEach { s -> runCatching { File(VideoState.dirSegmen(ctx), s.nama).delete() } }
        VideoState.hapus(ctx)

        val ms = SystemClock.elapsedRealtime() - mulai
        Log.i(TAG, "Selesai: ${outW}x$outH, ${catatan.frameTerakhir} frame, ${keluar.length() / 1024} KB, ${ms}ms")
        return Hasil(
            path = keluar.absolutePath,
            lebar = outW,
            tinggi = outH,
            durasiMs = catatan.frameTerakhir * 1000L / fps,
            frame = catatan.frameTerakhir,
            bytes = keluar.length(),
            ms = ms,
            fpsProses = if (ms > 0) catatan.frameTerakhir * 1000.0 / ms else 0.0,
        )
    }

    /**
     * Penajaman dilakukan di resolusi **sumber**, bukan setelah diskala: hasil
     * visual hampir sama, biayanya seperempat (jumlah piksel naik kuadrat),
     * dan halo di tepi justru lebih kecil.
     */
    private fun siapkanFrame(src: Bitmap, w: Int, h: Int, tajam: Boolean): IntArray {
        val sw = src.width
        val sh = src.height
        val px = IntArray(sw * sh)
        src.getPixels(px, 0, sw, 0, 0, sw, sh)
        if (tajam) VideoProbe.tajamkan(px, sw, sh)

        val bmp = Bitmap.createBitmap(px, sw, sh, Bitmap.Config.ARGB_8888)
        val hasil = if (sw == w && sh == h) {
            bmp
        } else {
            Bitmap.createScaledBitmap(bmp, w, h, true).also { bmp.recycle() }
        }
        val out = IntArray(w * h)
        hasil.getPixels(out, 0, w, 0, 0, w, h)
        hasil.recycle()
        return out
    }
}
