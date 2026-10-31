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

**Status:** Hampir selesai — 1.1, 1.2, 1.3 selesai; sisa batch foto & mutu
**Commit:** `a0bb737` — "fase 1 Enchancer Photo"

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

Sisa Fase 1:

- Batch foto (FR-11), onboarding satu layar, dukungan HEIC/PNG/WebP, dialog foto terlalu besar.
- Uji di Android 13+ (dialog `POST_NOTIFICATIONS`) dan Android 14+ (`foregroundServiceType` ditegakkan).
- Mutu: set uji foto #1–#13, TC-01–TC-12, 20 proses berturut-turut, ikon adaptive, `allowBackup="false"`, APK rilis bertanda tangan.

---

## Fase 2 — Video

**Status:** Belum dimulai

Rencana besar: MediaCodec (decode → enhance per frame → encode → mux), audio passthrough, foreground service + wake lock, UI video dengan estimasi waktu dan jeda/batal, simpan ke `Movies/Arulume`.
