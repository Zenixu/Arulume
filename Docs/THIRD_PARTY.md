# THIRD_PARTY: Arulume

Daftar komponen dan model pihak ketiga beserta lisensinya.

| | |
|---|---|
| Versi | 1.1 (ncnn + model Fase 0.5 tercatat) |
| Tanggal | 31 Oktober 2026 |

> **Cara memakai dokumen ini.** Kolom "Versi", "Sumber", dan "SHA-256" sengaja dikosongkan (`TBD`). Isi dengan data nyata saat komponen dipasang. **Jangan menebak.** Lisensi yang tertulis di sini adalah pemahaman awal; **verifikasi ulang** pada repositori atau file `LICENSE` resmi tiap komponen sebelum dipakai atau dibagikan.

---

## 1. Komponen perangkat lunak

| Komponen | Fungsi | Lisensi (perkiraan) | Versi | Sumber |
|---|---|---|---|---|
| Next.js | Kerangka UI | MIT | TBD | TBD |
| React | UI | MIT | TBD | TBD |
| Tailwind CSS | Styling | MIT | TBD | TBD |
| Capacitor | Jembatan web ↔ native | MIT | TBD | TBD |
| ncnn (Tencent) | Engine inferensi + Vulkan | BSD-3-Clause | 20261020 | `https://github.com/Tencent/ncnn/releases` → `ncnn-20261020-android-vulkan-shared.zip` (`libncnn.so` di `android/app/src/main/jniLibs/arm64-v8a/`, header di `android/app/src/main/cpp/ncnn/`) |
| Android Jetpack/AndroidX (yang dipakai) | Komponen Android | Apache-2.0 | TBD | TBD |
| Zustand (jika dipakai) | State | MIT | TBD | TBD |

## 2. Model AI

| Model | Fungsi | Lisensi (perkiraan) | Format | Ukuran | SHA-256 | Sumber | Fase | Dibundel? |
|---|---|---|---|---|---|---|---|---|
| Real-ESRGAN compact umum (`realesr-general-x4v3` atau setara) | Foto umum | BSD-3-Clause | ncnn (`.param`/`.bin`) | TBD | TBD | TBD | 1 | Ya |
| Real-ESRGAN `realesr-animevideov3` (x2) | Foto, dipakai di Fase 0.5 | BSD-3-Clause | ncnn (`.param`/`.bin`) | 1,2 MB | `548a36f9c3f4ab8da56cd3b13badf23968bee207b396dad14d04b830e5f2ab2d` (.bin), `b88ff4f00ebf019a7fdac17fdd45a7fd3665d37509efc5baf2e4da2e24420a04` (.param) | `realesrgan-ncnn-vulkan-20220424-ubuntu.zip` dari `https://github.com/xinntao/Real-ESRGAN/releases` | 0.5 | Ya |
| Real-ESRGAN `realesr-animevideov3` (x4) | Foto, pembanding skala | BSD-3-Clause | ncnn | 1,2 MB | `548a36f9c3f4ab8da56cd3b13badf23968bee207b396dad14d04b830e5f2ab2d` (.bin), `850a248e7c14c27e5bd8cf7265113a9441036a7db63963bb8aa5169d788a435e` (.param) | sama | 0.5 | Ya |
| Real-ESRGAN x4plus (penuh) | Kualitas maksimum (berat) | BSD-3-Clause | ncnn | TBD | TBD | TBD | opsional | Opsional |
| GFPGAN | Restorasi wajah | Apache-2.0 | ncnn | TBD | TBD | TBD | 3 | Tidak (salin manual) |
| CodeFormer | Restorasi wajah | **Non-komersial** (S-Lab) | ncnn | TBD | TBD | TBD | 3 | Tidak (salin manual) |
| RIFE (ncnn) | Interpolasi frame | MIT | ncnn | TBD | TBD | TBD | 3 | Tidak |

Catatan penting:
- **A8 terjawab (Fase 0.5).** Model compact berformat ncnn **tersedia resmi**: `realesr-animevideov3` x2/x4 ikut dalam `realesrgan-ncnn-vulkan-20220424-ubuntu.zip`. Tidak perlu konversi.
- **Tidak ada pembedahan APK pihak lain.** Rencana awal memeriksa Remini/Wink dibatalkan: bobot model di dalam APK proprietary tidak boleh dipakai ulang, dan unduhannya pun terhalang (Cloudflare/ISP). Seluruh model di sini berasal dari rilis resmi berlisensi BSD-3.
- **CodeFormer** hanya untuk penggunaan non-komersial. Gunakan hanya untuk pemakaian pribadi; jangan dibagikan sebagai produk.
- Model besar (> 100 MB) tidak dibundel di APK.

## 3. Aset desain

| Aset | Lisensi (perkiraan) | Catatan |
|---|---|---|
| Plus Jakarta Sans (atau Inter/DM Sans) | SIL Open Font License 1.1 | Dibundel lokal; sertakan teks lisensi |
| Ikon (outline) | Tergantung set ikon yang dipilih | Catat nama set dan lisensinya saat dipilih |
| Maskot Aru | **Open source (dinyatakan pemilik)** | Set 40 ekspresi di `assets/mascot/`, di-*trace* dari lembar stiker yang pemilik ambil dari set open-source di internet (pin). Pemilik menyatakan penggunaannya aman. **Catatan:** URL sumber dan nama lisensinya belum dicatat — lengkapi baris ini dengan tautan aslinya sebelum APK dibagikan ke orang lain, karena "open source" tanpa nama lisensi (MIT/CC0/CC-BY) tidak bisa diverifikasi. |

## 4. Cara memverifikasi checksum model

```bash
# Linux/macOS
sha256sum android/app/src/main/assets/models/*

# Windows (PowerShell)
Get-FileHash android\app\src\main\assets\models\* -Algorithm SHA256
```

Salin hasilnya ke tabel bagian 2. Saat build rilis, bandingkan ulang (lihat `BUILD.md` bagian 5).

## 5. Teks lisensi

Untuk setiap komponen yang disalurkan di dalam APK (khususnya ncnn, model, dan font), sertakan teks lisensinya di layar **Pengaturan → Tentang & lisensi**, atau di folder `licenses/` yang dibundel. Isi minimal: nama komponen, versi, lisensi, dan tautan sumber.

## 6. Aturan perubahan

- Menambah komponen atau model baru → tambah barisnya di sini **sebelum** digabung ke rilis.
- Memperbarui versi → ubah kolom versi dan checksum.
- Menghapus komponen → hapus barisnya dan teks lisensi terkait dari APK.
