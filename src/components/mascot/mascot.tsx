import { MASCOT_SHAPES, type MascotMood } from "./paths";

/**
 * Peran maskot yang dipakai di aplikasi (rujuk Docs/design.md bagian 5.3 & 5.4).
 * Nilainya adalah nama berkas di `assets/mascot/svg/`.
 */
export const MASCOT_ROLE = {
  netral: "duduk-manis",
  mengintip: "mengintip",
  melambai: "angkat-kaki",
  sibuk: "ide",
  berbinar: "berbinar",
  bingung: "pusing-spiral",
  mengantuk: "datar",
  kepanasan: "keringat",
  tidur: "tidur-zzz",
  jejak: "jejak-kaki",
  hati: "peluk-hati",
  oke: "oke",
  kosong: "amplop",
  kaget: "kaget",
  gagal: "menangis",
  marah: "marah-ledakan",
} as const satisfies Record<string, MascotMood>;

export type MascotRole = keyof typeof MASCOT_ROLE;

export interface MascotProps {
  /** Nama ekspresi (40 pilihan) atau salah satu peran di MASCOT_ROLE. */
  mood: MascotMood | MascotRole;
  /** Sisi terpanjang dalam px/dp. Default 96. Rasio asli dipertahankan. */
  size?: number;
  /** Label untuk pembaca layar. Kosongkan kalau maskot hanya dekoratif. */
  label?: string;
  /** Animasi mengambang ringan. Otomatis berhenti bila pengguna minta kurangi gerakan. */
  animate?: boolean;
  className?: string;
}

function resolveMood(mood: MascotProps["mood"]): MascotMood {
  if (mood in MASCOT_SHAPES) return mood as MascotMood;
  const mapped = MASCOT_ROLE[mood as MascotRole];
  if (mapped) return mapped;
  return MASCOT_ROLE.netral;
}

/**
 * Maskot Aru sebagai SVG inline satu warna (`currentColor`).
 *
 * Warna mengikuti CSS di sekitarnya: `text-[#111214] dark:text-[#F7F6F3]`.
 * Mata dan detail adalah lubang transparan, jadi jangan menaruhnya di atas foto.
 */
export function Mascot({
  mood,
  size = 96,
  label,
  animate = false,
  className = "",
}: MascotProps) {
  const shape = MASCOT_SHAPES[resolveMood(mood)];
  const [vbW, vbH] = shape.viewBox.split(/\s+/).slice(2, 4).map(Number);
  const ratio = vbH / vbW;
  const width = size;
  const height = Math.round(size * ratio);

  return (
    <svg
      viewBox={shape.viewBox}
      width={width}
      height={height}
      fill="currentColor"
      role={label ? "img" : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
      focusable="false"
      className={`${animate ? "mascot-float" : ""} ${className}`.trim() || undefined}
    >
      <g transform={shape.transform}>
        <path d={shape.d} />
      </g>
    </svg>
  );
}
