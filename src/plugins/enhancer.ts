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
  model: string;
  scale: number;
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
}

export const Enhancer = registerPlugin<EnhancerPlugin>("Enhancer");

/** True kalau berjalan di dalam shell native (bukan `next dev` di browser). */
export function isNative(): boolean {
  const cap = (globalThis as { Capacitor?: { isNativePlatform?: () => boolean } })
    .Capacitor;
  return cap?.isNativePlatform?.() === true;
}
