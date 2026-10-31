package id.aruthtale.arulume

import android.os.Bundle
import com.getcapacitor.BridgeActivity

/**
 * Docs/ARCHITECTURE.md bagian 6: plugin harus didaftarkan SEBELUM `super.onCreate`,
 * kalau tidak bridge belum siap saat WebView memanggilnya.
 */
class MainActivity : BridgeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        registerPlugin(EnhancerPlugin::class.java)
        super.onCreate(savedInstanceState)
    }
}
