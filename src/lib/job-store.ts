"use client";

/**
 * State satu alur kerja: foto terpilih → hasil.
 *
 * Disimpan di memori saja (bukan Preferences) karena isinya cuma rujukan
 * sementara ke berkas cache; Riwayat permanen menyusul di Fase 1.3 (FR-08).
 */
import { create } from "zustand";
import type { CompletedEvent, FailedEvent, PickedMedia } from "@/plugins/enhancer";

interface JobState {
  photo: PickedMedia | null;
  result: CompletedEvent | null;
  failure: FailedEvent | null;
  setPhoto: (p: PickedMedia | null) => void;
  setResult: (r: CompletedEvent | null) => void;
  setFailure: (f: FailedEvent | null) => void;
  reset: () => void;
}

export const useJobStore = create<JobState>((set) => ({
  photo: null,
  result: null,
  failure: null,
  setPhoto: (photo) => set({ photo, result: null, failure: null }),
  setResult: (result) => set({ result, failure: null }),
  setFailure: (failure) => set({ failure }),
  reset: () => set({ photo: null, result: null, failure: null }),
}));

/** Pesan manusiawi untuk setiap ErrorCode (Docs/PRD.md bagian 10). */
export function pesanError(code: string): string {
  switch (code) {
    case "E_PICK_CANCELLED":
      return "Tidak ada foto yang dipilih.";
    case "E_UNSUPPORTED_FORMAT":
      return "Format foto ini belum didukung.";
    case "E_DECODE_FAILED":
      return "Foto tidak bisa dibaca. Coba foto lain.";
    case "E_MODEL_MISSING":
      return "Mesin pemroses tidak ditemukan. Pasang ulang aplikasi.";
    case "E_VULKAN_UNAVAILABLE":
      return "Perangkat ini tidak mendukung mode GPU. Coba lagi dengan mode biasa.";
    case "E_OUT_OF_MEMORY":
      return "Memori tidak cukup untuk foto sebesar ini.";
    case "E_INPUT_TOO_LARGE":
      return "Foto terlalu besar untuk diproses.";
    case "E_SAVE_FAILED":
      return "Hasil gagal disimpan ke galeri.";
    case "E_CANCELLED":
      return "Pemrosesan dibatalkan.";
    case "E_THERMAL_ABORT":
      return "HP terlalu panas, pemrosesan dihentikan.";
    case "E_STORAGE_FULL":
      return "Penyimpanan penuh.";
    default:
      return "Terjadi kesalahan. Coba lagi.";
  }
}
