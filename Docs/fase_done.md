# Fase Selesai — Arulume

Catatan ini menjawab satu pertanyaan saja: **apa yang sudah benar-benar selesai**, per fase.
Daftar pekerjaan dan urutannya tetap di [`TASKS.md`](TASKS.md); ini arsip hasilnya.

Aturan pakai:

- Satu bagian per fase. Tulis tanggal selesai, commit, dan bukti uji di perangkat.
- Centang di `TASKS.md` saat item benar-benar lulus, lalu ringkas hasilnya di sini.
- Fase yang belum selesai **tidak** ditulis sebagai selesai — pakai status `Sedang berjalan` dan sebut sisanya.
- Jangan simpan token, keystore, atau sandi di dokumen ini.

---

## Fase 0 — Fondasi & Prototipe Native

**Status:** Selesai · 31 Oktober 2026
**Commit:** `0494d77` (fondasi), `03ee93f` (native + benchmark)

Yang selesai:

- Dokumen produk lengkap: PRD, design, arsitektur, build, third-party, agents, README, CHANGELOG.
- `LICENSE`, `.gitignore` (tanpa keystore, `local.properties`, `assets/public/`).
- Desain 16 layar di Stitch (tema gelap & terang) + audit kontras token.
- 40 maskot SVG/PNG orisinal, satu warna `currentColor`.
- Scaffold aplikasi: Next.js 16 static export (`output: 'export'`) + Capacitor 8, id `id.aruthtale.arulume`.
- Manifest **tanpa** izin `INTERNET` (aturan A7 terbukti).
- Mesin native: ncnn + JNI + model Real-ESRGAN compact (`realesr-animevideov3` x2 & x4), tiling 256/pad 16.
- `benchmark-fase0.md` terisi: CPU vs GPU di Redmi Note 8 (Adreno 610, Snapdragon 665, API 30, arm64).

Bukti:

- APK terpasang dan menjalankan inferensi tanpa jaringan.
- 1024² tanpa tiling → OOM-kill; dengan tile 256 → stabil.
- Vulkan 15–60% lebih lambat dari CPU di perangkat ini → jalur cepat = CPU.

---

## Fase 1 — MVP Foto

**Status:** Sedang berjalan — semua fitur P0 terpasang dan teruji di HP; sisa 4 butir Definition of Done
**Commit:** `a0bb737` (foto), `155b7f0` (riwayat, pengaturan, notifikasi), `04175c6` (batch, format, onboarding), `ee03c38` (tampilan), `55cc11d` (ikon)

Yang sudah selesai dan teruji di perangkat:

- **Pilih foto** lewat Photo Picker (API 33+) / SAF, tanpa izin runtime; tahan proses dibunuh (pending pick di `SharedPreferences`).
- **Editor**: pilih **2×** atau **4×**, ukuran keluaran dihitung jujur (`2048 / skala` sebagai sumber).
- **Proses**: job asinkron di `enhancer-worker`, event `progress` / `completed` / `failed`, bisa dibatalkan.
- **Hasil tidak otomatis ke galeri** (FR-06 revisi): ditulis ke `cacheDir/hasil/`, cache dipangkas 3 terbaru.
- **Layar Hasil**: pembanding sebelum/sesudah yang bisa digeser satu jari, cubit untuk zoom sampai 5×, plus alternatif tombol Bandingkan / Sebelum / Sesudah.
- **Simpan manual**: tombol "Simpan ke Galeri" → `Pictures/Arulume/Arulume_{yyyyMMdd-HHmmss}.jpg`; berubah jadi "Buka di Galeri" setelah tersimpan.
- **Tombol back** kembali ke Beranda dulu (lewat `OnBackPressedDispatcher`), bukan langsung keluar.
- Badge "Offline" di Beranda dihapus; klaim offline tetap lewat teks "Semua diproses di HP. Tanpa unggah."

Bukti uji (Redmi Note 8, 31 Oktober 2026):

- 2× dari 1600×900 → 2048×1152 selesai 14,8 dtk; 4× foto kecil selesai 2,0 dtk.
- Galeri hanya bertambah setelah tombol Simpan ditekan (`Arulume_20261031_113847.jpg`).
- Back dari layar Info perangkat → Beranda, aplikasi tetap hidup; back lagi → ke launcher.

### Fase 1.2 — Riwayat & Pengaturan

**Status:** Selesai · 31 Oktober 2026

- **Riwayat (FR-08)**: satu berkas `filesDir/riwayat.json`, maks 60 entri, ditulis di thread worker.
  Layar `/riwayat` (daftar + mode pilih + hapus dua tahap) dan `/riwayat/lihat` (detail memakai `CompareView`
  yang sama, tanpa memproses ulang). Menghapus entri **tidak** menghapus foto di galeri.
- **Beranda**: bagian "Terbaru" menampilkan 3 entri nyata dari riwayat + tautan "Lihat semua".
- **Pengaturan (FR-09)**: tema `Gelap/Terang/Ikuti sistem` (`arulume.theme`), skala bawaan 2×/4× (`arulume.skala`),
  tombol bersihkan cache, info versi. Skrip anti-kedip dipasang di `layout.tsx`.
- **Perbaikan**: `tandaiTersimpan` membandingkan **nama berkas**, bukan path penuh
  (`/data/user/0/...` vs `/data/data/...` membuat `savedUri` selalu kosong).

Bukti: tema berganti dan bertahan; `riwayat.json` berisi entri nyata; galeri bertambah hanya setelah Simpan.

### Fase 1.3 — Notifikasi progres & proses tetap hidup

**Status:** Selesai · 31 Oktober 2026 · FR-12, FR-13

Masalah yang diselesaikan: kalau aplikasi ditinggal saat mengekstrak foto berat, proses lama bisa ikut mati.

- **Notifikasi lokal, bukan Firebase.** FCM mewajibkan izin `INTERNET` + `google-services.json`, keduanya
  dilarang `AGENTS.md` aturan 1. Notifikasi dibuat dengan `NotificationCompat` (`ProsesNotif.kt`):
  kanal `arulume.proses` (IMPORTANCE_LOW, senyap, `ongoing`) dengan bar progres, dan `arulume.hasil` untuk hasil.
  Firebase CLI tidak dipakai sama sekali.
- **`EnhancerService.kt`**: foreground service tipe `dataSync`, `startForeground` < 5 dtk, `PARTIAL_WAKE_LOCK`
  berbatas 30 menit. Aksi `MULAI` dan `BATAL`.
- **`JobRunner.kt`**: logika job dipindah keluar dari plugin menjadi `object`, jadi tetap hidup walau
  Activity/WebView sudah hilang. Plugin hanya menjembatani event (`progress`/`completed`/`failed`).
- **Progres nyata dari C++**: `ProgressBridge` di `enhancer_jni.cpp` memanggil `TileProgress.onTile(done, total)`
  setiap tile selesai; `JobRunner` memetakannya ke 15–88%, sisanya decode/encode/simpan.
- **Pemulihan UI**: `getJobStatus()` dipakai layar Editor saat dibuka ulang; kalau job masih hidup bar progres
  disambung, kalau sudah selesai user diarahkan ke hasil.
- **Izin baru**: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`, `WAKE_LOCK`.
  `INTERNET` tetap tidak ada (diperiksa di APK hasil build).

Bukti uji (Redmi Note 8, API 30):

- Job 2× (foto 944×2048) berjalan 18,4 dtk; notifikasi `id=1001` bergerak 15% → 100% dengan teks "12/12 bagian".
- Home ditekan saat job berjalan → job tetap selesai, hasil tertulis, entri riwayat bertambah.
- Belum diuji ulang: pembunuhan proses oleh sistem (`am kill`/Doze) saat job berjalan — bukti kuat saat ini hanya Home + layar mati.
- Job gagal (berkas hilang) → notifikasi "Gagal memperbesar foto · Foto tidak bisa dibaca", bukan teks `ENOENT`.

---

## 1.3 Batch foto, format hasil & onboarding

**Status:** Selesai · 31 Oktober 2026 · FR-11, FR-09

- **Satu job, banyak foto.** `pickMedia({ multiple: true })` memakai `ACTION_PICK_IMAGES` +
  `EXTRA_ALLOW_MULTIPLE` (fallback `ACTION_OPEN_DOCUMENT`), hasil `ClipData` dikumpulkan di
  `MainActivity.onActivityResult` → `ArrayList<Uri>`. Batas `MAX_BATCH = 20`.
- **Diproses berurutan** di `enhancer-worker`: satu foto gagal tidak menghentikan antrian,
  batal membatalkan seluruh antrian, progres notifikasi = persen gabungan job
  (`itemPercent` per foto dikirim terpisah).
- **Layar `/batch`**: daftar foto + keadaan (menunggu/proses/selesai/gagal), progres gabungan,
  simpan satu per satu atau "Simpan semua". Tidak ada foto yang masuk galeri tanpa ditekan.
- **Nama berkas unik**: `ImagePipeline.writeResult` menambah akhiran `-2`, `-3`, … kalau dua foto
  selesai di detik yang sama (batch bisa begitu).
- **Format hasil**: JPEG / PNG / WebP, dipilih di Pengaturan (`arulume.format`), dipakai editor
  maupun batch. PNG/WebP transparan diratakan ke putih sebelum inferensi (mesin native RGB).
- **Pemberitahuan batas output**: Editor menampilkan "Diproses pada N px sisi terpanjang → W × H"
  dan peringatan "Foto dikecilkan dulu supaya hasil pas 2048 px" saat sumber harus diperkecil.
- **Onboarding** `/mulai`: satu layar (maskot, tiga janji: tidak diunggah, hasil kamu putuskan,
  gratis tanpa akun), tampil sekali lewat `arulume.onboarding`.

Bukti uji (Redmi Note 8, API 30):

- Job batch 2 foto (1600×900 + 800×450) selesai 34 dtk, notifikasi 13% → 84% → "2 foto selesai
  diperbesar", `itemDone` index 0 (2048×1152) dan index 1 (1600×900), riwayat bertambah 2 entri.
- Pemilih multi-foto terbuka dalam mode pilih-banyak; kalau MIUI membunuh proses saat pemilih
  di depan, `getPendingPick()` mengembalikan daftar dan aplikasi lanjut ke `/batch` (bukan editor).
- Onboarding muncul pada peluncuran pertama (`pm clear`), tombol Mulai menyimpan penanda.
- Pengaturan menampilkan bagian FORMAT HASIL (JPG/PNG/WebP) dengan keterangan per pilihan.

### Mutu keluaran (set uji PRD 18.3)

Diuji satu per satu lewat jembatan plugin di Redmi Note 8 (API 30), skala 2×,
batas keluaran 2048 px, format JPEG:

| Foto | Masukan | Hasil | Waktu |
|---|---|---|---|
| #1 normal | 1000×1000 JPEG | 2000×2000 | 62,7 dtk* |
| #2 buram | 640×480 JPEG | 1280×960 | ~14 dtk |
| #3 transparan | 600×600 PNG alpha | 1200×1200 PNG, alpha utuh | 26,5 dtk |
| #4 EXIF 90° | 800×600 (tag 6) | 1200×1600 (diputar) | 26,0 dtk |
| #4 EXIF 270° | 800×600 (tag 8) | 1200×1600 (diputar) | 30,4 dtk |
| #5 WebP | 900×700 WebP | 1800×1400 | 31,3 dtk |
| #7 12 MP | 4000×3000 JPEG | 2048×1536 | 38,6 dtk |
| #8 panorama | 8000×1000 JPEG | 2048×256 | 8,7 dtk |
| #9 mini | 32×32 JPEG | 64×64 | 5,3 dtk |
| #10 rusak | JPEG terpotong 4 KB | `E_DECODE_FAILED` "Foto tidak bisa dibaca" | 5,2 dtk |
| #12 teks | 1200×800 JPEG | 2048×1364 | 30,0 dtk |
| #13 gelap | 800×600 JPEG berderau | 1600×1200 | 21,8 dtk |

\* HP sedang dipakai aplikasi lain (RAM tersisa sedikit, swap penuh); pada kondisi
lapang foto yang sama selesai ~15 dtk.

**20 foto berturut-turut** (10 job × 2 foto, antrian batch, aplikasi di latar depan):
semua selesai, riwayat 15 → 35 entri, 37,0 dtk per dua foto dan tidak melambat di job
ke-10, proses aplikasi tetap sama (tanpa crash, tanpa restart), tidak ada entri di
log crash.

Dua perbaikan yang lahir dari uji ini:

- **Alpha PNG/WebP dipertahankan** (TC-04). Sebelumnya transparansi diratakan ke putih.
  Sekarang bitmap sumber disimpan, lalu topeng alpha-nya diskalakan ke ukuran hasil dan
  dipasang kembali (`ImagePipeline.pasangAlpha`). Hasil uji #3: 443.936 px opak dan
  985.792 px transparan dari 1.440.000 px — sesuai bentuk aslinya.
- **Berkas terpotong ditolak.** `BitmapFactory` tetap "berhasil" mendekode JPEG yang
  terpotong (sisanya abu-abu). Sekarang `ImagePipeline.berkasUtuh` memeriksa penanda
  akhir (JPEG `FF D9`, PNG `IEND`) sebelum dekode, jadi user dapat pesan jelas.

### Sapaan beranda & mata maskot (revisi tampilan)

Keluhan: maskot terbaca sebagai siluet tanpa mata, dan kalimat beranda terasa kaku.

- Judul beranda: "Kembalikan cahaya fotomu" → **"Selamat datang di Arulume"** (menyapa, bukan slogan).
- Subjudul: "Semua diproses di HP. Tanpa unggah." → **"Perjelas foto dan video lama, semuanya di HP-mu."**
  (menyebut fungsi: foto *dan* video, tanpa kata teknis).
- **Mata:** dulu mata hanya lubang transparan di dalam path, jadi di layar terbaca sebagai
  dua bolong kosong. Sekarang tiap lubang mata diberi pupil (`src/components/mascot/eyes.ts`),
  sehingga mata terbaca sebagai mata pada ukuran 160 px sampai 40 px.
- Posisi pupil dicari otomatis: tiap SVG dirasterkan, lubang tertutup dideteksi, lalu dua lubang
  berukuran mirip di bagian atas dipakai sebagai mata. Hasilnya diperiksa satu per satu; hanya
  ekspresi yang benar-benar tepat yang dipakai: `duduk-manis`, `mata-bulat`, `mengendap`,
  `berbaring`, `duduk-ekor-panjang`. Ekspresi lain tetap siluet sampai ada pass desain khusus.
- Layar perkenalan (`/mulai`) memakai `mata-bulat` dan daftar riwayat kosong memakai `mengendap`,
  supaya layar besar yang pertama dilihat user selalu bermata.

Catatan teknis yang bikin pupil sempat tidak terlihat: isi `<g transform>` pada `paths.ts`
memakai koordinat path asli (skala 0,1 dan sumbu Y terbalik). Pupil harus digambar **di luar**
`<g>` dengan koordinat `viewBox`, bukan di dalamnya.

### Ikon peluncur baru

Ikon aplikasi diganti dengan logo Arulume (kucing yang membentuk huruf "A"), tanpa teks —
nama aplikasi sudah tampil di bawah ikon pada layar utama. Sumber: `assets/logo/ikon.png`,
pembuat semua ukuran: `assets/logo/buat-ikon.py`.

- Ikon adaptif (Android 8+): latar putih, logo 50 % kanvas 108 dp (zona aman 72 dp),
  plus lapisan monokrom untuk ikon bertema Android 13+.
- Ikon lama (Android 7): persegi membulat dan bulat, logo 84 %/78 %.
- Berkas vektor lama (`drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`,
  `drawable-v24/…`) dihapus supaya tidak ada dua sumber ikon.

### Status terhadap Definition of Done Fase 1 (PRD bagian 22)

| # | Syarat | Status | Bukti / sisa |
|---|---|---|---|
| 1 | Semua FR P0 Fase 1 berjalan | Hampir | FR-01…FR-13 terpasang; yang belum diuji end-to-end: bagikan hasil (TC-12) |
| 2 | Set uji 18.3 ≥ 98 % tanpa crash | Hampir | 11 dari 11 foto valid lulus, #10 (rusak) gagal terkode sesuai rancangan; #6 HEIC dan #11 wajah belum punya berkas uji |
| 3 | TC-01…TC-12 lulus | Belum | Lulus: TC-02, TC-04, TC-05, TC-10, TC-11. Belum: TC-01 (mode pesawat), TC-03 (batal 50 %), TC-06 (dialog batas 4× 12 MP), TC-07 (matikan GPU — belum ada tombolnya), TC-08 (penyimpanan penuh), TC-09 (rotasi), TC-12 (bagikan) |
| 4 | APK rilis bertanda tangan ≤ 60 MB | Belum | Baru APK debug; keystore belum dibuat |
| 5 | `aapt dump permissions` tanpa `INTERNET` | Lulus | Manifest hasil build tidak memuat `INTERNET`; hanya `WRITE_EXTERNAL_STORAGE` (maxSdk 28), `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`, `WAKE_LOCK` |
| 6 | Semua fitur jalan saat mode pesawat | Struktur lulus | Tidak ada izin jaringan sama sekali, jadi tidak ada jalur yang butuh internet; uji eksplisit masuk TC-01 |
| 7 | Hasil di `Pictures/Arulume`, foto asli utuh | Lulus | Galeri bertambah hanya setelah tombol Simpan; sumber hanya dibaca |
| 8 | Uji visual A/B: peningkatan terlihat ≥ 80 % foto | Belum | Belum ada lembar penilaian; pemilik sudah melihat sebagian hasil |
| 9 | Tidak ada kebocoran memori pada 20 proses | Lulus | 20/20 selesai, proses sama (tanpa restart), tidak ada entri log crash |
| 10 | `THIRD_PARTY.md` + layar Tentang lengkap | Lulus | Versi ncnn, model, lisensi, maskot, dan logo tercatat |

**Sisa Fase 1** (4 hal, semuanya pengujian dan rilis — bukan fitur baru):

1. Lembar TC-01…TC-12 dijalankan dan dicatat; tambah berkas uji #6 (HEIC) dan #11 (wajah).
2. Uji di Android 13+ (dialog `POST_NOTIFICATIONS`) dan Android 14+ (`foregroundServiceType` ditegakkan).
3. APK rilis bertanda tangan, dipasang di HP pemilik, ukuran dicek ≤ 60 MB.
4. Uji rotasi layar saat proses, audit aksesibilitas dasar, dan penilaian visual A/B foto uji.

---

## Fase 2 — Video

**Status:** Belum dimulai

Rencana besar: MediaCodec (decode → enhance per frame → encode → mux), audio passthrough, foreground service + wake lock, UI video dengan estimasi waktu dan jeda/batal, simpan ke `Movies/Arulume`.
