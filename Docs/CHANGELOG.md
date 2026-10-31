# Changelog

Semua perubahan penting pada Arulume dicatat di sini. Format mengikuti [Keep a Changelog](https://keepachangelog.com/) dan penomoran versi mengikuti [Semantic Versioning](https://semver.org/) (`MAJOR.MINOR.PATCH`). Untuk Android, `versionCode` naik setiap rilis.

Kategori: **Added** (baru) · **Changed** (diubah) · **Fixed** (diperbaiki) · **Removed** (dihapus) · **Security** (keamanan).

---

## [0.1.0] — 2026-10-31

### Added
- **Video (Fase 2):** layar `/video` — pilih klip, lihat metadata (durasi, resolusi, fps), pilih target
  HD (1280) atau Full HD (1920), lalu ekstrak di latar. Keluaran H.264 + audio asli, hasil di
  `cacheDir/hasil-video/`, disimpan manual ke `Movies/Arulume` sebagai `Arulume_{yyyyMMdd-HHmmss}.mp4`.
- Progres video per frame (`progress` dengan `kind:"video"`, `frame`, `totalFrame`) + notifikasi progres.
- **Proses video tahan terputus:** hasil ditulis per segmen (2–3 dtk) dengan checkpoint
  `filesDir/video-job.json` (`VideoState`), segmen disambung ulang tanpa re-encode (`VideoGabung`),
  dan decode dilanjutkan dari sync frame terakhir (`FrameStream.lompatKe`). Kalau aplikasi ditutup,
  proses dibunuh sistem, atau HP kehabisan daya, pekerjaan **tidak diulang dari awal**.
- Tombol **"Jeda dulu"** (menyimpan potongan, bukan membuang) dan **"Lanjutkan"** di layar `/video`;
  layanan `START_STICKY` melanjutkan sendiri job yang belum selesai saat proses dihidupkan kembali.
- Kartu pintasan di Beranda: "Video sedang diproses" / "Video terhenti · X% · ketuk untuk melanjutkan".
- Pengaturan → **Pembaruan**: tombol "Periksa update" membuka halaman rilis GitHub di browser, dan
  nomor versi dibaca dari paket yang terpasang (bukan konstanta di kode).

### Changed
- `versionCode` 2, `versionName` 0.1.0; build rilis ditandatangani kunci rilis di luar repo
  (`~/.arulume/release.properties`), dengan fallback kunci debug bila kunci tidak tersedia.

### Fixed
- Berkas video hasil yang rusak ketika proses berhenti di tengah (MP4 hanya menulis `moov` saat ditutup).
- Warna hasil video yang sebelumnya gelap/oversaturasi (pemilihan format warna runtime, kernel penajam
  berbobot total 1, UV digabung hanya bila kedua plane 2×2).

### Security
- Aplikasi tetap **tanpa izin `INTERNET`** dan tanpa pustaka jaringan. Tombol pembaruan hanya membuka
  URL rilis resmi lewat `ACTION_VIEW`; unduhan dan pemasangan diserahkan ke browser/sistem.

## [Unreleased]

### Added
- **MVP foto (Fase 1):** alur pilih foto → Editor (2×/4×) → proses asinkron → layar Hasil
  (pembanding geser satu jari, cubit sampai 5×) → tombol Simpan manual ke `Pictures/Arulume`
  sebagai `Arulume_{yyyyMMdd-HHmmss}.jpg`. Hasil **tidak** masuk galeri otomatis.
- Pilih banyak foto sekaligus (Photo Picker `ACTION_PICK_IMAGES` + `EXTRA_ALLOW_MULTIPLE`, fallback SAF),
  maksimal 20 foto, diproses berurutan dalam satu job; layar `/batch` dengan keadaan per foto dan "Simpan semua".
- Riwayat (`filesDir/riwayat.json`, maks 60 entri) + layar `/riwayat` (pilih & hapus dua tahap) dan
  `/riwayat/lihat`; Beranda menampilkan 3 entri terbaru.
- Pengaturan: tema Gelap/Terang/Ikuti sistem, skala bawaan, format hasil JPEG/PNG/WebP, bersihkan cache,
  info versi, dan layar Tentang & lisensi.
- Notifikasi progres lokal (`ProsesNotif.kt`) + `EnhancerService` foreground `dataSync` +
  `PARTIAL_WAKE_LOCK`; `JobRunner` memindahkan job keluar dari plugin sehingga tetap selesai walau
  WebView/Activity sudah hilang. Progres per tile datang dari C++ lewat `ProgressBridge`.
- Onboarding satu layar `/mulai` (tampil sekali lewat `arulume.onboarding`).
- Ketahanan berkas: orientasi EXIF, alpha PNG/WebP dipasang ulang setelah diperbesar, HEIC (API 28+),
  deteksi berkas terpotong (`E_DECODE_FAILED`), fallback tile bertahap sampai `E_OUT_OF_MEMORY`.
- Ikon peluncur dari logo Arulume (tanpa teks): adaptive icon + lapisan monokrom + 5 densitas PNG,
  dibuat ulang lewat `assets/logo/buat-ikon.py`.
- **Mesin inferensi native (Fase 0.5):** ncnn `20261020` (`libncnn.so` + header, Vulkan opsional) di
  `jniLibs/arm64-v8a/` dan `cpp/ncnn/`; `enhancer_jni.cpp` (JNI `nativeCreate`/`nativeUpscale`/
  `nativeInfo`/`nativeDestroy`, tile + padding), `EnhancerNative.kt`, CMake di `app/build.gradle`
  (`ndkVersion 28.2.13676358`, CMake 3.22.1).
- Model resmi `realesr-animevideov3-x2` dan `-x4` (BSD-3) di `assets/models/`, checksum tercatat
  di `THIRD_PARTY.md`.
- `runSelfTest()` di `EnhancerPlugin` (berjalan di `enhancer-worker`, tidak memblokir UI) +
  tombol **Uji GPU** / **Uji CPU** di `/uji` yang menyapu ukuran 256², 512², 1024².
- Hasil benchmark nyata di `benchmark-fase0.md` §5 + tangkapan layar `assets/screenshots/fase0-uji-mesin.png`.
- **Scaffold Fase 0 (`0.0.1`):** Next.js 16 (App Router, static export) + React 19 + Tailwind v4,
  Capacitor 8.6 (`android/`, `id.aruthtale.arulume`), plugin Kotlin `EnhancerPlugin` dengan
  `getCapabilities()` (ABI, API, RAM, core CPU, Vulkan, nama GPU via EGL) + `clearCache()`.
- Token desain diterapkan di `src/app/globals.css` (Tailwind v4 `@theme`, mode gelap default).
- Font Plus Jakarta Sans dibundel lokal (`src/app/fonts/`) — tanpa CDN.
- Komponen `<Mascot />` (`src/components/mascot/`) membaca 40 ekspresi sebagai SVG `currentColor`.
- Layar Beranda sesuai `design.md` 7.1 + halaman uji `/uji` untuk `getCapabilities()`.
- Manifest Android: izin `INTERNET` dihapus, `allowBackup="false"` + `data_extraction_rules`,
  Vulkan sebagai fitur opsional, `WRITE_EXTERNAL_STORAGE` maksimal API 28, `abiFilters "arm64-v8a"`.
- Dokumen perencanaan awal: `PRD.md`, `design.md`, `README.md`, `ARCHITECTURE.md`, `TASKS.md`, `BUILD.md`, `THIRD_PARTY.md`, `benchmark-fase0.md`, `AGENTS.md`.
- Nama produk ditetapkan: **Arulume** (ID aplikasi `id.aruthtale.arulume`).
- `LICENSE` (proprietary, penggunaan pribadi) dan `.gitignore` (Node, Android, keystore, model).
- Mockup desain 8 layar × 2 mode (gelap & terang) dari Google Stitch, diekspor ke `stitch/`:
  Beranda, Editor, Memproses, Hasil, Riwayat, Pengaturan, Error, Onboarding.
- Design system Stitch "Arulume Warm Dark" / "Arulume Warm Light" (`stitch/DESIGN.md`, `stitch/DESIGN-light.md`).
- Layar Onboarding masuk ke `design.md` bagian 7.8.
- Aset maskot: 40 ekspresi di `assets/mascot/` — SVG satu jalur `currentColor` (~2,7 KB/berkas),
  PNG `ink`/`paper` 3×, `preview.png`, dan sumber asli. Dipakai untuk 10 mood inti `design.md` bagian 5.4.

### Changed
- Beranda tidak lagi memakai slogan: judul "Kembalikan cahaya fotomu" → **"Selamat datang di Arulume"**,
  subjudul → **"Perjelas foto dan video lama, semuanya di HP-mu."**
- Maskot: mata sebelumnya hanya lubang transparan di dalam path, sekarang diberi pupil
  (`src/components/mascot/eyes.ts`) untuk 5 ekspresi yang sudah diperiksa.
- Ikon vektor lama (`drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`) dihapus;
  ikon sekarang PNG mipmap dari `assets/logo/ikon.png`.
- Seluruh dokumen memakai nama **Arulume**: `prd-local-enhancer.md` → `PRD.md`, `Local Enhancer` → `Arulume`,
  `id.pribadi.enhancer` → `id.aruthtale.arulume`, `Pictures/Enhancer` → `Pictures/Arulume`, `Movies/Enhancer` → `Movies/Arulume`.
- `design.md` ditetapkan sebagai **sumber tunggal token**; ditambah tabel pemetaan token Material 3 → Arulume (bagian 3.4).
- Aturan desain baru: tanpa bottom navigation bar, tanpa jargon di layar utama (lihat `stitch/README.md`).
- Ekspor Stitch lama (`stitch_arulume_offline_photo_enhancer/`) ditandai **legacy**; digantikan `stitch/`.

### Removed
- Catatan "nama kerja Local Enhancer" di `README.md` (tidak relevan lagi).

### Fixed
- Kontras mode terang: `accent-text`, `success`, `danger`, dan `warning` gagal 4,5:1 (3,51-4,38:1).
  Digelapkan menjadi `#8E6200`, `#28784B`, `#BD3B3B`, `#9C5A16` sehingga semua 19 pasangan lolos AA
  (terendah 4,61:1). Mockup mode terang ikut diperbarui dan di-render ulang.

---

## Template rilis (salin saat merilis)

```
## [0.1.0] - YYYY-MM-DD  (versionCode: 1)

### Added
- 

### Changed
- 

### Fixed
- 

### Security
- Izin INTERNET tidak ada (diverifikasi dengan aapt).

Catatan: ukuran APK ___ MB · model: ___ · diuji di: ___ (Android ___)
```

## Rencana versi (acuan)

| Versi | Fase | Isi utama |
|---|---|---|
| 0.0.x | Fase 0 | Spike teknis: satu foto diproses dan tersimpan, benchmark |
| 0.1.0 | Fase 1 (MVP) | Alur foto lengkap, riwayat, pengaturan |
| 0.2.0 | Fase 1 (lanjutan) | Batch foto, penyempurnaan UI/UX |
| 1.0.0 | Fase 1 selesai | Memenuhi Definition of Done Fase 1 |
| 1.1.0+ | Fase 2 | Peningkatan video |
| 1.2.0+ | Fase 3 | Restorasi wajah, interpolasi frame |

Versi di atas hanya acuan; sesuaikan dengan kemajuan nyata.
