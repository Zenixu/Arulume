package id.aruthtale.arulume

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/**
 * Menyatukan potongan-potongan hasil encode (lihat [VideoState]) menjadi satu
 * MP4 akhir, lalu menempelkan audio asli tanpa decode/encode ulang.
 *
 * Kenapa dipotong: MP4 tidak bisa "disambung" di tengah karena indeks (moov)
 * baru ditulis saat berkas ditutup. Dengan memotong tiap beberapa detik,
 * pekerjaan yang terhenti hanya kehilangan satu potongan terakhir, dan
 * penyambungan di sini hanya menyalin paket H.264 apa adanya (cepat, tanpa
 * turun kualitas).
 */
object VideoGabung {

    private const val TAG = "Arulume/VideoGabung"

    /**
     * @param segmen potongan berurutan (masing-masing berisi [frameSegmen] frame,
     *               kecuali potongan terakhir).
     * @return true bila berkas akhir berhasil dibuat.
     */
    fun gabung(
        ctx: Context,
        segmen: List<File>,
        sumber: Uri,
        keluar: File,
        fps: Int,
    ): Boolean {
        if (segmen.isEmpty()) return false
        keluar.parentFile?.mkdirs()
        if (keluar.exists()) keluar.delete()

        var muxer: MediaMuxer? = null
        val extractorVideo = ArrayList<MediaExtractor>()
        var extractorAudio: MediaExtractor? = null
        try {
            val durasiFrameUs = 1_000_000L / fps.coerceAtLeast(1)

            // 1. Format video diambil dari potongan pertama.
            val pertama = MediaExtractor().apply { setDataSource(segmen[0].absolutePath) }
            extractorVideo.add(pertama)
            var trackV = -1
            var formatV: MediaFormat? = null
            for (i in 0 until pertama.trackCount) {
                val f = pertama.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) {
                    trackV = i
                    formatV = f
                    break
                }
            }
            if (trackV < 0 || formatV == null) {
                Log.w(TAG, "Potongan pertama tanpa track video")
                return false
            }
            pertama.selectTrack(trackV)

            // 2. Track audio asli (bila ada) ikut didaftarkan sebelum muxer mulai.
            var trackA = -1
            var formatA: MediaFormat? = null
            runCatching {
                val ea = MediaExtractor()
                ea.setDataSource(ctx, sumber, null)
                for (i in 0 until ea.trackCount) {
                    val f = ea.getTrackFormat(i)
                    val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("audio/")) {
                        ea.selectTrack(i)
                        trackA = i
                        formatA = f
                        extractorAudio = ea
                        break
                    }
                }
                if (trackA < 0) ea.release()
            }

            val mx = MediaMuxer(keluar.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = mx
            val indeksV = mx.addTrack(formatV)
            val indeksA = if (formatA != null) mx.addTrack(formatA!!) else -1
            mx.start()

            // 3. Salin seluruh paket video, PTS digeser sesuai posisi potongan.
            val info = MediaCodec.BufferInfo()
            val buf = ByteBuffer.allocateDirect(2 * 1024 * 1024)
            var baseUs = 0L
            var totalDitulis = 0
            for ((urutan, file) in segmen.withIndex()) {
                val ex = if (urutan == 0) pertama else MediaExtractor().apply {
                    setDataSource(file.absolutePath)
                    selectTrack(0)
                    // pastikan track video yang dipilih
                    for (i in 0 until trackCount) {
                        val mime = getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
                        if (mime.startsWith("video/")) {
                            unselectTrack(0)
                            selectTrack(i)
                            break
                        }
                    }
                }
                if (urutan != 0) extractorVideo.add(ex)
                while (true) {
                    val ukuran = ex.readSampleData(buf, 0)
                    if (ukuran < 0) break
                    info.offset = 0
                    info.size = ukuran
                    info.presentationTimeUs = ex.sampleTime + baseUs
                    info.flags = if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else {
                        0
                    }
                    buf.position(0)
                    buf.limit(ukuran)
                    mx.writeSampleData(indeksV, buf, info)
                    totalDitulis++
                    ex.advance()
                }
                // Geser basis sebesar durasi potongan ini (jumlah frame × durasi frame).
                baseUs = info.presentationTimeUs + durasiFrameUs
            }

            // 4. Salin audio asli.
            val ea = extractorAudio
            if (ea != null && indeksA >= 0) {
                val bufA = ByteBuffer.allocateDirect(512 * 1024)
                val infoA = MediaCodec.BufferInfo()
                while (true) {
                    val ukuran = ea.readSampleData(bufA, 0)
                    if (ukuran < 0) break
                    infoA.offset = 0
                    infoA.size = ukuran
                    infoA.presentationTimeUs = ea.sampleTime
                    infoA.flags = if (ea.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else {
                        0
                    }
                    bufA.position(0)
                    bufA.limit(ukuran)
                    runCatching { mx.writeSampleData(indeksA, bufA, infoA) }
                    ea.advance()
                }
            }

            runCatching { mx.stop() }
            Log.i(TAG, "Gabung selesai: $totalDitulis paket video, ${keluar.length() / 1024} KB")
            return keluar.exists() && keluar.length() > 0
        } catch (t: Throwable) {
            Log.w(TAG, "Gagal menggabungkan: ${t.message}")
            runCatching { muxer?.stop() }
            keluar.delete()
            return false
        } finally {
            runCatching { muxer?.release() }
            extractorVideo.forEach { runCatching { it.release() } }
            runCatching { extractorAudio?.release() }
        }
    }
}
