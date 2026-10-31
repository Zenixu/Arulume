/**
 * Ikon outline 24 dp, stroke 1,75 — sesuai Docs/design.md bagian 3.3.
 * Ditulis sendiri supaya tidak menambah dependensi ikon (dan aman offline).
 */
type IconProps = {
  className?: string;
  size?: number;
};

const base = (size: number) => ({
  width: size,
  height: size,
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.75,
  strokeLinecap: "round" as const,
  strokeLinejoin: "round" as const,
  "aria-hidden": true,
  focusable: "false" as const,
});

export function IconSettings({ className, size = 24 }: IconProps) {
  return (
    <svg {...base(size)} className={className}>
      <circle cx="12" cy="12" r="3" />
      <path d="M19.4 15a1.7 1.7 0 0 0 .34 1.87l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.7 1.7 0 0 0-1.87-.34 1.7 1.7 0 0 0-1.03 1.56V21a2 2 0 1 1-4 0v-.09A1.7 1.7 0 0 0 9 19.3a1.7 1.7 0 0 0-1.87.34l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.7 1.7 0 0 0 4.7 15a1.7 1.7 0 0 0-1.56-1.03H3a2 2 0 1 1 0-4h.09A1.7 1.7 0 0 0 4.7 9a1.7 1.7 0 0 0-.34-1.87l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.7 1.7 0 0 0 9 4.7h.09A1.7 1.7 0 0 0 10.7 3.14V3a2 2 0 1 1 4 0v.09A1.7 1.7 0 0 0 15.8 4.7a1.7 1.7 0 0 0 1.87-.34l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.7 1.7 0 0 0 20.3 9v.09a1.7 1.7 0 0 0 1.56 1.03H22a2 2 0 1 1 0 4h-.09A1.7 1.7 0 0 0 19.4 15Z" />
    </svg>
  );
}

export function IconPlane({ className, size = 16 }: IconProps) {
  return (
    <svg {...base(size)} className={className}>
      <path d="M17.8 19.2 16 11l3.5-3.5a2.1 2.1 0 0 0-3-3L13 8 4.8 6.2a1 1 0 0 0-1 1.6l5.5 4.4-2 3.6-2.6-.4a.8.8 0 0 0-.7 1.3l2.4 2.4 2.4 2.4a.8.8 0 0 0 1.3-.7l-.4-2.6 3.6-2 4.4 5.5a1 1 0 0 0 1.6-1Z" />
    </svg>
  );
}

export function IconPhotoPlus({ className, size = 24 }: IconProps) {
  return (
    <svg {...base(size)} className={className}>
      <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h7" />
      <path d="M16 5h6M19 2v6" />
      <circle cx="9" cy="9" r="2" />
      <path d="m3 17 5-5 4 4 3-3 3 3" />
    </svg>
  );
}

export function IconVideo({ className, size = 24 }: IconProps) {
  return (
    <svg {...base(size)} className={className}>
      <rect x="2" y="6" width="14" height="12" rx="2" />
      <path d="m16 10 6-3v10l-6-3z" />
    </svg>
  );
}

export function IconChevronRight({ className, size = 24 }: IconProps) {
  return (
    <svg {...base(size)} className={className}>
      <path d="m9 18 6-6-6-6" />
    </svg>
  );
}

export function IconArrowLeft({ className, size = 24 }: IconProps) {
  return (
    <svg {...base(size)} className={className}>
      <path d="M19 12H5M12 19l-7-7 7-7" />
    </svg>
  );
}

export function IconCheck({ className, size = 24 }: IconProps) {
  return (
    <svg {...base(size)} className={className}>
      <path d="m20 6-11 11-5-5" />
    </svg>
  );
}
