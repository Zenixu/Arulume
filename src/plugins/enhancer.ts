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
  percent: number; // 0..100
  tileIndex?: number;
  tileTotal?: number;
  etaSec?: number;
}

export interface CompletedEvent {
  jobId: string;
  outputUri: string; // content:// atau file path privat
  outputWidth: number;
  outputHeight: number;
  durationMs: number;
  usedGpu: boolean;
  tileSizeUsed: number;
  beforePreviewPath: string; // file di cache untuk WebView
  afterPreviewPath: string;
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

export interface EnhancerPlugin {
  getCapabilities(): Promise<DeviceCapabilities>;
  pickMedia(opts: {
    type: "image" | "video";
    multiple?: boolean;
  }): Promise<{ items: PickedMedia[] }>;
  startEnhanceImage(opts: EnhanceImageOptions): Promise<{ jobId: string }>;
  cancelJob(opts: { jobId: string }): Promise<void>;
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
}

export const Enhancer = registerPlugin<EnhancerPlugin>("Enhancer");

/** True kalau berjalan di dalam shell native (bukan `next dev` di browser). */
export function isNative(): boolean {
  const cap = (globalThis as { Capacitor?: { isNativePlatform?: () => boolean } })
    .Capacitor;
  return cap?.isNativePlatform?.() === true;
}
