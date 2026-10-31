# Arulume

> *Bring your photos back to light.* · *Kembalikan cahaya fotomu.*

Aplikasi Android untuk memperjelas dan memperbesar foto (video menyusul), berjalan **100% offline di HP**. Bagian dari brand **Aruthtale**.

| | |
|---|---|
| Status | Fase 1 (MVP foto) berjalan — alur pilih foto → 2×/4× → pratinjau geser & zoom → simpan manual sudah terpasang di perangkat |
| Platform | Android 7.0+ (API 24), arm64 |
| Distribusi | Sideload APK, penggunaan pribadi (bukan Play Store) |
| ID aplikasi | `id.aruthtale.arulume` |
| Dokumen dibuat | 31 Oktober 2026 |

---

## Prinsip

1. **Offline total.** Model AI ikut terpasang di dalam APK. Tidak ada server, akun, unduhan model, analitik, atau koneksi internet. Izin `INTERNET` dihapus dari manifest.
2. **Privat.** Foto dan video tidak pernah keluar dari HP.
3. **Foto adalah bintangnya.** UI bersih, satu aksi utama per layar, maskot kucing hanya muncul di momen tertentu.
4. **Bertahap.** Foto dibuktikan dulu (Fase 1), video menyusul (Fase 2), fitur wajah dan lainnya terakhir (Fase 3).

## Fitur

| Fase | Fitur |
|---|---|
| 1 | Pilih foto → atur preset (Cepat / Seimbang / Maksimal) dan skala (2x/3x/4x) → proses → bandingkan sebelum/sesudah → simpan ke galeri. Riwayat, pengaturan, batch |
| 2 | Peningkatan video pendek, pemrosesan di latar (foreground service), penanganan panas |
| 3 | Restorasi wajah, interpolasi frame, model tambahan |

## Cara kerja singkat

```
UI React (Next.js static export, di WebView)
   │  Capacitor bridge: plugin "Enhancer"
   ▼
Plugin Kotlin  → picker, antrian job, decode/encode, simpan ke galeri
   │  JNI
   ▼
C++ (ncnn + Vulkan) → model Real-ESRGAN compact, tiling, fallback CPU
```

Detail lengkap ada di `ARCHITECTURE.md`.

## Tech stack

Next.js (App Router, TypeScript, static export) · React · Tailwind CSS · Capacitor · Kotlin · C++17 · ncnn (Vulkan) · Real-ESRGAN (model compact) · Android NDK + CMake.

## Prasyarat (di PC pembuild)

- Node.js LTS
- Android Studio, dengan Android SDK, **NDK**, dan **CMake** (via SDK Manager)
- JDK sesuai yang dibundel Android Studio
- ncnn prebuilt untuk Android dengan Vulkan (unduh dari halaman rilis ncnn, pilih paket `android-vulkan`)
- Model ncnn (`.param` + `.bin`), lihat `THIRD_PARTY.md`

HP: Android 7.0+ arm64, disarankan RAM ≥ 6 GB dan dukungan Vulkan.

## Memulai

```bash
# 1. Pasang dependensi
npm install

# 2. Build UI (static export ke folder out/)
npm run build

# 3. Tambah platform Android (sekali saja)
npx cap add android

# 4. Sinkronkan UI ke proyek Android
npx cap sync android

# 5. Buka Android Studio, lalu Run ke HP (USB debugging) atau Build APK
npx cap open android
```

Selama pengembangan UI, ulangi langkah 2 dan 4 setiap kali UI berubah.

Panduan build rilis, penandatanganan, dan update ada di `BUILD.md`.

## Struktur proyek

```
arulume/
├─ src/
│  ├─ app/                  # halaman Next.js
│  ├─ components/           # slider before/after, kartu, dialog, <Mascot />
│  ├─ plugins/enhancer.ts   # definisi plugin (kontrak JS ↔ Kotlin)
│  ├─ store/                # state aplikasi
│  └─ lib/                  # util
├─ android/app/src/main/
│  ├─ assets/models/        # *.param, *.bin (model dibundel)
│  ├─ cpp/                  # CMakeLists.txt, native-lib.cpp, ncnn/
│  └─ java/.../             # MainActivity, EnhancerPlugin.kt, job/, media/
├─ capacitor.config.ts
├─ next.config.js
├─ LICENSE                  # proprietary, penggunaan pribadi
├─ .gitignore
├─ Docs/                    # README, PRD, design, ARCHITECTURE, TASKS, BUILD, THIRD_PARTY, benchmark, CHANGELOG, AGENTS
├─ assets/mascot/           # 40 ekspresi Aru: svg/, png/ink/, png/paper/, preview.png, source/
├─ assets/screenshots/      # bukti uji di HP (Fase 0)
├─ src/                     # app/ (halaman), components/, plugins/enhancer.ts
├─ android/                 # proyek native: MainActivity.kt, EnhancerPlugin.kt
└─ stitch/                  # mockup Stitch: dark/ + light/ (code.html + screen.png), DESIGN.md
```

## Peta dokumen

| File | Isi |
|---|---|
| `PRD.md` | Persyaratan produk lengkap (tujuan, fitur, kriteria, risiko) |
| `design.md` | Desain, token, maskot, komponen, layar, prompt Stitch |
| `ARCHITECTURE.md` | Arsitektur teknis, alur job, memori, threading |
| `TASKS.md` | Daftar tugas bercentang per fase |
| `fase_done.md` | Arsip fase yang sudah selesai + bukti uji per perangkat |
| `BUILD.md` | Build, penandatanganan, instalasi, update, verifikasi |
| `THIRD_PARTY.md` | Komponen dan model pihak ketiga beserta lisensi |
| `benchmark-fase0.md` | Template catatan benchmark Fase 0 |
| `CHANGELOG.md` | Riwayat perubahan |
| `AGENTS.md` | Aturan untuk asisten coding AI |
| `../LICENSE` | Lisensi aplikasi (proprietary, penggunaan pribadi) |
| `../stitch/` | Ekspor desain dari Google Stitch (turunan `design.md`) |

## Lisensi dan penggunaan

Aplikasi ini untuk **penggunaan pribadi**. Teks lisensinya ada di `LICENSE` (seluruh hak dilindungi; bukan open source). Lisensi tiap komponen dan model dicatat di `THIRD_PARTY.md`. Jika suatu hari dibagikan ke orang lain, periksa ulang lisensi seluruh model (beberapa, seperti CodeFormer, hanya non-komersial).
