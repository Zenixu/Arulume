# Changelog

Semua perubahan penting pada Arulume dicatat di sini. Format mengikuti [Keep a Changelog](https://keepachangelog.com/) dan penomoran versi mengikuti [Semantic Versioning](https://semver.org/) (`MAJOR.MINOR.PATCH`). Untuk Android, `versionCode` naik setiap rilis.

Kategori: **Added** (baru) · **Changed** (diubah) · **Fixed** (diperbaiki) · **Removed** (dihapus) · **Security** (keamanan).

---

## [Unreleased]

### Added
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
