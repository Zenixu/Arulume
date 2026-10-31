package id.aruthtale.arulume

import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.nio.ByteBuffer

/**
 * Pembungkus `MediaCodec` untuk menulis video H.264 hasil olahan.
 *
 * Sengaja memakai mode ByteBuffer + `getInputImage()` (bukan Surface/EGL) supaya
 * tidak perlu kode OpenGL sama sekali: satu frame ARGB ditulis langsung ke
 * bidang Y/U/V milik dekoder. Biayanya konversi ARGB→YUV di CPU, yang dari
 * pengukuran jauh lebih murah daripada satu frame AI.
 */
class VideoEncoder(
    private val lebar: Int,
    private val tinggi: Int,
    private val fps: Int,
    private val bitrate: Int,
    private val muxer: MediaMuxer,
) {
    private companion object {
        const val TAG = "Arulume/VideoEnc"
        const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC
    }

    private val codec: MediaCodec = MediaCodec.createEncoderByType(MIME)
    private val info = MediaCodec.BufferInfo()
    private var trackIndex = -1
    private var mulai = false
    private var frame = 0
    private var selesai = false

    /** Larik bantu agar tidak mengalokasi tiap frame. */
    private val barisU = ByteArray(lebar / 2)
    private val barisV = ByteArray(lebar / 2)

    init {
        val warna = pilihFormatWarna()
        Log.i(TAG, "Format warna encoder: 0x${Integer.toHexString(warna)}")
        val format = MediaFormat.createVideoFormat(MIME, lebar, tinggi).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, warna)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
    }

    /**
     * Cari format warna yang benar-benar didukung encoder di HP ini. Beberapa
     * perangkat menolak `COLOR_FormatYUV420Flexible`, jadi urutannya: flexible →
     * semi-planar (NV12) → planar (I420).
     */
    private fun pilihFormatWarna(): Int {
        val calon = intArrayOf(
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
            MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar,
        )
        return try {
            val daftar = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (info in daftar.codecInfos) {
                if (!info.isEncoder) continue
                if (info.supportedTypes.none { it.equals(MIME, true) }) continue
                val didukung = info.getCapabilitiesForType(MIME).colorFormats.toSet()
                for (c in calon) if (didukung.contains(c)) return c
            }
            calon[0]
        } catch (t: Throwable) {
            Log.w(TAG, "Gagal membaca kemampuan encoder: ${t.message}")
            calon[0]
        }
    }

    /** Kirim satu frame ARGB (ukuran harus sama dengan lebar×tinggi encoder). */
    fun tulis(px: IntArray, ptsUs: Long) {
        val idx = codec.dequeueInputBuffer(20_000)
        if (idx < 0) throw IllegalStateException("Encoder tidak menerima frame")
        val kapasitas = codec.getInputBuffer(idx)?.capacity() ?: -1
        val img = codec.getInputImage(idx) ?: throw IllegalStateException("Encoder tanpa bidang YUV")
        if (frame == 0) {
            val p0 = img.planes[0]
            Log.i(TAG, "Input: kapasitas=$kapasitas format=${img.format} " +
                "yRow=${p0.rowStride} uPix=${img.planes[1].pixelStride} uRow=${img.planes[1].rowStride} " +
                "perlu=${lebar * tinggi * 3 / 2}")
        }
        isiYuv(img, px)
        val ukuran = lebar * tinggi * 3 / 2
        codec.queueInputBuffer(idx, 0, ukuran, ptsUs, 0)
        frame++
        kuras()
    }

    /** Tandai akhir stream lalu tulis sisa paket. */
    fun tutup() {
        if (selesai) return
        val idx = codec.dequeueInputBuffer(50_000)
        if (idx >= 0) {
            codec.queueInputBuffer(idx, 0, 0, frame * 1_000_000L / fps, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        }
        val batas = System.currentTimeMillis() + 10_000
        while (!selesai && System.currentTimeMillis() < batas) kuras()
        runCatching { codec.stop() }
        runCatching { codec.release() }
    }

    private fun kuras() {
        while (true) {
            when (val out = codec.dequeueOutputBuffer(info, 10_000)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> return
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    trackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    mulai = true
                }
                else -> {
                    if (out < 0) return
                    val buf = codec.getOutputBuffer(out)
                    if (buf != null && info.size > 0 && mulai && trackIndex >= 0) {
                        // Kodek memberi flag konfigurasi (SPS/PPS) di samping data.
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            info.size = 0
                        }
                        if (info.size > 0) {
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            muxer.writeSampleData(trackIndex, buf, info)
                        }
                    }
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) selesai = true
                    codec.releaseOutputBuffer(out, false)
                    if (selesai) return
                }
            }
        }
    }

    /** ARGB → YUV 4:2:0 (BT.601 limited range) langsung ke bidang milik kodek. */
    private fun isiYuv(img: Image, px: IntArray) {
        val pY = img.planes[0]
        val pU = img.planes[1]
        val pV = img.planes[2]
        val bY: ByteBuffer = pY.buffer
        val bU: ByteBuffer = pU.buffer
        val bV: ByteBuffer = pV.buffer
        val yRow = pY.rowStride
        val uRow = pU.rowStride
        val vRow = pV.rowStride
        val uPix = pU.pixelStride
        val vPix = pV.pixelStride
        val gabungUv = uPix == 2 && vPix == 2
        val barisY = ByteArray(lebar)
        val barisUV = ByteArray(uRow)

        // Tulis luma per baris (satu put per baris jauh lebih cepat daripada
        // satu put per piksel).
        for (y in 0 until tinggi) {
            val srcBase = y * lebar
            for (x in 0 until lebar) {
                val c = px[srcBase + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                var yv = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                if (yv < 16) yv = 16 else if (yv > 235) yv = 235
                barisY[x] = yv.toByte()
            }
            bY.position(y * yRow)
            bY.put(barisY)
            // Krominans: satu nilai per 2×2 blok.
            if (y % 2 == 0) {
                val uBase = (y / 2) * uRow
                val vBase = (y / 2) * vRow
                var o = 0
                var x = 0
                while (x < lebar) {
                    val c0 = px[srcBase + x]
                    val c1 = px[srcBase + minOf(x + 1, lebar - 1)]
                    val r = (((c0 shr 16) and 0xFF) + ((c1 shr 16) and 0xFF)) / 2
                    val g = (((c0 shr 8) and 0xFF) + ((c1 shr 8) and 0xFF)) / 2
                    val b = ((c0 and 0xFF) + (c1 and 0xFF)) / 2
                    var uu = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    var vv = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    if (uu < 16) uu = 16 else if (uu > 240) uu = 240
                    if (vv < 16) vv = 16 else if (vv > 240) vv = 240
                    barisU[o] = uu.toByte()
                    barisV[o] = vv.toByte()
                    o++
                    x += 2
                }
                val lebarC = o
                if (gabungUv && vBase == uBase + 1) {
                    // Semi-planar (NV12): U dan V berselang-seling di satu bidang.
                    java.util.Arrays.fill(barisUV, 0)
                    for (i in 0 until lebarC) {
                        barisUV[i * 2] = barisU[i]
                        barisUV[i * 2 + 1] = barisV[i]
                    }
                    bU.position(uBase)
                    bU.put(barisUV, 0, minOf(barisUV.size, lebarC * 2))
                } else {
                    for (i in 0 until lebarC) {
                        bU.put(uBase + i * uPix, barisU[i])
                        bV.put(vBase + i * vPix, barisV[i])
                    }
                }
            }
        }
    }
}
