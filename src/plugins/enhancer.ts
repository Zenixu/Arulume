/**
 * Kontrak plugin `Enhancer` (JS ↔ Kotlin).
 *
 * Ini SATU-SATUNYA tempat definisi kontrak. Kalau berubah, ubah bersamaan:
 *   1. file ini
 *   2. android/app/src/main/java/.../EnhancerPlugin.kt
 *   3. Docs/PRD.md bagian 10
 * Lihat Docs/AGENTS.md (Definition of done).
 */
import { registerPlugin, type PluginListenerHandle } from "@capacitor/core";

export type ModelId = string; // contoh: 'realesr-general-x4v3'

export interface DeviceCapabilities {
  abi: string; // 'arm64-v8a'
  androidApi: number; // contoh: 30
  ramTotalMb: number;
  cpuCores: number;
  vulkanAvailable: boolean;
  gpuName?: string;
  models: ModelInfo[];
}

export interface ModelInfo {
  id: ModelId;
  label: string;
  nativeScale: number; // skala asli model (mis. 4)
  supportedScales: number[]; // skala yang boleh dipilih
  kind: "image" | "video";
  license: string;
  sizeBytes: number;
}

export interface PickedMedia {
  uri: string; // content:// URI dari picker
  name: string;
  mime: string;
  sizeBytes: number;
  width: number; // setelah koreksi orientasi
  height: number;
  durationMs?: number; // video saja
  fps?: number; // video saja
  previewPath?: string; // file thumbnail di cache
}

export interface EnhanceImageOptions {
  uri: string;
  model: ModelId;
  scale: number; // 2 | 3 | 4
  tileSize: number; // 0 = otomatis
  useGpu: boolean;
  keepExif: boolean;
  output: {
    format: "jpeg" | "png" | "webp";
    quality: number; // 70..100
    target: "gallery" | "private";
  };
  maxOutputLongSide: number; // default 4096
  autoDownscaleInput: boolean;
}

export type JobStage = "decoding" | "enhancing" | "encoding" | "saving";

export interface ProgressEvent {
  jobId: string;
  stage: JobStage;
  /** Persen seluruh job (batch: gabungan semua foto). */
  percent: number; // 0..100
  /** Persen foto yang sedang diproses saja (batch). */
  itemPercent?: number;
  /** Indeks foto saat ini, mulai 0 (batch). */
  index?: number;
  total?: number;
  tileIndex?: number;
  tileTotal?: number;
  etaSec?: number;
  /** Terisi untuk job video (Fase 2). */
  kind?: "image" | "video";
  frame?: number;
  totalFrame?: number;
}

export interface CompletedEvent {
  jobId: string;
  /** Jumlah foto yang diminta (1 untuk job tunggal). */
  total?: number;
  /** Jumlah foto yang berhasil / gagal (batch). */
  ok?: number;
  failed?: number;
  cancelled?: boolean;
  /** Daftar hasil per foto, urut sesuai pilihan (batch). */
  entries?: CompletedEvent[];
  /**
   * Path hasil resolusi penuh di cache aplikasi. Belum masuk galeri: user
   * melihat dulu di layar Hasil, lalu memanggil `saveToGallery`.
   */
  resultPath: string;
  /** Terisi hanya setelah user menekan Simpan (lihat `SavedResult`). */
  outputUri: string;
  outputWidth: number;
  outputHeight: number;
  durationMs: number;
  usedGpu: boolean;
  tileSizeUsed: number;
  beforePreviewPath: string; // file di cache untuk WebView
  afterPreviewPath: string;
}

export interface SavedResult {
  uri: string; // content:// di Pictures/Arulume
  width: number;
  height: number;
}

export type ErrorCode =
  | "E_PICK_CANCELLED"
  | "E_UNSUPPORTED_FORMAT"
  | "E_DECODE_FAILED"
  | "E_MODEL_MISSING"
  | "E_VULKAN_UNAVAILABLE"
  | "E_OUT_OF_MEMORY"
  | "E_INPUT_TOO_LARGE"
  | "E_SAVE_FAILED"
  | "E_CANCELLED"
  | "E_THERMAL_ABORT"
  | "E_STORAGE_FULL"
  | "E_UNKNOWN";

export interface FailedEvent {
  jobId: string;
  code: ErrorCode;
  message: string;
  recoverable: boolean;
}

export interface SelfTestResult {
  ok: boolean;
  modelId: string;
  engine: string;
  inputSize: number;
  outputPixels: number;
  expectedWidth: number;
  loadMs: number;
  inferMs: number;
  tile: number;
  threads: number;
  vulkan: boolean;
  native: string;
  sample: number[];
}

/**
 * Satu baris Riwayat (PRD FR-08). Disimpan native di `filesDir/riwayat.json`.
 * `hasResult` false = berkas cache sudah dipangkas; arahkan user ke galeri.
 */
export interface HistoryEntry {
  id: string;
  createdAt: number;
  /** `video` untuk hasil Fase 2; entri lama tidak punya kolom ini. */
  kind?: "image" | "video";
  model: string;
  scale: number;
  /** Hanya untuk video: durasi klip & jumlah frame. */
  videoDurationMs?: number;
  frames?: number;
  srcWidth: number;
  srcHeight: number;
  outWidth: number;
  outHeight: number;
  durationMs: number;
  resultPath: string;
  beforePreviewPath: string;
  afterPreviewPath: string;
  savedUri: string;
  savedName: string;
  hasResult: boolean;
}

export interface JobStatus {
  jobId?: string;
  stage?: JobStage | "done" | "failed" | "idle";
  percent?: number;
  aktif: boolean;
  /** Indeks foto yang sedang diproses (batch). */
  index?: number;
  total?: number;
}

/** Hasil satu foto di dalam antrian batch. */
export interface ItemDoneEvent {
  jobId: string;
  index: number;
  total: number;
  entry: CompletedEvent;
}

/** Satu foto gagal; antrian batch tetap lanjut ke foto berikutnya. */
export interface ItemFailedEvent {
  jobId: string;
  index: number;
  total: number;
  code: ErrorCode;
  message: string;
}

export interface BatchOptions {
  uris: string[];
  model: ModelId;
  scale: number;
  tileSize?: number;
  useGpu?: boolean;
  maxOutputLongSide?: number;
  output?: {
    format: "jpeg" | "png" | "webp";
    quality?: number;
  };
}

/** Pengaturan proses video (Fase 2). */
export interface VideoOptions {
  uri: string;
  /** Sisi panjang hasil: 1280 (720p) atau 1920 (1080p). */
  target?: number;
  /** 0 = otomatis dari resolusi & fps. */
  bitrate?: number;
  /** Penajaman ringan setelah diskala. */
  tajam?: boolean;
}

export interface VideoProgressEvent {
  jobId: string;
  kind: "video";
  stage: string;
  percent: number;
  frame: number;
  totalFrame: number;
}

export interface VideoDoneEvent {
  jobId: string;
  kind: "video";
  /** Path hasil di cache; belum masuk galeri. */
  path: string;
  width: number;
  height: number;
  frames: number;
  bytes: number;
  /** Durasi klip hasil. */
  durationMs: number;
  /** Lama proses. */
  ms: number;
}

export interface VideoFailedEvent {
  jobId: string;
  kind: "video";
  code: ErrorCode | "E_VIDEO_ENCODE";
  message: string;
  /** Persen terakhir sebelum berhenti. */
  percent?: number;
  frame?: number;
  totalFrame?: number;
  /** True bila job bisa dilanjutkan dari frame terakhir (tidak diulang). */
  bisaLanjut?: boolean;
}

/** Hasil pemeriksaan pembaruan dari halaman rilis GitHub. */
export interface UpdateCheck {
  versiSekarang: string;
  adaBaru: boolean;
  versiTerbaru?: string;
  catatan?: string;
  namaBerkas?: string;
  ukuran?: number;
  url?: string;
  galat?: string;
}

export interface UpdateProgressEvent {
  percent: number;
  total: number;
}

export interface VideoStatus {
  jobId?: string;
  stage?: string;
  percent?: number;
  aktif: boolean;
  frame?: number;
  totalFrame?: number;
  /** Ada job terhenti yang bisa dilanjutkan. */
  bisaLanjut?: boolean;
  path?: string;
}

export interface EnhancerPlugin {
  getCapabilities(): Promise<DeviceCapabilities>;
  runSelfTest(opts?: {
    modelId?: string;
    size?: number;
    vulkan?: boolean;
    tile?: number;
    threads?: number;
  }): Promise<SelfTestResult>;
  pickMedia(opts: {
    type: "image" | "video";
    multiple?: boolean;
    /** Batas atas saat `multiple` (default & maksimum 20). */
    maxItems?: number;
  }): Promise<{ items: PickedMedia[]; skipped?: number }>;
  /**
   * Hasil pemilih foto yang terselamatkan setelah proses aplikasi mati saat
   * pemilih di depan. `items` kosong kalau tidak ada yang tertunda.
   */
  getPendingPick(): Promise<{ items: PickedMedia[] }>;
  startEnhanceImage(opts: EnhanceImageOptions): Promise<{ jobId: string }>;
  /** Memproses beberapa foto berurutan dalam satu job (PRD FR-11). */
  startEnhanceBatch(
    opts: BatchOptions,
  ): Promise<{ jobId: string; total: number }>;
  /** Menyalin hasil dari cache ke galeri `Pictures/Arulume`. */
  saveToGallery(opts: { path: string; quality?: number }): Promise<SavedResult>;
  cancelJob(opts: { jobId: string }): Promise<void>;
  /**
   * Keadaan job terakhir yang diketahui native. Dipakai saat layar Editor
   * dibuka ulang (mis. aplikasi ditutup saat proses berjalan): `aktif` true
   * berarti proses masih hidup di layanan latar.
   */
  getJobStatus(): Promise<JobStatus>;
  /** Memulai proses video; hasil datang lewat event `videoDone`. */
  prosesVideo(opts: VideoOptions): Promise<{ jobId: string; kind: "video" }>;
  getVideoStatus(): Promise<VideoStatus>;
  /** Versi aplikasi yang terpasang. */
  versiApp(): Promise<{ nama: string; kode: number }>;
  /**
   * Membuka halaman rilis di browser — jalan cadangan bila pemeriksaan di
   * dalam aplikasi gagal.
   */
  bukaHalamanRilis(opts?: { url?: string }): Promise<{ ok: boolean }>;
  /** Memeriksa rilis terbaru langsung dari GitHub (hanya untuk pembaruan). */
  cekUpdate(): Promise<UpdateCheck>;
  /** Mengunduh APK pembaruan; progres lewat event `updateProgress`. */
  unduhUpdate(opts: {
    url: string;
    nama?: string;
    ukuran?: number;
  }): Promise<{ ok: boolean; path: string; ukuran: number }>;
  /** Membuka pemasang Android untuk APK yang sudah diunduh. */
  pasangUpdate(opts: { path: string }): Promise<{ ok: boolean }>;
  /** Melanjutkan job video yang terhenti dari frame terakhir. */
  lanjutkanVideo(): Promise<{
    jobId: string;
    kind: "video";
    percent: number;
    frame: number;
    totalFrame: number;
  }>;
  /** Menjeda job video; potongan tersimpan untuk dilanjutkan nanti. */
  hentikanVideo(): Promise<{ ok: boolean }>;
  /** Menyalin hasil video ke galeri `Movies/Arulume`. */
  saveVideoToGallery(opts: { path: string }): Promise<{ uri: string; name: string }>;
  /** Meminta izin notifikasi (Android 13+). Notifikasi lokal, bukan FCM. */
  mintaIzinNotifikasi(): Promise<{ granted: boolean }>;
  listHistory(): Promise<{ items: HistoryEntry[] }>;
  getHistory(opts: { id: string }): Promise<HistoryEntry>;
  deleteHistory(opts: { id: string }): Promise<{ deleted: boolean }>;
  clearHistory(): Promise<void>;
  clearCache(): Promise<{ freedBytes: number }>;
  openInGallery(opts: { uri: string }): Promise<void>;
  shareResult(opts: { uri: string; mime: string }): Promise<void>;
  addListener(
    ev: "progress",
    fn: (e: ProgressEvent) => void,
  ): Promise<PluginListenerHandle>;
  addListener(
    ev: "completed",
    fn: (e: CompletedEvent) => void,
  ): Promise<PluginListenerHandle>;
  addListener(
    ev: "failed",
    fn: (e: FailedEvent) => void,
  ): Promise<PluginListenerHandle>;
  addListener(
    ev: "itemDone",
    fn: (e: ItemDoneEvent) => void,
  ): Promise<PluginListenerHandle>;
  addListener(
    ev: "itemFailed",
    fn: (e: ItemFailedEvent) => void,
  ): Promise<PluginListenerHandle>;
  addListener(
    ev: "videoDone",
    fn: (e: VideoDoneEvent) => void,
  ): Promise<PluginListenerHandle>;
  addListener(
    ev: "videoFailed",
    fn: (e: VideoFailedEvent) => void,
  ): Promise<PluginListenerHandle>;
  addListener(
    ev: "updateProgress",
    fn: (e: UpdateProgressEvent) => void,
  ): Promise<PluginListenerHandle>;
}

export const Enhancer = registerPlugin<EnhancerPlugin>("Enhancer");

/** True kalau berjalan di dalam shell native (bukan `next dev` di browser). */
export function isNative(): boolean {
  const cap = (globalThis as { Capacitor?: { isNativePlatform?: () => boolean } })
    .Capacitor;
  return cap?.isNativePlatform?.() === true;
}
