---
name: Arulume Warm Light
colors:
  # === Token Arulume (kanonik, dari Docs/design.md) ===
  bg: '#F7F6F3'
  surface: '#FFFFFF'
  surface-2: '#EFEDE8'
  border: '#E2DFD8'
  text: '#111214'
  text-2: '#5B5E66'
  accent: '#F5B83D'
  on-accent: '#111214'
  accent-text: '#8E6200'
  success: '#28784B'
  danger: '#BD3B3B'
  warning: '#9C5A16'
  # === Alias Material 3 untuk Stitch (nilai sama, bukan warna baru) ===
  background: '#F7F6F3'
  surface-dim: '#F7F6F3'
  surface-container-lowest: '#FFFFFF'
  surface-container-low: '#FFFFFF'
  surface-container: '#FFFFFF'
  surface-container-high: '#EFEDE8'
  surface-container-highest: '#E2DFD8'
  surface-variant: '#EFEDE8'
  outline: '#E2DFD8'
  outline-variant: '#E2DFD8'
  on-surface: '#111214'
  on-background: '#111214'
  on-surface-variant: '#5B5E66'
  secondary: '#5B5E66'
  on-secondary: '#FFFFFF'
  secondary-container: '#EFEDE8'
  on-secondary-container: '#111214'
  primary: '#F5B83D'
  on-primary: '#111214'
  primary-container: '#F5B83D'
  on-primary-container: '#111214'
  surface-tint: '#F5B83D'
  error: '#BD3B3B'
  on-error: '#FFFFFF'
  error-container: '#BD3B3B'
  on-error-container: '#FFFFFF'
typography:
  title:
    fontFamily: Plus Jakarta Sans
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
  heading:
    fontFamily: Plus Jakarta Sans
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 26px
  body:
    fontFamily: Plus Jakarta Sans
    fontSize: 15px
    fontWeight: '400'
    lineHeight: 22px
  body-medium:
    fontFamily: Plus Jakarta Sans
    fontSize: 15px
    fontWeight: '500'
    lineHeight: 22px
  caption:
    fontFamily: Plus Jakarta Sans
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
rounded:
  sm: 12px
  md: 16px
  lg: 24px
  full: 9999px
spacing:
  xs: 4px
  sm: 8px
  md: 12px
  lg: 16px
  xl: 24px
  xxl: 32px
  xxxl: 48px
  margin: 20px
---

# Arulume — design system, mode terang (kanonik)

Arulume adalah aplikasi Android offline untuk memperjelas dan memperbesar foto. Bagian dari brand Aruthtale. Tagline: "Kembalikan cahaya fotomu" / "Bring your photos back to light".

Varian mode terang. Nilai warna mengikuti tabel mode terang di `Docs/design.md` bagian 3.1. **Kalau ada konflik, `Docs/design.md` yang menang.**

## Aturan mutlak

1. **Satu aksen saja: amber `#F5B83D`.** Tidak ada warna merek kedua. Tidak ada biru, ungu, teal, atau hijau dekoratif.
2. **Teks di atas amber selalu `#111214`** (gelap), tidak pernah putih.
3. **Tanpa gradien, tanpa bayangan, tanpa glow.** Pemisahan memakai perbedaan warna surface dan garis tipis `#2F3136`.
4. **Hanya empat gaya teks:** title 24/32/700, heading 18/26/600, body 15/22/400-500, caption 12/16/500. Tidak ada ukuran lain.
5. **Satu tombol utama per layar.** Tombol utama: tinggi 56, radius 16, amber, teks gelap. Aksi lain lebih tenang (surface-2 atau teks saja).
6. **Foto adalah bintangnya.** Di layar Editor dan Hasil, foto memegang area terbesar dan tidak boleh tertutup elemen dekoratif atau maskot.
7. **Maskot hanya di momen tertentu:** Beranda, onboarding, riwayat kosong, memproses (kecil), sukses singkat, error. **Tidak** di Editor, tidak di atas foto Hasil, tidak di Pengaturan.
8. **Tanpa istilah teknis di layar utama.** Kata terlarang: tile, model, inference, tensor, Vulkan, ncnn, GPU, checksum. Hanya boleh di bagian "Lanjutan" dan "Tentang".
9. **Target sentuh minimum 48×48 dp**, margin layar 20 dp kiri-kanan.
10. **Ikon outline**, stroke 1,75-2, ukuran 24. Bukan ikon isi (filled), kecuali status aktif.
11. Tanpa emoji sebagai ikon atau hiasan.
12. **Tanpa bottom navigation bar / tab bar.** Navigasi: Pengaturan lewat ikon gear di bar atas Beranda; Riwayat lewat baris "Terbaru" di Beranda atau dari baris "Terbaru" hasil terakhir. Maksimal satu tombol kembali di bar atas.
13. **Tanpa jargon di layar utama** — termasuk kata "AI", "model", "skala" pada label status. Tulis "Seimbang", bukan "AI Seimbang".

## Maskot: Aru

Kucing hitam kecil, siluet solid satu warna, telinga runcing, dua mata bulat putih, ekor melengkung. Ekspresi dibawa oleh mata dan satu simbol kecil (hati, tanda tanya, percikan). Tanpa gradasi, tanpa garis halus. Ukuran 64 / 96 / 160 / 240 dp.

## Layar dan salinan (Indonesia)

1. **Beranda** — nama "Arulume", badge "Offline" (ikon pesawat), ikon pengaturan. Maskot duduk. Tagline "Kembalikan cahaya fotomu". Tombol utama "Pilih Foto". Tombol sekunder "Pilih Video" dengan tag "Segera". Baris "Terbaru" berisi thumbnail hasil (maks 6) atau state kosong "Belum ada cerita".
2. **Editor** — judul "Atur Peningkatan", foto besar ±45% tinggi layar, info resolusi + ukuran file. Chip preset "Cepat / Seimbang / Maksimal" (default Seimbang). Segmen skala "2x / 3x / 4x" (default 2x). Bagian "Lanjutan" tertutup. Tombol utama "Tingkatkan" menempel di bawah. Tanpa maskot.
3. **Memproses** — foto diredupkan di latar, teks "Meningkatkan… 62%", progress bar amber 8 dp, perkiraan sisa waktu, tombol teks "Batal". Maskot kecil ≤64 dp di sisi, tidak menutupi progres.
4. **Hasil** — slider sebelum/sesudah ±70% tinggi layar dengan pegangan bulat 40 dp dan label "Asli"/"Hasil", petunjuk cubit-zoom sekali. Info resolusi baru + lama proses + "Tersimpan di Galeri". Tombol "Bagikan" dan "Buka di Galeri" (sekunder), "Tingkatkan Lagi", tombol utama "Selesai". Tanpa maskot di area foto.
5. **Riwayat** — grid 2 kolom, thumbnail rasio asli dipotong rapi, label tanggal dan skala (caption). State kosong: maskot mengintip + "Belum ada cerita". Tekan lama untuk memilih.
6. **Pengaturan** — daftar sederhana: Tema (Gelap/Terang/Ikuti sistem), Preset bawaan, Format bawaan, Hapus cache, Ekspor log lokal, Tentang & lisensi. Tanpa maskot, tanpa tombol amber besar.
7. **Error** — maskot bingung 96 dp, pesan manusia singkat, satu tombol utama "Coba lagi", kode teknis kecil di bawah (caption, mis. `E_INPUT_TOO_LARGE`).
8. **Onboarding** — satu layar, maskot melambai 160-240 dp, judul janji "Offline & privat", tiga poin singkat, tombol utama "Mulai".

## Nada salinan

Kalimat pendek, hangat, tanpa jargon, satu ide per kalimat. Contoh: "Foto ini terlalu besar untuk 4x. Coba 2x atau perkecil fotonya." · "Perangkat memakai mode standar, proses bisa lebih lama." · "Ups, ada yang tidak berjalan. Coba lagi ya."
