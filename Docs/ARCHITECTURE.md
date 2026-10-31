# ARCHITECTURE: Arulume

Dokumen teknis arsitektur. Pelengkap `PRD.md` (bagian 7, 10, 11). Item bertanda **[VERIFIKASI]** harus dibuktikan di Fase 0.

| | |
|---|---|
| Versi | 1.0 (Draft) |
| Tanggal | 31 Oktober 2026 |

---

## 1. Gambaran lapisan

```
┌────────────────────────────────────────────────────────┐
│ UI (WebView): Next.js static export + React + TS       │
│ Tidak ada inferensi di sini. Hanya tampilan & state.   │
└───────────────▲────────────────────────────────────────┘
                │ Capacitor bridge: Enhancer (registerPlugin)
┌───────────────┴────────────────────────────────────────┐
│ Plugin (Kotlin): EnhancerPlugin                        │
│  ├─ PickerController      (Photo Picker / SAF)         │
│  ├─ JobManager            (antrian, status, cancel)    │
│  ├─ ImageIO               (decode, EXIF, encode)       │
│  ├─ MediaSaver            (MediaStore / privat)        │
│  ├─ DeviceInfo            (RAM, ABI, Vulkan, GPU)      │
│  ├─ PreviewBuilder        (thumbnail & pratinjau cache)│
│  └─ (Fase 2) VideoPipeline, ForegroundService          │
└───────────────▲────────────────────────────────────────┘
                │ JNI
┌───────────────┴────────────────────────────────────────┐
│ Native (C++): libarulume.so                            │
│  ├─ Engine: ncnn::Net + Vulkan (atau CPU)              │
│  ├─ Tiler: bagi, padding, tempel                       │
│  └─ Scaler: resampling ke skala akhir                  │
└────────────────────────────────────────────────────────┘
```

Aturan batas antar lapisan:

- UI **tidak** membaca file gambar mentah. UI hanya menerima path pratinjau kecil dari plugin.
- Plugin **tidak** mengandung logika inferensi. Ia mengatur siklus job dan I/O.
- Native **tidak** tahu soal Android UI/MediaStore. Ia menerima buffer piksel dan mengembalikan buffer piksel.

## 2. Threading

| Thread | Tugas | Aturan |
|---|---|---|
| UI/WebView | Render React, input pengguna | Tidak pernah memblokir; tidak ada komputasi berat |
| Capacitor plugin thread | Menerima panggilan `@PluginMethod` | Hanya validasi dan memulai job, kembali segera |
| `enhancer-worker` (single thread executor) | Menjalankan satu job penuh (decode → inferensi → encode → simpan) | Satu job aktif sekaligus |
| Thread internal ncnn | Paralelisme CPU / antrian Vulkan | Jumlah thread CPU dibatasi (mis. jumlah core besar) agar HP tidak terlalu panas |

Pengiriman event ke UI memakai `notifyListeners` dan dibatasi frekuensinya (maks ±10 event per detik).

## 3. Siklus hidup job

```
        startEnhance*
             │
          QUEUED ──cancel──────────────┐
             │                         │
         DECODING ──error──────────────┤
             │                         │
         ENHANCING ──error/cancel──────┤
             │                         ▼
         ENCODING                  FAILED / CANCELLED
             │                     (bersihkan temp)
          SAVING ──error───────────────┘
             │
         COMPLETED
```

Aturan:
- Tepat satu event terminal per job: `completed` **atau** `failed` (pembatalan dikirim sebagai `failed` dengan kode `E_CANCELLED`).
- Setiap transisi ke status terminal memicu pembersihan file sementara dan pelepasan sumber daya (bitmap, buffer native, wake lock).
- Pembatalan memakai `AtomicBoolean`. Native memeriksanya **di antara tile**, jadi jeda pembatalan paling lama sama dengan waktu satu tile. Inferensi satu tile tidak bisa diinterupsi di tengah.
- Penyimpanan ke galeri memakai `IS_PENDING = 1` selama menulis, lalu diubah ke 0 saat selesai. Jika gagal, entri dihapus.

## 4. Pipeline foto (ringkas)

1. Decode (`ImageDecoder` API 28+, `BitmapFactory` di bawahnya) dengan sampling aman bila input sangat besar.
2. Terapkan orientasi EXIF.
3. Validasi dimensi output (`input × skala ≤ batas`), hitung perkiraan memori.
4. Pisahkan alpha bila ada.
5. Tiling → inferensi per tile → rakit.
6. Resampling bila skala pilihan ≠ skala asli model.
7. Gabung alpha.
8. Encode (JPEG/PNG/WebP), salin EXIF (orientasi dinormalkan).
9. Simpan ke MediaStore atau `filesDir`.
10. Buat pratinjau kecil sebelum/sesudah di cache.
11. Kirim `completed`.

Detail langkah ada di PRD bagian 11.1.

## 5. Strategi memori

- **Jangan menahan lebih dari yang perlu.** Bitmap input dilepas begitu data piksel sudah disalin ke buffer native.
- Buffer output RGB: `W × H × 3` byte. Contoh 4096×4096 ≈ 48 MB. Batas default sisi panjang output 4096 px melindungi dari OOM.
- Tile sementara hanya sebesar `tile + padding` per sisi, dibebaskan setelah ditempel.
- Ukuran tile otomatis meniru logika proyek Real-ESRGAN-ncnn-vulkan: dipilih dari anggaran heap GPU (semakin kecil anggaran, semakin kecil tile), dengan padding sekitar 10 px **[VERIFIKASI]** nilai pastinya di perangkat target.
- **Fallback bertahap** saat gagal: tile lebih kecil (maks 2 kali) → CPU → `E_OUT_OF_MEMORY` dengan saran konkret.
- Uji kebocoran: 20 proses berturut-turut dengan Android Studio Profiler (native + Java heap).

## 6. Kontrak plugin

Definisi lengkap (TypeScript) ada di PRD bagian 10. Ringkasan metode: `getCapabilities`, `pickMedia`, `startEnhanceImage`, `cancelJob`, `clearCache`, `openInGallery`, `shareResult`. Event: `progress`, `completed`, `failed`.

Aturan penting:
- `startEnhance*` kembali segera dengan `jobId`; hasil lewat event.
- Satu job aktif; job lain masuk antrian atau ditolak dengan pesan jelas.
- Setiap error membawa `ErrorCode` dari daftar tetap. Tidak ada error tanpa kode.

Pendaftaran plugin (Android): kelas `EnhancerPlugin` meng-extend `Plugin` dengan `@CapacitorPlugin(name = "Enhancer")`, metode diberi `@PluginMethod`, dan didaftarkan di `MainActivity` dengan `registerPlugin(EnhancerPlugin.class)` **sebelum** `super.onCreate`. Di JS: `registerPlugin<EnhancerPlugin>('Enhancer')`.

## 7. Pemetaan error

| Sumber | Kondisi | Kode |
|---|---|---|
| Picker | Pengguna batal | `E_PICK_CANCELLED` |
| Decode | Format tak didukung | `E_UNSUPPORTED_FORMAT` |
| Decode | File rusak | `E_DECODE_FAILED` |
| Validasi | Output melebihi batas | `E_INPUT_TOO_LARGE` |
| Aset | Model tidak ada | `E_MODEL_MISSING` |
| Native | Vulkan tak ada/gagal init | `E_VULKAN_UNAVAILABLE` (lalu fallback CPU) |
| Native | Memori habis | `E_OUT_OF_MEMORY` |
| Penyimpanan | Ruang tidak cukup | `E_STORAGE_FULL` |
| Penyimpanan | Gagal menulis | `E_SAVE_FAILED` |
| Job | Dibatalkan | `E_CANCELLED` |
| Video | Panas parah | `E_THERMAL_ABORT` |
| Lain-lain | Tak terduga | `E_UNKNOWN` |

Pesan untuk pengguna ada di `design.md` bagian 8. Kode teknis hanya ditampilkan kecil di layar error.

## 8. Penyimpanan dan model

- Model: `android/app/src/main/assets/models/`. ncnn dapat memuat dari asset manager atau dari path file; bila butuh path file, salin sekali ke `filesDir/models/` saat pertama dijalankan dan verifikasi checksum **[VERIFIKASI]**.
- Hasil: MediaStore `Pictures/Arulume` (default) atau `filesDir/results/` (privat).
- Cache pratinjau: `cacheDir/enhancer/`, dibersihkan setelah job, saat aplikasi dibuka, dan lewat "Hapus Cache".
- Setting: `@capacitor/preferences`. Riwayat: `filesDir/history.json`.
- `android:allowBackup="false"`.

## 9. Keamanan dan privasi

- Tanpa izin `INTERNET`; tanpa SDK analitik, iklan, atau crash reporter jarak jauh. **[VERIFIKASI]** bahwa UI tetap termuat tanpa izin ini (jika gagal, lihat risiko R6 di PRD).
- WebView tidak boleh menavigasi ke URL eksternal.
- Font dan semua aset UI dibundel lokal.
- Log hanya lokal dan tidak berisi isi gambar.

## 10. Rancangan Fase 2 (video), ringkas

```
MediaExtractor ─► MediaCodec(decoder) ─► frame
                                           │ konversi ke RGB
                                           ▼
                                  Engine (tile, inferensi)
                                           │
                                           ▼
MediaCodec(encoder) ◄───────── frame hasil ┘
        │
        ▼
   MediaMuxer ◄── audio (passthrough dari extractor)
        │
        ▼
MediaStore (Movies/Arulume)
```

- Streaming per frame, tidak ada penyimpanan seluruh frame.
- Berjalan di foreground service dengan wake lock parsial, pemantauan `PowerManager` thermal status, dan jeda/batal per frame.
- Evaluasi flicker antar frame sebagai kriteria penerimaan utama.

## 11. Catatan build

- ABI hanya `arm64-v8a` (`ndk.abiFilters`).
- `externalNativeBuild` dengan CMake menunjuk ke `cpp/CMakeLists.txt`; ncnn prebuilt di `cpp/ncnn/`.
- Deklarasikan fitur Vulkan sebagai opsional (`required="false"`) agar APK tetap terpasang di perangkat tanpa Vulkan.
- Next.js: `output: 'export'`, `images.unoptimized: true`, `trailingSlash: true`; seluruh pemanggilan Capacitor di komponen client.

## 12. Catatan keputusan (ADR ringkas)

| ID | Keputusan | Alasan |
|---|---|---|
| AD1 | Inferensi native (ncnn), bukan onnxruntime-web | WebView lambat, memori ketat, WebGPU Android belum merata |
| AD2 | Model dibundel di APK | Offline sejak instal, tanpa izin internet |
| AD3 | Plugin lokal di proyek Android, bukan paket npm | Build lebih sederhana; bisa diekstrak nanti |
| AD4 | Photo Picker/SAF, bukan izin baca media | Lebih privat, tanpa izin luas |
| AD5 | Simpan via MediaStore | Kompatibel scoped storage |
| AD6 | Video diproses streaming | Hemat memori dan disk |
| AD7 | Job asinkron berbasis event | UI responsif, mendukung cancel |
| AD8 | Satu job aktif sekaligus | Mencegah kehabisan memori dan panas berlebih |
