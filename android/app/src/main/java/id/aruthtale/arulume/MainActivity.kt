package id.aruthtale.arulume

import android.app.Activity
import android.content.Intent
import android.net.Uri
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
            val uris = ArrayList<Uri>()
            if (ok && data != null) {
                // Pilihan banyak foto datang sebagai ClipData; satu foto lewat `data`.
                val clip = data.clipData
                if (clip != null) {
                    for (i in 0 until clip.itemCount) {
                        clip.getItemAt(i)?.uri?.let { uris.add(it) }
                    }
                }
                data.data?.let { if (!uris.contains(it)) uris.add(it) }
            }
            if (uris.isNotEmpty()) {
                EnhancerPlugin.rememberPick(applicationContext, uris)
            }
            EnhancerPlugin.deliverPick(ok, uris)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}
