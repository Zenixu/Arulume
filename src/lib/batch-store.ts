"use client";

/**
 * State antrian batch (PRD FR-11): beberapa foto diproses berurutan dalam satu
 * job. Disimpan di memori saja — berkas hasilnya ada di cache, dan Riwayat
 * permanen ditulis native per foto.
 */
import { create } from "zustand";
import type { CompletedEvent, JobStage, PickedMedia } from "@/plugins/enhancer";

export type ItemState = "menunggu" | "proses" | "selesai" | "gagal";

export interface BatchItem {
  media: PickedMedia;
  state: ItemState;
  entry?: CompletedEvent;
  pesan?: string;
  /** Terisi setelah user menekan Simpan untuk foto ini. */
  savedUri?: string;
}

interface BatchState {
  items: BatchItem[];
  jobId: string | null;
  percent: number;
  stage: JobStage | "done" | "failed" | null;
  aktif: boolean;
  /** Pesan kegagalan seluruh job (bukan satu foto). */
  gagalJob: string | null;
  siapkan: (media: PickedMedia[]) => void;
  mulai: (jobId: string) => void;
  progres: (percent: number, stage: JobStage) => void;
  itemSelesai: (index: number, entry: CompletedEvent) => void;
  itemGagal: (index: number, pesan: string) => void;
  selesai: (ok: number, gagal: number, cancelled: boolean) => void;
  gagal: (pesan: string) => void;
  tandaiTersimpan: (index: number, uri: string) => void;
  reset: () => void;
}

export const useBatchStore = create<BatchState>((set) => ({
  items: [],
  jobId: null,
  percent: 0,
  stage: null,
  aktif: false,
  gagalJob: null,

  siapkan: (media) =>
    set({
      items: media.map((m) => ({ media: m, state: "menunggu" as ItemState })),
      jobId: null,
      percent: 0,
      stage: null,
      aktif: false,
      gagalJob: null,
    }),

  mulai: (jobId) =>
    set((s) => ({
      jobId,
      aktif: true,
      percent: 0,
      stage: "decoding",
      gagalJob: null,
      items: s.items.map((it) => ({ ...it, state: "menunggu" as ItemState })),
    })),

  progres: (percent, stage) =>
    set((s) => ({
      percent,
      stage,
      aktif: true,
      // Foto yang sedang diproses ditandai; yang sudah selesai tidak diturunkan.
      items: s.items.map((it) =>
        it.state === "menunggu" && percent > 0 ? { ...it, state: "proses" } : it,
      ),
    })),

  itemSelesai: (index, entry) =>
    set((s) => ({
      items: s.items.map((it, i) =>
        i === index ? { ...it, state: "selesai" as ItemState, entry, pesan: undefined } : it,
      ),
    })),

  itemGagal: (index, pesan) =>
    set((s) => ({
      items: s.items.map((it, i) =>
        i === index ? { ...it, state: "gagal" as ItemState, pesan } : it,
      ),
    })),

  selesai: (ok, gagal, cancelled) =>
    set((s) => ({
      aktif: false,
      stage: "done",
      percent: 100,
      items: s.items.map((it) => ({
        ...it,
        state:
          it.state === "selesai"
            ? ("selesai" as ItemState)
            : it.state === "gagal"
              ? ("gagal" as ItemState)
              : cancelled
                ? ("menunggu" as ItemState)
                : it.state,
      })),
      gagalJob:
        ok === 0 && gagal > 0
          ? "Tidak ada foto yang berhasil diproses."
          : s.gagalJob,
    })),

  gagal: (pesan) => set({ aktif: false, stage: "failed", gagalJob: pesan }),

  tandaiTersimpan: (index, uri) =>
    set((s) => ({
      items: s.items.map((it, i) => (i === index ? { ...it, savedUri: uri } : it)),
    })),

  reset: () =>
    set({
      items: [],
      jobId: null,
      percent: 0,
      stage: null,
      aktif: false,
      gagalJob: null,
    }),
}));

/** Ringkasan untuk tombol bawah: berapa yang siap disimpan. */
export function ringkasBatch(items: BatchItem[]) {
  const selesai = items.filter((i) => i.state === "selesai").length;
  const gagal = items.filter((i) => i.state === "gagal").length;
  const tersimpan = items.filter((i) => i.savedUri).length;
  return { selesai, gagal, tersimpan };
}
