# AGENTS.md: Arulume

Aturan untuk asisten coding AI (Claude Code, Cursor, Copilot, dll.) yang bekerja di repositori ini. Baca seluruhnya sebelum mengubah apa pun.

## Ringkasan proyek

Arulume adalah aplikasi Android **offline** untuk memperjelas dan memperbesar foto (video menyusul), milik brand Aruthtale. UI memakai Next.js (static export) + React di dalam Capacitor; pemrosesan AI memakai ncnn + Vulkan di native (C++/Kotlin). Dipasang lewat APK pribadi, bukan Play Store.

Dokumen utama (baca sesuai kebutuhan):
- `PRD.md`: persyaratan, kriteria penerimaan, kontrak plugin
- `design.md`: token desain, maskot, komponen, layar, teks
- `ARCHITECTURE.md`: lapisan, threading, siklus job, memori
- `TASKS.md`: daftar tugas per fase
- `BUILD.md`: build, tanda tangan, verifikasi
- `THIRD_PARTY.md`: lisensi komponen dan model

## Aturan mutlak (jangan dilanggar)

1. **Jaringan hanya untuk pembaruan.** Pemrosesan foto/video harus tetap 100% offline dan **tidak boleh** ada analitik, crash reporter jarak jauh, iklan, CDN font, atau pemuatan model dari URL. Satu-satunya pemakaian jaringan yang diizinkan: **memeriksa dan mengunduh APK pembaruan dari halaman rilis resmi** `https://github.com/Zenixu/Arulume/releases` (izin `INTERNET` + `REQUEST_INSTALL_PACKAGES`, lihat `Pembaruan.kt`). Pengecualian ini disetujui pemilik proyek pada 2026-10-31; jangan menambah pemakaian jaringan lain tanpa persetujuan baru.
2. **Jangan menjalankan inferensi AI di JavaScript/WebView.** Semua inferensi di native (ncnn).
3. **Jangan memblokir thread UI atau thread plugin Capacitor** dengan pekerjaan berat. Pekerjaan berat berjalan di `enhancer-worker`.
4. **Jangan menulis hasil ke folder `assets` atau menimpa file asli pengguna.** Simpan hasil lewat MediaStore atau `filesDir`.
5. **Jangan menggunakan fitur Next.js yang butuh server** (API routes, SSR, middleware, optimasi `next/image`). Proyek memakai `output: 'export'`.
6. **Pemanggilan Capacitor hanya di komponen client** (`'use client'`).
7. **Jangan menyalin ilustrasi atau karakter milik pihak lain** untuk maskot. Maskot harus orisinal.
8. **Jangan menambah komponen atau model baru** tanpa memperbarui `THIRD_PARTY.md` (versi, lisensi, checksum).
9. **Jangan commit** keystore, kata sandi, atau file `local.properties`.

## Konvensi

### Umum
- Bahasa kode, nama variabel, dan komentar teknis: **Inggris**. Teks UI: sesuai tabel di `design.md` bagian 8.
- Perubahan kecil dan fokus; satu tujuan per commit.
- Pesan commit gaya *Conventional Commits*: `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`.

### TypeScript/React
- TypeScript ketat; hindari `any`.
- Kontrak plugin hanya di `src/plugins/enhancer.ts`; ubah bersamaan dengan sisi Kotlin dan PRD bagian 10.
- Warna, jarak, radius, dan font **hanya** lewat token (CSS variables/Tailwind) dari `design.md`. Jangan menulis nilai warna langsung (hex) di komponen.
- Maskot hanya lewat komponen `<Mascot />`, dan hanya di layar yang diizinkan (`design.md` bagian 5.3).
- Hormati `prefers-reduced-motion`.

### Kotlin
- Metode `@PluginMethod` hanya memvalidasi dan memulai job, lalu kembali segera.
- Setiap job berakhir dengan tepat satu event terminal (`completed` atau `failed`) dan membersihkan sumber daya.
- Setiap error memakai `ErrorCode` dari daftar di PRD; tidak ada error tanpa kode.

### C++
- C++17, tanpa pengecualian bocor lintas JNI; ubah error menjadi kode yang dikembalikan ke Kotlin.
- Periksa flag pembatalan di antara tile.
- Bebaskan semua buffer native pada semua jalur (sukses, gagal, batal).

## Perintah umum

```bash
npm install
npm run build            # static export → out/
npx cap sync android     # salin out/ ke proyek Android
npx cap open android     # buka Android Studio
cd android && ./gradlew assembleDebug
npm run lint && npm run typecheck   # jika skrip tersedia
```

## Definition of done untuk setiap perubahan

- [ ] Sesuai persyaratan terkait di PRD (cantumkan ID, mis. FR-03).
- [ ] Tidak melanggar aturan mutlak di atas.
- [ ] Jika kontrak plugin berubah: sisi TS, Kotlin, dan PRD bagian 10 diperbarui bersamaan.
- [ ] Jika tampilan berubah: konsisten dengan `design.md`; perbarui `design.md` bila ada keputusan desain baru.
- [ ] Jika menambah komponen/model: `THIRD_PARTY.md` diperbarui.
- [ ] Tugas terkait dicentang di `TASKS.md`; perubahan penting dicatat di `CHANGELOG.md`.
- [ ] Build debug berhasil. Untuk perubahan native, uji di perangkat nyata jika memungkinkan.

## Hal yang belum pasti: jangan berasumsi

Item bertanda **[VERIFIKASI]** di PRD dan `ARCHITECTURE.md` (mis. A7: UI tanpa izin `INTERNET`; A8: ketersediaan model dalam format ncnn) harus **dibuktikan** lewat uji nyata sebelum dianggap benar. Jika belum terbukti, nyatakan sebagai asumsi dan jangan membangun fitur di atasnya tanpa konfirmasi pemilik. Angka performa di PRD adalah target awal sampai `benchmark-fase0.md` terisi.

Jangan mengarang versi pustaka, nama API, atau perintah. Jika tidak yakin, periksa dokumentasi resmi atau tanyakan.

## Kapan harus bertanya ke pemilik

- Keputusan di tabel "Pertanyaan terbuka" (PRD bagian 23, `design.md` bagian 13).
- Perubahan yang menyentuh privasi/offline, lisensi model, atau tanda tangan aplikasi.
- Menambah ketergantungan besar atau mengubah arsitektur (lihat ADR di `ARCHITECTURE.md`).

## Struktur direktori

```
src/app/ · src/components/ · src/plugins/ · src/store/ · src/lib/
android/app/src/main/assets/models/   # model (.param/.bin)
android/app/src/main/cpp/             # CMakeLists.txt, native-lib.cpp, ncnn/
android/app/src/main/java/.../        # MainActivity, EnhancerPlugin.kt, job/, media/
```

## Keamanan dan privasi

- Log hanya lokal dan tidak berisi isi gambar.
- Jangan mencatat URI/nama file pengguna ke log yang bisa diekspor tanpa persetujuan eksplisit.
- WebView tidak boleh membuka URL eksternal.
- `android:allowBackup="false"`.
