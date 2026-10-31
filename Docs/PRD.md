# PRD: Arulume

Aplikasi Android pribadi untuk meningkatkan kualitas foto dan video, berjalan **100% offline di HP**, dibangun dengan React (Next.js) + Capacitor + plugin native, dipasang lewat APK tanpa Play Store.

| Item | Isi |
|---|---|
| Versi dokumen | 1.0 (Draft) |
| Tanggal | 31 Oktober 2026 |
| Pemilik produk | Pengguna sendiri (developer tunggal) |
| Platform | Android (arm64) |
| Distribusi | Sideload APK, penggunaan pribadi |
| Status | Siap dieksekusi mulai Fase 0 |

> **Catatan penting tentang dokumen ini.** Spesifikasi HP target belum diketahui. Semua angka performa di bagian 13 adalah **target awal** yang wajib dikalibrasi ulang setelah benchmark Fase 0 (bagian 20). Item bertanda **[VERIFIKASI]** adalah asumsi teknis yang harus dibuktikan dulu di Fase 0 sebelum dijadikan keputusan final.

---

## Daftar Isi

1. Ringkasan eksekutif
2. Latar belakang dan masalah
3. Tujuan, non-tujuan, dan indikator keberhasilan
4. Pengguna dan use case
5. Ruang lingkup dan fase rilis
6. Asumsi, batasan, dan dependensi
7. Arsitektur
8. Persyaratan fungsional
9. Alur pengguna
10. Kontrak plugin Capacitor (JS ke Kotlin)
11. Pipeline pemrosesan native
12. Model AI dan lisensi
13. Persyaratan non-fungsional
14. Data dan penyimpanan
15. Izin (permission) dan manifest
16. Desain UI/UX
17. Penanganan error dan edge case
18. Rencana pengujian
19. Build, penandatanganan, instalasi, dan update
20. Milestone dan urutan kerja
21. Risiko dan mitigasi
22. Definition of Done per fase
23. Pertanyaan terbuka
24. Glosarium
25. Lampiran

---

## 1. Ringkasan eksekutif

Arulume adalah aplikasi Android untuk memperbesar dan mempertajam foto, dan pada fase lanjutan juga video, memakai model AI open source (Real-ESRGAN) yang dijalankan langsung di HP lewat ncnn dengan akselerasi GPU Vulkan.

Prinsip utama:

- **Offline total.** Model ikut terpasang di dalam APK. Tidak ada server, akun, unduhan model, analitik, atau koneksi internet. Izin `INTERNET` dihapus dari manifest.
- **Privat.** Foto dan video tidak pernah keluar dari HP.
- **Sederhana.** UI dibangun dengan React supaya cepat diiterasi. Bagian berat (inferensi AI, decode/encode video) berjalan di native Android, bukan di JavaScript.
- **Bertahap.** Foto dibuktikan dulu (Fase 1), video menyusul (Fase 2), fitur wajah dan lainnya terakhir (Fase 3).

Hasil akhir yang harus tercapai: pengguna memilih foto dari galeri, menekan satu tombol, lalu mendapat foto hasil yang lebih tajam dan lebih besar tersimpan di galeri, tanpa internet, tanpa crash, dalam waktu yang wajar.

## 2. Latar belakang dan masalah

### 2.1 Latar belakang

Aplikasi seperti Wink dan Remini populer untuk memperbaiki kualitas foto dan video. Keduanya proprietary, sebagian fiturnya berbayar, dan fitur beratnya diproses di server cloud, artinya media pengguna diunggah ke pihak ketiga. Padahal teknik dasarnya (super-resolution dan face restoration) tersedia sebagai proyek open source.

### 2.2 Masalah yang diselesaikan

| # | Masalah | Dampak |
|---|---|---|
| P1 | Aplikasi sejenis mengunggah media ke server | Risiko privasi untuk foto/video pribadi |
| P2 | Fitur bagus dibatasi langganan atau watermark | Biaya berulang, hasil tidak bebas dipakai |
| P3 | Butuh internet | Tidak bisa dipakai saat offline atau sinyal buruk |
| P4 | Tidak bisa dikustomisasi | Tidak bisa memilih model, skala, atau format sesuai kebutuhan |

### 2.3 Peluang

Model compact seperti Real-ESRGAN versi `compact` berukuran hanya beberapa MB dan cukup ringan untuk berjalan di GPU HP kelas menengah lewat ncnn-Vulkan. Capacitor memungkinkan UI web dibungkus jadi APK dengan akses ke kode native.

## 3. Tujuan, non-tujuan, dan indikator keberhasilan

### 3.1 Tujuan

| ID | Tujuan | Prioritas |
|---|---|---|
| G1 | Meningkatkan kualitas dan resolusi foto langsung di HP tanpa internet | P0 |
| G2 | Memastikan aplikasi tidak pernah mengirim data ke luar perangkat | P0 |
| G3 | Menyimpan hasil ke galeri dengan satu langkah | P0 |
| G4 | Stabil: tidak crash pada input wajar, gagal dengan pesan yang jelas pada input berlebihan | P0 |
| G5 | Memproses video pendek secara offline (Fase 2) | P1 |
| G6 | Mendukung restorasi wajah dan fitur tambahan (Fase 3) | P2 |
| G7 | Dapat dibangun ulang dan diperbarui sendiri oleh pemilik tanpa layanan eksternal | P0 |

### 3.2 Non-tujuan (sengaja tidak dikerjakan)

- Publikasi ke Play Store atau toko aplikasi lain.
- Akun pengguna, sinkronisasi cloud, berbagi sosial, atau monetisasi.
- Dukungan iOS, tablet khusus, atau Android di bawah API 24.
- Fitur editing manual (crop, filter kreatif, retouch kulit ala Wink) pada Fase 1 sampai 2.
- Menyamai kualitas pemrosesan server GPU kelas data center.
- Pemrosesan real-time atau live camera.
- Penghapus objek dan watermark (butuh model inpainting, di luar ruang lingkup).

### 3.3 Indikator keberhasilan (terukur)

| ID | Indikator | Target |
|---|---|---|
| M1 | Aplikasi berfungsi penuh dengan mode pesawat aktif | 100% fitur Fase 1 berjalan |
| M2 | Izin `INTERNET` tidak ada di manifest final | Terverifikasi lewat `aapt dump permissions` |
| M3 | Tingkat keberhasilan pemrosesan foto pada set uji standar (bagian 18.3) | ≥ 98% tanpa crash |
| M4 | Waktu proses foto referensi (1000×1000 px, skala 4x, GPU) | Target awal ≤ 20 detik, dikalibrasi di Fase 0 |
| M5 | Peningkatan kualitas yang terlihat | Lolos uji visual A/B pemilik pada ≥ 80% foto uji |
| M6 | Ukuran APK Fase 1 | ≤ 60 MB |
| M7 | Foto hasil tersimpan di galeri dan terbuka normal di aplikasi galeri | 100% pada set uji |

## 4. Pengguna dan use case

### 4.1 Persona

**Pemilik (satu-satunya pengguna).** Developer yang ingin memperbaiki foto dan video pribadi di HP sendiri, tidak mau mengunggah ke layanan pihak ketiga, nyaman memasang APK manual.

### 4.2 Use case utama

| ID | Use case | Fase |
|---|---|---|
| UC1 | Mempertajam dan memperbesar satu foto lama beresolusi rendah | 1 |
| UC2 | Memperbaiki foto hasil kompresi (misalnya dari chat) | 1 |
| UC3 | Memproses beberapa foto sekaligus (batch) | 1 (P1) |
| UC4 | Memperbaiki video pendek beresolusi rendah | 2 |
| UC5 | Memperbaiki wajah buram pada foto | 3 |
| UC6 | Membandingkan sebelum dan sesudah sebelum menyimpan | 1 |

## 5. Ruang lingkup dan fase rilis

| Fase | Nama | Isi | Hasil yang harus ada |
|---|---|---|---|
| **0** | Spike teknis | Membuktikan ncnn-Vulkan + Real-ESRGAN jalan di HP lewat plugin Capacitor; mengukur performa; memverifikasi asumsi [VERIFIKASI] | Laporan benchmark + APK prototipe yang memproses 1 foto |
| **1** | MVP Foto | Seluruh alur foto (FR-01 sampai FR-12) | APK pribadi yang stabil untuk foto |
| **2** | Video | Alur video (FR-20 sampai FR-27) | Video pendek dapat dienhance dan tersimpan |
| **3** | Lanjutan | Restorasi wajah, interpolasi frame, batch lanjut | Sesuai prioritas setelah Fase 2 |

Aturan perpindahan fase: fase berikutnya hanya dimulai jika Definition of Done fase sebelumnya (bagian 22) terpenuhi.

## 6. Asumsi, batasan, dan dependensi

### 6.1 Asumsi

| ID | Asumsi | Status |
|---|---|---|
| A1 | HP target berprosesor arm64 dengan Android 7.0 (API 24) atau lebih baru | **Terkonfirmasi** — Redmi Note 8, `arm64-v8a`, Android 11 (API 30) |
| A2 | HP mendukung Vulkan 1.0+; jika tidak, aplikasi memakai CPU yang jauh lebih lambat | **Terkonfirmasi** — Vulkan 1.1 + `vulkan.compute` (Adreno 610); stabilitas tetap diuji di Fase 0 |
| A3 | RAM HP ≥ 4 GB (disarankan ≥ 6 GB untuk video) | **Terkonfirmasi di batas bawah** — total ~3,55 GiB (varian 4 GB), sisa bebas ~1,3 GB |
| A4 | Pemilik mengizinkan pemasangan dari sumber tidak dikenal | Terpenuhi |
| A5 | Pemilik memiliki PC dengan Android Studio untuk build | Perlu dikonfirmasi |
| A6 | Model compact cukup bagus untuk kebutuhan pribadi | Diuji di Fase 0 |
| A7 | Aplikasi tetap memuat UI normal tanpa izin `INTERNET`, karena Capacitor menyajikan aset lokal di dalam proses | **Terbukti** — APK 0.0.1 jalan di Redmi Note 8 tanpa izin `INTERNET` (logcat + `aapt2`) |
| A8 | Model `realesr-general-x4v3` (atau yang setara) tersedia atau dapat dikonversi ke format ncnn | **Terbukti** — dipakai `realesr-animevideov3` x2/x4 resmi (BSD-3); lihat `benchmark-fase0.md` §5 |

### 6.2 Batasan teknis

- Capacitor hanya memuat file statis. Next.js **wajib** memakai static export (`output: 'export'`). Tidak boleh ada API route, SSR, atau middleware.
- WebView tidak cocok untuk inferensi berat. Semua pemrosesan AI berjalan di native (C++/Kotlin).
- Isi APK read-only. Hasil tidak boleh ditulis ke folder `assets`.
- Memori HP terbatas. Pemrosesan wajib memakai tiling.
- FFmpegKit sudah dihentikan pengembangannya, jadi video memakai MediaCodec/MediaMuxer bawaan Android.
- Kualitas hasil dibatasi kemampuan model compact; bukan setara layanan cloud.

### 6.3 Dependensi

| Dependensi | Fungsi | Lisensi (verifikasi ulang saat dipakai) |
|---|---|---|
| Next.js + React | UI | MIT |
| Capacitor | Jembatan web ke native | MIT |
| ncnn (Tencent) | Engine inferensi + Vulkan | BSD-3-Clause |
| Real-ESRGAN (model + referensi ncnn-vulkan) | Super-resolution | BSD-3-Clause |
| Android NDK + CMake | Kompilasi C++ | Bawaan SDK |
| Android Studio | Build APK | Gratis |

## 7. Arsitektur

### 7.1 Diagram lapisan

```
┌──────────────────────────────────────────────────────────┐
│  UI Layer (WebView)                                      │
│  Next.js (static export) + React + TypeScript            │
│  Layar: Beranda, Editor, Hasil, Riwayat, Pengaturan      │
└───────────────▲──────────────────────────────────────────┘
                │ Capacitor bridge (registerPlugin('Enhancer'))
                │ panggilan JS→Kotlin, event Kotlin→JS
┌───────────────┴──────────────────────────────────────────┐
│  Plugin Layer (Kotlin)  EnhancerPlugin                   │
│  - picker media (Photo Picker / SAF)                     │
│  - manajemen job (antrian, cancel, progress)             │
│  - decode/encode gambar, EXIF                            │
│  - simpan ke MediaStore / penyimpanan privat             │
│  - deteksi kemampuan perangkat                           │
│  - (Fase 2) MediaCodec/MediaMuxer, foreground service    │
└───────────────▲──────────────────────────────────────────┘
                │ JNI
┌───────────────┴──────────────────────────────────────────┐
│  Native Layer (C++)                                      │
│  ncnn + Vulkan  →  model Real-ESRGAN (.param/.bin)       │
│  tiling, padding, blending, fallback CPU                 │
└───────────────▲──────────────────────────────────────────┘
                │
┌───────────────┴──────────────────────────────────────────┐
│  Aset & Penyimpanan                                      │
│  assets/models/*.param|*.bin   (di dalam APK, read-only) │
│  cache app (preview, temp)     filesDir (riwayat, config)│
│  MediaStore: Pictures/Arulume, Movies/Arulume          │
└──────────────────────────────────────────────────────────┘
```

### 7.2 Tech stack

| Lapisan | Pilihan | Alasan |
|---|---|---|
| UI | Next.js (App Router) + TypeScript, static export | Sesuai preferensi pemilik, ekosistem React |
| Styling | Tailwind CSS (disarankan) | Cepat untuk UI mobile |
| State | Zustand atau React state biasa | Ringan, tanpa boilerplate |
| Jembatan | Capacitor (versi stabil terbaru saat proyek dimulai) | Standar untuk web → APK |
| Plugin | Plugin lokal di dalam proyek Android (`EnhancerPlugin.kt`), bukan paket npm | Paling sederhana untuk pemakaian pribadi; bisa diekstrak jadi paket nanti |
| Native | C++17 + ncnn prebuilt Android Vulkan | Performa terbaik di HP |
| ABI | `arm64-v8a` saja | Memperkecil APK; HP modern semuanya arm64 |
| minSdk | 24 | Vulkan resmi didukung mulai Android 7.0 (ikuti juga minimum Capacitor yang dipakai) |
| Simpanan setting/riwayat | `@capacitor/preferences` (setting) + file JSON di `filesDir` (riwayat) | Tidak butuh database untuk skala ini |

### 7.3 Keputusan arsitektur utama

| ID | Keputusan | Alternatif yang ditolak | Alasan |
|---|---|---|---|
| AD1 | Inferensi di native (ncnn), bukan onnxruntime-web | Inferensi di WebView | WebView lambat, batas memori ketat, WebGPU Android belum merata |
| AD2 | Model dibundel di `assets/` | Unduh saat pertama dijalankan | Memenuhi syarat offline sejak instal, tanpa izin internet |
| AD3 | Plugin lokal Kotlin | Paket npm terpisah | Mengurangi kompleksitas build |
| AD4 | Pemilih media memakai Photo Picker/SAF | Izin `READ_MEDIA_IMAGES` | Tidak butuh izin baca penyimpanan luas, lebih privat |
| AD5 | Hasil disimpan lewat MediaStore | Menulis path langsung | Kompatibel Android 10+ (scoped storage) |
| AD6 | Video diproses streaming per frame | Ekstrak semua frame ke disk | Hemat penyimpanan dan memori |
| AD7 | Job bersifat asinkron: mulai → event progress → selesai | Satu promise panjang | UI tetap responsif, mendukung cancel |

## 8. Persyaratan fungsional

Prioritas: **P0** wajib untuk fase tersebut, **P1** sebaiknya ada, **P2** opsional.

### 8.1 Fase 1: Foto

#### FR-01 Pemilihan foto (P0)
- Pengguna menekan "Pilih Foto" dan membuka pemilih sistem (Android Photo Picker, fallback `ACTION_OPEN_DOCUMENT`).
- Format input: JPEG, PNG, WebP; HEIC/HEIF pada Android 9 (API 28) ke atas.
- Aplikasi membaca metadata: nama, ukuran file, lebar, tinggi, orientasi EXIF.

**Kriteria penerimaan:**
- Memilih foto menampilkan thumbnail, resolusi, dan ukuran file di layar Editor.
- Membatalkan pemilih tidak menimbulkan error dan kembali ke layar sebelumnya.
- Format tidak didukung menampilkan pesan jelas (`E_UNSUPPORTED_FORMAT`).
- Tidak ada izin baca penyimpanan yang diminta untuk fitur ini.

#### FR-02 Pengaturan proses (P0)
Pengguna dapat memilih sebelum memproses:

| Opsi | Nilai | Default |
|---|---|---|
| Model | Daftar dari `listModels()` | Model umum terbaik hasil Fase 0 |
| Skala output | 2x, 3x (jika model mendukung), 4x | 2x |
| Format output | JPEG, PNG, WebP | JPEG |
| Kualitas (JPEG/WebP) | 70 sampai 100 | 95 |
| Gunakan GPU | Aktif/nonaktif | Aktif jika Vulkan tersedia |
| Ukuran tile | Otomatis / 64 / 128 / 200 / 256 | Otomatis |
| Pertahankan EXIF | Ya/tidak | Ya |
| Lokasi simpan | Galeri / Privat | Galeri |

Jika model hanya mendukung skala asli 4x tetapi pengguna memilih 2x, hasil 4x diperkecil ke 2x dengan resampling berkualitas (Lanczos/bicubic area).

**Kriteria penerimaan:**
- Opsi yang tidak valid untuk model terpilih dinonaktifkan di UI.
- Pengaturan terakhir diingat antar sesi.
- Pengguna dapat mengembalikan semua ke default.

#### FR-03 Pemrosesan foto (P0)
Detail pipeline ada di bagian 11.1. Persyaratan:
- Orientasi EXIF diterapkan sebelum inferensi sehingga hasil tidak miring.
- Gambar diproses per tile dengan padding tumpang tindih agar tidak ada garis sambungan.
- Kanal alpha (PNG transparan): RGB ditingkatkan oleh model, alpha diperbesar dengan interpolasi bicubic, lalu digabung kembali.
- Jika sisi panjang output melebihi batas (default 4096 px), aplikasi menolak sebelum memproses dengan opsi "kecilkan input otomatis" atau "turunkan skala".
- Jika GPU gagal (Vulkan tidak ada, kehabisan memori GPU), aplikasi mengulang otomatis dengan tile lebih kecil, lalu turun ke CPU, dengan pemberitahuan ke pengguna.

**Kriteria penerimaan:**
- Foto uji 1000×1000 px diproses ke 2000×2000 px (skala 2x) tanpa artefak garis tile yang terlihat.
- Foto dengan EXIF rotasi 90° keluar dengan orientasi benar.
- PNG transparan tetap transparan pada hasil.
- Tidak ada crash pada seluruh set uji bagian 18.3.

#### FR-04 Progres dan pembatalan (P0)
- UI menampilkan tahap (`decoding`, `enhancing`, `encoding`, `saving`), persen, dan perkiraan sisa waktu.
- Tombol Batal menghentikan proses di antara tile (maksimum jeda: durasi satu tile).
- Setelah batal, file sementara dibersihkan dan tidak ada file hasil parsial di galeri.

**Kriteria penerimaan:**
- Progres bergerak monoton dan mencapai 100% hanya saat hasil benar-benar tersimpan.
- Batal menghasilkan status `cancelled`, UI kembali ke Editor, tidak ada file sisa.

#### FR-05 Pratinjau sebelum/sesudah (P0)
- Layar Hasil menampilkan slider perbandingan (geser kiri-kanan) antara asli dan hasil.
- Pengguna dapat zoom (cubit) dan geser, dengan zoom tersinkron pada kedua gambar.
- Pratinjau memakai versi turunan (sisi panjang maks ±1600 px) yang dibuat plugin di cache, bukan file penuh, agar WebView tidak kehabisan memori.

**Kriteria penerimaan:**
- Slider berjalan mulus (tanpa patah-patah jelas) pada HP target.
- Membuka foto hasil 4096 px tidak membuat WebView crash.

#### FR-06 Penyimpanan hasil (P0) — direvisi 31 Oktober 2026
- Hasil **tidak** otomatis masuk galeri. Selesai diproses, hasil ditulis ke cache privat (`cacheDir/hasil/`) dan layar Hasil menampilkan perbandingan sebelum/sesudah.
- User menekan **"Simpan ke Galeri"** → barulah disalin ke `Pictures/Arulume/` lewat MediaStore. Tanpa tekan, tidak ada berkas baru di galeri.
- Nama file: `Arulume_{yyyyMMdd-HHmmss}.jpg`.
- Hasil di cache dipangkas otomatis (sisakan 3 terbaru) supaya tidak menumpuk.
- Tidak pernah menimpa file asli.
- Pada Android 9 ke bawah, izin `WRITE_EXTERNAL_STORAGE` diminta saat menekan Simpan (bukan saat proses), dengan `maxSdkVersion=28`.

**Kriteria penerimaan:**
- Hasil muncul di aplikasi Galeri dalam hitungan detik setelah selesai.
- Foto asli tidak berubah (hash sama sebelum dan sesudah).
- Gagal menyimpan (penyimpanan penuh) menampilkan `E_SAVE_FAILED` dan hasil tetap tersedia sementara di cache untuk dicoba simpan ulang.

#### FR-07 Aksi setelah selesai (P0)
- Tombol: Simpan ulang/Simpan sebagai, Bagikan (share sheet sistem, bukan jaringan aplikasi), Buka di Galeri, Proses lagi dengan pengaturan lain, Selesai.

#### FR-08 Riwayat (P1)
- Menyimpan daftar pekerjaan (waktu, nama, model, skala, durasi, status, lokasi hasil) sebagai JSON di `filesDir`.
- Pengguna dapat membuka ulang hasil, menghapus entri, atau menghapus semua riwayat.
- Menghapus entri tidak menghapus file di galeri kecuali pengguna memilih demikian.

#### FR-09 Pengaturan aplikasi (P1)
- Default model/skala/format, tema (terang/gelap/ikuti sistem), batas resolusi output, hapus cache, ekspor log lokal, info versi, info model terpasang beserta lisensinya.

#### FR-10 Deteksi kemampuan perangkat (P0)
- Saat aplikasi dibuka, plugin mengembalikan: ABI, level API, RAM total, jumlah core CPU, ketersediaan Vulkan dan nama GPU, daftar model terpasang.
- UI menampilkan peringatan jika Vulkan tidak ada ("pemrosesan memakai CPU, akan jauh lebih lambat").

#### FR-11 Batch foto (P1, selesai)
- Memilih beberapa foto, diproses berurutan (satu per satu, bukan paralel) dengan pengaturan yang sama.
- Satu kegagalan tidak menghentikan batch; ringkasan akhir menampilkan sukses/gagal per file.
- Batal menghentikan seluruh antrian.

#### FR-12 Notifikasi progres & penyelesaian (P1, selesai)
- Selama job berjalan tampil notifikasi **ongoing** dengan bar progres (`setProgress`) di kanal `arulume.proses`, diperbarui tiap ±3% atau tiap satu bagian tile.
- Saat selesai/gagal/batal, notifikasi progres ditutup dan diganti satu notifikasi di kanal `arulume.hasil` (ketuk → buka aplikasi).
- Izin `POST_NOTIFICATIONS` (Android 13+) diminta sekali saat user menekan Proses; ditolak pun proses tetap berjalan.
- **Bukan** Firebase/FCM: notifikasi dibuat lokal dengan `NotificationCompat` (lihat `AGENTS.md` aturan 1).
- Pekerjaan berat berjalan di **foreground service** (`EnhancerService`, tipe `dataSync`) + `PARTIAL_WAKE_LOCK`, jadi tetap berjalan saat aplikasi ditinggal, layar mati, atau proses Activity dimatikan sistem (FR-13).

#### FR-13 Proses tetap berjalan di latar (P0, selesai)
- Menekan Home, mengunci layar, atau berpindah aplikasi **tidak** menghentikan job; job selesai dengan hasil utuh.
- Job berjalan di `EnhancerService` (foreground, `dataSync`) sehingga prioritas proses tetap tinggi dan tidak dibekukan Doze/MIUI.
- Saat layar kembali dibuka, layar Editor membaca `getJobStatus()`: bila job masih hidup, bar progres disambung; bila sudah selesai, user diarahkan ke hasil.
- Hasil tetap tercatat di Riwayat walau WebView sudah tidak ada.
- Batas: satu job aktif; `PARTIAL_WAKE_LOCK` dilepas otomatis saat job berakhir atau setelah 30 menit.

### 8.2 Fase 2: Video

#### FR-20 Pemilihan video (P0)
- Format input: MP4/MOV (H.264, HEVC) dan format lain yang didukung decoder bawaan perangkat.
- Metadata: durasi, resolusi, fps, codec, rotasi, ada/tidaknya audio.

#### FR-21 Pengaturan video (P0)
| Opsi | Nilai | Default |
|---|---|---|
| Model | Model compact (diuji flicker) | Hasil evaluasi Fase 2 |
| Skala | 2x (3x/4x opsional) | 2x |
| Codec output | H.264 / HEVC (sesuai dukungan encoder perangkat) | H.264 |
| Bitrate | Otomatis berdasar resolusi, atau manual | Otomatis |
| Batas durasi | Default 60 detik; di atas itu muncul peringatan estimasi waktu | 60 detik |
| Batas resolusi input | Default sisi panjang maks 1280 px | 1280 px |

#### FR-22 Pipeline video (P0)
Detail di 11.2. Persyaratan:
- Pemrosesan **streaming**: decode satu frame → enhance → encode, tanpa menyimpan seluruh frame di memori atau disk.
- Frame rate dan rasio aspek asli dipertahankan; rotasi diterapkan dengan benar.
- Audio disalin tanpa re-encode (passthrough) bila format kompatibel; jika tidak, di-transcode ke AAC.

#### FR-23 Layanan latar depan (P0)
- Pemrosesan video berjalan di **foreground service** dengan notifikasi progres, agar tidak dimatikan sistem saat layar mati atau aplikasi di latar belakang. Tipe foreground service mengikuti aturan versi Android terbaru **[VERIFIKASI]**.
- Wake lock parsial dipegang selama proses dan dilepas saat selesai/gagal/batal.

#### FR-24 Manajemen termal dan baterai (P0)
- Pada Android 10+ (API 29+), pantau `PowerManager` thermal status. Saat mencapai level parah (`THERMAL_STATUS_SEVERE` atau lebih), proses dijeda otomatis sampai suhu turun atau dihentikan dengan `E_THERMAL_ABORT` dan hasil parsial dibuang.
- Peringatan sebelum mulai jika baterai < 30% dan tidak sedang mengisi daya.

#### FR-25 Progres, jeda, batal (P0)
- Progres per frame (frame ke-N dari total), estimasi sisa waktu, tombol Jeda/Lanjut dan Batal.
- Batal menghapus file sementara dan tidak meninggalkan video parsial.

#### FR-26 Penyimpanan hasil video (P0)
- Simpan ke `Movies/Arulume/` lewat MediaStore, dengan pola penamaan sama seperti foto.
- Pastikan ruang kosong cukup sebelum mulai (perkiraan ukuran output + margin).

#### FR-27 Pratinjau video (P1)
- Pemutar sebelum/sesudah berdampingan atau bergantian; boleh memakai pemutar bawaan sistem.

### 8.3 Fase 3: Lanjutan (P2, dirinci saat fase dimulai)

| ID | Fitur | Catatan |
|---|---|---|
| FR-30 | Restorasi wajah (GFPGAN atau setara) pada foto | Perlu deteksi wajah (mis. RetinaFace via ncnn atau ML Kit face detection); model besar, jangan dibundel, salin manual ke penyimpanan HP |
| FR-31 | Interpolasi frame (RIFE ncnn) | Menaikkan fps video |
| FR-32 | Pilihan model tambahan (anime, dll.) | Memperluas `listModels()` |
| FR-33 | Resume pemrosesan video setelah terputus | Perlu segmentasi per potongan |

## 9. Alur pengguna

### 9.1 Pertama kali dibuka
1. Aplikasi terbuka, memuat UI dari aset lokal.
2. `getCapabilities()` dipanggil. Hasilnya disimpan di state.
3. Jika Vulkan tidak ada → banner peringatan CPU.
4. Jika model wajib tidak ditemukan di assets → layar error fatal `E_MODEL_MISSING` dengan instruksi pasang ulang APK.
5. Layar Beranda tampil.

### 9.2 Alur utama: enhance satu foto
```
Beranda
  └─ [Pilih Foto]
       └─ Photo Picker sistem
            ├─ batal → kembali ke Beranda
            └─ pilih → Editor
                 ├─ tampil thumbnail + info (resolusi, ukuran)
                 ├─ atur model / skala / format (opsional)
                 └─ [Proses]
                      ├─ validasi ukuran output → jika terlalu besar: dialog opsi
                      ├─ startEnhanceImage() → jobId
                      ├─ layar Progres (tahap, %, ETA) + [Batal]
                      │     ├─ batal → bersihkan temp → kembali ke Editor
                      │     └─ error → layar error dengan kode + [Coba lagi]/[Ubah setelan]
                      └─ selesai → layar Hasil
                           ├─ slider sebelum/sesudah, zoom
                           ├─ hasil sudah tersimpan otomatis (jika "Simpan otomatis" aktif)
                           └─ [Bagikan] [Buka di Galeri] [Proses lagi] [Selesai]
```

### 9.3 Alur fallback GPU
1. Inferensi GPU gagal (Vulkan error / out of memory).
2. Plugin mengulang dengan tile lebih kecil (maks 2 kali, mis. 256 → 128 → 64).
3. Masih gagal → ulang di CPU, UI menampilkan "Beralih ke CPU, lebih lambat".
4. CPU gagal → `E_OUT_OF_MEMORY` dengan saran: turunkan skala atau perkecil input.

### 9.4 Alur video (Fase 2)
1. Pilih video → Editor video (info durasi, resolusi, fps, estimasi waktu proses).
2. Validasi: durasi, resolusi, ruang penyimpanan, baterai.
3. Mulai → foreground service aktif + notifikasi progres.
4. Loop streaming: decode frame → enhance → encode → tulis ke muxer.
5. Selesai → mux audio → simpan ke MediaStore → layar Hasil.
6. Jika panas parah → jeda otomatis atau abort dengan pesan.

## 10. Kontrak plugin Capacitor (JS ke Kotlin)

Plugin bernama `Enhancer`. Sisi Kotlin: kelas yang meng-extend `Plugin` dengan `@CapacitorPlugin(name = "Enhancer")` dan metode `@PluginMethod`, didaftarkan di `MainActivity` sebelum `super.onCreate`. Sisi JS: `registerPlugin<EnhancerPlugin>('Enhancer')`.

### 10.1 Antarmuka TypeScript

```ts
// src/plugins/enhancer.ts
import { registerPlugin, PluginListenerHandle } from '@capacitor/core';

export type ModelId = string; // contoh: 'realesr-general-x4v3'

export interface DeviceCapabilities {
  abi: string;                 // 'arm64-v8a'
  androidApi: number;          // contoh: 34
  ramTotalMb: number;
  cpuCores: number;
  vulkanAvailable: boolean;
  gpuName?: string;
  models: ModelInfo[];
}

export interface ModelInfo {
  id: ModelId;
  label: string;
  nativeScale: number;         // skala asli model (mis. 4)
  supportedScales: number[];   // skala yang boleh dipilih
  kind: 'image' | 'video';
  license: string;
  sizeBytes: number;
}

export interface PickedMedia {
  uri: string;                 // content:// URI dari picker
  name: string;
  mime: string;
  sizeBytes: number;
  width: number;               // setelah koreksi orientasi
  height: number;
  durationMs?: number;         // video saja
  fps?: number;                // video saja
  previewPath?: string;        // file thumbnail di cache
}

export interface EnhanceImageOptions {
  uri: string;
  model: ModelId;
  scale: number;               // 2 | 3 | 4
  tileSize: number;            // 0 = otomatis
  useGpu: boolean;
  keepExif: boolean;
  output: {
    format: 'jpeg' | 'png' | 'webp';
    quality: number;           // 70..100
    target: 'gallery' | 'private';
  };
  maxOutputLongSide: number;   // default 4096
  autoDownscaleInput: boolean;
}

export type JobStage = 'decoding' | 'enhancing' | 'encoding' | 'saving';

export interface ProgressEvent {
  jobId: string;
  stage: JobStage;
  percent: number;             // 0..100
  tileIndex?: number;
  tileTotal?: number;
  etaSec?: number;
}

export interface CompletedEvent {
  jobId: string;
  outputUri: string;           // content:// atau file path privat
  outputWidth: number;
  outputHeight: number;
  durationMs: number;
  usedGpu: boolean;
  tileSizeUsed: number;
  beforePreviewPath: string;   // file di cache untuk WebView
  afterPreviewPath: string;
}

export type ErrorCode =
  | 'E_PICK_CANCELLED' | 'E_UNSUPPORTED_FORMAT' | 'E_DECODE_FAILED'
  | 'E_MODEL_MISSING'  | 'E_VULKAN_UNAVAILABLE' | 'E_OUT_OF_MEMORY'
  | 'E_INPUT_TOO_LARGE'| 'E_SAVE_FAILED' | 'E_CANCELLED'
  | 'E_THERMAL_ABORT'  | 'E_STORAGE_FULL' | 'E_UNKNOWN';

export interface FailedEvent {
  jobId: string;
  code: ErrorCode;
  message: string;
  recoverable: boolean;
}

export interface EnhancerPlugin {
  getCapabilities(): Promise<DeviceCapabilities>;
  pickMedia(opts: { type: 'image' | 'video'; multiple?: boolean }): Promise<{ items: PickedMedia[] }>;
  startEnhanceImage(opts: EnhanceImageOptions): Promise<{ jobId: string }>;
  cancelJob(opts: { jobId: string }): Promise<void>;
  /** Keadaan job terakhir yang diketahui native (dipakai saat layar dibuka ulang). */
  getJobStatus(): Promise<JobStatus>;
  /** Meminta izin POST_NOTIFICATIONS (Android 13+); notifikasi lokal, bukan FCM. */
  mintaIzinNotifikasi(): Promise<{ granted: boolean }>;
  clearCache(): Promise<{ freedBytes: number }>;
  openInGallery(opts: { uri: string }): Promise<void>;
  shareResult(opts: { uri: string; mime: string }): Promise<void>;
  addListener(ev: 'progress',  fn: (e: ProgressEvent)  => void): Promise<PluginListenerHandle>;
  addListener(ev: 'completed', fn: (e: CompletedEvent) => void): Promise<PluginListenerHandle>;
  addListener(ev: 'failed',    fn: (e: FailedEvent)    => void): Promise<PluginListenerHandle>;
}

export const Enhancer = registerPlugin<EnhancerPlugin>('Enhancer');
```

`JobStatus` = `{ jobId?: string; stage?: 'decoding' | 'enhancing' | 'encoding' | 'saving' | 'done' | 'failed'; percent?: number; aktif: boolean }`.
Nilai `stage` pada `ProgressEvent` dan `JobStatus` memakai nama yang sama.

Fase 2 menambahkan `startEnhanceVideo`, `pauseJob`, `resumeJob`, dengan event progres yang membawa `frameIndex` dan `frameTotal`.

### 10.2 Aturan kontrak
- Metode yang berjalan lama (`startEnhance*`) **tidak** memblokir; mengembalikan `jobId` segera.
- Semua hasil dan kegagalan dikirim lewat event. Event `completed` dan `failed` saling eksklusif untuk satu `jobId`.
- Hanya satu job aktif pada satu waktu (antrian di sisi Kotlin untuk batch).
- Frekuensi event progres dibatasi (maks ±10 per detik) agar jembatan tidak terbebani.
- File pratinjau ditampilkan di WebView lewat `Capacitor.convertFileSrc(path)`.
- Error selalu membawa `code` dari daftar di atas; tidak ada error tanpa kode.

## 11. Pipeline pemrosesan native

### 11.1 Foto

1. **Decode.** `ImageDecoder` (API 28+) atau `BitmapFactory` (di bawahnya); batasi sampling jika dimensi input melebihi batas aman.
2. **Koreksi orientasi.** Terapkan rotasi/flip dari EXIF.
3. **Validasi.** Hitung dimensi output = input × skala; jika sisi panjang > batas, tawarkan perkecil input atau turunkan skala. Perkiraan memori dihitung dahulu.
4. **Pisahkan alpha** (jika ada).
5. **Tiling.** Bagi gambar ke tile berukuran `tileSize` dengan padding (±10 px) agar konteks tepi tersedia.
6. **Inferensi per tile.** ncnn (Vulkan atau CPU), model dari `assets/models/` yang disalin sekali ke `cacheDir`/`filesDir` bila ncnn butuh path file (alternatif: muat dari asset manager) **[VERIFIKASI]**.
7. **Rakit.** Buang padding, tempel tile ke buffer output; periksa tidak ada garis sambungan.
8. **Resampling skala.** Jika skala pilihan ≠ skala asli model, ubah ukuran hasil.
9. **Gabung alpha** (jika ada).
10. **Encode.** JPEG/PNG/WebP sesuai opsi, salin EXIF (kecuali orientasi yang sudah diterapkan, ubah jadi normal).
11. **Simpan.** `MediaStore.Images` dengan `RELATIVE_PATH = Pictures/Arulume` (API 29+), flag `IS_PENDING` selama menulis agar tidak terlihat setengah jadi.
12. **Pratinjau.** Buat versi turunan kecil asli dan hasil di cache untuk UI.
13. **Bersihkan** file sementara; kirim event `completed`.

Memori: buffer RGB output ukuran W×H×3 byte. Contoh: 4096×4096 ≈ 48 MB. Batas default 4096 px sisi panjang melindungi dari kehabisan memori.

### 11.2 Video (Fase 2)

1. **Demux.** `MediaExtractor` memilih track video dan audio.
2. **Decode.** `MediaCodec` decoder (hardware bila ada) ke `Surface` atau buffer YUV; konversi ke RGB per frame.
3. **Enhance.** Setiap frame lewat pipeline tile seperti foto (tanpa encode/simpan per frame).
4. **Encode.** `MediaCodec` encoder (H.264/HEVC) menerima frame hasil lewat Surface atau buffer; PTS asli dipertahankan.
5. **Mux.** `MediaMuxer` menulis video; audio disalin dari extractor (passthrough).
6. **Simpan.** `MediaStore.Video` ke `Movies/Arulume`.
7. **Pengendalian.** Foreground service, wake lock, pemantauan termal, cek flag batal/jeda tiap frame.
8. **Kualitas.** Evaluasi flicker antar frame; jika mengganggu, coba model yang dirancang untuk video, kurangi kekuatan restorasi, atau gunakan upscaler biasa tanpa restorasi wajah.

Memori: hanya beberapa frame berada di memori pada satu waktu (decode, enhance, encode), bukan seluruh video.

## 12. Model AI dan lisensi

| Model | Fungsi | Perkiraan ukuran | Fase | Lisensi (verifikasi ulang) |
|---|---|---|---|---|
| Real-ESRGAN compact umum (`realesr-general-x4v3` atau setara) | Foto umum | beberapa MB | 1 | BSD-3-Clause |
| Real-ESRGAN `animevideov3` (compact) | Video / ilustrasi anime | ±2-3 MB | 2 | BSD-3-Clause |
| Real-ESRGAN x4plus (penuh) | Foto kualitas maksimum, lebih berat | puluhan MB | opsional | BSD-3-Clause |
| GFPGAN | Restorasi wajah | ratusan MB | 3 | Apache-2.0 |
| CodeFormer | Restorasi wajah | ratusan MB | 3 | **Non-komersial** (S-Lab), hanya untuk pemakaian pribadi |
| RIFE (ncnn) | Interpolasi frame | puluhan MB | 3 | MIT |

Aturan:
- Hanya model berlisensi yang mengizinkan penggunaan pribadi yang dibundel; lisensi dicatat di layar "Tentang".
- Jika model dalam format PyTorch, konversi ke ncnn (`.param` + `.bin`) menjadi tugas Fase 0 **[VERIFIKASI A8]**.
- Model > 100 MB tidak dibundel di APK; disalin manual ke penyimpanan HP dan dimuat dari sana (Fase 3).
- Checksum SHA-256 setiap file model dicatat di repo dan diverifikasi saat build.

## 13. Persyaratan non-fungsional

### 13.1 Performa (target awal, dikalibrasi di Fase 0)

| ID | Skenario | Target |
|---|---|---|
| NFR-P1 | Cold start sampai UI siap | ≤ 3 detik |
| NFR-P2 | Foto 1000×1000, skala 4x, GPU | ≤ 20 detik |
| NFR-P3 | Foto 1000×1000, skala 4x, CPU | ≤ 120 detik (fallback, bukan jalur utama) |
| NFR-P4 | Respons UI saat memproses | Tidak ada freeze > 100 ms (inferensi tidak di thread UI/WebView) |
| NFR-P5 | Video 720p→1440p, 10 detik, 30 fps (300 frame) | Diukur di Fase 0/2; target awal ≤ 10× durasi video, bukan janji |
| NFR-P6 | Slider pratinjau | ≥ 30 fps efektif |

### 13.2 Memori
- Puncak memori native selama foto tidak boleh membuat proses dibunuh sistem pada HP target (dibuktikan lewat uji set 18.3).
- Tile size otomatis dipilih dari RAM tersedia dan kemampuan GPU.
- Tidak ada kebocoran memori: tiga pemrosesan berturut-turut tidak meningkatkan memori dasar secara signifikan (uji dengan Android Studio Profiler).

### 13.3 Termal dan baterai
- Pemrosesan foto tunggal tidak memicu pembatasan termal pada HP target.
- Video: lihat FR-24 (jeda/abort otomatis).
- Peringatan baterai rendah sebelum video.

### 13.4 Ukuran
- APK Fase 1 ≤ 60 MB (ABI arm64 saja, model compact, tanpa model besar).

### 13.5 Kompatibilitas
- Android 7.0 (API 24) atau lebih baru; arm64-v8a.
- Resolusi layar umum HP 360-440 dp lebar; mendukung rotasi layar tanpa kehilangan state job.

### 13.6 Privasi dan keamanan
- Izin `INTERNET` dihapus; tidak ada SDK analitik, crash reporter jarak jauh, atau iklan.
- Log hanya lokal, tanpa konten gambar, dapat diekspor manual oleh pengguna.
- `android:allowBackup="false"` agar riwayat dan cache tidak ikut ter-backup otomatis ke cloud.
- File sementara disimpan di `cacheDir` privat dan dibersihkan setelah job dan saat aplikasi dibuka.
- Tidak ada `usesCleartextTraffic`; WebView tidak membuka URL eksternal (navigasi di luar aplikasi diblokir).

### 13.7 Keandalan
- Aplikasi dimatikan paksa saat memproses tidak boleh meninggalkan file parsial di galeri (gunakan `IS_PENDING` dan penghapusan saat gagal).
- Saat dibuka ulang setelah crash, cache sisa dibersihkan.

### 13.8 Kemudahan pemeliharaan
- Kode dibagi jelas: `ui/`, `plugins/`, `android/.../EnhancerPlugin.kt`, `android/.../cpp/`.
- Versi ncnn dan model dicatat di `THIRD_PARTY.md`.
- Skrip build satu perintah menghasilkan APK.

### 13.9 Aksesibilitas
- Ukuran target sentuh ≥ 48 dp, kontras teks memadai, label pada tombol ikon, dukungan tema gelap.

## 14. Data dan penyimpanan

| Data | Lokasi | Dihapus kapan |
|---|---|---|
| Model AI | `assets/models/` (di dalam APK) | Hanya saat aplikasi dihapus |
| Salinan model kerja (jika ncnn butuh path file) | `filesDir/models/` | Dibuat ulang bila hilang |
| Setting | `@capacitor/preferences` | Saat data aplikasi dihapus |
| Riwayat | `filesDir/history.json` | Oleh pengguna / data aplikasi dihapus |
| Pratinjau dan file sementara | `cacheDir/enhancer/` | Setelah job, saat start, atau tombol Hapus Cache |
| Hasil (galeri) | MediaStore `Pictures/Arulume`, `Movies/Arulume` | Oleh pengguna lewat Galeri |
| Hasil (privat) | `filesDir/results/` | Oleh pengguna lewat Riwayat |

Struktur entri riwayat:

```json
{
  "id": "job-20261031-085400-ab12",
  "createdAt": "2026-10-31T08:54:00+07:00",
  "type": "image",
  "sourceName": "IMG_0001.jpg",
  "model": "realesr-general-x4v3",
  "scale": 2,
  "outputUri": "content://media/external/images/media/12345",
  "outputWidth": 2000,
  "outputHeight": 2000,
  "durationMs": 14200,
  "usedGpu": true,
  "status": "completed"
}
```

## 15. Izin (permission) dan manifest

| Izin | Diperlukan? | Alasan |
|---|---|---|
| `INTERNET` | **Dihapus** | Menjamin offline dan privasi (verifikasi A7) |
| `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` | Tidak | Memakai Photo Picker/SAF |
| `WRITE_EXTERNAL_STORAGE` | Hanya API ≤ 28 (`maxSdkVersion="28"`) | Simpan ke galeri di Android 9 ke bawah |
| `POST_NOTIFICATIONS` | Dipakai sejak Fase 1 (Android 13+), diminta kontekstual | FR-12, FR-23 |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` | Dipakai sejak Fase 1 (`EnhancerService`) | FR-12, FR-13, FR-23 |
| `WAKE_LOCK` | Dipakai sejak Fase 1 (PARTIAL, dibatasi 30 menit per job) | FR-13, FR-23 |

Pengaturan manifest lain: `android:allowBackup="false"`, `abiFilters "arm64-v8a"`, deklarasi fitur Vulkan sebagai opsional (`android.hardware.vulkan.version` dengan `required="false"`) agar aplikasi tetap terpasang di perangkat tanpa Vulkan dan memakai fallback CPU.

## 16. Desain UI/UX

### 16.1 Prinsip
Satu layar satu tujuan, tombol utama selalu terlihat, tanpa istilah teknis yang membingungkan di mode default (pengaturan lanjutan disembunyikan di bagian "Lanjutan").

### 16.2 Daftar layar

| Layar | Tujuan |
|---|---|
| Beranda | Pilih Foto, Pilih Video (Fase 2), akses Riwayat dan Pengaturan |
| Editor | Lihat thumbnail dan info; atur opsi; tombol Proses |
| Progres | Tahap, persen, ETA, tombol Batal/Jeda |
| Hasil | Slider sebelum/sesudah, aksi simpan/bagikan/buka |
| Riwayat | Daftar pekerjaan lalu |
| Pengaturan | Default, tema, cache, log, tentang dan lisensi |
| Error | Kode, penjelasan singkat, tombol tindakan |
| Onboarding | Sekali di awal: janji "offline & privat" + tombol Mulai/Lewati |

### 16.3 Wireframe teks

```
┌─ Beranda ───────────────┐   ┌─ Editor ────────────────┐
│  Arulume      ⚙  │   │ ←  Atur Peningkatan     │
│                         │   │ ┌─────────────────────┐ │
│  ┌───────────────────┐  │   │ │   [thumbnail foto]  │ │
│  │   Pilih Foto      │  │   │ └─────────────────────┘ │
│  └───────────────────┘  │   │ 1000×1000 · 1,2 MB      │
│  ┌───────────────────┐  │   │ Skala:  (2x) 3x  4x     │
│  │   Pilih Video  ★  │  │   │ Format: JPEG ▾          │
│  └───────────────────┘  │   │ Model:  Umum ▾          │
│  Riwayat terakhir ▸     │   │ ▸ Lanjutan              │
│  [▢][▢][▢]              │   │ ┌─────────────────────┐ │
│                         │   │ │       PROSES        │ │
│  ★ = Fase 2             │   │ └─────────────────────┘ │
└─────────────────────────┘   └─────────────────────────┘

┌─ Progres ───────────────┐   ┌─ Hasil ─────────────────┐
│  Meningkatkan foto…     │   │ ┌─────────┬───────────┐ │
│  ▓▓▓▓▓▓▓▓░░░░  62%      │   │ │ Asli  ◀▶  Hasil    │ │
│  Tile 18 / 29           │   │ └─────────┴───────────┘ │
│  Sisa ±8 detik          │   │ 2000×2000 · GPU · 14 dtk│
│  GPU aktif              │   │ ✓ Tersimpan di Galeri   │
│  ┌───────────────────┐  │   │ [Bagikan] [Buka Galeri] │
│  │      BATAL        │  │   │ [Proses Lagi] [Selesai] │
│  └───────────────────┘  │   └─────────────────────────┘
└─────────────────────────┘
```

### 16.4 Status dan pesan
Setiap layar mendefinisikan state: *idle, loading, error, kosong*. Pesan error berbahasa Indonesia sederhana + kode, contoh: "Foto terlalu besar untuk diproses pada skala 4x. Turunkan skala atau perkecil foto. (E_INPUT_TOO_LARGE)".

## 17. Penanganan error dan edge case

| Situasi | Perilaku yang diharapkan |
|---|---|
| Pengguna membatalkan picker | Kembali tanpa error |
| File rusak/tidak bisa di-decode | `E_DECODE_FAILED`, tawarkan pilih file lain |
| Format tak didukung (mis. GIF animasi, RAW) | `E_UNSUPPORTED_FORMAT` |
| Output melebihi batas | Dialog: perkecil input otomatis / turunkan skala / batal |
| Vulkan tidak ada | Peringatan + otomatis CPU |
| Vulkan error saat proses | Fallback bertahap (tile kecil → CPU), beri tahu pengguna |
| Out of memory | `E_OUT_OF_MEMORY` + saran konkret |
| Penyimpanan penuh | `E_STORAGE_FULL`, hasil tetap di cache untuk dicoba ulang |
| Model hilang dari assets | `E_MODEL_MISSING`, layar fatal, instruksi pasang ulang |
| Aplikasi ke latar belakang saat foto diproses | Proses lanjut selama proses hidup; notifikasi saat selesai bila izin ada |
| Layar diputar saat proses | State job dipertahankan; UI memulihkan progres |
| Aplikasi dimatikan paksa | Tidak ada file parsial di galeri; cache dibersihkan saat buka berikutnya |
| Foto sangat kecil (mis. < 64 px) | Tetap diproses, tile menyesuaikan |
| Foto sangat panjang (panorama) | Validasi sisi panjang; tiling tetap bekerja |
| PNG transparan | Alpha dipertahankan |
| Foto dengan EXIF rotasi | Orientasi benar pada hasil |
| Thermal parah (video) | Jeda/abort sesuai FR-24 |
| Panggilan masuk saat proses | Proses tidak terganggu; aplikasi tidak crash |
| Dua job diminta bersamaan | Job kedua masuk antrian atau ditolak dengan pesan jelas |

## 18. Rencana pengujian

### 18.1 Matriks perangkat
Minimal: HP pemilik (utama). Disarankan tambahan: satu emulator Android API 24 (uji CPU fallback) dan satu perangkat berbeda bila ada.

### 18.2 Jenis pengujian
- **Fungsional:** setiap FR diuji terhadap kriteria penerimaannya.
- **Performa:** benchmark terstandar (18.4).
- **Stabilitas:** 20 pemrosesan berturut-turut tanpa restart; uji rotasi layar dan background/foreground.
- **Offline:** mode pesawat aktif + verifikasi manifest tanpa `INTERNET`.
- **Regresi visual:** bandingkan hasil dengan referensi tersimpan (visual, opsional PSNR/SSIM).
- **Instalasi:** instal bersih, update di atas versi lama (kunci signing sama), uninstall.

### 18.3 Set uji foto standar

| # | Foto uji | Tujuan |
|---|---|---|
| 1 | JPEG 1000×1000 normal | Jalur utama |
| 2 | JPEG 640×480 buram | Peningkatan terlihat |
| 3 | PNG transparan | Alpha |
| 4 | JPEG dengan EXIF rotasi 90° dan 270° | Orientasi |
| 5 | WebP | Format |
| 6 | HEIC (API 28+) | Format |
| 7 | JPEG 12 MP dari kamera | Batas ukuran, auto-downscale |
| 8 | Panorama panjang | Tiling ekstrem |
| 9 | Foto 32×32 | Ukuran minimum |
| 10 | File rusak/terpotong | Penanganan error |
| 11 | Foto wajah | Kualitas wajah (tanpa restorasi khusus) |
| 12 | Foto teks/dokumen | Ketajaman teks |
| 13 | Foto gelap/noise | Perilaku pada noise |

### 18.4 Protokol benchmark (Fase 0)
Catat untuk setiap foto uji dan setiap kombinasi (GPU/CPU × tile 64/128/200/256 × skala 2/4): waktu total, waktu inferensi, puncak memori, suhu sebelum/sesudah, keberhasilan. Hasilnya menentukan tile default, batas input, dan angka target final NFR-P.

### 18.5 Kasus uji fungsional inti

| ID | Langkah | Hasil yang diharapkan |
|---|---|---|
| TC-01 | Buka app dengan mode pesawat | UI muncul, fitur lengkap |
| TC-02 | Pilih foto #1, skala 2x, Proses | Hasil 2000×2000 tersimpan di Galeri |
| TC-03 | Batal di 50% | Tidak ada file di Galeri, kembali ke Editor |
| TC-04 | Foto #3 (PNG transparan) | Alpha utuh |
| TC-05 | Foto #4 | Orientasi benar |
| TC-06 | Foto #7 skala 4x | Dialog batas output, opsi bekerja |
| TC-07 | Matikan GPU di Pengaturan | Proses via CPU, selesai |
| TC-08 | Penyimpanan hampir penuh | `E_STORAGE_FULL`, hasil tetap tersedia di cache |
| TC-09 | Putar layar saat proses | Progres tetap |
| TC-10 | 20 proses berturut-turut | Tanpa crash, memori stabil |
| TC-11 | `aapt dump permissions` pada APK final | Tidak ada `INTERNET` |
| TC-12 | Bagikan hasil | Share sheet sistem terbuka dengan file benar |

## 19. Build, penandatanganan, instalasi, dan update

### 19.1 Prasyarat di PC
Node.js LTS, Android Studio (dengan SDK, NDK, CMake), JDK sesuai Android Studio, prebuilt ncnn Android Vulkan.

### 19.2 Konfigurasi Next.js untuk Capacitor

```js
// next.config.js
module.exports = {
  output: 'export',
  images: { unoptimized: true },
  trailingSlash: true,
};
```

```ts
// capacitor.config.ts
import type { CapacitorConfig } from '@capacitor/cli';
const config: CapacitorConfig = {
  appId: 'id.aruthtale.arulume',
  appName: 'Arulume',
  webDir: 'out',
  android: { allowMixedContent: false },
};
export default config;
```

Semua pemanggilan Capacitor ada di komponen client (`'use client'`), karena tidak tersedia saat build statis.

### 19.3 Perintah build

```bash
npm install
npm run build          # menghasilkan folder out/ (static export)
npx cap add android    # sekali saja
npx cap sync android   # salin out/ ke proyek Android
npx cap open android   # buka Android Studio → Build APK
```

### 19.4 Penandatanganan
- Debug APK cukup untuk uji.
- Untuk pemakaian harian, buat **keystore sendiri** dan tanda tangani APK release. **Simpan keystore dan sandinya dengan aman**: update di atas aplikasi terpasang hanya bisa dilakukan dengan tanda tangan yang sama; bila hilang, aplikasi harus dihapus dulu (data lokal hilang).
- Jangan commit keystore ke repositori publik.

### 19.5 Instalasi
1. Kirim APK ke HP (kabel/USB, Bluetooth, atau penyimpanan).
2. Izinkan "Pasang dari sumber tidak dikenal" untuk aplikasi pengirim/pemasang.
3. Instal. Google Play Protect mungkin menampilkan peringatan untuk aplikasi tak dikenal; ini normal untuk APK pribadi.

### 19.6 Update
Naikkan `versionCode` setiap rilis, tanda tangan dengan keystore yang sama, instal di atas versi lama (data riwayat tetap ada).

## 20. Milestone dan urutan kerja

Setiap milestone punya kriteria keluar. Tidak melanjutkan sebelum kriteria terpenuhi.

### Fase 0: Spike teknis
- [ ] Proyek Next.js static export + Capacitor Android berjalan di HP.
- [ ] Plugin `Enhancer` minimal: `getCapabilities()` mengembalikan data benar.
- [ ] ncnn prebuilt terintegrasi lewat CMake/JNI, build arm64 sukses.
- [ ] Satu model Real-ESRGAN compact berhasil memproses satu foto (GPU Vulkan).
- [ ] Verifikasi A7 (aplikasi jalan tanpa izin `INTERNET`).
- [ ] Verifikasi A8 (format model ncnn tersedia/dapat dikonversi).
- [ ] Benchmark sesuai 18.4 + keputusan tile default dan batas input.
- **Keluar:** laporan benchmark + prototipe yang menyimpan satu foto hasil ke galeri.

### Fase 1: MVP Foto
- [ ] FR-01 sampai FR-07, FR-10, FR-13 (P0).
- [ ] Tiling lengkap, alpha, EXIF, fallback GPU → CPU.
- [ ] Progres, cancel, pratinjau slider, simpan MediaStore.
- [ ] FR-08, FR-09, FR-12 (P1). FR-11 batch sudah (uji 2 foto 1600×900 + 800×450, 34 dtk).
- [ ] Seluruh set uji 18.3 + TC-01 sampai TC-12 lulus.
- [ ] Izin `INTERNET` dihapus pada build final; APK release bertanda tangan.
- **Keluar:** Definition of Done Fase 1 (bagian 22).

### Fase 2: Video
- [ ] Decode/encode MediaCodec streaming, audio passthrough.
- [ ] Foreground service, wake lock, thermal handling.
- [ ] UI video, estimasi waktu, jeda/batal.
- [ ] Uji klip 5, 10, 30, 60 detik; evaluasi flicker.
- **Keluar:** Definition of Done Fase 2.

### Fase 3: Lanjutan
Dirinci ulang saat Fase 2 selesai.

## 21. Risiko dan mitigasi

| ID | Risiko | Kemungkinan | Dampak | Mitigasi |
|---|---|---|---|---|
| R1 | HP tidak mendukung Vulkan dengan baik atau driver bermasalah | Sedang | Tinggi | Fallback CPU, deteksi runtime, uji lebih awal di Fase 0 |
| R2 | Kehabisan memori pada foto besar | Tinggi | Sedang | Tiling, batas output, auto-downscale, dialog peringatan |
| R3 | HP panas dan melambat (terutama video) | Tinggi | Sedang | Batas durasi/resolusi, monitoring termal, jeda otomatis |
| R4 | Video flicker karena frame diproses terpisah | Sedang | Sedang | Model video-friendly, kurangi kekuatan, evaluasi di Fase 2 |
| R5 | Kompleksitas build NDK/CMake/JNI | Sedang | Sedang | Mulai dari contoh ncnn Android yang ada, spike Fase 0 |
| R6 | Asumsi A7 salah (tanpa `INTERNET` UI gagal dimuat) | Rendah | Sedang | Diuji di Fase 0; fallback: pertahankan izin tetapi nonaktifkan semua jaringan di kode dan blok navigasi eksternal |
| R7 | Model compact kurang memuaskan | Sedang | Sedang | Sediakan model x4plus (lebih berat) sebagai opsi |
| R8 | Kunci signing hilang | Rendah | Sedang | Cadangkan keystore di tempat aman |
| R9 | Perubahan versi Capacitor/Android/NDK memutus build | Sedang | Rendah | Kunci versi dependensi, catat di `THIRD_PARTY.md` |
| R10 | Lisensi model salah dipakai | Rendah | Sedang | Catat lisensi per model; CodeFormer hanya pribadi non-komersial |
| R11 | Sistem membunuh proses di latar belakang (foto) | Sedang | Rendah | Pemrosesan foto singkat; video memakai foreground service |

## 22. Definition of Done per fase

### Fase 1 dianggap selesai bila seluruhnya benar:
1. Semua FR P0 Fase 1 berjalan dan lulus kriteria penerimaannya.
2. Set uji 18.3 memberi keberhasilan ≥ 98% tanpa crash; kegagalan yang tersisa menampilkan error terkode.
3. TC-01 sampai TC-12 lulus.
4. APK release bertanda tangan terpasang normal di HP pemilik; ukuran ≤ 60 MB.
5. `aapt dump permissions` tidak memuat `INTERNET`.
6. Seluruh fitur berfungsi dengan mode pesawat aktif.
7. Hasil tersimpan di `Pictures/Arulume`, foto asli tidak berubah.
8. Hasil uji visual A/B: peningkatan terlihat pada ≥ 80% foto uji.
9. Tidak ada kebocoran memori yang terdeteksi pada uji 20 proses berturut-turut.
10. `THIRD_PARTY.md` dan layar "Tentang" memuat versi dan lisensi semua komponen/model.

### Fase 2 dianggap selesai bila:
1. Klip uji 10 dan 30 detik berhasil diproses dan tersimpan di `Movies/Arulume` dengan audio utuh dan sinkron.
2. Frame rate, rasio aspek, dan rotasi tetap benar.
3. Proses tetap berjalan saat layar mati (foreground service) dan batal bersih.
4. Aplikasi tidak crash atau dimatikan sistem pada klip uji; penanganan termal terbukti (jeda/abort tidak merusak file).
5. Flicker dinilai dapat diterima oleh pemilik pada klip uji.

## 23. Pertanyaan terbuka

| # | Pertanyaan | Mempengaruhi |
|---|---|---|
| Q1 | ~~Merek/tipe HP, chipset, RAM, versi Android?~~ **Terjawab 31 Okt 2026:** Redmi Note 8, Snapdragon 665, Adreno 610, RAM 4 GB (~3,55 GiB), Android 11 (API 30), Vulkan 1.1 | Tile default, batas, target performa, kelayakan video |
| Q2 | Apakah PC pembuild punya Android Studio terpasang dan koneksi untuk mengunduh SDK/NDK? | Jadwal Fase 0 |
| Q3 | Jenis konten utama (foto wajah, dokumen, pemandangan, anime)? | Pemilihan model default |
| Q4 | Seberapa panjang video yang ingin diproses secara umum? | Kelayakan Fase 2, batas durasi |
| Q5 | Apakah perlu restorasi wajah ala Remini sejak awal? | Prioritas Fase 3 |
| Q6 | Apakah hasil sebaiknya selalu masuk galeri atau juga ada mode privat? | Default FR-06 |

## 24. Glosarium

| Istilah | Arti |
|---|---|
| Super-resolution | Teknik AI memperbesar gambar sambil menambah detail |
| Real-ESRGAN | Model super-resolution open source |
| ncnn | Framework inferensi neural network ringan dari Tencent, mendukung Vulkan |
| Vulkan | API grafis/komputasi GPU lintas platform |
| Tiling | Memecah gambar jadi potongan agar muat di memori |
| Capacitor | Pembungkus aplikasi web menjadi aplikasi native |
| Plugin | Kode native yang dipanggil dari JavaScript lewat Capacitor |
| JNI | Jembatan antara Kotlin/Java dan C++ |
| MediaStore | API Android untuk menyimpan/membaca media bersama (galeri) |
| Scoped storage | Model akses penyimpanan Android 10+ |
| Sideload | Memasang APK langsung tanpa toko aplikasi |
| Static export | Mode Next.js yang menghasilkan file HTML/JS/CSS statis |
| Foreground service | Layanan Android yang tetap berjalan dengan notifikasi |
| Flicker | Kedipan antar frame video akibat hasil per frame tidak konsisten |

## 25. Lampiran

### 25.1 Struktur folder yang disarankan

```
local-enhancer/
├─ src/
│  ├─ app/                  # halaman Next.js (App Router)
│  ├─ components/           # slider before/after, kartu, dialog
│  ├─ plugins/enhancer.ts   # definisi plugin (bagian 10)
│  ├─ store/                # state aplikasi
│  └─ lib/                  # util: format ukuran, estimasi, dll.
├─ android/
│  └─ app/
│     ├─ build.gradle       # abiFilters arm64-v8a, externalNativeBuild
│     └─ src/main/
│        ├─ AndroidManifest.xml
│        ├─ assets/models/  # *.param, *.bin
│        ├─ cpp/            # CMakeLists.txt, native-lib.cpp, ncnn/
│        └─ java/.../       # MainActivity, EnhancerPlugin.kt, job/, media/
├─ capacitor.config.ts
├─ next.config.js
├─ THIRD_PARTY.md           # versi + lisensi + checksum model
└─ docs/benchmark-fase0.md  # hasil benchmark
```

### 25.2 Referensi untuk dipelajari saat implementasi
- Dokumentasi Capacitor: membuat plugin Android kustom, `registerPlugin`, event listener.
- Dokumentasi Next.js: static export dan batasannya.
- Repositori ncnn (Tencent): panduan build Android dan contoh Vulkan.
- Repositori Real-ESRGAN dan Real-ESRGAN-ncnn-vulkan: model dan format ncnn.
- Dokumentasi Android: Photo Picker, MediaStore (scoped storage), MediaCodec/MediaMuxer, foreground service, thermal API.

### 25.3 Ringkasan keputusan yang sudah final
1. Offline total, model dibundel di APK, izin internet dihapus.
2. Inferensi native (ncnn-Vulkan), UI React.
3. Foto dulu, video belakangan.
4. Hasil ke galeri lewat MediaStore, bukan ke folder APK.
5. Distribusi sideload dengan keystore pribadi.
