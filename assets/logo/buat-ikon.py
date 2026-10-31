#!/usr/bin/env python3
"""Membuat semua ukuran ikon peluncur Android dari `assets/logo/ikon.png`.

Jalankan dari akar repo:

    python3 assets/logo/buat-ikon.py

Butuh Pillow (`pip install pillow`). Hasilnya menimpa berkas di
`android/app/src/main/res/mipmap-*/`:

- `ic_launcher.png`          ikon lama (API < 26), persegi membulat
- `ic_launcher_round.png`    ikon lama bentuk bulat
- `ic_launcher_foreground.png` lapisan depan ikon adaptif (kanvas 108 dp)
- `ic_launcher_monochrome.png` lapisan monokrom untuk ikon bertema Android 13+

Latar ikon adaptif diatur di `res/values/ic_launcher_background.xml` (putih).
"""

from pathlib import Path

from PIL import Image, ImageDraw

AKAR = Path(__file__).resolve().parents[2]
SUMBER = AKAR / "assets/logo/ikon.png"
RES = AKAR / "android/app/src/main/res"

# Ukuran px per kerapatan layar.
IKON_LAMA = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
IKON_ADAPTIF = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}

# Bagian lebar kanvas yang dipakai logo.
SKALA_LAMA = 0.84       # ikon lama: logo hampir memenuhi bidang
SKALA_BULAT = 0.78      # ikon bulat: sedikit lebih kecil karena sudutnya terpotong
SKALA_ADAPTIF = 0.50    # zona aman ikon adaptif (72 dp dari 108 dp)


def siluet(gambar: Image.Image) -> Image.Image:
    """Ubah logo berlatar putih menjadi siluet hitam transparan.

    Dipakai untuk lapisan monokrom: Android hanya memakai kanal alpha, lalu
    mewarnainya sendiri sesuai tema.
    """
    abu = gambar.convert("L")
    keluaran = Image.new("RGBA", gambar.size, (0, 0, 0, 0))
    piksel_abu = abu.load()
    piksel = keluaran.load()
    for y in range(gambar.height):
        for x in range(gambar.width):
            nilai = piksel_abu[x, y]
            if nilai >= 235:          # putih → transparan
                continue
            if nilai <= 120:          # gelap → pekat
                alfa = 255
            else:                     # tepi anti-alias
                alfa = int((235 - nilai) * 255 / (235 - 120))
            piksel[x, y] = (0, 0, 0, alfa)
    return keluaran


def tempel(kanvas: Image.Image, gambar: Image.Image, bagian: float) -> None:
    """Tempel `gambar` di tengah `kanvas`, selebar `bagian` × sisi kanvas."""
    sisi = kanvas.width
    ukuran = int(sisi * bagian)
    kecil = gambar.resize((ukuran, ukuran), Image.LANCZOS)
    posisi = ((sisi - ukuran) // 2, (sisi - ukuran) // 2)
    kanvas.paste(kecil, posisi, kecil if kecil.mode == "RGBA" else None)


def main() -> None:
    logo = Image.open(SUMBER).convert("RGB")
    mono = siluet(logo)

    for dpi, sisi in IKON_LAMA.items():
        folder = RES / f"mipmap-{dpi}"
        folder.mkdir(parents=True, exist_ok=True)

        # Persegi membulat (dibuat 4× lalu diperkecil agar tepinya halus).
        persegi = Image.new("RGBA", (sisi * 4, sisi * 4), (0, 0, 0, 0))
        ImageDraw.Draw(persegi).rounded_rectangle(
            [0, 0, sisi * 4 - 1, sisi * 4 - 1], radius=int(sisi * 4 * 0.22), fill=(255, 255, 255, 255)
        )
        tempel(persegi, logo, SKALA_LAMA)
        persegi.resize((sisi, sisi), Image.LANCZOS).save(folder / "ic_launcher.png")

        bulat = Image.new("RGBA", (sisi * 4, sisi * 4), (0, 0, 0, 0))
        ImageDraw.Draw(bulat).ellipse([0, 0, sisi * 4 - 1, sisi * 4 - 1], fill=(255, 255, 255, 255))
        tempel(bulat, logo, SKALA_BULAT)
        bulat.resize((sisi, sisi), Image.LANCZOS).save(folder / "ic_launcher_round.png")

    for dpi, sisi in IKON_ADAPTIF.items():
        folder = RES / f"mipmap-{dpi}"

        depan = Image.new("RGBA", (sisi, sisi), (255, 255, 255, 255))
        tempel(depan, logo, SKALA_ADAPTIF)
        depan.save(folder / "ic_launcher_foreground.png")

        lapis_mono = Image.new("RGBA", (sisi, sisi), (0, 0, 0, 0))
        tempel(lapis_mono, mono, SKALA_ADAPTIF)
        lapis_mono.save(folder / "ic_launcher_monochrome.png")

    print("Ikon dibuat untuk:", ", ".join(IKON_ADAPTIF))


if __name__ == "__main__":
    main()
