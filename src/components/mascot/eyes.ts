/**
 * Posisi mata maskot Aru untuk ekspresi yang punya dua lubang mata jelas.
 *
 * DIHASILKAN dari pemindaian `paths.ts`: tiap SVG dirasterkan, lubang tertutup
 * dicari, lalu dua lubang berukuran mirip di bagian atas dipakai sebagai mata.
 * Hasilnya diperiksa satu per satu; ekspresi yang titiknya tidak jatuh di mata
 * sengaja tidak dimasukkan (maskot tetap tampil sebagai siluet).
 *
 * Satuan mengikuti `viewBox` masing-masing ekspresi. Pupil digambar dengan
 * `currentColor`, jadi warnanya selalu sama dengan badan maskot.
 */
export interface MascotEye {
  cx: number;
  cy: number;
  r: number;
}

export const MASCOT_EYES: Partial<Record<string, MascotEye[]>> = {
  "berbaring": [{ cx: 69.4, cy: 88.9, r: 16.1 }, { cx: 126.4, cy: 79.2, r: 16.5 }],
  "duduk-ekor-panjang": [{ cx: 59.7, cy: 141.1, r: 24.0 }, { cx: 131.5, cy: 136.8, r: 24.5 }],
  "duduk-manis": [{ cx: 165.6, cy: 135.1, r: 30.5 }, { cx: 249.2, cy: 139.0, r: 30.6 }],
  "mata-bulat": [{ cx: 115.7, cy: 200.5, r: 45.3 }, { cx: 250.7, cy: 203.1, r: 48.2 }],
  "mengendap": [{ cx: 58.6, cy: 101.2, r: 21.3 }, { cx: 127.0, cy: 112.7, r: 23.9 }],
};
