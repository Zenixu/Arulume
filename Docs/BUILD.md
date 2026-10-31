# BUILD: Arulume

Panduan membangun, menandatangani, memasang, dan memperbarui APK Arulume untuk pemakaian pribadi.

| | |
|---|---|
| Versi | 1.0 (Draft) |
| Tanggal | 31 Oktober 2026 |

---

## 1. Prasyarat

| Kebutuhan | Keterangan |
|---|---|
| Node.js LTS | Untuk build Next.js dan Capacitor CLI (terpasang: Node 22.23.3) |
| Android Studio | Termasuk Android SDK; pasang **NDK** dan **CMake** lewat SDK Manager |
| **JDK 21** | **Wajib.** Gradle 8.14.3 tidak bisa jalan di Java 25 (`Unsupported class file major version 69`). Lihat 1.1. |
| ncnn prebuilt | Paket Android dengan Vulkan dari halaman rilis ncnn, ekstrak ke `android/app/src/main/cpp/ncnn/` |
| Model ncnn | `.param` + `.bin` di `android/app/src/main/assets/models/` (lihat `THIRD_PARTY.md`) |
| HP | Android 7.0+ arm64, mode pengembang dan USB debugging aktif |

### 1.1 JDK untuk Gradle

Android Gradle Plugin 8.13 + Gradle 8.14.3 mendukung JDK 17-21, **bukan 25**. Kalau `java -version`
menunjukkan 25, arahkan Gradle ke JDK 21 lewat berkas **global** (jangan di repo, karena path-nya
khas mesin):

```bash
# 1. Unduh JDK 21 (contoh: Temurin)
curl -sL -o /tmp/jdk21.tar.gz \
  "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"
mkdir -p ~/.jdks && tar -xzf /tmp/jdk21.tar.gz -C ~/.jdks

# 2. Arahkan Gradle ke JDK itu
cat >> ~/.gradle/gradle.properties <<'EOF'
org.gradle.java.home=/home/<user>/.jdks/jdk-21.0.13+9
org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8
EOF

# 3. Pastikan
cd android && ./gradlew --stop && ./gradlew --version | grep "Launcher JVM"
```

### 1.2 Versi yang dipakai (31 Oktober 2026)

| Komponen | Versi |
|---|---|
| Node.js / npm | 22.23.3 / 10.9.9 |
| Next.js / React | 16.4.3 / 19.3.0 |
| Tailwind CSS | 4 (CSS-first, tanpa `tailwind.config.js`) |
| Capacitor | 8.6.0 (core, cli, android), preferences 8.0.1 |
| Android Gradle Plugin / Gradle | 8.13.0 / 8.14.3 |
| Kotlin | 2.2.20 |
| JDK untuk Gradle | Temurin 21.0.13 |
| compileSdk / targetSdk / minSdk | 36 / 36 / 24 |
| NDK / CMake | 28.2.13676358 / 3.22.1 |
| ABI | `arm64-v8a` saja |

## 2. Konfigurasi penting

### 2.1 `next.config.js`
```js
module.exports = {
  output: 'export',
  images: { unoptimized: true },
  trailingSlash: true,
};
```

### 2.2 `capacitor.config.ts`
```ts
import type { CapacitorConfig } from '@capacitor/cli';
const config: CapacitorConfig = {
  appId: 'id.aruthtale.arulume',
  appName: 'Arulume',
  webDir: 'out',
};
export default config;
```

### 2.3 Gradle (modul `app`), potongan
- `minSdk 24`
- `ndk { abiFilters "arm64-v8a" }`
- `externalNativeBuild { cmake { path "src/main/cpp/CMakeLists.txt" } }`
- `versionCode` dan `versionName` dinaikkan setiap rilis

### 2.4 `AndroidManifest.xml`, potongan
- **Hapus** `<uses-permission android:name="android.permission.INTERNET" />` **[VERIFIKASI A7: pastikan UI tetap termuat]**
- `android:allowBackup="false"`
- Fitur Vulkan sebagai opsional: `<uses-feature android:name="android.hardware.vulkan.version" android:version="0x400003" android:required="false" />`
- `WRITE_EXTERNAL_STORAGE` hanya dengan `android:maxSdkVersion="28"`
- Fase 2: tambahkan izin foreground service (dengan tipe yang sesuai versi Android terbaru), `WAKE_LOCK`, dan `POST_NOTIFICATIONS`

## 3. Build pengembangan (debug)

```bash
npm install
npm run build            # hasil: out/
npx cap add android      # sekali saja
npx cap sync android     # salin out/ ke android/
npx cap open android     # buka Android Studio
```

Di Android Studio: sambungkan HP lewat USB, pilih perangkat, tekan **Run**. APK debug juga bisa dibuat lewat **Build → Build APK(s)**.

Alur harian saat mengubah UI: `npm run build` → `npx cap sync android` → Run.
Saat hanya mengubah Kotlin/C++: cukup Run dari Android Studio.

## 4. Build rilis (untuk pemakaian harian)

### 4.1 Buat keystore (sekali saja)
```bash
keytool -genkeypair -v -keystore arulume.jks -alias arulume \
  -keyalg RSA -keysize 2048 -validity 10000
```

**Penting:**
- Simpan `arulume.jks` dan kedua kata sandinya di tempat aman, **cadangkan** di lebih dari satu tempat.
- Update aplikasi hanya bisa dipasang di atas versi lama jika tanda tangannya **sama**. Jika keystore hilang, aplikasi harus dihapus dulu dan data lokal ikut hilang.
- Jangan commit keystore atau kata sandinya ke Git. Simpan kredensial di `~/.gradle/gradle.properties` atau variabel lingkungan, bukan di repo.

### 4.2 Bangun dan tandatangani
Cara termudah: Android Studio → **Build → Generate Signed Bundle / APK → APK**, pilih keystore, tipe `release`.

Alternatif lewat Gradle dengan `signingConfigs` yang membaca kredensial dari properti lokal, lalu:
```bash
cd android && ./gradlew assembleRelease
```

Hasil: `android/app/build/outputs/apk/release/app-release.apk`.

### 4.3 Perkecil APK (opsional)
- Pertahankan hanya `arm64-v8a`.
- Jangan membundel model besar (> 100 MB); salin manual ke penyimpanan HP (Fase 3).
- Target Fase 1: ≤ 60 MB.

## 5. Verifikasi sebelum dipasang

| Pemeriksaan | Perintah / cara | Hasil yang diharapkan |
|---|---|---|
| Tidak ada izin internet | `aapt dump permissions app-release.apk` | `INTERNET` tidak muncul |
| Ukuran | Lihat ukuran file | ≤ 60 MB (Fase 1) |
| ABI | `unzip -l app-release.apk \| grep lib/` | Hanya `arm64-v8a` |
| Model ada | `unzip -l app-release.apk \| grep models/` | File `.param`/`.bin` terdaftar |
| Checksum model | Bandingkan SHA-256 dengan `THIRD_PARTY.md` | Sama |
| Tanda tangan | `apksigner verify --print-certs app-release.apk` | Sertifikat milik sendiri |
| Offline | Pasang, aktifkan mode pesawat, proses satu foto | Berhasil dan tersimpan di galeri |

## 6. Instalasi di HP (tanpa Play Store)

1. Kirim APK ke HP (kabel USB, Bluetooth, atau penyimpanan lain).
2. Aktifkan **Pasang dari sumber tidak dikenal** untuk aplikasi yang akan membuka APK (mis. Files).
3. Buka APK dan instal.
4. Google Play Protect mungkin menampilkan peringatan untuk aplikasi tak dikenal. Itu normal untuk APK pribadi; pilih lanjutkan jika kamu yang membangunnya.

Alternatif lewat kabel (HP dalam mode USB debugging):
```bash
adb install -r app-release.apk
```

## 7. Update aplikasi

1. Naikkan `versionCode` (bilangan bulat, selalu bertambah) dan `versionName`.
2. Tandatangani dengan **keystore yang sama**.
3. Pasang di atas versi lama (`adb install -r` atau buka APK). Riwayat dan pengaturan tetap ada.
4. Catat perubahan di `CHANGELOG.md`.

## 8. Pemecahan masalah umum

| Gejala | Kemungkinan penyebab | Tindakan |
|---|---|---|
| Layar putih saat dibuka | `out/` belum tersinkron, atau UI butuh izin yang dihapus | `npm run build` + `npx cap sync android`; cek log (Chrome `chrome://inspect` untuk WebView) |
| Build NDK gagal | NDK/CMake belum terpasang atau versi tidak cocok | Pasang via SDK Manager, samakan versi di Gradle |
| `UnsatisfiedLinkError` | `.so` tidak terbawa (ABI salah) | Pastikan HP arm64 dan `abiFilters` benar |
| Aplikasi crash saat foto besar | Memori habis | Kecilkan tile, turunkan skala; periksa batas output |
| Hasil tidak muncul di galeri | `IS_PENDING` tidak dikembalikan ke 0 atau path salah | Periksa `MediaSaver` dan `RELATIVE_PATH` |
| Gagal update: "paket konflik" | Tanda tangan berbeda | Pakai keystore yang sama; jika hilang, hapus dulu aplikasi lama |
| Vulkan tidak terdeteksi | HP atau driver tidak mendukung | Aplikasi memakai CPU (lebih lambat); periksa `getCapabilities()` |

## 9. Daftar periksa rilis

- [ ] `versionCode` dan `versionName` dinaikkan
- [ ] `CHANGELOG.md` diperbarui
- [ ] `THIRD_PARTY.md` sesuai komponen dan model yang dibundel
- [ ] Izin `INTERNET` tidak ada (diverifikasi dengan `aapt`)
- [ ] Set uji foto lulus; offline terverifikasi
- [ ] APK bertanda tangan dengan keystore yang benar
- [ ] Keystore dan sandi sudah dicadangkan
