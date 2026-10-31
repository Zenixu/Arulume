package id.aruthtale.arulume

/**
 * Pembungkus tipis untuk pustaka native `libarulume_native.so`
 * (lihat `src/main/cpp/enhancer_jni.cpp`).
 *
 * Semua fungsi di sini **berat** dan wajib dipanggil dari thread
 * `enhancer-worker`, bukan dari thread UI (Docs/AGENTS.md #3).
 */
object EnhancerNative {

    init {
        System.loadLibrary("arulume_native")
    }

    /**
     * Memuat model dan menyiapkan mesin.
     *
     * @return handle (> 0) bila berhasil, `0` bila model gagal dimuat.
     */
    external fun nativeCreate(
        paramPath: String,
        binPath: String?,
        threads: Int,
        useVulkan: Boolean
    ): Long

    /**
     * Memperbesar `pixels` (ARGB) berukuran [width] x [height].
     *
     * @param tile ukuran potongan; `0` = satu lintasan penuh.
     * @param pad  tumpang-tindih antar potongan (piksel sumber).
     * @return larik ARGB hasil, atau `null` bila inferensi gagal.
     */
    external fun nativeUpscale(
        handle: Long,
        pixels: IntArray,
        width: Int,
        height: Int,
        tile: Int,
        pad: Int,
        progress: TileProgress?
    ): IntArray?

    /**
     * Dipanggil dari C++ setiap satu tile selesai (thread `enhancer-worker`).
     * Dipakai untuk bar progres di notifikasi dan di layar Editor.
     */
    fun interface TileProgress {
        fun onTile(done: Int, total: Int)
    }

    /** Ringkasan konfigurasi mesin dalam bentuk JSON. */
    external fun nativeInfo(handle: Long): String

    /** Melepas model dan memori. Aman dipanggil dengan handle `0`. */
    external fun nativeDestroy(handle: Long)
}
