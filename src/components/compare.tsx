"use client";

/**
 * Pembanding sebelum/sesudah — Docs/design.md bagian 7.4.
 *
 * Satu jari menggeser batas pembanding, dua jari mencubit untuk memperbesar
 * (sekaligus menggeser gambar), dan tombol Sebelum/Sesudah tetap tersedia
 * supaya layar ini bisa dipakai tanpa gerakan geser sama sekali.
 */
import { useCallback, useRef, useState } from "react";

const ZOOM_MIN = 1;
const ZOOM_MAX = 5;

interface Props {
  before: string;
  after: string;
  /** lebar / tinggi hasil, dipakai supaya kotak tidak melompat saat dimuat. */
  ratio: number;
  labelBefore?: string;
  labelAfter?: string;
}

type Mode = "banding" | "sebelum" | "sesudah";

export function CompareView({
  before,
  after,
  ratio,
  labelBefore = "Sebelum",
  labelAfter = "Sesudah",
}: Props) {
  const boxRef = useRef<HTMLDivElement | null>(null);
  const pointers = useRef(new Map<number, { x: number; y: number }>());
  const pinch = useRef<{
    dist: number;
    zoom: number;
    cx: number;
    cy: number;
    panX: number;
    panY: number;
  } | null>(null);
  const last = useRef<{ x: number; y: number } | null>(null);

  const [split, setSplit] = useState(50);
  const [zoom, setZoom] = useState(1);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const [mode, setMode] = useState<Mode>("banding");

  const clampPan = useCallback((p: { x: number; y: number }, z: number) => {
    const box = boxRef.current;
    if (!box) return p;
    const maxX = (box.clientWidth * (z - 1)) / 2;
    const maxY = (box.clientHeight * (z - 1)) / 2;
    return {
      x: Math.max(-maxX, Math.min(maxX, p.x)),
      y: Math.max(-maxY, Math.min(maxY, p.y)),
    };
  }, []);

  const pilihMode = useCallback((m: Mode) => {
    setMode(m);
    setSplit(m === "sebelum" ? 100 : m === "sesudah" ? 0 : 50);
  }, []);

  function onPointerDown(e: React.PointerEvent<HTMLDivElement>) {
    boxRef.current?.setPointerCapture(e.pointerId);
    pointers.current.set(e.pointerId, { x: e.clientX, y: e.clientY });
    last.current = { x: e.clientX, y: e.clientY };

    if (pointers.current.size === 2) {
      const [a, b] = [...pointers.current.values()];
      pinch.current = {
        dist: Math.hypot(a.x - b.x, a.y - b.y),
        zoom,
        cx: (a.x + b.x) / 2,
        cy: (a.y + b.y) / 2,
        panX: pan.x,
        panY: pan.y,
      };
    }
  }

  function onPointerMove(e: React.PointerEvent<HTMLDivElement>) {
    if (!pointers.current.has(e.pointerId)) return;
    pointers.current.set(e.pointerId, { x: e.clientX, y: e.clientY });

    if (pointers.current.size >= 2 && pinch.current) {
      const [a, b] = [...pointers.current.values()];
      const jarak = Math.hypot(a.x - b.x, a.y - b.y);
      const z = Math.max(
        ZOOM_MIN,
        Math.min(ZOOM_MAX, (pinch.current.zoom * jarak) / Math.max(1, pinch.current.dist)),
      );
      const cx = (a.x + b.x) / 2;
      const cy = (a.y + b.y) / 2;
      setZoom(z);
      setPan(
        clampPan(
          {
            x: pinch.current.panX + (cx - pinch.current.cx),
            y: pinch.current.panY + (cy - pinch.current.cy),
          },
          z,
        ),
      );
      return;
    }

    if (zoom > 1.02) {
      // Sudah diperbesar: satu jari menggeser gambar, bukan batas pembanding.
      const prev = last.current ?? { x: e.clientX, y: e.clientY };
      setPan((p) =>
        clampPan({ x: p.x + (e.clientX - prev.x), y: p.y + (e.clientY - prev.y) }, zoom),
      );
      last.current = { x: e.clientX, y: e.clientY };
      return;
    }

    const box = boxRef.current;
    if (!box) return;
    const r = box.getBoundingClientRect();
    const pct = ((e.clientX - r.left) / r.width) * 100;
    setSplit(Math.max(0, Math.min(100, pct)));
    setMode("banding");
  }

  function onPointerUp(e: React.PointerEvent<HTMLDivElement>) {
    pointers.current.delete(e.pointerId);
    if (pointers.current.size < 2) pinch.current = null;
    if (pointers.current.size === 0) last.current = null;
  }

  function ubahZoom(faktor: number) {
    const z = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, zoom * faktor));
    setZoom(z);
    setPan((p) => clampPan(p, z));
  }

  const geser = zoom > 1.02;

  return (
    <div className="flex flex-col gap-3">
      <div
        ref={boxRef}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerUp}
        className="relative touch-none select-none overflow-hidden rounded-lg border border-border bg-black"
        style={{ aspectRatio: `${ratio}`, maxHeight: "52vh", margin: "0 auto", width: "100%" }}
      >
        <div
          className="absolute inset-0"
          style={{
            transform: `translate(${pan.x}px, ${pan.y}px) scale(${zoom})`,
            transition: pointers.current.size === 0 ? "transform 120ms ease-out" : "none",
          }}
        >
          {/* eslint-disable-next-line @next/next/no-img-element -- berkas cache lokal, bukan aset Next */}
          <img
            src={after}
            alt="Foto setelah diperbesar"
            draggable={false}
            className="absolute inset-0 size-full object-contain"
          />
          {/* eslint-disable-next-line @next/next/no-img-element -- berkas cache lokal, bukan aset Next */}
          <img
            src={before}
            alt="Foto sebelum diperbesar"
            draggable={false}
            className="absolute inset-0 size-full object-contain"
            style={{ clipPath: `inset(0 ${100 - split}% 0 0)` }}
          />
        </div>

        {/* Label sisi */}
        <span className="pointer-events-none absolute left-3 top-3 rounded-full bg-black/60 px-2.5 py-1 text-caption text-white transition-opacity" style={{ opacity: split > 12 ? 1 : 0 }}>
          {labelBefore}
        </span>
        <span className="pointer-events-none absolute right-3 top-3 rounded-full bg-black/60 px-2.5 py-1 text-caption text-white transition-opacity" style={{ opacity: split < 88 ? 1 : 0 }}>
          {labelAfter}
        </span>

        {/* Batas pembanding */}
        <div
          className="pointer-events-none absolute inset-y-0 w-0.5 bg-white/90 shadow-[0_0_8px_rgba(0,0,0,0.5)]"
          style={{ left: `calc(${split}% - 1px)`, opacity: split > 1 && split < 99 ? 1 : 0 }}
        >
          <span className="absolute left-1/2 top-1/2 flex size-10 -translate-x-1/2 -translate-y-1/2 items-center justify-center rounded-full bg-white text-on-accent shadow-lg">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
              <path d="M9 6 4 12l5 6M15 6l5 6-5 6" />
            </svg>
          </span>
        </div>

        {geser ? (
          <button
            type="button"
            onClick={() => {
              setZoom(1);
              setPan({ x: 0, y: 0 });
            }}
            className="absolute bottom-3 right-3 rounded-full bg-black/70 px-3 py-1.5 text-caption text-white"
          >
            Perkecil
          </button>
        ) : null}
      </div>

      {/* Kontrol: tanpa gerakan geser pun tetap bisa membandingkan */}
      <div className="flex items-center gap-2">
        <div className="flex flex-1 rounded-full border border-border bg-surface p-1">
          {(
            [
              ["banding", "Bandingkan"],
              ["sebelum", labelBefore],
              ["sesudah", labelAfter],
            ] as const
          ).map(([nilai, teks]) => (
            <button
              key={nilai}
              type="button"
              onClick={() => pilihMode(nilai)}
              aria-pressed={mode === nilai}
              className={`h-9 flex-1 rounded-full text-caption transition-colors ${
                mode === nilai ? "bg-accent font-semibold text-on-accent" : "text-text-2"
              }`}
            >
              {teks}
            </button>
          ))}
        </div>
        <button
          type="button"
          onClick={() => ubahZoom(-0.5)}
          aria-label="Perkecil tampilan"
          className="flex size-11 items-center justify-center rounded-full border border-border bg-surface text-heading text-text-2"
        >
          −
        </button>
        <button
          type="button"
          onClick={() => ubahZoom(0.5)}
          aria-label="Perbesar tampilan"
          className="flex size-11 items-center justify-center rounded-full border border-border bg-surface text-heading text-text-2"
        >
          +
        </button>
      </div>

      <p className="text-center text-caption text-text-2">
        Geser untuk membandingkan · cubit untuk memperbesar
      </p>
    </div>
  );
}
