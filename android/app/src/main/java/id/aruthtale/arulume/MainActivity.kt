package id.aruthtale.arulume

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import com.getcapacitor.BridgeActivity

/**
 * Docs/ARCHITECTURE.md bagian 6: plugin harus didaftarkan SEBELUM `super.onCreate`,
 * kalau tidak bridge belum siap saat WebView memanggilnya.
 */
class MainActivity : BridgeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        registerPlugin(EnhancerPlugin::class.java)
        super.onCreate(savedInstanceState)

        // Tombol back lewat OnBackPressedDispatcher, bukan `onBackPressed()`:
        // di AndroidX modern override `onBackPressed()` sering tidak terpanggil.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    tanganiBack()
                }
            },
        )
    }

    /**
     * Kembali ke Beranda dulu, baru keluar. Halaman mendaftarkan
     * `window.arulumeBack()` (src/components/back-handler.tsx); `true` berarti
     * halaman sudah pindah sendiri, jadi aplikasi tidak perlu ditutup.
     */
    private fun tanganiBack() {
        val web = bridge?.webView
        if (web == null) {
            keluar()
            return
        }
        web.evaluateJavascript("(window.arulumeBack && window.arulumeBack()) === true") { hasil ->
            if (hasil?.trim() != "true") keluar()
        }
    }

    /** Di Beranda: sembunyikan aplikasi seperti menekan Home, bukan mematikan proses. */
    private fun keluar() {
        moveTaskToBack(true)
    }

    /**
     * Hasil pemilih foto lewat jalur `startActivityForResult` milik Activity,
     * jadi selalu sampai ke sini walau bridge sempat dibuat ulang. URI-nya
     * disimpan ke disk supaya Beranda bisa melanjutkan lewat `getPendingPick()`.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == EnhancerPlugin.REQ_PICK) {
            val ok = resultCode == Activity.RESULT_OK
            val uri = data?.data
            if (ok && uri != null) {
                EnhancerPlugin.rememberPick(applicationContext, uri)
            }
            EnhancerPlugin.deliverPick(ok, uri)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}
