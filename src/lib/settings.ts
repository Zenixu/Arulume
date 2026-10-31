"use client";

/**
 * Pengaturan yang cukup disimpan di perangkat lewat `localStorage`
 * (PRD FR-09): tema dan skala bawaan. Tidak ada yang dikirim ke mana pun —
 * aplikasi ini tidak punya izin internet.
 *
 * Nilai tema memakai kunci yang sama dengan skrip anti-kedip di
 * `src/app/layout.tsx`: "dark" | "light" | "system".
 */

export type Tema = "gelap" | "terang" | "sistem";

const K_TEMA = "arulume.theme";
const K_SKALA = "arulume.skala";

export const LABEL_TEMA: Record<Tema, string> = {
  gelap: "Gelap",
  terang: "Terang",
  sistem: "Ikuti sistem",
};

function nilaiTema(t: Tema): "dark" | "light" | "system" {
  return t === "gelap" ? "dark" : t === "terang" ? "light" : "system";
}

function dariNilai(v: string | null): Tema {
  if (v === "light") return "terang";
  if (v === "system") return "sistem";
  return "gelap";
}

export function bacaTema(): Tema {
  if (typeof window === "undefined") return "gelap";
  try {
    return dariNilai(window.localStorage.getItem(K_TEMA));
  } catch {
    return "gelap";
  }
}

/** Terapkan tema sekarang juga (tanpa memuat ulang layar). */
export function terapkanTema(t: Tema) {
  if (typeof window === "undefined") return;
  try {
    const v = nilaiTema(t);
    if (v === "system") {
      const gelap = window.matchMedia("(prefers-color-scheme: dark)").matches;
      document.documentElement.dataset.theme = gelap ? "dark" : "light";
    } else {
      document.documentElement.dataset.theme = v;
    }
    window.localStorage.setItem(K_TEMA, v);
  } catch {
    // localStorage bisa diblokir; tema tetap jalan untuk sesi ini.
  }
}

export function bacaSkalaBawaan(): 2 | 4 {
  if (typeof window === "undefined") return 2;
  try {
    return window.localStorage.getItem(K_SKALA) === "4" ? 4 : 2;
  } catch {
    return 2;
  }
}

const K_ONBOARDING = "arulume.onboarding";

/** Onboarding hanya tampil sekali; disimpan lokal, tidak butuh jaringan. */
export function bacaOnboarding(): boolean {
  if (typeof window === "undefined") return true;
  try {
    return window.localStorage.getItem(K_ONBOARDING) === "1";
  } catch {
    return true;
  }
}

export function simpanOnboarding() {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(K_ONBOARDING, "1");
  } catch {
    // abaikan
  }
}

/** Format berkas hasil. JPEG paling kecil; PNG/WebP untuk kualitas tanpa rugi. */
export type Format = "jpeg" | "png" | "webp";

const K_FORMAT = "arulume.format";

export const LABEL_FORMAT: Record<Format, string> = {
  jpeg: "JPG",
  png: "PNG",
  webp: "WebP",
};

export const KETERANGAN_FORMAT: Record<Format, string> = {
  jpeg: "Paling kecil, cocok untuk dibagikan.",
  png: "Tanpa kompresi rugi, berkas lebih besar.",
  webp: "Kecil tapi tetap tajam.",
};

export function bacaFormat(): Format {
  if (typeof window === "undefined") return "jpeg";
  try {
    const v = window.localStorage.getItem(K_FORMAT);
    return v === "png" || v === "webp" ? v : "jpeg";
  } catch {
    return "jpeg";
  }
}

export function simpanFormat(f: Format) {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(K_FORMAT, f);
  } catch {
    // abaikan
  }
}

export function simpanSkalaBawaan(s: 2 | 4) {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(K_SKALA, String(s));
  } catch {
    // abaikan
  }
}
