package id.aruthtale.arulume

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Notifikasi lokal untuk proses perbesar foto.
 *
 * Semua notifikasi dibuat di perangkat ini (NotificationCompat) — tidak ada
 * Firebase, FCM, atau server apa pun. Aplikasi tetap tanpa izin INTERNET
 * (Docs/AGENTS.md #1).
 *
 * Dua kanal:
 * - `proses`  (LOW, senyap): bar progres selama job berjalan, tidak bisa digeser.
 * - `hasil`   (DEFAULT)    : pemberitahuan sekali saat foto selesai/gagal.
 */
object ProsesNotif {

    const val KANAL_PROSES = "arulume.proses"
    const val KANAL_HASIL = "arulume.hasil"

    const val ID_PROSES = 1001
    private const val ID_HASIL = 1002

    fun siapkanKanal(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (nm.getNotificationChannel(KANAL_PROSES) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    KANAL_PROSES,
                    "Proses foto",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Menampilkan kemajuan saat foto sedang diperbesar."
                    setShowBadge(false)
                    enableVibration(false)
                },
            )
        }
        if (nm.getNotificationChannel(KANAL_HASIL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    KANAL_HASIL,
                    "Hasil foto",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Memberi tahu saat foto selesai diperbesar."
                },
            )
        }
    }

    /** Notifikasi progres yang tidak bisa digeser (ongoing). */
    fun bangunProgres(
        ctx: Context,
        judul: String,
        teks: String,
        persen: Int,
    ): Notification {
        val buka = bukaAplikasi(ctx)
        return NotificationCompat.Builder(ctx, KANAL_PROSES)
            .setSmallIcon(R.drawable.ic_notif_proses)
            .setContentTitle(judul)
            .setContentText(teks)
            .setContentIntent(buka)
            .setProgress(100, persen.coerceIn(0, 100), persen <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun perbarui(ctx: Context, judul: String, teks: String, persen: Int) {
        if (!bolehKirim(ctx)) return
        try {
            NotificationManagerCompat.from(ctx)
                .notify(ID_PROSES, bangunProgres(ctx, judul, teks, persen))
        } catch (t: Throwable) {
            // Notifikasi gagal tampil tidak boleh menggagalkan pemrosesan.
        }
    }

    /** Pemberitahuan akhir; progres ditutup, hasil muncul sekali. */
    fun selesai(ctx: Context, judul: String, teks: String) {
        NotificationManagerCompat.from(ctx).cancel(ID_PROSES)
        if (!bolehKirim(ctx)) return
        val n = NotificationCompat.Builder(ctx, KANAL_HASIL)
            .setSmallIcon(R.drawable.ic_notif_proses)
            .setContentTitle(judul)
            .setContentText(teks)
            .setContentIntent(bukaAplikasi(ctx))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(ID_HASIL, n)
        } catch (t: Throwable) {
            // diamkan
        }
    }

    fun bersihkan(ctx: Context) {
        try {
            NotificationManagerCompat.from(ctx).cancel(ID_PROSES)
        } catch (t: Throwable) {
            // diamkan
        }
    }

    /** API 33+ butuh izin POST_NOTIFICATIONS; di bawah itu selalu boleh. */
    fun bolehKirim(ctx: Context): Boolean =
        NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    private fun bukaAplikasi(ctx: Context): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getActivity(ctx, 0, intent, flags)
    }
}
