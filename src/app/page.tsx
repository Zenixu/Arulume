"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Mascot, MASCOT_ROLE } from "@/components/mascot";
import {
  IconPhotoPlus,
  IconSettings,
  IconVideo,
} from "@/components/icons";
import { Enhancer, isNative } from "@/plugins/enhancer";
import { useJobStore } from "@/lib/job-store";

/**
 * Layar Beranda — Docs/design.md bagian 7.1.
 * Satu aksi utama ("Pilih Foto"), satu aksi sekunder ("Pilih Video", Fase 2).
 */
export default function Beranda() {
  const router = useRouter();
  const setPhoto = useJobStore((s) => s.setPhoto);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // Kalau proses mati saat pemilih foto di depan, hasilnya diselamatkan plugin
  // dan diambil di sini supaya alurnya tidak putus.
  useEffect(() => {
    if (!isNative()) return;
    let batal = false;
    (async () => {
      try {
        const { items } = await Enhancer.getPendingPick();
        if (batal || items.length === 0) return;
        setPhoto(items[0]);
        router.push("/editor");
      } catch {
        // Diamkan: tidak ada yang tertunda.
      }
    })();
    return () => {
      batal = true;
    };
  }, [router, setPhoto]);

  async function pilihFoto() {
    setNotice(null);
    if (!isNative()) {
      setNotice(
        "Pemilih foto baru aktif di aplikasi Android. Jalankan lewat Capacitor untuk mencoba.",
      );
      return;
    }
    setBusy(true);
    try {
      const { items } = await Enhancer.pickMedia({ type: "image" });
      if (items.length === 0) return;
      setPhoto(items[0]);
      router.push("/editor");
    } catch (e) {
      const err = e as { code?: string; message?: string };
      const code = err.code ?? "";
      // Jangan bocorkan kode internal Capacitor (UNIMPLEMENTED) ke layar —
      // AGENTS.md: setiap error memakai ErrorCode dari PRD bagian 10.
      if (code === "E_PICK_CANCELLED") {
        setNotice(null);
      } else if (code === "UNIMPLEMENTED" || /unimplemented/i.test(err.message ?? "")) {
        setNotice("Pemilih foto belum aktif di build ini.");
      } else {
        setNotice(`Gagal memilih foto (${code || "E_UNKNOWN"}).`);
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="mx-auto flex min-h-dvh w-full max-w-[480px] flex-col px-5">
      {/* Bar atas: nama + pengaturan */}
      <header className="flex h-16 items-center justify-between">
        <span className="text-title tracking-tight">Arulume</span>
        <div className="flex items-center gap-2">
          <Link
            href="/uji"
            aria-label="Info perangkat (uji Fase 0)"
            className="flex size-12 items-center justify-center rounded-full text-text-2 transition-colors hover:text-text"
          >
            <IconSettings />
          </Link>
        </div>
      </header>

      {/* Maskot + tagline */}
      <section className="flex flex-1 flex-col items-center justify-center gap-4 py-6 text-center">
        <Mascot mood={MASCOT_ROLE.netral} size={160} animate />
        <div className="flex flex-col gap-1.5">
          <h1 className="text-title">Kembalikan cahaya fotomu</h1>
          <p className="text-body text-text-2">
            Semua diproses di HP. Tanpa unggah.
          </p>
        </div>
      </section>

      {/* Aksi */}
      <section className="flex flex-col gap-3 pb-5">
        <button
          type="button"
          onClick={pilihFoto}
          disabled={busy}
          className="flex h-14 w-full items-center justify-center gap-2.5 rounded-md bg-accent text-on-accent transition-opacity active:opacity-90 disabled:opacity-60"
        >
          <IconPhotoPlus size={22} />
          <span className="text-heading">Pilih Foto</span>
        </button>

        <button
          type="button"
          disabled
          className="flex h-[52px] w-full items-center justify-between rounded-md border border-border bg-surface px-4 opacity-70"
        >
          <span className="flex items-center gap-2.5">
            <IconVideo size={20} className="text-text-2" />
            <span className="text-body text-text">Pilih Video</span>
          </span>
          <span className="rounded-full bg-surface-2 px-2 py-0.5 text-caption text-text-2">
            Segera
          </span>
        </button>

        {notice ? (
          <p
            role="status"
            className="rounded-sm bg-surface-2 px-3 py-2 text-caption text-text-2"
          >
            {notice}
          </p>
        ) : null}
      </section>

      {/* Terbaru — state kosong */}
      <section className="flex flex-col gap-2 pb-8">
        <h2 className="text-caption uppercase tracking-wide text-text-2">
          Terbaru
        </h2>
        <div className="flex items-center gap-3 rounded-md border border-border bg-surface px-3 py-3">
          <Mascot mood={MASCOT_ROLE.mengintip} size={40} className="text-text-2" />
          <div className="flex flex-col">
            <span className="text-body text-text">Belum ada cerita</span>
            <span className="text-caption text-text-2">
              Foto yang diproses akan muncul di sini.
            </span>
          </div>
        </div>
      </section>
    </main>
  );
}
