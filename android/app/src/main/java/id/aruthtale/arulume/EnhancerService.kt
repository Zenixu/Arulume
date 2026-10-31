package id.aruthtale.arulume

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Layanan latar untuk proses perbesar foto.
 *
 * Kenapa ada: WebView boleh ditutup, layar boleh mati, aplikasi boleh
 * ditinggal — inferensi ncnn tetap jalan sampai selesai. Tanpa layanan
 * foreground, Android (apalagi MIUI) membekukan atau membunuh proses begitu
 * aplikasi tidak terlihat, dan pekerjaan 10–40 detik ikut hilang.
 *
 * Layanan ini **tidak** memakai jaringan: hanya CPU + berkas lokal
 * (Docs/AGENTS.md #1 tetap utuh — tidak ada izin INTERNET).
 */
class EnhancerService : Service() {

    companion object {
        private const val TAG = "Arulume/Service"

        const val ACTION_MULAI = "id.aruthtale.arulume.action.MULAI"
        const val ACTION_BATAL = "id.aruthtale.arulume.action.BATAL"

        /** Menjalankan job di layanan foreground. Aman dipanggil dari plugin. */
        fun mulai(ctx: Context, params: JobRunner.Params) {
            val i = params.keIntent(Intent(ctx, EnhancerService::class.java))
                .setAction(ACTION_MULAI)
            ContextCompat.startForegroundService(ctx, i)
        }

        /**
         * Menutup layanan setelah pekerjaan berakhir: tampilkan notifikasi
         * hasil lebih dulu, baru hentikan foreground (urutan ini mencegah
         * notifikasi hasil ikut terhapus).
         */
        fun selesai(ctx: Context, judul: String, teks: String) {
            ProsesNotif.selesai(ctx, judul, teks)
            try {
                ctx.stopService(Intent(ctx, EnhancerService::class.java))
            } catch (t: Throwable) {
                Log.w(TAG, "Stop layanan gagal: ${t.message}")
            }
        }

        fun batalkan(ctx: Context) {
            val i = Intent(ctx, EnhancerService::class.java).setAction(ACTION_BATAL)
            try {
                ctx.startService(i)
            } catch (t: Throwable) {
                Log.w(TAG, "Batal gagal: ${t.message}")
            }
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ProsesNotif.siapkanKanal(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val aksi = intent?.action
        if (aksi == ACTION_BATAL) {
            JobRunner.batalkan(null)
            hentikan()
            return START_NOT_STICKY
        }

        // startForeground wajib dalam 5 detik sejak startForegroundService.
        naikkanKeForeground(ProsesNotif.bangunProgres(this, "Menyiapkan foto", "Memulai…", 0))
        ambilWakeLock()

        val params = JobRunner.Params.dariIntent(intent)
        if (params == null) {
            Log.w(TAG, "Intent tanpa parameter job")
            hentikan()
            return START_NOT_STICKY
        }

        JobRunner.mulai(applicationContext, params)
        return START_NOT_STICKY
    }

    private fun naikkanKeForeground(n: android.app.Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    ProsesNotif.ID_PROSES,
                    n,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                startForeground(ProsesNotif.ID_PROSES, n)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "startForeground gagal", t)
        }
    }

    /**
     * CPU harus tetap bangun saat layar mati. Tanpa ini, pekerjaan berat bisa
     * tertahan doze dan terlihat "berhenti" padahal belum selesai.
     */
    private fun ambilWakeLock() {
        if (wakeLock?.isHeld == true) return
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "arulume:proses").apply {
                setReferenceCounted(false)
                acquire(30 * 60 * 1000L)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "WakeLock gagal: ${t.message}")
        }
    }

    private fun lepasWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (t: Throwable) {
            Log.w(TAG, "Lepas WakeLock gagal: ${t.message}")
        }
        wakeLock = null
    }

    /** Dipanggil JobRunner saat pekerjaan berakhir (sukses, gagal, atau batal). */
    private fun hentikan() {
        lepasWakeLock()
        ProsesNotif.bersihkan(this)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        lepasWakeLock()
        super.onDestroy()
    }
}
