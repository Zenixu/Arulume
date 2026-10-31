# stitch/ — ekspor desain Arulume

Mockup layar Arulume dari **Google Stitch**, dipakai sebagai acuan visual saat membangun UI Next.js.
Dokumen ini bukan sumber kebenaran token — sumbernya `../Docs/design.md`.

## Project Stitch

| Mode | Project ID | Buka |
|---|---|---|
| Gelap (default) | `16084830281983020534` | https://stitch.google.com/projects/16084830281983020534 |
| Terang | `13677577835905979214` | https://stitch.google.com/projects/13677577835905979214 |

Design system: **Arulume Warm Dark** dan **Arulume Warm Light** (font Plus Jakarta Sans, aksen amber `#F5B83D`, roundness 12).

## Struktur

```
stitch/
├─ DESIGN.md              # design system mode gelap (dipakai Stitch)
├─ DESIGN-light.md        # design system mode terang
├─ _manifest.json         # id layar, URL canvas, judul
├─ dark/<layar>/          # code.html + screen.png
└─ light/<layar>/         # code.html + screen.png
```

Setiap folder layar berisi:

- `code.html` — HTML statis hasil Stitch (Tailwind CDN + Google Fonts; **hanya untuk pratinjau**).
- `screen.png` — 780×1768 px (390×884 @2x), **dirender lokal dari `code.html`**, bukan dari server Stitch. Jadi kalau HTML diedit, PNG-nya selalu ikut benar.

## Layar mode gelap

| Layar | Folder | Screen ID | Judul di Stitch |
|---|---|---|---|
| 01 Beranda | `dark/beranda/` | [`96586901`](https://stitch.google.com/projects/16084830281983020534?node-id=96586901f45b4df798ba959aa147254b) | Arulume - Beranda |
| 02 Editor | `dark/editor/` | [`cf519e4f`](https://stitch.google.com/projects/16084830281983020534?node-id=cf519e4f5960402ba2b218b16fdac353) | Arulume - Atur Peningkatan |
| 03 Memproses | `dark/memproses/` | [`f9a72f24`](https://stitch.google.com/projects/16084830281983020534?node-id=f9a72f24308b4cb99d163f129832e854) | Arulume - Memproses Foto (Pembaruan Status) |
| 04 Hasil | `dark/hasil/` | [`9f795dd0`](https://stitch.google.com/projects/16084830281983020534?node-id=9f795dd0b16b426a9ac1f1c433c1e643) | Arulume - Hasil |
| 05 Riwayat | `dark/riwayat/` | [`d858b26e`](https://stitch.google.com/projects/16084830281983020534?node-id=d858b26e16ec4053afb3295e545fd3b1) | Arulume - Riwayat |
| 06 Pengaturan | `dark/pengaturan/` | [`f710fce9`](https://stitch.google.com/projects/16084830281983020534?node-id=f710fce9f50147958e56b52de05dedf3) | Arulume - Pengaturan (Tanpa Navigasi Bawah) |
| 07 Error | `dark/error/` | [`da5b558b`](https://stitch.google.com/projects/16084830281983020534?node-id=da5b558b079f4676adfd8b3fc884eae9) | Arulume - Error Foto Terlalu Besar |
| 08 Onboarding | `dark/onboarding/` | [`5c181cf1`](https://stitch.google.com/projects/16084830281983020534?node-id=5c181cf15f544b3fb485c84ed6ad1b40) | Arulume - Onboarding |

## Layar mode terang

| Layar | Folder | Screen ID | Judul di Stitch |
|---|---|---|---|
| 01 Beranda | `light/beranda/` | [`b4650f90`](https://stitch.google.com/projects/13677577835905979214?node-id=b4650f906d8a4b04abd99cc2a7a79e7a) | Arulume Home |
| 02 Editor | `light/editor/` | [`55ec2602`](https://stitch.google.com/projects/13677577835905979214?node-id=55ec2602f6f14c9f80e496ad07128aca) | Atur Peningkatan |
| 03 Memproses | `light/memproses/` | [`8f8a5d5a`](https://stitch.google.com/projects/13677577835905979214?node-id=8f8a5d5a1f0c40358efdb310d20d28c8) | Memproses |
| 04 Hasil | `light/hasil/` | [`b0d45b1c`](https://stitch.google.com/projects/13677577835905979214?node-id=b0d45b1c3e8143b78ae640cce34981a8) | Hasil - Arulume |
| 05 Riwayat | `light/riwayat/` | [`6c267859`](https://stitch.google.com/projects/13677577835905979214?node-id=6c26785992514b41a48fe3046cb69b8e) | Riwayat - Arulume |
| 06 Pengaturan | `light/pengaturan/` | [`554fc7a0`](https://stitch.google.com/projects/13677577835905979214?node-id=554fc7a05a754d7c89fbd1db1207729a) | Pengaturan - Arulume |
| 07 Error | `light/error/` | [`4eda7610`](https://stitch.google.com/projects/13677577835905979214?node-id=4eda76109b5b4193baec6feebce17ab6) | Error Terlalu Besar - Arulume |
| 08 Onboarding | `light/onboarding/` | [`467263be`](https://stitch.google.com/projects/13677577835905979214?node-id=467263be03cf40e691765ae4e8ee955e) | Onboarding 1 - Arulume |

## Cara memperbarui

1. Ubah dulu `../Docs/design.md` (token, komponen, salinan). Dokumen itu yang menang.
2. Samakan `DESIGN.md` / `DESIGN-light.md` (front-matter token + aturan prose).
3. Regenerate atau edit layar lewat CLI:

```bash
# generate layar baru ke project yang ada
stitch generate screen --project 16084830281983020534 --device MOBILE --prompt "<prompt>"

# edit satu layar (tanpa mengubah layout lain)
stitch edit screen <screen-id> --project 16084830281983020534 --prompt "<perubahan>"

# ambil HTML + screenshot
stitch get screen <screen-id> --project 16084830281983020534 --format json
```

4. Unduh `htmlCode.downloadUrl` dan `screenshot.downloadUrl` dengan header
   `Authorization: Bearer <access_token>` (token ada di `~/.stitch/credentials.json`), lalu
   simpan `dataString` hasil decode sebagai `code.html` / `screen.png`.
5. Cek tidak ada warna di luar token:

```bash
grep -ohE '#[0-9a-fA-F]{6}' dark/*/code.html | sort -u
```

## Aturan yang sudah ditanam di design system

- Satu aksen amber; teks di atas amber selalu `#111214`.
- Tanpa gradien, tanpa bayangan, tanpa glow.
- Empat gaya teks saja (title 24/700, heading 18/600, body 15, caption 12).
- Satu tombol utama per layar; target sentuh ≥ 48 dp; margin 20 dp.
- **Tanpa bottom navigation bar.** Pengaturan dari ikon gear di Beranda; Riwayat dari baris "Terbaru".
- Tanpa jargon di layar utama (tidak ada "AI", "model", "tile", "GPU").
- Maskot Aru hanya di Beranda, onboarding, riwayat kosong, memproses (kecil), sukses singkat, error. Tidak di Editor dan tidak di atas foto Hasil.

## Catatan penting untuk implementasi

- `code.html` memakai **Tailwind CDN dan Google Fonts dari internet**. Itu hanya untuk pratinjau desain. Aplikasi asli **wajib** membundel font Plus Jakarta Sans secara lokal dan tidak boleh memuat apa pun dari jaringan (lihat `../Docs/AGENTS.md` aturan 1).
- Mockup ini memakai token bernama Material (`bg-bg`, `text-on-surface`, `bg-accent`). Saat menulis kode aplikasi, terjemahkan ke token Arulume sesuai tabel di `../Docs/design.md` bagian 3.4.
- Foto contoh di layar Editor/Hasil/Riwayat adalah gambar placeholder, bukan aset proyek.
