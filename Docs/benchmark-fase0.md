# Benchmark Fase 0: Arulume

Template catatan benchmark. Isi saat Fase 0 berjalan. Hasilnya menentukan tile default, batas input, dan angka target NFR-P di PRD. **Isi dengan hasil pengukuran nyata; jangan memperkirakan.**

| | |
|---|---|
| Tanggal uji | |
| Penguji | |
| Versi aplikasi/commit | |

---

## 1. Perangkat uji

| Item | Nilai |
|---|---|
| Merek / tipe | Xiaomi Redmi Note 8 (`ginkgo`) |
| Chipset | Qualcomm Snapdragon 665 (Trinket), 8 core, aarch64 |
| GPU | Qualcomm Adreno 610 (OpenGL ES 3.2 V@0502.0) |
| RAM | 3.720.708 kB total (~3,55 GiB, varian 4 GB); MemAvailable saat dicek ~1,3 GB |
| Versi Android (API) | Android 11 (API 30), MIUI V12.5.3.0.RCOIDXM, patch keamanan 2022-07-01 |
| Dukungan Vulkan (versi) | **Vulkan 1.1** (`android.hardware.vulkan.version = 4198400` = 0x401000), + `vulkan.compute`, `vulkan.level = 1` |
| Baterai saat uji | |
| Mode daya | (normal / hemat) |
| Suhu ruang / kondisi | |

Layar: 1080 × 2340 px, densitas 440 dpi (≈ 393 dp lebar). Kanvas desain 360 dp aman; aset ikut bucket xxhdpi.
ABI: `arm64-v8a, armeabi-v7a, armeabi` → `abiFilters "arm64-v8a"` sesuai rencana.

Sumber data: `adb` (REA: `inspect_adb_device`, `list_adb_features`, `inspect_adb_display`) + `adb shell cat /proc/meminfo`, `/proc/cpuinfo`, `dumpsys SurfaceFlinger`. Diambil 31 Okt 2026 via ADB Wi-Fi.

**Catatan awal (bukan hasil ukur):** RAM hanya ~3,55 GiB dan Adreno 610 kelas menengah-bawah, jadi
target NFR-P2 (1000×1000 → 4x ≤ 20 detik) dan batas output 4096 px berisiko tidak realistis.
Angka final ditentukan bagian 5-6 setelah protokol dijalankan, lalu PRD diperbarui.

## 2. Perangkat lunak yang diuji

| Item | Nilai |
|---|---|
| Versi ncnn | |
| Model | |
| Format model | |
| Ukuran model | |
| SHA-256 model | |
| Jumlah thread CPU | |
| Padding tile | |

## 3. Set foto uji

Gunakan set standar dari PRD bagian 18.3 (minimal #1, #2, #7, #8, #9, #13).

| # | Nama file | Resolusi | Format | Ukuran file |
|---|---|---|---|---|
| 1 | | | | |
| 2 | | | | |
| 7 | | | | |
| 8 | | | | |
| 9 | | | | |
| 13 | | | | |

## 4. Protokol

1. Mulai dari kondisi HP dingin (tutup aplikasi berat lain, layar menyala, kecerahan sedang).
2. Untuk setiap kombinasi, jalankan **3 kali**, catat rata-rata. Beri jeda agar suhu turun antar uji.
3. Catat: waktu total, waktu inferensi saja, puncak memori (Android Studio Profiler), suhu sebelum dan sesudah (jika bisa), serta apakah berhasil atau gagal.
4. Kombinasi: **Mesin** (GPU/CPU) × **Tile** (64/128/200/256) × **Skala** (2x/4x).
5. Catat juga gejala: HP panas, aplikasi dimatikan sistem, garis sambungan antar tile, warna aneh.

## 5. Hasil awal: mesin native (citra sintetis)

Diukur lebih dulu dengan **citra sintetis** (gradien + kotak) supaya tidak
bergantung pada isi foto dan bisa diulang persis. Tombol **Uji GPU** / **Uji CPU**
di halaman *Info perangkat* menjalankan tiga ukuran berturut-turut.

- Perangkat: Redmi Note 8 (`ginkgo`), Snapdragon 665, Adreno 610, RAM 3.633 MB, Android 11.
- Model: `realesr-animevideov3-x2` (ncnn, 1,2 MB), keluaran selalu 2x.
- Thread CPU: 4. Ukuran 1024 memakai potongan `tile = 256`, `pad = 16`.
- Angka = **rentang dari dua kali jalan** (bukan rata-rata 3 kali seperti protokol §4).
  Selisih antar jalan cukup besar (CPU 1024²: 32,7 vs 38,6 dtk) — HP ini juga dipakai
  untuk hal lain, jadi suhu dan beban latar memengaruhi.

| Mesin | Masuk | Keluaran | Muat model | Inferensi (2× jalan) | Catatan |
|---|---|---|---|---|---|
| CPU | 256² | 512² | 131–242 ms | **1,50–1,79 dtk** | satu lintasan |
| CPU | 512² | 1024² | 142–180 ms | **5,70–7,32 dtk** | satu lintasan |
| CPU | 1024² | 2048² | 166–266 ms | **32,7–38,6 dtk** | 16 tile |
| GPU (Vulkan) | 256² | 512² | 4,80–5,65 dtk | **2,34–2,40 dtk** | muat termasuk inisialisasi Vulkan |
| GPU (Vulkan) | 512² | 1024² | 4,15–4,52 dtk | **9,57–9,74 dtk** | |
| GPU (Vulkan) | 1024² | 2048² | 3,28–4,93 dtk | **45,1–45,7 dtk** | 16 tile |

**Temuan penting:**

1. **Vulkan lebih lambat daripada CPU di HP ini** pada semua ukuran (2,34 vs 1,50 dtk;
   45,1 vs 32,7 dtk). Model ini hanya 41 lapisan dengan tensor kecil, sehingga
   biaya *dispatch* per lapisan di Adreno 610 lebih besar daripada keuntungan paralelnya.
   Inisialisasi Vulkan juga menambah ±4 detik pada pemuatan model — untuk satu foto,
   kerugian itu tidak pernah terbayar.
2. **Satu lintasan 1024² mematikan aplikasi** (proses hilang tanpa log Java — kehabisan
   memori native). Pemotongan tile bukan optimasi, melainkan syarat agar tidak mati.
3. Hasil CPU dan GPU identik pada piksel contoh (`-16449295, -16744207, -65537`),
   jadi jalur Vulkan secara numerik benar — hanya tidak ekonomis.
4. Kecepatan ini **jauh dari cukup** untuk foto 12 MP: 1024² saja butuh ±33 detik.
   Perlu model lebih ringan, resolusi kerja lebih kecil, atau pembatasan sisi panjang.
5. Tangkapan layar bukti: `assets/screenshots/fase0-uji-mesin.png` (tabel GPU).

## 6. Hasil: foto #1 (referensi)

| Mesin | Tile | Skala | Waktu total (dtk) | Waktu inferensi (dtk) | Puncak memori (MB) | Suhu awal→akhir | Berhasil? | Catatan |
|---|---|---|---|---|---|---|---|---|
| GPU | 64 | 2x | | | | | | |
| GPU | 128 | 2x | | | | | | |
| GPU | 200 | 2x | | | | | | |
| GPU | 256 | 2x | | | | | | |
| GPU | 64 | 4x | | | | | | |
| GPU | 128 | 4x | | | | | | |
| GPU | 200 | 4x | | | | | | |
| GPU | 256 | 4x | | | | | | |
| CPU | 64 | 2x | | | | | | |
| CPU | 128 | 2x | | | | | | |
| CPU | 64 | 4x | | | | | | |
| CPU | 128 | 4x | | | | | | |

## 7. Hasil: foto lain

Ulangi tabel yang sama (atau versi ringkas dengan tile terbaik) untuk foto #2, #7, #8, #9, #13.

| Foto | Mesin | Tile | Skala | Waktu total | Puncak memori | Berhasil? | Catatan |
|---|---|---|---|---|---|---|---|
| #2 | | | | | | | |
| #7 | | | | | | | |
| #8 | | | | | | | |
| #9 | | | | | | | |
| #13 | | | | | | | |

## 8. Uji stabilitas

| Uji | Hasil | Catatan |
|---|---|---|
| 20 proses berturut-turut (foto #1, tile terbaik) | | Memori awal vs akhir: |
| Rotasi layar saat proses | | |
| Aplikasi ke latar belakang saat proses | | |
| Mode pesawat aktif | | |
| Fallback GPU → CPU dipicu manual | | |

## 9. Penilaian kualitas visual

Skala: 1 (tidak terlihat peningkatan) sampai 5 (sangat jelas).

| Foto | Model | Skor ketajaman | Skor kealamian | Artefak (garis tile, halo, warna) | Catatan |
|---|---|---|---|---|---|
| #1 | | | | | |
| #2 | | | | | |
| #11 (wajah) | | | | | |
| #12 (teks) | | | | | |
| #13 (noise) | | | | | |

## 10. Verifikasi asumsi

| Asumsi | Hasil | Bukti / catatan |
|---|---|---|
| A7: UI termuat tanpa izin `INTERNET` | ☑ **Ya** ☐ Tidak | APK debug `0.0.1` terpasang di Redmi Note 8. Logcat: `Loading app at https://localhost` → seluruh chunk JS, CSS, dan font `plus_jakarta_sans_latin*.woff2` dimuat dari aset lokal. `aapt2 dump permissions` → hanya `WRITE_EXTERNAL_STORAGE (maxSdkVersion=28)` + `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. **Tidak ada `INTERNET`.** Tangkapan layar: `assets/screenshots/fase0-beranda.png`. |
| A8: model compact tersedia/terkonversi ke ncnn | ☑ **Ya** ☐ Tidak | Model resmi `realesr-animevideov3-x2` (BSD-3) dari rilis `realesrgan-ncnn-vulkan-20220424` dipasang di `android/app/src/main/assets/models/`. `getCapabilities().models` kini melaporkan `realesr-animevideov3-x2` dan `-x4` dengan `nativeScale` 2 dan 4. Inferensi nyata berhasil di perangkat — lihat §5. |
| Model dapat dimuat langsung dari assets | ☐ Ya ☑ **Perlu disalin ke `cacheDir`** | ncnn butuh jalur berkas nyata. `EnhancerPlugin.copyAsset()` menyalin `.param`/`.bin` ke `cacheDir/models` sekali, lalu `nativeCreate` memuatnya. Waktu muat 131–266 ms (CPU). |
| Vulkan tersedia dan stabil di HP target | ☑ **Ya** (tersedia) ☑ **Ya** (stabil, tapi lebih lambat) | `vulkanAvailable = true`, `gpuName = "Adreno (TM) 610"`. Inferensi Vulkan berjalan tanpa crash dan hasilnya identik dengan CPU, **tetapi 15–60% lebih lambat** (§5). Kesimpulan: Vulkan bukan jalur cepat di perangkat ini. |

## 11. Keputusan hasil benchmark

| Keputusan | Nilai final | Dasar (rujuk bagian di atas) |
|---|---|---|
| Model default foto | `realesr-animevideov3-x2` (sementara) | Satu-satunya model terpasang dan terbukti jalan. Belum ada pembanding. |
| Tile default (GPU) | 256 | §5: tanpa tile, 1024² mematikan proses. Tile 256 = syarat memori, bukan optimasi kecepatan. |
| Tile default (CPU) | 256 | Sama; CPU justru lebih cepat dari GPU. |
| Batas sisi panjang output (px) | 2048 | Batas tertinggi yang terbukti selesai tanpa dimatikan sistem. |
| Batas input per skala (px) | 2x: 1024, 3x: belum, 4x: belum | Hanya x2 yang diukur; x4 belum dijalankan di perangkat. |
| Pemetaan preset Cepat | CPU, 2x, sisi masuk ≤ 512 | 5,7–7,3 dtk untuk 512² — masih terasa lama tapi paling dekat ke "cepat". |
| Pemetaan preset Seimbang | CPU, 2x, sisi masuk ≤ 1024 | 32,7–38,6 dtk. |
| Pemetaan preset Maksimal | **belum bisa dipetakan** | Perlu model/resolusi lain; x4 belum diukur dan kemungkinan kehabisan memori. |
| NFR-P2 (foto 1000×1000, 4x, GPU) | ☑ **Tidak terpenuhi** | Target ≤ 20 dtk. Yang terukur: 1024² **2x** di GPU = 45,1–45,7 dtk. 4x akan jauh lebih berat. |
| NFR-P3 (CPU fallback) | ☑ **Tidak terpenuhi** | Target ≤ 120 dtk untuk 4x; 1024² **2x** di CPU saja 32,7–38,6 dtk, dan x4 ≈ 4× beban. |
| Apakah video layak di HP ini? | ☑ **Tidak** | Satu frame 512² butuh 5,7–7,3 dtk CPU. 300 frame (10 dtk @30fps) ≈ 29–37 menit. |

## 12. Kesimpulan dan tindak lanjut

- **Ringkasan hasil:** jalur native sudah utuh (JS → plugin → JNI → ncnn → piksel balik)
  dan terbukti berjalan di perangkat nyata tanpa internet. Model BSD-3 resmi dipakai,
  jadi tidak ada risiko lisensi dari APK pihak lain.
- **Masalah yang ditemukan:**
  1. Vulkan di Adreno 610 **lebih lambat** daripada CPU 4 thread untuk model sekecil ini.
  2. Tanpa tile, citra 1024² menghabiskan memori dan proses dimatikan sistem.
  3. Kecepatan absolut terlalu rendah: 32,7–38,6 dtk (CPU) untuk 1024²→2048².
- **Perubahan yang harus dibawa ke PRD:**
  - NFR-P2 dan NFR-P3 perlu angka baru yang realistis, atau ruang lingkup foto dibatasi
    (mis. sisi masuk ≤ 768 px) dan janji waktunya diturunkan.
  - Jalur GPU bukan prioritas; simpan sebagai opsi, bukan default.
  - Video di HP kelas ini sebaiknya ditandai "tidak didukung" atau dipindah ke perangkat lain.
- **Langkah berikutnya sebelum Fase 1:**
  1. Ukur model x4 dan bandingkan kualitas visual pada foto asli (bagian 6–9 masih kosong).
  2. Uji model yang lebih ringan / konversi ke fp16 untuk menekan waktu.
  3. Ukur puncak memori per kombinasi supaya batas aman punya angka, bukan tebakan.
- **Siap pindah ke Fase 1?** ☐ Ya ☑ **Belum** — angka waktu dan batas memori belum memenuhi NFR,
  dan tabel kualitas visual (§9) masih kosong.