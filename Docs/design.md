# Design Guide: Arulume

Panduan desain dan UI/UX untuk **Arulume**, aplikasi Android offline untuk memperjelas dan memperbesar foto (video menyusul), bagian dari brand **Aruthtale**.

| Item | Isi |
|---|---|
| Versi | 1.0 (Draft) |
| Tanggal | 31 Oktober 2026 |
| Platform | Android, portrait, kanvas acuan 360×800 dp |
| Terkait | `PRD.md` (bagian 16 dirangkum dan diperluas di sini) |
| Fungsi dokumen | Sumber tunggal untuk warna, font, maskot, komponen, layar, dan teks. Dipakai sebagai acuan prompt Google Stitch dan saat membangun UI Next.js |

Setiap kali desain berubah (di Stitch atau Figma), **perbarui dokumen ini lebih dulu**.

---

## 1. Prinsip desain

1. **Foto adalah bintangnya.** Di layar Editor dan Hasil, foto menempati area terbesar. Maskot tidak ada atau sangat kecil.
2. **Maskot adalah bumbu, bukan hiasan di mana-mana.** Hanya muncul di momen tertentu (bagian 5.3).
3. **Satu aksi utama per layar.** Satu tombol amber besar. Aksi lain bersifat sekunder dan tampil lebih tenang.
4. **Bahasa manusia.** Tidak ada istilah teknis (tile, model, inference, GPU) di layar utama. Detail teknis hanya di "Lanjutan".
5. **Tenang dan lega.** Spasi besar, teks sedikit, tidak ada elemen dekoratif yang tidak punya fungsi.
6. **Tidak membuat pusing.** Maksimal satu keputusan penting per layar. Default sudah baik, sehingga pengguna bisa langsung menekan tombol utama.
7. **Jujur soal batas.** Jika foto terlalu besar atau HP lambat, katakan dengan singkat dan beri jalan keluar.

## 2. Brand

| Elemen | Ketentuan |
|---|---|
| Nama | **Arulume** (dari "Aru" milik Aruthtale + "lume" dari *lumen*/cahaya) |
| Tagline | Inggris: *Bring your photos back to light* · Indonesia: *Kembalikan cahaya fotomu* |
| Kesan | Tenang, hangat, jernih, privat |
| Janji utama | Semua diproses di HP, tanpa internet, tanpa unggah |
| Identitas | Kucing hitam siluet dengan mata bulat putih (maskot kerja: **Aru**, bisa diganti) |
| ID aplikasi | `id.aruthtale.arulume` |

## 3. Token desain

> **Sumber tunggal.** Dokumen ini adalah satu-satunya sumber kebenaran untuk warna, tipografi, jarak, radius, dan gerak. Ekspor Stitch/Figma (`stitch/DESIGN.md`, `code.html`) adalah **turunan** dan wajib mengikuti nilai di sini. Jika sebuah layar butuh nilai yang tidak ada, tambahkan dulu ke dokumen ini, baru pakai di desain dan kode.

### 3.1 Warna

Mode gelap adalah **default**. Mode terang tersedia dan mengikuti pengaturan sistem atau pilihan pengguna.

| Token | Gelap | Terang | Pemakaian |
|---|---|---|---|
| `bg` | `#111214` | `#F7F6F3` | Latar layar |
| `surface` | `#1B1C1F` | `#FFFFFF` | Kartu, panel, sheet |
| `surface-2` | `#25272B` | `#EFEDE8` | Chip, input, area sekunder |
| `border` | `#2F3136` | `#E2DFD8` | Garis tipis |
| `text` | `#F7F6F3` | `#111214` | Teks utama |
| `text-2` | `#A4A7AE` | `#5B5E66` | Teks sekunder |
| `accent` | `#F5B83D` | `#F5B83D` | Tombol utama, progres, state aktif |
| `on-accent` | `#111214` | `#111214` | Teks/ikon di atas amber |
| `accent-text` | `#F5B83D` | `#8E6200` | Teks atau ikon amber di atas latar (versi terang dibuat lebih gelap agar terbaca) |
| `success` | `#6FCF97` | `#28784B` | Status berhasil |
| `danger` | `#FF6B6B` | `#BD3B3B` | Error, hapus |
| `warning` | `#F2994A` | `#9C5A16` | Peringatan |

Aturan:
- **Hanya satu warna aksen (amber).** Jangan menambah warna merek lain.
- Teks di atas tombol amber selalu `on-accent` (gelap), bukan putih.
- Jangan memakai amber untuk teks kecil di mode terang; pakai `accent-text` atau `text`.

#### Hasil ukur kontras (31 Oktober 2026)

Diukur dengan rumus kontras WCAG 2.1 pada 19 pasangan token (setiap warna teks di atas `bg`, `surface`, `surface-2`, plus `on-accent` di atas `accent`):

| Mode | Pasangan diukur | Terendah | Tertinggi | Gagal 4,5:1 |
|---|---|---|---|---|
| Gelap | 19 | 5,39:1 (`danger` di `surface-2`) | 17,34:1 (`text` di `bg`) | 0 |
| Terang | 19 | 4,61:1 (`accent-text` di `surface-2`) | 18,74:1 (`text` di `surface`) | 0 |

Karena itu `accent-text`, `success`, `danger`, dan `warning` **mode terang digelapkan** dari versi awal (`#9A6A00`→`#8E6200`, `#2E8B57`→`#28784B`, `#C73E3E`→`#BD3B3B`, `#B86A1A`→`#9C5A16`) agar lolos AA untuk teks kecil. **Jangan mengembalikan nilai lama.**

Kalau menambah pasangan warna baru, ukur ulang dengan alat pemeriksa kontras (target AA: 4,5:1 teks biasa, 3:1 teks besar dan komponen UI).

### 3.2 Tipografi

Font: **Plus Jakarta Sans** (alternatif: DM Sans, Inter). Dibundel lokal di aplikasi, **tidak** memuat dari internet, karena aplikasi tanpa izin `INTERNET`.

| Token | Ukuran / tinggi baris | Berat | Pemakaian |
|---|---|---|---|
| `title` | 24 / 32 | 700 | Judul layar, angka besar |
| `heading` | 18 / 26 | 600 | Judul bagian, label tombol utama |
| `body` | 15 / 22 | 400-500 | Isi, deskripsi |
| `caption` | 12 / 16 | 500 | Keterangan, info file, badge |

Hanya empat gaya di atas. Hindari ukuran dan berat di luar tabel.

### 3.3 Jarak, bentuk, dan ukuran

| Token | Nilai |
|---|---|
| Skala jarak | 4, 8, 12, 16, 24, 32, 48 dp |
| Margin layar | 20 dp kiri-kanan |
| Radius kecil / sedang / besar | 12 / 16 / 24 dp |
| Tombol utama | tinggi 56 dp, lebar penuh, radius 16 dp |
| Target sentuh minimum | 48×48 dp |
| Bayangan | Tidak dipakai; pemisahan dengan perbedaan warna surface dan garis tipis |
| Ikon | Outline, stroke konsisten (1,75-2 dp), ukuran 24 dp |

### 3.4 Pemetaan nama token Stitch/Material → token Arulume

Stitch menghasilkan token bergaya Material 3. Nama boleh berbeda, **nilai harus sama**. Pakai tabel ini saat menerjemahkan hasil Stitch ke kode.

| Nama M3 di Stitch | Token Arulume | Nilai (gelap) |
|---|---|---|
| `background`, `surface-dim` | `bg` | `#111214` |
| `surface`, `surface-container`, `surface-container-low` | `surface` | `#1B1C1F` |
| `surface-container-high`, `surface-variant` | `surface-2` | `#25272B` |
| `surface-container-highest`, `outline`, `outline-variant` | `border` | `#2F3136` |
| `on-surface`, `on-background` | `text` | `#F7F6F3` |
| `on-surface-variant`, `secondary` | `text-2` | `#A4A7AE` |
| `primary-container`, `surface-tint`, `primary-fixed-dim` | `accent` | `#F5B83D` |
| `on-primary`, `on-primary-container` | `on-accent` | `#111214` |
| `primary`, `primary-fixed` | `accent-text` | `#F5B83D` (gelap) / `#8E6200` (terang) |
| `error`, `danger` | `danger` | `#FF6B6B` |
| `tertiary` (biru), `tertiary-container` | **tidak dipakai** | ganti ke `accent` atau `text-2` |

Aturan:
- **Jangan menambah warna baru.** Kalau hasil Stitch memunculkan warna di luar tabel (paling sering `tertiary` biru atau `primary` kuning pucat `#ffd999`), ganti ke token Arulume terdekat.
- Token dekoratif (`surface-container-*`, `primary-fixed`, `inverse-*`) tidak dipakai di kode aplikasi; hanya `bg/surface/surface-2/border/text/text-2/accent/on-accent/accent-text/success/danger/warning`.

## 4. Gerak

- Durasi 150-250 ms, easing lembut. Tidak ada animasi yang menunda pengguna.
- Transisi layar: geser atau pudar sederhana.
- Animasi maskot: ringan (kedip mata, ekor bergoyang pelan), dibuat dengan CSS atau Lottie kecil.
- **Hormati "Hapus animasi / kurangi gerakan"** milik sistem (`prefers-reduced-motion`): ganti dengan perubahan statis.
- Progres pemrosesan selalu diwakili elemen yang jelas (bar + persen), bukan hanya animasi maskot.

## 5. Maskot

### 5.1 Karakter
Kucing hitam kecil, **siluet solid satu warna**, telinga runcing, dua mata bulat putih, ekor melengkung. Ekspresi dibawa oleh mata dan satu simbol kecil (hati, tanda tanya, percikan). Tidak ada detail rumit, gradasi, atau garis halus.

Asal desain: set 40 ekspresi berasal dari lembar stiker milik pemilik proyek (`assets/mascot/source/maskot-sheet.jpeg`), lalu dipecah dan di-*trace* menjadi SVG satu warna. Bentuk kepala, telinga, dan ekor harus tetap konsisten di seluruh set. **Jangan** menambahkan ilustrasi dari pihak lain ke dalam set ini; kalau memakai referensi gaya, buat karakter baru (lihat `design.md` bagian 11.5 dan aturan 7 di `AGENTS.md`). Status lisensi aset dicatat di `THIRD_PARTY.md`.

### 5.2 Format teknis

Aset siap pakai ada di `assets/mascot/` (lihat `assets/mascot/README.md` untuk manifest lengkap 40 ekspresi).

| Berkas | Isi |
|---|---|
| `assets/mascot/svg/*.svg` | **Dipakai aplikasi.** 40 SVG satu jalur, `fill="currentColor"`, latar transparan, rata-rata 2,7 KB |
| `assets/mascot/png/ink/*.png` | Siluet `#111214` transparan, 3× (fallback / pratinjau) |
| `assets/mascot/png/paper/*.png` | Siluet `#F7F6F3` transparan, 3× (fallback / pratinjau) |
| `assets/mascot/preview.png` | Kontak-sheet 40 ikon, dua tema |
| `assets/mascot/source/maskot-sheet.jpeg` | Sumber asli, 8 baris × 5 kolom |

- SVG memakai `currentColor`; warna ditentukan CSS di sekitarnya (`text-[#111214] dark:text-[#F7F6F3]`). **Jangan** menyimpan versi berwarna.
- Mata dan detail adalah **lubang transparan**. Maskot harus berada di atas `surface`/`bg` yang kontras, tidak pernah di atas foto.
- Ukuran standar: 64, 96, 160, 240 dp. Terbaca pada 32 dp, kecuali `asap` dan `ide` (detail halus) — minimal 48 dp.
- Ikon aplikasi: kepala kucing dengan mata bulat yang terbaca sebagai lensa; latar polos satu warna; aman untuk adaptive icon (konten di zona aman 66 dp dari 108 dp).
- Aset dibuat dengan memecah lembar stiker lalu *trace* ke SVG (potrace). Kalau ada ekspresi baru, tambahkan sebagai SVG baru di folder yang sama dan catat di `assets/mascot/README.md`.

### 5.3 Kapan maskot boleh muncul

| Kondisi | Boleh? | Ekspresi | Ukuran |
|---|---|---|---|
| Beranda | Ya | Duduk tenang, ekor melambai pelan | 96-160 dp |
| Onboarding | Ya | Mengintip, melambai | 160-240 dp |
| Riwayat kosong | Ya | Mengintip dari tepi | 120 dp |
| Memproses | Ya, kecil | Sibuk ringan, tidak menutupi progres | ≤ 64 dp |
| Sukses | Ya, singkat | Mata berbinar, tampil sesaat | 64-96 dp |
| Error | Ya | Bingung (mata dengan tanda tanya) | 96 dp |
| Video: panas/baterai | Ya | Mengantuk atau kepanasan | 96 dp |
| **Editor** | **Tidak** (atau ikon ≤ 24 dp) | - | - |
| **Hasil (area foto)** | **Tidak** | - | - |
| Pengaturan | Tidak | - | - |

### 5.4 Daftar ekspresi (10 mood inti + 30 tambahan)

Set lengkap 40 ekspresi sudah tersedia di `assets/mascot/svg/`. Sepuluh mood inti wajib ada:

| # | Mood | Berkas |
|---|---|---|
| 1 | Netral / duduk | `duduk-manis` |
| 2 | Mengintip | `mengintip` |
| 3 | Melambai | `angkat-kaki` |
| 4 | Sibuk / berpikir | `ide` |
| 5 | Berbinar (sukses) | `berbinar` |
| 6 | Bingung (error) | `pusing-spiral` |
| 7 | Mengantuk | `datar` |
| 8 | Kepanasan | `keringat` |
| 9 | Tidur | `tidur-zzz` |
| 10 | Jejak kaki | `jejak-kaki` |

Alternatif dan 30 ekspresi lain (kaget, menangis, marah, oke, amplop, telepon, dll.) beserta pemakaiannya ada di `assets/mascot/README.md`.

## 6. Komponen

| Komponen | Spesifikasi |
|---|---|
| **Tombol utama** | Latar `accent`, teks `on-accent` (heading), tinggi 56, radius 16, satu per layar, menempel di bawah pada layar aksi |
| **Tombol sekunder** | Latar `surface-2`, teks `text`, tinggi 48-52 |
| **Tombol teks** | Tanpa latar, teks `accent-text`, untuk "Batal", "Lanjutan" |
| **Chip preset** | Tiga opsi (Cepat / Seimbang / Maksimal), terpilih = latar `accent` dengan teks `on-accent`, tidak terpilih = `surface-2` |
| **Segmen skala** | 2x / 3x / 4x, gaya sama dengan chip; opsi tidak valid dinonaktifkan dan redup |
| **Kartu foto** | Radius 16, tanpa bayangan, info resolusi dan ukuran di bawah (caption) |
| **Slider sebelum/sesudah** | Garis pembagi tipis `accent` dengan pegangan bulat 40 dp; label "Asli" dan "Hasil" kecil di sudut; mendukung cubit-zoom tersinkron |
| **Progress bar** | Tinggi 8 dp, radius penuh, isi `accent`, latar `surface-2`; persen dan tahap dalam teks di atasnya |
| **Badge offline** | Pil kecil dengan ikon pesawat + "Offline", warna `surface-2`, selalu terlihat di Beranda |
| **Grid riwayat** | 2 kolom, thumbnail rasio asli dipotong rapi, label tanggal dan skala (caption) |
| **Dialog** | Radius 24, judul (heading), isi singkat, maksimal dua tombol (utama + teks) |
| **Snackbar/toast** | Satu baris, hilang otomatis 3-4 detik |
| **Bagian Lanjutan** | Collapsible, tertutup secara default |

## 7. Layar

Berikut isi tiap layar. Teks di tanda kutip adalah salinan awal (lihat bagian 8 untuk dua bahasa).

### 7.1 Beranda
- **Bar atas:** nama "Arulume" (kiri), badge Offline, ikon pengaturan (kanan).
- **Tengah:** maskot duduk (96-160 dp), tagline satu baris.
- **Aksi:** tombol utama **"Pilih Foto"**; tombol sekunder **"Pilih Video"** dengan tag kecil "Segera" (sampai Fase 2).
- **Bawah:** baris "Terbaru" berisi thumbnail hasil terakhir (maks 6). Jika kosong: maskot mengintip + "Belum ada cerita".
- **State:** normal, kosong. Jika Vulkan tidak tersedia, banner kecil non-menakutkan: "Perangkat memakai mode standar, proses bisa lebih lama."

### 7.2 Editor
- **Atas:** tombol kembali, judul "Atur Peningkatan".
- **Foto** besar (±45% tinggi layar), di bawahnya info: resolusi, ukuran file.
- **Preset:** chip Cepat / Seimbang / Maksimal (default: Seimbang).
- **Skala:** 2x / 3x / 4x (default 2x). Jika output melebihi batas, tampilkan peringatan inline dan sarankan skala lebih kecil.
- **Lanjutan** (tertutup): format (JPEG/PNG/WebP), kualitas, gunakan GPU, lokasi simpan, pertahankan EXIF.
- **Bawah (menempel):** tombol utama **"Tingkatkan"**.
- Maskot tidak tampil.

Pemetaan preset ke parameter teknis (disesuaikan setelah benchmark Fase 0):

| Preset | Arti untuk pengguna | Parameter (acuan) |
|---|---|---|
| Cepat | Hasil lebih cepat | Model compact, skala 2x, tile besar |
| Seimbang | Pilihan terbaik untuk kebanyakan foto | Model umum, skala sesuai pilihan |
| Maksimal | Kualitas tertinggi, lebih lama | Model terberat yang tersedia, tile kecil |

### 7.3 Memproses
- Foto asli diredupkan di latar.
- Tengah: tahap ("Meningkatkan… 62%"), progress bar amber, perkiraan sisa waktu.
- Maskot kecil (≤ 64 dp) di sisi, tidak menutupi progres.
- Tombol teks **"Batal"**.
- Jika pindah ke GPU→CPU: pesan kecil "Beralih ke mode standar".

### 7.4 Hasil
- **Slider sebelum/sesudah** mengambil ±70% tinggi layar; petunjuk cubit-zoom muncul sekali.
- Info: resolusi baru, lama proses, status "Tersimpan di Galeri".
- Aksi: **"Bagikan"** dan **"Buka di Galeri"** (sekunder), **"Tingkatkan Lagi"**, **"Selesai"** (utama).
- Maskot tidak ada di area foto; boleh berbinar sesaat saat pertama muncul (≤ 64 dp, di luar foto).

### 7.5 Riwayat
- Grid 2 kolom, dengan tanggal dan skala.
- Ketuk membuka Hasil; tekan lama untuk memilih dan menghapus entri.
- Kosong: maskot mengintip + "Belum ada cerita".

### 7.6 Pengaturan
Daftar sederhana: Tema (Gelap/Terang/Ikuti sistem), Preset bawaan, Format bawaan, Hapus cache, Ekspor log lokal, Tentang & lisensi (versi aplikasi, model terpasang, lisensi komponen).

### 7.7 Error
- Maskot bingung (96 dp), pesan singkat dalam bahasa manusia, satu tombol utama ("Coba lagi" atau "Ubah pengaturan"), kode teknis kecil di bawah (caption, mis. `E_INPUT_TOO_LARGE`).

### 7.8 Onboarding (sekali, saat pertama dibuka)
- Satu layar saja. Maskot Aru melambai (160-240 dp) di atas.
- Judul janji: "Offline & privat", subjudul "Fotomu tidak pernah keluar dari HP ini."
- Tiga baris singkat berikon: "Tanpa internet, tanpa akun" · "Semua diproses di HP" · "Hasil langsung ke galeri".
- Tombol utama **"Mulai"**; tombol teks **"Lewati"**. Tiga titik halaman di atas tombol (titik pertama amber).
- Tidak meminta izin apa pun. Tidak ada kolom akun, email, atau login.

### 7.9 Layar Fase 2 (video), acuan awal
Editor video (durasi, resolusi, perkiraan waktu), peringatan baterai/panas, progres per frame dengan Jeda dan Batal, hasil dengan pemutar sebelum/sesudah. Dirinci saat Fase 2 dimulai.

## 8. Gaya teks (copywriting)

Aturan: kalimat pendek, hangat, tanpa jargon, tanpa tanda seru berlebihan, satu ide per kalimat.

| Konteks | Indonesia | Inggris |
|---|---|---|
| Tagline | Kembalikan cahaya fotomu | Bring your photos back to light |
| Tombol utama beranda | Pilih Foto | Choose Photo |
| Tombol video | Pilih Video · *Segera* | Choose Video · *Soon* |
| Riwayat kosong | Belum ada cerita | No stories yet |
| Badge | Offline | Offline |
| Preset | Cepat · Seimbang · Maksimal | Fast · Balanced · Max |
| Tombol proses | Tingkatkan | Enhance |
| Progres | Meningkatkan… 62% | Enhancing… 62% |
| Batal | Batal | Cancel |
| Sukses | Tersimpan di Galeri | Saved to Gallery |
| Aksi hasil | Bagikan · Buka di Galeri · Tingkatkan Lagi · Selesai | Share · Open in Gallery · Enhance Again · Done |
| Foto terlalu besar | Foto ini terlalu besar untuk 4x. Coba 2x atau perkecil fotonya. | This photo is too large for 4x. Try 2x or a smaller photo. |
| Mode CPU | Perangkat memakai mode standar, proses bisa lebih lama. | Your device is using standard mode, so it may take longer. |
| Penyimpanan penuh | Ruang penyimpanan tidak cukup. Kosongkan sedikit lalu coba lagi. | Not enough storage. Free up some space and try again. |
| Gagal umum | Ups, ada yang tidak berjalan. Coba lagi ya. | Something went wrong. Please try again. |

Kata yang **dilarang** di layar utama: tile, model, inference, tensor, Vulkan, ncnn, checksum. Kata ini hanya boleh muncul di "Lanjutan" dan "Tentang".

Bahasa UI bawaan (Indonesia atau Inggris) masih keputusan terbuka (bagian 13).

## 9. Aksesibilitas

- Kontras teks memenuhi WCAG AA (verifikasi tiap pasangan token).
- Target sentuh ≥ 48 dp, jarak antar tombol ≥ 8 dp.
- Semua ikon dan maskot informatif punya deskripsi (`contentDescription`/`aria-label`); maskot dekoratif ditandai tersembunyi dari pembaca layar.
- Informasi tidak bergantung pada warna saja (mis. status error juga memakai ikon dan teks).
- Mendukung ukuran font besar dari pengaturan sistem tanpa memotong teks.
- Mendukung TalkBack untuk alur utama (pilih → atur → proses → hasil).
- Hormati pengurangan gerakan (bagian 4).
- Slider sebelum/sesudah punya alternatif non-geser (tombol "Asli" / "Hasil" untuk berpindah tampilan).

## 10. Penerapan di Next.js + Tailwind

Stack terpasang: **Next.js 16 (App Router, static export) + Tailwind CSS v4**. Tailwind v4 tidak memakai
`tailwind.config.js`; token didefinisikan langsung di CSS lewat `@theme`. Implementasi nyata ada di
`src/app/globals.css` — kutipan di bawah adalah ringkasannya.

```css
/* src/app/globals.css */
@import "tailwindcss";

/* Mode gelap = default. Mode terang lewat [data-theme="light"]. */
:root,
[data-theme="dark"] {
  --bg: #111214;  --surface: #1B1C1F;  --surface-2: #25272B;
  --border: #2F3136;  --text: #F7F6F3;  --text-2: #A4A7AE;
  --accent: #F5B83D;  --on-accent: #111214;  --accent-text: #F5B83D;
  --success: #6FCF97; --danger: #FF6B6B; --warning: #F2994A;
}

[data-theme="light"] {
  --bg: #F7F6F3;  --surface: #FFFFFF;  --surface-2: #EFEDE8;
  --border: #E2DFD8;  --text: #111214;  --text-2: #5B5E66;
  --accent: #F5B83D;  --on-accent: #111214;  --accent-text: #8E6200;
  --success: #28784B; --danger: #BD3B3B; --warning: #9C5A16;
}

@theme inline {
  --color-bg: var(--bg);
  --color-surface: var(--surface);
  --color-surface-2: var(--surface-2);
  --color-border: var(--border);
  --color-text: var(--text);
  --color-text-2: var(--text-2);
  --color-accent: var(--accent);
  --color-on-accent: var(--on-accent);
  --color-accent-text: var(--accent-text);
  --color-success: var(--success);
  --color-danger: var(--danger);
  --color-warning: var(--warning);

  --font-sans: var(--font-jakarta), system-ui, sans-serif;

  /* empat gaya teks: text-title / text-heading / text-body / text-caption */
  --text-title: 24px;    --text-title--line-height: 32px;   --text-title--font-weight: 700;
  --text-heading: 18px;  --text-heading--line-height: 26px; --text-heading--font-weight: 600;
  --text-body: 15px;     --text-body--line-height: 22px;
  --text-caption: 12px;  --text-caption--line-height: 16px; --text-caption--font-weight: 500;

  /* radius: rounded-sm 12 / rounded-md 16 / rounded-lg 24 */
  --radius-sm: 12px;
  --radius-md: 16px;
  --radius-lg: 24px;
}
```

Pemakaian di komponen: `bg-bg`, `bg-surface`, `bg-surface-2`, `border-border`, `text-text`,
`text-text-2`, `bg-accent`, `text-on-accent`, `text-accent-text`, `text-title`, `text-body`, `rounded-md`.

Catatan implementasi:

- Font dibundel lokal lewat `next/font/local` dari `src/app/fonts/plus-jakarta-sans-*.woff2`
  (variable, wght 400-800). **Tidak** memuat dari CDN — aplikasi tanpa izin `INTERNET`.
- Tema default gelap: `<html data-theme="dark">`. Skrip kecil di `layout.tsx` hanya menerapkan pilihan
  tersimpan (`localStorage["arulume.theme"]`) sebelum paint. "Ikuti sistem" menyusul di Fase 1 (FR-09).
- Safe area (status bar dan gesture bar) ditangani lewat `env(safe-area-inset-*)` di `body`.
- Maskot: `<Mascot mood="duduk-manis" size={160} animate />` — SVG inline `currentColor`, warna ikut
  CSS di sekitarnya. Peran siap pakai: `MASCOT_ROLE.netral`, `.mengintip`, `.melambai`, `.bingung`, dst.
- Animasi maskot (`.mascot-float`) otomatis mati saat `prefers-reduced-motion: reduce`.

## 11. Google Stitch: status dan panduan

### 11.1 Status (31 Oktober 2026)

Mockup seluruh layar sudah dibuat di Stitch dan diekspor ke folder `stitch/` di repositori.

| Item | Nilai |
|---|---|
| Project mode gelap | `16084830281983020534` |
| Project mode terang | `13677577835905979214` |
| Design system | "Arulume Warm Dark" / "Arulume Warm Light" (Plus Jakarta Sans, aksen `#F5B83D`, roundness 12) |
| Layar per mode | 8: Beranda, Editor, Memproses, Hasil, Riwayat, Pengaturan, Error, Onboarding |
| Berkas | `stitch/<mode>/<layar>/code.html` + `screen.png` (780×1768, dirender lokal dari HTML) |
| Cara kerja & regenerate | `stitch/README.md` |

Aturan:

- **Dokumen ini menang** atas hasil Stitch. Kalau hasil Stitch menyimpang dari token atau aturan di sini, perbaiki hasil Stitch-nya. Kalau ternyata keputusan Stitch lebih baik, **perbarui dokumen ini dulu**, baru hasilnya dipertahankan.
- Nama token Material di HTML Stitch diterjemahkan ke token Arulume lewat tabel bagian 3.4.
- `code.html` memakai Tailwind CDN dan Google Fonts untuk pratinjau. Aplikasi asli wajib membundel font lokal dan tidak memuat apa pun dari jaringan.

### 11.2 Kalau mengulang dari nol (arsip)

Urutan kerja:
1. Tempel **prompt utama** (bagian 11.3) untuk menghasilkan layar pertama (Beranda).
2. Perbaiki bertahap dengan perintah pendek, satu perubahan per perintah.
3. Setelah gaya Beranda sesuai, minta layar lain satu per satu.
4. Ekspor HTML + screenshot ke `stitch/`, lalu perbarui dokumen ini.
5. Maskot sering kurang rapi jika digambar Stitch. Anggap sebagai penanda posisi, lalu ganti dengan SVG maskot buatan sendiri.

### 11.3 Prompt utama

```
Design a mobile app (Android, portrait, 360x800) called "Arulume": an offline photo enhancer that sharpens and upscales low-quality photos directly on the phone. Privacy-first: nothing is uploaded anywhere.

DESIGN GOAL
Clean, calm, minimal, and easy on the eyes. Users should never feel overwhelmed. The photo is always the hero. One primary action per screen, generous white space, very little text, and large touch targets (min 48dp).

STYLE
- Dark mode by default. Background charcoal #111214, surfaces #1B1C1F, text warm white #F7F6F3, secondary text neutral gray #A4A7AE.
- One single accent color only: warm amber #F5B83D, used for the primary button, active states, and progress. Text on amber is dark charcoal.
- Font: Plus Jakarta Sans. Only four text styles: title 24, heading 18, body 15, caption 12.
- Rounded corners (12-24dp), soft flat surfaces, no heavy shadows, no gradients, no clutter.
- Simple outline icons with consistent stroke.

MASCOT
An original, minimal mascot: a small solid-black cat silhouette with two round white eyes, pointed ears, and a curved tail. Flat, single-color, friendly. Use it sparingly, only on the home screen, empty states, success, and error moments. It must never cover or compete with the photo. On photo screens (editor and result) it is absent or tiny.

SCREENS (one consistent flow)
1. Home: app name "Arulume", tagline "Bring your photos back to light". Large primary button "Choose Photo". Secondary button "Choose Video" with a small "Soon" tag. A "Recent" row of small result thumbnails, or an empty state with the mascot peeking and the text "No stories yet". An "Offline" badge (airplane icon) and a settings icon in the top bar.
2. Editor: selected photo large at top with resolution and file size below. Three preset chips: "Fast", "Balanced", "Max". Scale selector 2x, 3x, 4x. A collapsed "Advanced" section (format, quality, GPU on/off). One big amber button "Enhance" fixed at the bottom.
3. Processing: photo dimmed in the background, clear amber progress bar with percentage, stage text ("Enhancing... 62%"), estimated time left, and a "Cancel" text button. Small calm mascot that never covers the progress.
4. Result: before/after comparison slider taking about 70% of the screen height, pinch-to-zoom hint, output info (new resolution, time taken), "Saved to Gallery" confirmation. Buttons: "Share", "Open in Gallery", "Enhance Again", and primary "Done". No mascot over the photo.
5. History: clean 2-column grid of past results with date and scale labels. Empty state with the mascot.
6. Settings: simple list with theme (dark/light/system), default preset, default output format, "Clear cache", "About & licenses".
7. Error: the mascot looking confused, a short human message ("This photo is too large for 4x. Try 2x or a smaller photo."), one clear button "Try again".

TONE OF COPY
Short, warm, plain language. Avoid technical words like "tile", "model", or "inference" on the main screens.

NAVIGATION
No bottom navigation bar and no tab bar anywhere. Settings is reached from the gear icon on the home screen; History is reached from the "Recent" row. At most one back arrow in the top bar.

Also provide a light mode version using warm white #F7F6F3 background and charcoal text, with the same amber accent.
```

### 11.4 Perintah perbaikan (satu per satu)

- `Make the home screen calmer: reduce text, increase spacing, and make "Choose Photo" the only strong element.`
- `The mascot is too dominant. Make it smaller and keep it only in empty states.`
- `Redesign the Result screen so the before/after slider takes 70% of the screen height.`
- `Move all technical options into a collapsed "Advanced" section.`
- `Apply the same style to a light mode version of all screens.`
- `Make the primary button text dark charcoal for better contrast on amber.`

### 11.5 Jika memakai gambar referensi
Saat mengunggah gambar kucing sebagai referensi gaya, tambahkan: *"Inspired by this flat black cat style, but create an original character. Do not copy the reference."*

## 12. Daftar periksa desain (sebelum dianggap final)

> Status 31 Okt 2026: seluruh item di bawah sudah diperiksa pada mockup `stitch/` (mode gelap dan terang). Ulangi pemeriksaan setiap kali sebuah layar diubah.

- [ ] Setiap layar punya tepat satu tombol utama.
- [ ] Foto tidak pernah tertutup maskot atau elemen dekoratif.
- [ ] Hanya satu warna aksen yang dipakai.
- [ ] Hanya empat gaya teks yang dipakai.
- [ ] Tidak ada istilah teknis di layar utama.
- [ ] Kontras seluruh pasangan warna lulus AA (gelap dan terang).
- [ ] Target sentuh ≥ 48 dp di semua layar.
- [ ] Layar kosong, error, dan loading sudah dirancang, bukan hanya layar normal.
- [ ] Maskot tampil benar di mode gelap dan terang.
- [ ] Font dibundel lokal (tidak ada permintaan jaringan).
- [ ] Animasi mengikuti pengaturan kurangi gerakan.
- [ ] Ikon aplikasi terbaca pada ukuran kecil dan aman untuk adaptive icon.

## 13. Keputusan terbuka

| # | Pertanyaan | Default sementara | Status |
|---|---|---|---|
| D1 | Bahasa UI bawaan: Indonesia atau Inggris, atau keduanya? | Indonesia, dengan opsi Inggris | Terbuka — mockup memakai Indonesia |
| D2 | Nama maskot | "Aru" (nama kerja) | Terbuka |
| D3 | Aksen: amber atau teal? | Amber `#F5B83D` | **Tetap amber** (dipakai di seluruh mockup) |
| D4 | Maskot digambar sendiri atau oleh ilustrator? | Digambar orisinal (bukan salinan referensi) | Terbuka — versi Stitch hanya penanda posisi |
| D5 | Mode terang perlu sejak Fase 1? | Ya, tapi gelap tetap default | **Ya** — 8 layar mode terang sudah ada di `stitch/light/` |
| D6 | Apakah ada onboarding 1-2 layar saat pertama dibuka? | Satu layar singkat (janji "offline & privat") | **Ya, satu layar** (bagian 7.8) |
