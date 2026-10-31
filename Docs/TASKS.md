# TASKS: Arulume

Daftar tugas bercentang per fase. Rujukan: `PRD.md` (FR-xx, bagian 20 dan 22). **Jangan pindah fase sebelum kriteria keluar fase sebelumnya terpenuhi.**

Legenda: `[ ]` belum · `[x]` selesai · **P0** wajib · **P1** sebaiknya · **P2** opsional.

---

## Fase 0: Spike teknis

Tujuan: membuktikan jalur teknis dan mengukur performa di HP sendiri.

### 0.1 Persiapan
- [x] Catat spesifikasi HP (merek, chipset, RAM, versi Android) di `benchmark-fase0.md` — Redmi Note 8, via ADB Wi-Fi
- [x] Pasang Android Studio, SDK, NDK, CMake — SDK 36, NDK 28.2.13676358, CMake 3.22.1 (build lewat Gradle CLI; IDE Android Studio tidak terpasang)
- [x] Siapkan repo, `.gitignore` (termasuk keystore, `out/`, `android/app/build/`)
- [x] Buat `THIRD_PARTY.md` awal dari template

### 0.2 UI dan Capacitor
- [x] Inisialisasi Next.js (TypeScript, Tailwind), set `output: 'export'`, `images.unoptimized`, `trailingSlash`
- [x] Tambah Capacitor, `npx cap add android`
- [x] Halaman uji sederhana tampil di HP (sync + run) — Beranda + `/uji` tampil di Redmi Note 8 (Android 11); tangkapan layar di `assets/screenshots/`
- [x] Tambah token desain (CSS variables) dari `design.md` bagian 10

### 0.3 Plugin minimal
- [x] Buat `EnhancerPlugin.kt`, daftarkan di `MainActivity` (sebelum `super.onCreate`)
- [x] Definisikan `src/plugins/enhancer.ts` (`registerPlugin`)
- [x] `getCapabilities()` mengembalikan ABI, API, RAM, core CPU (+ Vulkan, nama GPU via EGL)
- [x] Tampilkan hasil di halaman uji — `/uji` menampilkan ABI `arm64-v8a`, API 30, RAM 3633 MB, 8 core, Vulkan tersedia, GPU `Adreno (TM) 610`

### 0.4 Native
- [x] Unduh ncnn prebuilt Android Vulkan, taruh di `cpp/ncnn/` — rilis `20261020`, `libncnn.so` di `jniLibs/arm64-v8a/`, header di `cpp/ncnn/`
- [x] `CMakeLists.txt` + `enhancer_jni.cpp`, `abiFilters "arm64-v8a"`
- [x] JNI: fungsi `enhance(...)` dipanggil dari Kotlin — `EnhancerNative.nativeCreate/nativeUpscale/nativeInfo/nativeDestroy`
- [x] Deteksi Vulkan + nama GPU dikirim ke `getCapabilities()` — sudah sejak 0.3, kini ditambah `native` di hasil uji

### 0.5 Model dan inferensi
- [x] **[VERIFIKASI A8]** Dapatkan model Real-ESRGAN compact dalam format ncnn — `realesr-animevideov3` x2 + x4 dari rilis resmi `realesrgan-ncnn-vulkan-20220424` (BSD-3)
- [x] Taruh di `assets/models/`, catat checksum SHA-256 — lihat `Docs/THIRD_PARTY.md`
- [x] Proses **satu citra** end-to-end (GPU) tanpa tiling — 256² dan 512² berhasil; 1024² tanpa tile **mematikan proses** (memori)
- [x] Tambah tiling + padding, cek tidak ada garis sambungan — `tile = 256`, `pad = 16`; 1024² selesai (belum dinilai visual, §9 kosong)
- [x] Fallback CPU berfungsi — CPU justru **lebih cepat** dari Vulkan di Adreno 610 (lihat `benchmark-fase0.md` §5)

### 0.6 Simpan hasil
- [ ] Simpan JPEG ke `Pictures/Arulume` lewat MediaStore (`IS_PENDING`)
- [ ] Hasil terbuka normal di aplikasi Galeri

### 0.7 Verifikasi asumsi
- [x] **[VERIFIKASI A7]** Hapus izin `INTERNET`; UI tetap termuat dan berfungsi — terbukti di Redmi Note 8
- [x] `aapt dump permissions` pada APK tidak memuat `INTERNET` — hanya `WRITE_EXTERNAL_STORAGE (maxSdkVersion=28)`
- [x] Cek model dapat dimuat dari assets atau perlu disalin ke `filesDir` — **perlu disalin**: ncnn butuh jalur berkas nyata, jadi `EnhancerPlugin` menyalin ke `cacheDir/models` sekali (131–266 ms)

### 0.8 Benchmark
- [ ] Jalankan protokol di `benchmark-fase0.md` (GPU/CPU × tile × skala)
- [ ] Tetapkan tile default, batas input, dan angka target NFR-P final
- [ ] Perbarui PRD (angka target) bila berbeda jauh

**Kriteria keluar Fase 0:** prototipe menyimpan satu foto hasil ke galeri tanpa internet; laporan benchmark terisi; A7 dan A8 terjawab.

---

## Fase 1: MVP Foto

### 1.1 Plugin (P0)
- [ ] `pickMedia` (Photo Picker, fallback SAF), metadata + thumbnail (FR-01)
- [ ] `startEnhanceImage` asinkron + event `progress`/`completed`/`failed` (FR-03, FR-04)
- [ ] `cancelJob` dengan pembersihan file sementara (FR-04)
- [ ] Validasi batas output + dialog opsi (perkecil input / turunkan skala)
- [ ] Orientasi EXIF, alpha (PNG transparan), format JPEG/PNG/WebP, HEIC (API 28+)
- [ ] Fallback bertahap: tile kecil → CPU → `E_OUT_OF_MEMORY`
- [ ] `MediaSaver` (galeri `Pictures/Arulume` dan privat) (FR-06)
- [ ] `PreviewBuilder` untuk sebelum/sesudah (FR-05)
- [ ] `openInGallery`, `shareResult` (FR-07)
- [ ] `clearCache`

### 1.2 UI (P0), mengikuti `design.md`
- [ ] Komponen dasar: tombol utama/sekunder, chip, segmen, dialog, progress bar
- [ ] Komponen `<Mascot mood size />` dengan SVG orisinal
- [ ] Layar Beranda
- [ ] Layar Editor (preset, skala, Lanjutan)
- [ ] Layar Memproses
- [ ] Layar Hasil (slider sebelum/sesudah + cubit-zoom tersinkron + alternatif tombol)
- [ ] Layar Error
- [ ] Tema gelap (default) dan terang; font dibundel lokal
- [ ] Pemetaan preset → parameter teknis

### 1.3 P1
- [ ] Riwayat (JSON di `filesDir`) (FR-08)
- [ ] Pengaturan (FR-09)
- [ ] Batch foto (FR-11)
- [ ] Onboarding satu layar ("offline & privat")

### 1.4 P2
- [ ] Notifikasi penyelesaian (FR-12)

### 1.5 Mutu
- [ ] Jalankan set uji foto #1-#13 (PRD 18.3)
- [ ] TC-01 sampai TC-12 lulus (PRD 18.5)
- [ ] 20 proses berturut-turut: tanpa crash, memori stabil
- [ ] Uji rotasi layar dan background/foreground saat proses
- [ ] Audit aksesibilitas dasar (kontras, target sentuh, TalkBack)
- [ ] Ikon aplikasi (adaptive icon) dan nama aplikasi
- [ ] `allowBackup="false"`, deklarasi Vulkan opsional
- [ ] Lengkapi `THIRD_PARTY.md` dan layar "Tentang & lisensi"
- [ ] Build rilis bertanda tangan (lihat `BUILD.md`), ukuran ≤ 60 MB

**Kriteria keluar Fase 1:** seluruh Definition of Done Fase 1 (PRD bagian 22) terpenuhi.

---

## Fase 2: Video

- [ ] Pilih video + metadata (durasi, resolusi, fps, codec, rotasi, audio) (FR-20)
- [ ] Pengaturan video + validasi durasi/resolusi/ruang/baterai (FR-21)
- [ ] Pipeline streaming: MediaExtractor → decoder → enhance → encoder → MediaMuxer (FR-22)
- [ ] Audio passthrough (transcode AAC bila tak kompatibel)
- [ ] Foreground service + wake lock + notifikasi progres (FR-23)
- [ ] Pemantauan thermal, jeda/abort otomatis (FR-24)
- [ ] Progres per frame, jeda/lanjut, batal bersih (FR-25)
- [ ] Simpan ke `Movies/Arulume` (FR-26)
- [ ] Pratinjau video (P1) (FR-27)
- [ ] Evaluasi flicker pada klip 5, 10, 30, 60 detik; pilih model dan pengaturan terbaik
- [ ] Layar UI video sesuai `design.md`
- [ ] Perbarui izin manifest (foreground service, wake lock, notifikasi) dan `THIRD_PARTY.md`

**Kriteria keluar Fase 2:** Definition of Done Fase 2 (PRD bagian 22).

---

## Fase 3: Lanjutan (dirinci saat dimulai)

- [ ] Restorasi wajah (deteksi wajah + GFPGAN atau setara), model besar tidak dibundel
- [ ] Interpolasi frame (RIFE ncnn)
- [ ] Model tambahan (mis. anime)
- [ ] Resume video setelah terputus

---

## Tugas desain (paralel)

- [x] Buat layar Beranda di Stitch memakai prompt `design.md` bagian 11.3
- [x] Iterasi bertahap sampai gaya pas, lalu layar lain (Editor, Memproses, Hasil)
- [x] Layar Riwayat, Pengaturan, Error, dan Onboarding
- [x] Versi mode terang untuk seluruh 8 layar
- [x] Ekspor mockup ke `stitch/` (HTML + PNG) dan perbarui `design.md` bagian 11
- [x] Gambar maskot orisinal — 40 ekspresi di `assets/mascot/svg/` (SVG `currentColor`), lihat `assets/mascot/README.md`
- [x] Cek kontras seluruh pasangan warna (gelap dan terang) — 19 pasangan per mode, semua ≥ 4,5:1 (hasil di `design.md` bagian 3.1)
- [ ] Rancang ikon aplikasi (adaptive icon)

## Tugas dokumen

- [x] Samakan nama, ID aplikasi, dan path simpan di seluruh dokumen (`Arulume`, `id.aruthtale.arulume`, `Pictures/Arulume`) — 31 Okt 2026
- [x] Konsolidasi token desain: `design.md` jadi sumber tunggal, `stitch/DESIGN.md` mengikuti
- [x] Tambah `LICENSE` dan `.gitignore`
- [ ] Perbarui PRD dengan hasil benchmark (angka NFR-P)
- [ ] Isi `CHANGELOG.md` setiap rilis
