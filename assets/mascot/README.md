# Maskot Aru — aset

Maskot Arulume: kucing hitam, siluet solid, mata bulat putih, gaya vektor datar.
Berasal dari satu lembar stiker 40 ekspresi (`source/maskot-sheet.jpeg`) yang dipecah,
di-*trace* ke SVG, dan dirapikan agar memenuhi aturan `Docs/design.md` bagian 5.

## Isi folder

| Folder | Isi |
|---|---|
| `svg/` | **Dipakai aplikasi.** 40 SVG satu jalur, `fill="currentColor"`, latar transparan. Rata-rata 2,7 KB. |
| `png/ink/` | Pratinjau/fallback: siluet `#111214` di atas transparan (mode terang). 3× resolusi. |
| `png/paper/` | Pratinjau/fallback: siluet `#F7F6F3` di atas transparan (mode gelap). 3× resolusi. |
| `preview.png` | Kontak-sheet 40 ikon dalam dua tema. |
| `source/maskot-sheet.jpeg` | Sumber asli (8 baris × 5 kolom). |

## Cara pakai

```tsx
// Mata adalah lubang (cut-out), jadi warna latar kartu yang terlihat lewat mata.
<span className="text-[#111214] dark:text-[#F7F6F3]">
  <img src="/mascot/duduk-manis.svg" alt="" width={96} height={96} />
</span>
```

Aturan (dari `Docs/design.md` bagian 5):

- SVG memakai `currentColor`; warna ditentukan oleh CSS di sekitarnya. **Jangan** menyimpan versi berwarna-warni.
- Mata dan detail adalah **lubang transparan**. Karena itu maskot harus diletakkan di atas `surface`/`bg` yang kontras — jangan di atas foto.
- Ukuran standar 64 / 96 / 160 / 240 dp. Tetap terbaca pada 32 dp (kecuali `asap` dan `ide` yang detailnya halus — minimal 48 dp).
- Maskot dekoratif diberi `aria-hidden="true"` / `alt=""`; maskot yang menyampaikan status diberi label.
- Maskot **tidak boleh** menutupi foto, dan tidak muncul di layar Editor, Pengaturan, atau di atas foto layar Hasil.

## 10 mood inti (rujuk `design.md` bagian 5.4)

| Mood | Berkas | Catatan |
|---|---|---|
| 1. Netral / duduk | `duduk-manis` | maskot utama Beranda |
| 2. Mengintip | `mengintip` | state kosong |
| 3. Melambai | `angkat-kaki` | onboarding |
| 4. Sibuk / berpikir | `ide` | layar Memproses, ≤ 64 dp |
| 5. Berbinar (sukses) | `berbinar` | sesaat setelah selesai |
| 6. Bingung (error) | `pusing-spiral` | layar Error |
| 7. Mengantuk | `datar` | antrian lama |
| 8. Kepanasan | `keringat` | video: termal/baterai |
| 9. Tidur | `tidur-zzz` | idle |
| 10. Jejak kaki | `jejak-kaki` | dekorasi/pemisah |

## Semua 40 ekspresi

| # | Berkas | Isi | Dipakai di |
|---|---|---|---|
| 1 | `amplop` | di dalam amplop | Riwayat kosong (alternatif) |
| 2 | `angkat-kaki` | mengangkat kaki, mata lebar | Onboarding (mood 3 melambai) |
| 3 | `asap` | kepulan asap di depan mulut | — |
| 4 | `berbaring` | berbaring memanjang | — |
| 5 | `berbinar` | mata bintang + garis pancaran | Sukses sesaat di layar Hasil (mood 5) |
| 6 | `berputar` | berputar (spiral titik) | — |
| 7 | `cangkir` | memegang cangkir | — |
| 8 | `dari-belakang` | tampak belakang, ekor terangkat | — |
| 9 | `datar` | wajah datar, mulut garis | Memproses/antrian lama (mood 7 mengantuk) |
| 10 | `diperban` | kepala diperban (silang) | — |
| 11 | `duduk-ekor-panjang` | duduk, ekor panjang | — |
| 12 | `duduk-manis` | duduk tenang, ekor melingkar | Beranda, state kosong (mood 1 netral) |
| 13 | `gagang-telepon` | gagang telepon di kepala | — |
| 14 | `garis-wajah` | wajah garis (outline) | — |
| 15 | `gigi-gemeter` | gigi gemeletuk, mata tertutup | — |
| 16 | `hati-bersemangat` | duduk dalam hati + garis gerak | Sukses alternatif |
| 17 | `ide` | bola lampu di atas kepala | Memproses kecil (mood 4 sibuk/berpikir) |
| 18 | `jejak-kaki` | jejak kaki | Pemisah/badge dekoratif (mood 10) |
| 19 | `kaget` | mata lebar + tanda seru | Error tak terduga |
| 20 | `kedip-lambaikan` | mengedip + mengangkat kaki | — |
| 21 | `keringat` | berkeringat, mata lebar | Video: panas/baterai (mood 8) |
| 22 | `lingkaran-cahaya` | lingkaran cahaya (halo) | — |
| 23 | `malu-tertutup` | kepala tertutup garis | — |
| 24 | `marah-berdiri` | marah, ekor bergerigi | — |
| 25 | `marah-ledakan` | menggembung + simbol ledakan | Error berat (mis. model hilang) |
| 26 | `mata-bintang` | mata bintang + bintang kecil | — |
| 27 | `mata-bulat` | mata bulat besar | Netral alternatif |
| 28 | `mata-separuh` | mata setengah terbuka | — |
| 29 | `menangis` | mata berair + tetes air mata | Gagal menyimpan |
| 30 | `mengendap` | merunduk, ekor melengkung | — |
| 31 | `mengintip` | mengintip dari balik kotak | Riwayat kosong, Beranda kosong (mood 2) |
| 32 | `oke` | balon 'ok' | Konfirmasi ringan |
| 33 | `peluk-hati` | duduk memeluk hati | Sukses alternatif, riwayat kosong |
| 34 | `pusing-spiral` | mata spiral (pusing) | Layar Error (mood 6 bingung) |
| 35 | `senang-mata-tutup` | mata tertutup, senang | — |
| 36 | `siap-lompat` | mengangkat kaki, siap lompat | — |
| 37 | `telepon` | di samping telepon berdering | Sibuk alternatif |
| 38 | `tenang-balon` | mata setengah, balon ucapan | — |
| 39 | `tidur-bulan` | tidur melingkar di bulan sabit | Idle alternatif |
| 40 | `tidur-zzz` | tidur + Zzz | Idle, layar kosong (mood 9 tidur) |

## Regenerasi

Alur yang dipakai (butuh `potrace` + ImageMagick + Pillow):

1. Deteksi 40 sel dengan *connected components* (`magick ... -connected-components`), bukan grid seragam —
   jarak antar stiker tidak persis sama sehingga potongan seragam memotong gambar.
2. Crop tiap sel (union bbox + 3 px), lalu threshold 145 pada 3× upscale.
3. `potrace --turdsize 2 --alphamax 1.2 --opttolerance 0.2` → SVG.
4. Bersihkan SVG: buang deklarasi XML/DOCTYPE/metadata, `fill="#000000"` → `currentColor`.
5. Render ulang pratinjau untuk memastikan 40 ikon tampil di dua tema.

## Status lisensi

Asal berkas: lembar stiker milik pemilik proyek. **Status lisensi belum dikonfirmasi** — dicatat di
`Docs/THIRD_PARTY.md` bagian 3. Sebelum APK dibagikan ke siapa pun, pastikan hak pakainya jelas.
