"use client";

/**
 * Onboarding satu layar (Docs/TASKS.md 1.3).
 *
 * Muncul sekali saat aplikasi pertama kali dibuka. Isinya janji inti produk:
 * offline, privat, dan hasil hanya masuk galeri kalau kamu menekan Simpan.
 * Tanpa akun, tanpa izin tambahan, tanpa jaringan.
 */
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Mascot } from "@/components/mascot";
import { IconCheck, IconLock, IconSparkle } from "@/components/icons";
import { bacaOnboarding, simpanOnboarding } from "@/lib/settings";

const POIN = [
  {
    ikon: IconLock,
    judul: "Tidak pernah diunggah",
    isi: "Foto diproses di HP ini. Aplikasi bahkan tidak punya izin internet.",
  },
  {
    ikon: IconSparkle,
    judul: "Hasil selalu kamu putuskan",
    isi: "Hasil muncul dulu untuk dibandingkan. Masuk galeri hanya kalau kamu menekan Simpan.",
  },
  {
    ikon: IconCheck,
    judul: "Gratis, tanpa akun",
    isi: "Tanpa daftar, tanpa langganan, tanpa iklan.",
  },
];

export default function OnboardingPage() {
  const router = useRouter();
  const [siap, setSiap] = useState(false);

  useEffect(() => {
    // Sudah pernah melihat? Jangan tahan di sini.
    if (bacaOnboarding()) router.replace("/");
    else setSiap(true);
  }, [router]);

  function mulai() {
    simpanOnboarding();
    router.replace("/");
  }

  if (!siap) return <main className="min-h-dvh bg-bg" />;

  return (
    <main className="mx-auto flex min-h-dvh w-full max-w-[480px] flex-col px-5">
      <header className="flex h-16 items-center">
        <span className="text-caption uppercase tracking-wide text-text-2">
          Arulume
        </span>
      </header>

      <section className="flex flex-1 flex-col justify-center gap-6 pb-8">
        <div className="flex flex-col items-center gap-4 text-center">
          <Mascot mood="mata-bulat" size={132} />
          <h1 className="text-title">Selamat datang di Arulume</h1>
          <p className="max-w-[34ch] text-body text-text-2">
            Foto lama, buram, atau kecil bisa dibuat lebih jelas — semuanya di HP
            ini.
          </p>
        </div>

        <ul className="flex flex-col gap-3">
          {POIN.map((p) => {
            const Ikon = p.ikon;
            return (
              <li
                key={p.judul}
                className="flex items-start gap-3 rounded-md border border-border bg-surface p-3.5"
              >
                <span className="mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-full bg-surface-2 text-accent-text">
                  <Ikon size={18} />
                </span>
                <span className="flex flex-col gap-0.5">
                  <span className="text-body text-text">{p.judul}</span>
                  <span className="text-caption text-text-2">{p.isi}</span>
                </span>
              </li>
            );
          })}
        </ul>
      </section>

      <section className="flex flex-col gap-3 pb-8">
        <button
          type="button"
          onClick={mulai}
          className="flex h-14 w-full items-center justify-center rounded-md bg-accent text-on-accent"
        >
          <span className="text-heading">Mulai</span>
        </button>
        <p className="text-center text-caption text-text-2">
          Aruthtale · dibuat untuk foto yang ingin kamu selamatkan
        </p>
      </section>
    </main>
  );
}
