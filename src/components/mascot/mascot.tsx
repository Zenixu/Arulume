import { MASCOT_EYES } from "./eyes";
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
  const nama = resolveMood(mood);
  const shape = MASCOT_SHAPES[nama];
  const mata = MASCOT_EYES[nama];
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
      {/* Pupil digambar di luar <g>: koordinat MASCOT_EYES memakai ruang viewBox,
          sedangkan isi <g> memakai koordinat path asli (skala 0,1 dan sumbu Y
          terbalik). Lubang mata transparan, jadi titik inilah yang membuat mata
          terbaca sebagai mata, bukan dua lubang kosong. */}
      {mata?.map((m) => (
        <circle key={`${m.cx}-${m.cy}`} cx={m.cx} cy={m.cy} r={m.r * 0.45} />
      ))}
    </svg>
  );
}
