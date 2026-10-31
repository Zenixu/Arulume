import type { CapacitorConfig } from "@capacitor/cli";

/**
 * Docs/BUILD.md bagian 2.2. appId = id.aruthtale.arulume (jangan diubah setelah
 * APK dipasang: perubahan appId = aplikasi baru, data lama hilang).
 */
const config: CapacitorConfig = {
  appId: "id.aruthtale.arulume",
  appName: "Arulume",
  webDir: "out",
  android: {
    allowMixedContent: false,
    // WebView tidak boleh membuka URL eksternal (Docs/ARCHITECTURE.md bagian 9)
    webContentsDebuggingEnabled: false,
  },
};

export default config;
