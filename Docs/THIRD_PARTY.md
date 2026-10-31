# THIRD_PARTY: Arulume

Daftar komponen dan model pihak ketiga beserta lisensinya.

| | |
|---|---|
| Versi | 1.0 (template, isi saat komponen benar-benar dipakai) |
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
| ncnn (Tencent) | Engine inferensi + Vulkan | BSD-3-Clause | TBD | TBD |
| Android Jetpack/AndroidX (yang dipakai) | Komponen Android | Apache-2.0 | TBD | TBD |
| Zustand (jika dipakai) | State | MIT | TBD | TBD |

## 2. Model AI

| Model | Fungsi | Lisensi (perkiraan) | Format | Ukuran | SHA-256 | Sumber | Fase | Dibundel? |
|---|---|---|---|---|---|---|---|---|
| Real-ESRGAN compact umum (`realesr-general-x4v3` atau setara) | Foto umum | BSD-3-Clause | ncnn (`.param`/`.bin`) | TBD | TBD | TBD | 1 | Ya |
| Real-ESRGAN `realesr-animevideov3` | Video / anime | BSD-3-Clause | ncnn | TBD | TBD | TBD | 2 | Ya |
| Real-ESRGAN x4plus (penuh) | Kualitas maksimum (berat) | BSD-3-Clause | ncnn | TBD | TBD | TBD | opsional | Opsional |
| GFPGAN | Restorasi wajah | Apache-2.0 | ncnn | TBD | TBD | TBD | 3 | Tidak (salin manual) |
| CodeFormer | Restorasi wajah | **Non-komersial** (S-Lab) | ncnn | TBD | TBD | TBD | 3 | Tidak (salin manual) |
| RIFE (ncnn) | Interpolasi frame | MIT | ncnn | TBD | TBD | TBD | 3 | Tidak |

Catatan penting:
- **Ketersediaan format ncnn** untuk model umum compact belum dipastikan (asumsi A8 di PRD). Jika hanya tersedia format PyTorch, lakukan konversi dan catat prosesnya di sini (alat, versi, perintah).
- **CodeFormer** hanya untuk penggunaan non-komersial. Gunakan hanya untuk pemakaian pribadi; jangan dibagikan sebagai produk.
- Model besar (> 100 MB) tidak dibundel di APK.

## 3. Aset desain

| Aset | Lisensi (perkiraan) | Catatan |
|---|---|---|
| Plus Jakarta Sans (atau Inter/DM Sans) | SIL Open Font License 1.1 | Dibundel lokal; sertakan teks lisensi |
| Ikon (outline) | Tergantung set ikon yang dipilih | Catat nama set dan lisensinya saat dipilih |
| Maskot Aru | **Belum dikonfirmasi** | Set 40 ekspresi di `assets/mascot/`, di-*trace* dari lembar stiker pemilik (`assets/mascot/source/maskot-sheet.jpeg`). **Wajib dikonfirmasi** asal-usulnya: kalau lembar stiker itu karya sendiri atau dibeli berlisensi, tulis sumbernya di sini. Kalau berasal dari set pihak lain, ganti sebelum APK dibagikan. |

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
