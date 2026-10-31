package id.aruthtale.arulume

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.PermissionState
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Plugin `Enhancer` — Docs/PRD.md bagian 10, Docs/ARCHITECTURE.md bagian 6.
 *
 * Aturan (Docs/AGENTS.md):
 * - Metode @PluginMethod hanya memvalidasi dan memulai job, lalu kembali segera.
 * - Setiap error membawa kode dari daftar ErrorCode. Tidak ada error tanpa kode.
 *
 * Status Fase 1: getCapabilities, clearCache, pickMedia, startEnhanceImage,
 * cancelJob, openInGallery, shareResult jalan. Video menyusul di Fase 2.
 */
@CapacitorPlugin(
    name = "Enhancer",
    permissions = [
        Permission(
            alias = "storage",
            strings = [Manifest.permission.WRITE_EXTERNAL_STORAGE],
        ),
        Permission(
            alias = "notifikasi",
            strings = ["android.permission.POST_NOTIFICATIONS"],
        ),
    ],
)
class EnhancerPlugin : Plugin() {

    /** Pendengar event dari [JobRunner]; dilepas saat plugin dihancurkan. */
    private var pendengarJob: ((String, JSObject) -> Unit)? = null

    override fun load() {
        instance = this
        // Job sekarang berjalan di layanan foreground; plugin hanya meneruskan
        // event-nya ke WebView (kalau WebView masih ada).
        val l: (String, JSObject) -> Unit = { event, data -> notifyListeners(event, data) }
        pendengarJob = l
        JobRunner.tambahPendengar(l)
    }

    override fun handleOnDestroy() {
        pendengarJob?.let { JobRunner.hapusPendengar(it) }
        pendengarJob = null
        if (instance === this) instance = null
        super.handleOnDestroy()
    }

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

    /** Menghapus cache pratinjau (`cacheDir/enhancer/` dan `cacheDir/hasil/`). Docs/ARCHITECTURE.md bagian 8. */
    @PluginMethod
    fun clearCache(call: PluginCall) {
        val dirs = listOf(
            File(context.cacheDir, "enhancer"),
            ImagePipeline.resultsDir(context),
        )
        val freed = dirs.sumOf { dirSize(it) }
        val ok = dirs.all { !it.exists() || it.deleteRecursively() }
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
                val out = EnhancerNative.nativeUpscale(handle, px, size, size, tile, 16, null)
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

    // ------------------------------------------------------- Fase 1: foto

    /**
     * Photo Picker (API 33+) dengan fallback SAF `ACTION_OPEN_DOCUMENT`.
     * Tidak butuh izin apa pun: sistem yang memberi izin baca per-URI.
     */
    @PluginMethod
    fun pickMedia(call: PluginCall) {
        val mediaType = call.getString("type") ?: "image"
        if (mediaType != "image") {
            call.reject("Video menyusul di Fase 2", "E_UNSUPPORTED_FORMAT")
            return
        }
        if (pendingPick) {
            call.reject("Pemilihan lain masih berjalan", "E_BUSY")
            return
        }

        // `multiple: true` untuk batch (PRD FR-11). Batas 20 foto per antrian
        // supaya memori dan waktu tunggu tetap wajar di perangkat kelas menengah.
        val multiple = call.getBoolean("multiple") ?: false
        val batas = (call.getInt("maxItems") ?: MAX_BATCH).coerceIn(2, MAX_BATCH)

        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Intent(MediaStore.ACTION_PICK_IMAGES).apply {
                this.type = "image/*"
                if (multiple) {
                    putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, batas)
                }
            }
        } else {
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                this.type = "image/*"
                if (multiple) putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
        }

        pendingPick = true
        pendingPickCall = call
        try {
            // Sengaja lewat Activity + requestCode sendiri: jalur callback Capacitor
            // hilang kalau proses mati saat pemilih di depan (MIUI), sedangkan
            // MainActivity.onActivityResult selalu kebagian hasilnya.
            activity.startActivityForResult(intent, REQ_PICK)
        } catch (t: Throwable) {
            pendingPick = false
            pendingPickCall = null
            Log.e(TAG, "Pemilih foto gagal dibuka", t)
            call.reject("Pemilih foto tidak tersedia di perangkat ini", "E_UNKNOWN")
        }
    }

    /** Dipanggil `MainActivity` setelah pemilih foto selesai. */
    fun onPickResult(ok: Boolean, uris: List<Uri>) {
        val call = pendingPickCall ?: return
        pendingPickCall = null
        pendingPick = false
        if (!ok || uris.isEmpty()) {
            call.reject("Pemilihan dibatalkan", "E_PICK_CANCELLED")
            return
        }
        forgetPendingPick()
        resolvePickedUri(call, uris)
    }

    /**
     * Cadangan kalau proses aplikasi mati saat pemilih foto di depan (MIUI agresif
     * membunuh aplikasi latar). `MainActivity.onActivityResult` menyimpan URI-nya ke
     * disk; WebView yang lahir ulang memanggil method ini untuk melanjutkan.
     */
    @PluginMethod
    fun getPendingPick(call: PluginCall) {
        val prefs = context.getSharedPreferences(PREFS_PICK, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_PICK, null)
        if (raw == null) {
            call.resolve(JSObject().put("items", JSArray()))
            return
        }
        prefs.edit().remove(KEY_PICK).apply()
        val uris = try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                arr.optString(i).takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
            }
        } catch (t: Throwable) {
            // Format lama: satu URI tersimpan sebagai string biasa.
            listOfNotNull(Uri.parse(raw))
        }
        resolvePickedUri(call, uris)
    }

    /** Baca metadata + pratinjau setiap URI terpilih, lalu balas ke JS. */
    private fun resolvePickedUri(call: PluginCall, uris: List<Uri>) {
        // Izin baca bertahan selama sesi; kalau sistem mengizinkan, simpan permanen.
        uris.forEach { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (t: Throwable) {
                Log.d(TAG, "Izin URI tidak permanen: ${t.message}")
            }
        }

        worker.execute {
            try {
                val ctx = context
                val items = JSArray()
                var gagal = 0
                for (uri in uris.take(MAX_BATCH)) {
                    try {
                        val (name, mime, size) = ImagePipeline.queryMeta(ctx, uri)
                        val (w, h) = ImagePipeline.bounds(ctx, uri)
                        if (w <= 0 || h <= 0) {
                            gagal++
                            continue
                        }
                        val thumb = ImagePipeline.decode(ctx, uri, 720)
                        val previewPath = if (thumb != null) {
                            val p = ImagePipeline.writePreview(ctx, thumb, 720)
                            thumb.recycle()
                            p
                        } else {
                            ""
                        }
                        items.put(
                            JSObject()
                                .put("uri", uri.toString())
                                .put("name", name)
                                .put("mime", mime)
                                .put("sizeBytes", size)
                                .put("width", w)
                                .put("height", h)
                                .put("previewPath", previewPath),
                        )
                    } catch (t: Throwable) {
                        // Satu foto rusak tidak boleh menggagalkan seluruh pilihan.
                        Log.w(TAG, "Lewati foto ${uri}: ${t.message}")
                        gagal++
                    }
                }

                if (items.length() == 0) {
                    call.reject("Foto tidak bisa dibaca", "E_DECODE_FAILED")
                    return@execute
                }
                call.resolve(JSObject().put("items", items).put("skipped", gagal))
            } catch (t: Throwable) {
                Log.e(TAG, "Baca foto gagal", t)
                call.reject("Foto tidak bisa dibaca", "E_DECODE_FAILED")
            }
        }
    }

    private fun forgetPendingPick() {
        context.getSharedPreferences(PREFS_PICK, Context.MODE_PRIVATE)
            .edit().remove(KEY_PICK).apply()
    }

    /**
     * Memulai job perbesar foto. Langsung mengembalikan `jobId`; hasil dikirim
     * lewat event `progress` / `completed` / `failed` (Docs/PRD.md bagian 10).
     */
    @PluginMethod
    fun startEnhanceImage(call: PluginCall) {
        val uriStr = call.getString("uri")
        if (uriStr.isNullOrBlank()) {
            call.reject("uri kosong", "E_DECODE_FAILED")
            return
        }
        val scale = (call.getInt("scale") ?: 2).coerceIn(2, 4)
        val modelId = call.getString("model")?.takeIf { it.isNotBlank() }
            ?: modelForScale(scale)
        if (modelId == null) {
            call.reject("Belum ada model di assets/models/", "E_MODEL_MISSING")
            return
        }

        val useGpu = call.getBoolean("useGpu") ?: false
        val tileReq = call.getInt("tileSize") ?: 0
        val maxOut = (call.getInt("maxOutputLongSide") ?: ImagePipeline.MAX_OUTPUT_SIDE)
            .coerceIn(256, ImagePipeline.MAX_OUTPUT_SIDE)
        val out = call.getObject("output")
        val quality = (out?.getInteger("quality") ?: 95).coerceIn(60, 100)
        val format = out?.getString("format") ?: "jpeg"

        val jobId = JobRunner.jobIdBaru()
        val params = JobRunner.Params(
            jobId = jobId,
            uris = listOf(uriStr),
            modelId = modelId,
            scale = scale,
            useGpu = useGpu,
            tileSize = tileReq,
            maxOutputLongSide = maxOut,
            quality = quality,
            format = format,
        )

        // Kontrak: kembali segera. Kerja berat pindah ke layanan foreground
        // supaya tetap jalan saat aplikasi ditinggal atau layar mati.
        call.resolve(JSObject().put("jobId", jobId))
        try {
            EnhancerService.mulai(context, params)
        } catch (t: Throwable) {
            Log.e(TAG, "Layanan proses gagal dimulai", t)
            notifyListeners(
                "failed",
                JSObject()
                    .put("jobId", jobId)
                    .put("code", "E_UNKNOWN")
                    .put("message", "Tidak bisa memulai proses di latar")
                    .put("recoverable", true),
            )
        }
    }

    /**
     * Memproses beberapa foto sekaligus (PRD FR-11). Foto dikerjakan berurutan di
     * satu layanan foreground; satu kegagalan tidak menghentikan antrian.
     * Event: `progress` (ada `index`/`total`), `itemDone`, `itemFailed`, `completed`.
     */
    @PluginMethod
    fun startEnhanceBatch(call: PluginCall) {
        val arr = call.getArray("uris")
        val uris = ArrayList<String>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val s = arr.optString(i, "")
                if (s.isNotBlank()) uris.add(s)
            }
        }
        if (uris.isEmpty()) {
            call.reject("Belum ada foto yang dipilih", "E_DECODE_FAILED")
            return
        }
        if (uris.size > MAX_BATCH) {
            call.reject("Maksimal $MAX_BATCH foto sekali proses", "E_TOO_MANY")
            return
        }
        if (JobRunner.sedangJalan() != null) {
            call.reject("Masih ada proses lain yang berjalan", "E_BUSY")
            return
        }

        val scale = (call.getInt("scale") ?: 2).coerceIn(2, 4)
        val modelId = call.getString("model")?.takeIf { it.isNotBlank() }
            ?: modelForScale(scale)
        if (modelId == null) {
            call.reject("Belum ada model di assets/models/", "E_MODEL_MISSING")
            return
        }

        val out = call.getObject("output")
        val maxOut = (call.getInt("maxOutputLongSide") ?: ImagePipeline.MAX_OUTPUT_SIDE)
            .coerceIn(256, ImagePipeline.MAX_OUTPUT_SIDE)
        val jobId = JobRunner.jobIdBaru()
        val params = JobRunner.Params(
            jobId = jobId,
            uris = uris,
            modelId = modelId,
            scale = scale,
            useGpu = call.getBoolean("useGpu") ?: false,
            tileSize = call.getInt("tileSize") ?: 0,
            maxOutputLongSide = maxOut,
            quality = (out?.getInteger("quality") ?: 95).coerceIn(60, 100),
            format = out?.getString("format") ?: "jpeg",
        )

        call.resolve(JSObject().put("jobId", jobId).put("total", uris.size))
        try {
            EnhancerService.mulai(context, params)
        } catch (t: Throwable) {
            Log.e(TAG, "Layanan proses gagal dimulai", t)
            notifyListeners(
                "failed",
                JSObject()
                    .put("jobId", jobId)
                    .put("code", "E_UNKNOWN")
                    .put("message", "Tidak bisa memulai proses di latar")
                    .put("recoverable", true),
            )
        }
    }

    @PluginMethod
    fun cancelJob(call: PluginCall) {
        JobRunner.batalkan(call.getString("jobId"))
        call.resolve()
    }

    /**
     * Keadaan job yang sedang berjalan. Dipakai layar Editor saat WebView lahir
     * ulang (mis. aplikasi ditutup lalu dibuka lagi) supaya progres tetap tampil
     * dan hasil yang sudah selesai tidak hilang.
     */
    @PluginMethod
    fun getJobStatus(call: PluginCall) {
        val s = JobRunner.status
        if (s == null) {
            call.resolve(JSObject().put("aktif", false))
            return
        }
        call.resolve(
            JSObject()
                .put("jobId", s.jobId)
                .put("stage", s.stage)
                .put("percent", s.percent)
                .put("aktif", s.aktif)
                .put("index", s.index)
                .put("total", s.total),
        )
    }

    /**
     * Meminta izin notifikasi (Android 13+). Di Android 12 ke bawah langsung
     * dianggap diberi. Notifikasi di sini murni lokal — tanpa Firebase/FCM.
     */
    @PluginMethod
    fun mintaIzinNotifikasi(call: PluginCall) {
        if (Build.VERSION.SDK_INT < 33) {
            call.resolve(JSObject().put("granted", true))
            return
        }
        if (getPermissionState("notifikasi") == PermissionState.GRANTED) {
            call.resolve(JSObject().put("granted", true))
            return
        }
        requestPermissionForAlias("notifikasi", call, "onIzinNotif")
    }

    @PermissionCallback
    private fun onIzinNotif(call: PluginCall) {
        call.resolve(
            JSObject().put("granted", getPermissionState("notifikasi") == PermissionState.GRANTED),
        )
    }

    /**
     * Menyimpan hasil yang sedang dilihat ke galeri `Pictures/Arulume`.
     * Dipanggil hanya saat user menekan tombol Simpan di layar Hasil.
     */
    @PluginMethod
    fun saveToGallery(call: PluginCall) {
        val path = call.getString("path")
        if (path.isNullOrBlank()) {
            call.reject("Berkas hasil tidak diketahui", "E_SAVE_FAILED")
            return
        }

        // Penyimpanan lama (API 24-28) butuh izin tulis; API 29+ tidak.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            getPermissionState("storage") != PermissionState.GRANTED
        ) {
            requestPermissionForAlias("storage", call, "onStoragePermissionSave")
            return
        }
        doSaveToGallery(call, path)
    }

    @PermissionCallback
    private fun onStoragePermissionSave(call: PluginCall) {
        val path = call.getString("path")
        if (getPermissionState("storage") == PermissionState.GRANTED && path != null) {
            doSaveToGallery(call, path)
        } else {
            call.reject("Izin menyimpan ke galeri ditolak", "E_SAVE_FAILED")
        }
    }

    private fun doSaveToGallery(call: PluginCall, path: String) {
        val dir = ImagePipeline.resultsDir(context).canonicalPath
        val file = try {
            File(path).canonicalFile
        } catch (t: Throwable) {
            call.reject("Berkas hasil tidak valid", "E_SAVE_FAILED")
            return
        }
        // Hanya berkas di folder hasil sendiri yang boleh disimpan.
        if (!file.path.startsWith(dir) || !file.exists()) {
            call.reject("Hasil sudah tidak ada, coba proses ulang", "E_SAVE_FAILED")
            return
        }

        val quality = (call.getInt("quality") ?: 95).coerceIn(60, 100)
        worker.execute {
            var bmp: Bitmap? = null
            try {
                bmp = ImagePipeline.decodeFile(file.absolutePath)
                if (bmp == null) {
                    call.reject("Hasil tidak bisa dibaca", "E_SAVE_FAILED")
                    return@execute
                }
                val fmt = ImagePipeline.OutFormat.dari(file.extension)
                val saved = ImagePipeline.saveToGallery(context, bmp, quality, fmt)
                if (saved == null) {
                    call.reject("Gagal menyimpan ke galeri", "E_SAVE_FAILED")
                    return@execute
                }
                Riwayat.tandaiTersimpan(
                    context,
                    file.absolutePath,
                    saved.uri.toString(),
                    saved.uri.lastPathSegment ?: "",
                )
                call.resolve(
                    JSObject()
                        .put("uri", saved.uri.toString())
                        .put("width", saved.width)
                        .put("height", saved.height),
                )
            } catch (t: Throwable) {
                Log.e(TAG, "Simpan ke galeri gagal", t)
                call.reject("Gagal menyimpan ke galeri", "E_SAVE_FAILED")
            } finally {
                bmp?.recycle()
            }
        }
    }

    @PluginMethod
    fun openInGallery(call: PluginCall) {
        val uriStr = call.getString("uri")
        if (uriStr.isNullOrBlank()) {
            call.reject("uri kosong", "E_UNKNOWN")
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(uriStr), "image/jpeg")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            call.resolve()
        } catch (t: Throwable) {
            Log.w(TAG, "Tidak ada aplikasi galeri: ${t.message}")
            call.reject("Tidak ada aplikasi galeri", "E_UNKNOWN")
        }
    }

    @PluginMethod
    fun shareResult(call: PluginCall) {
        val uriStr = call.getString("uri")
        if (uriStr.isNullOrBlank()) {
            call.reject("uri kosong", "E_UNKNOWN")
            return
        }
        val mime = call.getString("mime") ?: "image/jpeg"
        try {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, Uri.parse(uriStr))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Bagikan hasil"))
            call.resolve()
        } catch (t: Throwable) {
            Log.w(TAG, "Bagikan gagal: ${t.message}")
            call.reject("Tidak bisa membagikan", "E_UNKNOWN")
        }
    }

    /** Riwayat pekerjaan (PRD FR-08), terbaru lebih dulu. */
    @PluginMethod
    fun listHistory(call: PluginCall) {
        val items = JSArray()
        Riwayat.load(context).forEach { e ->
            val path = e.optString("resultPath")
            // hasResult=false artinya berkas cache sudah dipangkas; UI harus
            // mengarahkan user ke galeri (kalau sudah disimpan).
            e.put("hasResult", path.isNotEmpty() && File(path).exists())
            items.put(e)
        }
        call.resolve(JSObject().put("items", items))
    }

    /** Satu entri riwayat; dipakai layar "lihat lagi" tanpa membawa data di URL. */
    @PluginMethod
    fun getHistory(call: PluginCall) {
        val id = call.getString("id")
        if (id.isNullOrBlank()) {
            call.reject("id kosong", "E_UNKNOWN")
            return
        }
        val e = Riwayat.load(context).firstOrNull { it.optString("id") == id }
        if (e == null) {
            call.reject("Entri riwayat tidak ditemukan", "E_UNKNOWN")
            return
        }
        val path = e.optString("resultPath")
        e.put("hasResult", path.isNotEmpty() && File(path).exists())
        call.resolve(JSObject(e.toString()))
    }

    /** Hapus satu entri. Foto di galeri TIDAK ikut dihapus (PRD FR-08). */
    @PluginMethod
    fun deleteHistory(call: PluginCall) {
        val id = call.getString("id")
        if (id.isNullOrBlank()) {
            call.reject("id kosong", "E_UNKNOWN")
            return
        }
        call.resolve(JSObject().put("deleted", Riwayat.hapus(context, id)))
    }

    @PluginMethod
    fun clearHistory(call: PluginCall) {
        Riwayat.kosongkan(context)
        call.resolve()
    }

    /** Model yang skala aslinya cocok; kalau tidak ada, pakai model default. */
    private fun modelForScale(scale: Int): String? {
        val all = try {
            context.assets.list("models")?.filter { it.endsWith(".param") }?.sorted() ?: emptyList()
        } catch (t: Throwable) {
            emptyList()
        }
        val exact = all.firstOrNull { it.removeSuffix(".param").endsWith("-x$scale") }
        return (exact ?: all.firstOrNull())?.removeSuffix(".param")
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

    /** Status job yang sedang jalan; hanya satu pada satu waktu (worker tunggal). */


    /** Mencegah dua pemilih foto terbuka bersamaan. */
    private var pendingPick = false

    /** Call yang menunggu hasil pemilih foto. */
    private var pendingPickCall: PluginCall? = null

    companion object {
        private const val TAG = "Arulume/Enhancer"
        private const val PREFS_PICK = "arulume_pick"
        private const val KEY_PICK = "uri"

        /** Request code pemilih foto; dipakai `MainActivity`. */
        const val REQ_PICK = 0x4152

        @Volatile
        private var instance: EnhancerPlugin? = null

        /** Batas foto per antrian batch (PRD FR-11). */
        const val MAX_BATCH = 20

        /** Dipanggil `MainActivity.onActivityResult` untuk meneruskan hasil pemilih. */
        fun deliverPick(ok: Boolean, uris: List<Uri>) {
            instance?.onPickResult(ok, uris)
        }

        /**
         * Dipanggil `MainActivity.onActivityResult` saat hasil pemilih datang lewat
         * jalur lama (proses sempat mati). Disimpan di disk, bukan memori.
         */
        fun rememberPick(ctx: Context, uris: List<Uri>) {
            if (uris.isEmpty()) return
            val arr = JSONArray()
            uris.forEach { arr.put(it.toString()) }
            ctx.getSharedPreferences(PREFS_PICK, Context.MODE_PRIVATE)
                .edit().putString(KEY_PICK, arr.toString()).apply()
        }
    }
}
