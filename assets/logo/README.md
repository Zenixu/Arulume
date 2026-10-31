# Logo Arulume

Berkas di folder ini adalah sumber ikon peluncur aplikasi.

| Berkas | Isi |
|---|---|
| `ikon.png` | Logo utama: kucing yang membentuk huruf "A", latar putih, tanpa teks. 914 × 914 px. |
| `buat-ikon.py` | Pembuat semua ukuran ikon Android dari `ikon.png`. |

## Kenapa tanpa teks

Logo aslinya satu lembar dengan tulisan "Arulume" di bawah ikon. Teks itu dibuang
karena namanya sudah muncul di bawah ikon pada layar utama HP — kalau ikonnya
memuat teks juga, di ukuran 48 px tulisannya jadi tidak terbaca dan hanya
menambah bintik.

## Membuat ulang ikon

```bash
python3 assets/logo/buat-ikon.py
```

Hasilnya menimpa:

- `android/app/src/main/res/mipmap-*/ic_launcher.png` — ikon lama (Android < 8), persegi membulat
- `android/app/src/main/res/mipmap-*/ic_launcher_round.png` — ikon lama bentuk bulat
- `android/app/src/main/res/mipmap-*/ic_launcher_foreground.png` — lapisan depan ikon adaptif
- `android/app/src/main/res/mipmap-*/ic_launcher_monochrome.png` — lapisan monokrom (ikon bertema Android 13+)

Latar ikon adaptif ada di `android/app/src/main/res/values/ic_launcher_background.xml`
(putih, mengikuti latar logo). Ukuran logo di dalam kanvas diatur lewat konstanta
`SKALA_*` di skrip: ikon adaptif memakai 0,50 karena Android hanya menjamin area
72 dp dari kanvas 108 dp terlihat.

## Mengganti logo

1. Ganti `ikon.png` dengan logo baru, **persegi** dan **tanpa teks**, latar putih.
2. Jalankan skrip di atas.
3. Build ulang APK, lalu periksa hasilnya di layar utama HP — jangan hanya percaya
   pratinjau, karena bentuk topeng ikon berbeda tiap peluncur.
