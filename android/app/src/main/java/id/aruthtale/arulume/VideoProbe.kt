package id.aruthtale.arulume

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.Image
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.getcapacitor.JSObject

/**
 * Alat ukur untuk Fase 2 (video). **Bukan** pipeline produksi: tujuannya hanya
 * menjawab satu pertanyaan — berapa lama satu frame diproses di HP ini — supaya
 * batas durasi dan resolusi video ditentukan dari angka, bukan perkiraan.
 *
 * Yang diukur, per frame:
 * 1. decode H.264 (MediaCodec, mode ByteBuffer)
 * 2. YUV → ARGB (konversi manual, karena buffer dekoder tidak bisa dibaca langsung)
 * 3. skala klasik ke resolusi target
 * 4. penajaman 3×3 di Kotlin (untuk tahu apakah filter klasik cukup cepat)
 * 5. inferensi AI (ncnn) pada satu frame kecil, sebagai pembanding
 */
object VideoProbe {

    private const val TAG = "Arulume/Video"

    /** Metadata satu klip video. */
    data class Info(
        val width: Int,
        val height: Int,
        val fps: Float,
        val durationMs: Long,
        val mime: String,
        val rotation: Int,
        val hasAudio: Boolean,
        val bitrate: Int,
    ) {
        val totalFrame: Int get() = ((durationMs / 1000.0) * fps).toInt()
    }

    fun info(ctx: Context, uri: Uri): Info {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(ctx, uri, null)
            var video: MediaFormat? = null
            var audio = false
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") && video == null) video = f
                if (mime.startsWith("audio/")) audio = true
            }
            val f = video ?: throw IllegalArgumentException("Berkas ini tidak berisi video")

            val fps = if (f.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                f.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
            } else {
                30f
            }
            val durasiUs = if (f.containsKey(MediaFormat.KEY_DURATION)) f.getLong(MediaFormat.KEY_DURATION) else 0L
            val rotasi = try {
                if (f.containsKey(MediaFormat.KEY_ROTATION)) f.getInteger(MediaFormat.KEY_ROTATION) else 0
            } catch (t: Throwable) {
                0
            }
            val bitrate = try {
                if (f.containsKey(MediaFormat.KEY_BIT_RATE)) f.getInteger(MediaFormat.KEY_BIT_RATE) else 0
            } catch (t: Throwable) {
                0
            }
            return Info(
                width = f.getInteger(MediaFormat.KEY_WIDTH),
                height = f.getInteger(MediaFormat.KEY_HEIGHT),
                fps = if (fps > 0f) fps else 30f,
                durationMs = durasiUs / 1000,
                mime = f.getString(MediaFormat.KEY_MIME) ?: "video/avc",
                rotation = rotasi,
                hasAudio = audio,
                bitrate = bitrate,
            )
        } finally {
            ex.release()
        }
    }

    /**
     * Pengalir frame: satu dekoder `MediaCodec` mode ByteBuffer, frame dikonversi
     * ke Bitmap ARGB. Dipakai juga oleh pipeline produksi nanti.
     */
    class FrameStream(ctx: Context, uri: Uri) : AutoCloseable {

        val info: Info = VideoProbe.info(ctx, uri)

        private val extractor = MediaExtractor()
        private val codec: MediaCodec
        private val bufferInfo = MediaCodec.BufferInfo()
        private var inputSelesai = false
        private var keluaranSelesai = false
        private var rotasiDiterapkan = false

        /** Lama konversi YUV→ARGB pada frame terakhir, dalam milidetik. */
        var konversiMs: Long = 0
            private set

        /** PTS frame terakhir yang dikembalikan [berikutnya], mikrodetik. */
        var ptsUs: Long = 0L
            private set

        init {
            extractor.setDataSource(ctx, uri, null)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) {
                    track = i
                    format = f
                    break
                }
            }
            require(track >= 0 && format != null) { "Track video tidak ditemukan" }
            extractor.selectTrack(track)

            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            // null Surface = keluaran berupa buffer YUV yang bisa dibaca langsung.
            codec.configure(format, null, null, 0)
            codec.start()
        }

        /**
         * Lompat ke sekitar [waktuUs] (dibulatkan ke keyframe sebelumnya).
         * Dipakai saat melanjutkan job yang terhenti.
         */
        fun lompatKe(waktuUs: Long) {
            if (waktuUs <= 0) return
            extractor.seekTo(waktuUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            runCatching { codec.flush() }
            inputSelesai = false
            keluaranSelesai = false
        }

        /** Frame berikutnya sebagai Bitmap ARGB, atau `null` bila klip habis. */
        fun berikutnya(): Bitmap? {
            val batas = SystemClock.elapsedRealtime() + 5000
            while (!keluaranSelesai) {
                if (!inputSelesai) {
                    val idx = codec.dequeueInputBuffer(10_000)
                    if (idx >= 0) {
                        val buf = codec.getInputBuffer(idx)!!
                        val ukuran = extractor.readSampleData(buf, 0)
                        if (ukuran < 0) {
                            codec.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputSelesai = true
                        } else {
                            codec.queueInputBuffer(idx, 0, ukuran, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val out = codec.dequeueOutputBuffer(bufferInfo, 10_000)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (SystemClock.elapsedRealtime() > batas) throw IllegalStateException("Dekoder macet")
                    }
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Log.i(TAG, "Format keluaran: ${codec.outputFormat}")
                    else -> if (out >= 0) {
                        ptsUs = bufferInfo.presentationTimeUs
                        val akhir = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        val img = codec.getOutputImage(out)
                        var bmp: Bitmap? = null
                        if (img != null) {
                            val t = SystemClock.elapsedRealtime()
                            bmp = yuvKeBitmap(img)
                            konversiMs = SystemClock.elapsedRealtime() - t
                            img.close()
                        }
                        codec.releaseOutputBuffer(out, false)
                        if (akhir) keluaranSelesai = true
                        if (bmp != null) return putar(bmp)
                        if (akhir) return null
                    }
                }
            }
            return null
        }

        /** YUV_420_888 → Bitmap ARGB (BT.601 limited range). */
        private fun yuvKeBitmap(img: Image): Bitmap {
            val w = img.width
            val h = img.height
            val pY = img.planes[0]
            val pU = img.planes[1]
            val pV = img.planes[2]

            val yArr = ByteArray(pY.buffer.remaining()).also { pY.buffer.get(it) }
            val uArr = ByteArray(pU.buffer.remaining()).also { pU.buffer.get(it) }
            val vArr = ByteArray(pV.buffer.remaining()).also { pV.buffer.get(it) }

            val yRow = pY.rowStride
            val uRow = pU.rowStride
            val vRow = pV.rowStride
            val uPix = pU.pixelStride
            val vPix = pV.pixelStride

            val px = IntArray(w * h)
            var o = 0
            for (y in 0 until h) {
                val yBase = y * yRow
                val cBase = y / 2
                val uBase = cBase * uRow
                val vBase = cBase * vRow
                for (x in 0 until w) {
                    val yv = (yArr[yBase + x].toInt() and 0xFF) - 16
                    val uv = (uArr[uBase + (x / 2) * uPix].toInt() and 0xFF) - 128
                    val vv = (vArr[vBase + (x / 2) * vPix].toInt() and 0xFF) - 128

                    var r = (1.164f * yv + 1.596f * vv).toInt()
                    var g = (1.164f * yv - 0.391f * uv - 0.813f * vv).toInt()
                    var b = (1.164f * yv + 2.018f * uv).toInt()
                    if (r < 0) r = 0 else if (r > 255) r = 255
                    if (g < 0) g = 0 else if (g > 255) g = 255
                    if (b < 0) b = 0 else if (b > 255) b = 255
                    px[o++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        }

        /** Putar frame sesuai metadata rotasi (klip kamera HP sering 90°). */
        private fun putar(bmp: Bitmap): Bitmap {
            if (rotasiDiterapkan) return bmp
            rotasiDiterapkan = true
            if (info.rotation % 360 == 0) return bmp
            val m = Matrix().apply { postRotate(info.rotation.toFloat()) }
            val hasil = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (hasil !== bmp) bmp.recycle()
            return hasil
        }

        override fun close() {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching { extractor.release() }
        }
    }

    /** Penajaman 3×3 sederhana (unsharp) pada larik ARGB — untuk mengukur biaya di Kotlin. */
    fun tajamkan(px: IntArray, w: Int, h: Int) {
        if (w < 3 || h < 3) return
        val asal = px.copyOf()
        for (y in 1 until h - 1) {
            val atas = (y - 1) * w
            val tengah = y * w
            val bawah = (y + 1) * w
            for (x in 1 until w - 1) {
                // Kernel unsharp 3×3: pusat 9, delapan tetangga −1.
                val a0 = asal[atas + x - 1]
                val a1 = asal[atas + x]
                val a2 = asal[atas + x + 1]
                val b0 = asal[tengah + x - 1]
                val b1 = asal[tengah + x]
                val b2 = asal[tengah + x + 1]
                val c0 = asal[bawah + x - 1]
                val c1 = asal[bawah + x]
                val c2 = asal[bawah + x + 1]

                var r = 9 * ((b1 shr 16) and 0xFF) -
                    (((a0 shr 16) and 0xFF) + ((a1 shr 16) and 0xFF) + ((a2 shr 16) and 0xFF) +
                        ((b0 shr 16) and 0xFF) + ((b2 shr 16) and 0xFF) +
                        ((c0 shr 16) and 0xFF) + ((c1 shr 16) and 0xFF) + ((c2 shr 16) and 0xFF))
                var g = 9 * ((b1 shr 8) and 0xFF) -
                    (((a0 shr 8) and 0xFF) + ((a1 shr 8) and 0xFF) + ((a2 shr 8) and 0xFF) +
                        ((b0 shr 8) and 0xFF) + ((b2 shr 8) and 0xFF) +
                        ((c0 shr 8) and 0xFF) + ((c1 shr 8) and 0xFF) + ((c2 shr 8) and 0xFF))
                var b = 9 * (b1 and 0xFF) -
                    ((a0 and 0xFF) + (a1 and 0xFF) + (a2 and 0xFF) +
                        (b0 and 0xFF) + (b2 and 0xFF) +
                        (c0 and 0xFF) + (c1 and 0xFF) + (c2 and 0xFF))

                if (r < 0) r = 0 else if (r > 255) r = 255
                if (g < 0) g = 0 else if (g > 255) g = 255
                if (b < 0) b = 0 else if (b > 255) b = 255
                px[tengah + x] = -0x1000000 or (r shl 16) or (g shl 8) or b
            }
        }
    }

    /**
     * Jalankan pengukuran pada satu klip.
     *
     * @param maksFrame jumlah frame yang didekode untuk pengukuran
     * @param targetSisi sisi panjang hasil skala klasik (mis. 1280 untuk 720p)
     * @param aiSisi sisi pendek frame kecil untuk uji AI (mis. 320)
     */
    /** Metadata ringkas untuk layar pengaturan (tanpa membuka dekoder). */
    data class Meta(
        val lebar: Int,
        val tinggi: Int,
        val durasiMs: Long,
        val fps: Float,
        val kodek: String,
    )

    fun meta(ctx: Context, uri: Uri): Meta? {
        val r = android.media.MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, uri)
            fun angka(kunci: Int): Long =
                r.extractMetadata(kunci)?.toLongOrNull() ?: 0L
            val w = angka(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH).toInt()
            val h = angka(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT).toInt()
            val putar = angka(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION).toInt()
            val (lw, lh) = if (putar % 180 != 0) h to w else w to h
            Meta(
                lebar = lw,
                tinggi = lh,
                durasiMs = angka(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION),
                fps = angka(android.media.MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                    .toFloat(),
                kodek = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                    ?: "",
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Metadata video gagal: ${t.message}")
            null
        } finally {
            runCatching { r.release() }
        }
    }

    /** Satu bingkai untuk pratinjau di kartu pilihan video. */
    fun potongan(ctx: Context, uri: Uri, sisi: Int = 720): android.graphics.Bitmap? {
        val r = android.media.MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, uri)
            val bmp = r.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return null
            val panjang = maxOf(bmp.width, bmp.height)
            if (panjang <= sisi) return bmp
            val skala = sisi.toFloat() / panjang
            val kecil = android.graphics.Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * skala).toInt().coerceAtLeast(1),
                (bmp.height * skala).toInt().coerceAtLeast(1),
                true,
            )
            if (kecil !== bmp) bmp.recycle()
            kecil
        } catch (t: Throwable) {
            Log.w(TAG, "Potongan video gagal: ${t.message}")
            null
        } finally {
            runCatching { r.release() }
        }
    }

    fun jalankan(
        ctx: Context,
        uri: Uri,
        maksFrame: Int,
        targetSisi: Int,
        aiSisi: Int,
        paramPath: String,
        binPath: String,
    ): JSObject {
        val stream = FrameStream(ctx, uri)
        val info = stream.info
        var frame = 0
        var totalDekode = 0L
        var totalKonversi = 0L
        var totalSkala = 0L
        var totalTajam = 0L
        var aiMs = -1L
        var aiW = 0
        var aiH = 0
        var aiKeluaran = 0

        var handle = 0L
        try {
            while (frame < maksFrame) {
                val t0 = SystemClock.elapsedRealtime()
                val bmp = stream.berikutnya() ?: break
                totalDekode += SystemClock.elapsedRealtime() - t0
                totalKonversi += stream.konversiMs
                frame++

                // Biaya skala + penajam diukur pada satu frame saja (per frame konstan).
                if (frame == 1 || frame == maksFrame / 2) {
                    val skala = skalaKe(bmp, targetSisi)
                    val px = IntArray(skala.width * skala.height)
                    val t1 = SystemClock.elapsedRealtime()
                    skala.getPixels(px, 0, skala.width, 0, 0, skala.width, skala.height)
                    val t2 = SystemClock.elapsedRealtime()
                    tajamkan(px, skala.width, skala.height)
                    val t3 = SystemClock.elapsedRealtime()
                    totalSkala = t2 - t1
                    totalTajam = t3 - t2
                    Log.i(TAG, "Skala ${skala.width}x${skala.height}: piksel ${totalSkala}ms, penajam ${totalTajam}ms")

                    if (handle == 0L) {
                        val threads = maxOf(1, Runtime.getRuntime().availableProcessors() / 2)
                        handle = EnhancerNative.nativeCreate(paramPath, binPath, threads, false)
                    }
                    if (handle != 0L) {
                        val kecil = skalaKe(bmp, aiSisi)
                        aiW = kecil.width
                        aiH = kecil.height
                        val pxKecil = IntArray(aiW * aiH)
                        kecil.getPixels(pxKecil, 0, aiW, 0, 0, aiW, aiH)
                        val t4 = SystemClock.elapsedRealtime()
                        val hasil = EnhancerNative.nativeUpscale(
                            handle, pxKecil, aiW, aiH,
                            if (maxOf(aiW, aiH) > 512) 256 else 0, 16, null,
                        )
                        aiMs = SystemClock.elapsedRealtime() - t4
                        aiKeluaran = hasil?.size ?: 0
                        Log.i(TAG, "AI ${aiW}x${aiH}: $aiMs ms (keluaran $aiKeluaran px)")
                        kecil.recycle()
                    }
                    if (skala !== bmp) skala.recycle()
                }
                bmp.recycle()
            }
        } finally {
            stream.close()
            if (handle != 0L) EnhancerNative.nativeDestroy(handle)
        }

        if (frame == 0) throw IllegalStateException("Tidak ada frame yang berhasil didekode")

        val dekodeMs = totalDekode.toDouble() / frame
        val konversiMs = totalKonversi.toDouble() / frame
        val totalFrameKlip = maxOf(1, info.totalFrame)
        val perFrameKlasik = dekodeMs + totalSkala + totalTajam

        return JSObject()
            .put("ok", true)
            .put("width", info.width)
            .put("height", info.height)
            .put("fps", info.fps.toDouble())
            .put("durationMs", info.durationMs)
            .put("mime", info.mime)
            .put("rotation", info.rotation)
            .put("hasAudio", info.hasAudio)
            .put("bitrate", info.bitrate)
            .put("framesDecoded", frame)
            .put("decodeMsPerFrame", dekodeMs)
            .put("convertMsPerFrame", konversiMs)
            .put("scaleMsPerFrame", totalSkala)
            .put("sharpenMsPerFrame", totalTajam)
            .put("classicMsPerFrame", perFrameKlasik)
            .put("classicFps", if (perFrameKlasik > 0) 1000.0 / perFrameKlasik else 0.0)
            .put("clipFrames", totalFrameKlip)
            .put("clipClassicSeconds", perFrameKlasik * totalFrameKlip / 1000.0)
            .put("aiSize", "${aiW}x$aiH")
            .put("aiMsPerFrame", aiMs)
            .put("aiOutPixels", aiKeluaran)
            .put("aiClipSeconds", if (aiMs > 0) aiMs * totalFrameKlip / 1000.0 else -1.0)
    }

    private fun skalaKe(src: Bitmap, sisiPanjang: Int): Bitmap {
        val skala = sisiPanjang.toFloat() / maxOf(src.width, src.height)
        if (skala >= 1f) return src
        val w = maxOf(2, (src.width * skala).toInt())
        val h = maxOf(2, (src.height * skala).toInt())
        return Bitmap.createScaledBitmap(src, w, h, true)
    }
}
