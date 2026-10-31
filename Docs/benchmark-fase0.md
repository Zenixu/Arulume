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

## 5. Hasil: foto #1 (referensi)

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

## 6. Hasil: foto lain

Ulangi tabel yang sama (atau versi ringkas dengan tile terbaik) untuk foto #2, #7, #8, #9, #13.

| Foto | Mesin | Tile | Skala | Waktu total | Puncak memori | Berhasil? | Catatan |
|---|---|---|---|---|---|---|---|
| #2 | | | | | | | |
| #7 | | | | | | | |
| #8 | | | | | | | |
| #9 | | | | | | | |
| #13 | | | | | | | |

## 7. Uji stabilitas

| Uji | Hasil | Catatan |
|---|---|---|
| 20 proses berturut-turut (foto #1, tile terbaik) | | Memori awal vs akhir: |
| Rotasi layar saat proses | | |
| Aplikasi ke latar belakang saat proses | | |
| Mode pesawat aktif | | |
| Fallback GPU → CPU dipicu manual | | |

## 8. Penilaian kualitas visual

Skala: 1 (tidak terlihat peningkatan) sampai 5 (sangat jelas).

| Foto | Model | Skor ketajaman | Skor kealamian | Artefak (garis tile, halo, warna) | Catatan |
|---|---|---|---|---|---|
| #1 | | | | | |
| #2 | | | | | |
| #11 (wajah) | | | | | |
| #12 (teks) | | | | | |
| #13 (noise) | | | | | |

## 9. Verifikasi asumsi

| Asumsi | Hasil | Bukti / catatan |
|---|---|---|
| A7: UI termuat tanpa izin `INTERNET` | ☑ **Ya** ☐ Tidak | APK debug `0.0.1` terpasang di Redmi Note 8. Logcat: `Loading app at https://localhost` → seluruh chunk JS, CSS, dan font `plus_jakarta_sans_latin*.woff2` dimuat dari aset lokal. `aapt2 dump permissions` → hanya `WRITE_EXTERNAL_STORAGE (maxSdkVersion=28)` + `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. **Tidak ada `INTERNET`.** Tangkapan layar: `assets/screenshots/fase0-beranda.png`. |
| A8: model compact tersedia/terkonversi ke ncnn | ☐ Ya ☐ Tidak | Belum diuji (Fase 0.5) |
| Model dapat dimuat langsung dari assets | ☐ Ya ☐ Perlu disalin ke `filesDir` | Belum ada model terpasang; `getCapabilities().models` mengembalikan array kosong sesuai keadaan |
| Vulkan tersedia dan stabil di HP target | ☑ **Ya** (tersedia) ☐ Tidak | `getCapabilities()` di perangkat: `vulkanAvailable = true`, `gpuName = "Adreno (TM) 610"`, API 30. Stabilitas saat inferensi belum diuji (Fase 0.5). |

## 10. Keputusan hasil benchmark

| Keputusan | Nilai final | Dasar (rujuk bagian di atas) |
|---|---|---|
| Model default foto | | |
| Tile default (GPU) | | |
| Tile default (CPU) | | |
| Batas sisi panjang output (px) | | |
| Batas input per skala (px) | 2x: , 3x: , 4x: | |
| Pemetaan preset Cepat | | |
| Pemetaan preset Seimbang | | |
| Pemetaan preset Maksimal | | |
| NFR-P2 (foto 1000×1000, 4x, GPU) | | |
| NFR-P3 (CPU fallback) | | |
| Apakah video layak di HP ini? | ☐ Ya ☐ Dengan batas ☐ Tidak | |

## 11. Kesimpulan dan tindak lanjut

- Ringkasan hasil:
- Masalah yang ditemukan:
- Perubahan yang harus dibawa ke PRD:
- Siap pindah ke Fase 1? ☐ Ya ☐ Belum (alasan):
