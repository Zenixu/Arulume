package id.aruthtale.arulume

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import java.io.File
import java.util.concurrent.Executors

/**
 * Plugin `Enhancer` — Docs/PRD.md bagian 10, Docs/ARCHITECTURE.md bagian 6.
 *
 * Aturan (Docs/AGENTS.md):
 * - Metode @PluginMethod hanya memvalidasi dan memulai job, lalu kembali segera.
 * - Setiap error membawa kode dari daftar ErrorCode. Tidak ada error tanpa kode.
 *
 * Status Fase 0: getCapabilities + clearCache jalan. pickMedia/startEnhanceImage
 * menyusul di Fase 1 (belum didaftarkan supaya tidak ada stub yang menyesatkan).
 */
@CapacitorPlugin(name = "Enhancer")
class EnhancerPlugin : Plugin() {

    @PluginMethod
    fun getCapabilities(call: PluginCall) {
        val ctx: Context = context
        val ret = JSObject()

        ret.put("abi", Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
        ret.put("androidApi", Build.VERSION.SDK_INT)
        ret.put("ramTotalMb", totalRamMb(ctx))
        ret.put("cpuCores", Runtime.getRuntime().availableProcessors())
        ret.put("vulkanAvailable", hasVulkan(ctx))
        gpuName()?.let { ret.put("gpuName", it) }
        ret.put("models", listModels(ctx))

        call.resolve(ret)
    }

    /** Menghapus cache pratinjau (`cacheDir/enhancer/`). Docs/ARCHITECTURE.md bagian 8. */
    @PluginMethod
    fun clearCache(call: PluginCall) {
        val dir = File(context.cacheDir, "enhancer")
        val freed = dirSize(dir)
        val ok = !dir.exists() || dir.deleteRecursively()
        if (!ok) {
            call.reject("Gagal menghapus cache", "E_UNKNOWN")
            return
        }
        call.resolve(JSObject().put("freedBytes", freed))
    }

    /**
     * Uji mandiri Fase 0.5: memuat model sungguhan, memperbesar citra sintetis
     * 256x256, dan mengembalikan waktu + dimensi hasil.
     *
     * Berjalan di `enhancer-worker` (Docs/AGENTS.md #3); thread UI tidak pernah
     * menunggu. Tidak menyentuh berkas pengguna (Docs/AGENTS.md #4).
     */
    @PluginMethod
    fun runSelfTest(call: PluginCall) {
        val modelId = call.getString("modelId") ?: defaultModelId()
        val size = (call.getInt("size") ?: 256).coerceIn(32, 1024)
        val useVulkan = call.getBoolean("vulkan") ?: true
        val tile = call.getInt("tile") ?: 0
        val threads = call.getInt("threads")
            ?: maxOf(1, Runtime.getRuntime().availableProcessors() / 2)

        if (modelId == null) {
            call.reject("Belum ada model di assets/models/", "E_MODEL_MISSING")
            return
        }

        worker.execute {
            var handle = 0L
            try {
                val ctx = context
                val dir = File(ctx.cacheDir, "models").apply { mkdirs() }
                val param = File(dir, "$modelId.param")
                val bin = File(dir, "$modelId.bin")
                copyAsset(ctx, "models/$modelId.param", param)
                copyAsset(ctx, "models/$modelId.bin", bin)

                val tLoad = SystemClock.elapsedRealtime()
                handle = EnhancerNative.nativeCreate(
                    param.absolutePath, bin.absolutePath, threads, useVulkan
                )
                val loadMs = SystemClock.elapsedRealtime() - tLoad
                if (handle == 0L) {
                    call.reject("Model gagal dimuat (lihat logcat ArulumeNative)", "E_MODEL")
                    return@execute
                }

                val px = syntheticImage(size)
                val tRun = SystemClock.elapsedRealtime()
                val out = EnhancerNative.nativeUpscale(handle, px, size, size, tile, 16)
                val inferMs = SystemClock.elapsedRealtime() - tRun
                if (out == null) {
                    call.reject("Inferensi gagal", "E_INFER")
                    return@execute
                }

                val outW = size * (if (modelId.endsWith("-x2")) 2 else if (modelId.endsWith("-x3")) 3 else 4)
                val sample = JSArray()
                for (i in intArrayOf(0, out.size / 2, out.size - 1)) {
                    sample.put(out[i])
                }

                val ret = JSObject()
                    .put("ok", true)
                    .put("modelId", modelId)
                    .put("engine", "ncnn")
                    .put("inputSize", size)
                    .put("outputPixels", out.size)
                    .put("expectedWidth", outW)
                    .put("loadMs", loadMs)
                    .put("inferMs", inferMs)
                    .put("tile", tile)
                    .put("threads", threads)
                    .put("vulkan", useVulkan)
                    .put("native", EnhancerNative.nativeInfo(handle))
                    .put("sample", sample)
                Log.i(TAG, "Uji mandiri: ${ret.toString()}")
                call.resolve(ret)
            } catch (t: Throwable) {
                Log.e(TAG, "Uji mandiri gagal", t)
                call.reject("Uji mandiri gagal: ${t.message}", "E_UNKNOWN")
            } finally {
                if (handle != 0L) {
                    try {
                        EnhancerNative.nativeDestroy(handle)
                    } catch (t: Throwable) {
                        Log.w(TAG, "Lepas model gagal: ${t.message}")
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun totalRamMb(ctx: Context): Long {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem / (1024L * 1024L)
    }

    private fun hasVulkan(ctx: Context): Boolean = try {
        ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)
    } catch (t: Throwable) {
        Log.w(TAG, "Deteksi Vulkan gagal: ${t.message}")
        false
    }

    /**
     * Nama GPU dari context OpenGL ES offscreen (1x1 pbuffer).
     * Vulkan/ncnn menyusul di Fase 0.4; ini hanya untuk ditampilkan di halaman uji.
     */
    private fun gpuName(): String? {
        var dpy = EGL14.EGL_NO_DISPLAY
        var ctx = EGL14.EGL_NO_CONTEXT
        var surf = EGL14.EGL_NO_SURFACE
        return try {
            dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (dpy == EGL14.EGL_NO_DISPLAY) return null

            val version = IntArray(2)
            if (!EGL14.eglInitialize(dpy, version, 0, version, 1)) return null

            val configAttrs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(dpy, configAttrs, 0, configs, 0, 1, numConfigs, 0)) return null
            val config = configs[0] ?: return null

            ctx = EGL14.eglCreateContext(
                dpy, config, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
            )
            if (ctx == EGL14.EGL_NO_CONTEXT) return null

            surf = EGL14.eglCreatePbufferSurface(
                dpy, config,
                intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0
            )
            if (surf == EGL14.EGL_NO_SURFACE) return null

            if (!EGL14.eglMakeCurrent(dpy, surf, surf, ctx)) return null
            GLES20.glGetString(GLES20.GL_RENDERER)
        } catch (t: Throwable) {
            Log.w(TAG, "Baca nama GPU gagal: ${t.message}")
            null
        } finally {
            try {
                if (dpy != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(
                        dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
                    )
                    if (surf != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(dpy, surf)
                    if (ctx != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(dpy, ctx)
                    EGL14.eglTerminate(dpy)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Bersih-bersih EGL gagal: ${t.message}")
            }
        }
    }

    /**
     * Membaca berkas `.param` di folder `assets/models/`. Untuk sekarang kosong karena model belum
     * dibundel (Fase 0.5). Begitu model ditaruh, daftar ini otomatis terisi.
     */
    private fun listModels(ctx: Context): JSArray {
        val out = JSArray()
        val entries = try {
            ctx.assets.list("models") ?: emptyArray()
        } catch (t: Throwable) {
            Log.w(TAG, "Baca folder models gagal: ${t.message}")
            emptyArray()
        }
        for (name in entries.sorted()) {
            if (!name.endsWith(".param")) continue
            val id = name.removeSuffix(".param")
            val sizeBytes = try {
                ctx.assets.openFd("models/$id.bin").use { it.length }
            } catch (t: Throwable) {
                0L
            }
            val scales = JSArray().put(2).put(3).put(4)
            val nativeScale = when {
                id.endsWith("-x2") -> 2
                id.endsWith("-x3") -> 3
                else -> 4
            }
            out.put(
                JSObject()
                    .put("id", id)
                    .put("label", id)
                    .put("nativeScale", nativeScale)
                    .put("supportedScales", scales)
                    .put("kind", "image")
                    .put("license", "lihat Docs/THIRD_PARTY.md")
                    .put("sizeBytes", sizeBytes)
            )
        }
        return out
    }

    private fun dirSize(dir: File): Long =
        if (!dir.exists()) 0L
        else dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** Model pertama (urut abjad) di `assets/models/`. */
    private fun defaultModelId(): String? = try {
        context.assets.list("models")
            ?.filter { it.endsWith(".param") }
            ?.sorted()
            ?.firstOrNull()
            ?.removeSuffix(".param")
    } catch (t: Throwable) {
        Log.w(TAG, "Baca folder models gagal: ${t.message}")
        null
    }

    /** Menyalin model dari assets ke cache sekali saja (ncnn butuh path berkas nyata). */
    private fun copyAsset(ctx: Context, asset: String, target: File) {
        if (target.exists() && target.length() > 0L) return
        ctx.assets.open(asset).use { input ->
            target.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
        }
    }

    /** Citra uji deterministik: gradien + kotak-kotak, supaya hasil bisa dibandingkan. */
    private fun syntheticImage(size: Int): IntArray {
        val px = IntArray(size * size)
        val denom = (size - 1).coerceAtLeast(1)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val r = (x * 255) / denom
                val g = (y * 255) / denom
                val b = if (((x / 16) + (y / 16)) % 2 == 0) 255 else 200
                px[y * size + x] = (0xff shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return px
    }

    /** Satu thread khusus untuk kerja berat (Docs/AGENTS.md #3). */
    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "enhancer-worker").apply { isDaemon = true }
    }

    companion object {
        private const val TAG = "Arulume/Enhancer"
    }
}
