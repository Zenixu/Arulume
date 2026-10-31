"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Mascot, MASCOT_ROLE } from "@/components/mascot";
import {
  IconChevronRight,
  IconPhotoPlus,
  IconSettings,
  IconVideo,
} from "@/components/icons";
import { Enhancer, isNative, type HistoryEntry } from "@/plugins/enhancer";
import { bacaOnboarding } from "@/lib/settings";
import { useJobStore } from "@/lib/job-store";
import { useBatchStore } from "@/lib/batch-store";
import { Capacitor } from "@capacitor/core";

/**
 * Layar Beranda — Docs/design.md bagian 7.1.
 * Satu aksi utama ("Pilih Foto"), satu aksi sekunder ("Pilih Video", Fase 2).
 */
export default function Beranda() {
  const router = useRouter();
  const setPhoto = useJobStore((s) => s.setPhoto);
  const siapkanBatch = useBatchStore((s) => s.siapkan);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [terbaru, setTerbaru] = useState<HistoryEntry[]>([]);
  /** Job video yang sedang jalan atau terhenti — tampil sebagai kartu. */
  const [video, setVideo] = useState<{
    aktif: boolean;
    percent: number;
  } | null>(null);

  // Kalau proses mati saat pemilih foto di depan, hasilnya diselamatkan plugin
  // dan diambil di sini supaya alurnya tidak putus.
  useEffect(() => {
    if (!isNative()) return;
    let batal = false;
    (async () => {
      try {
        const { items } = await Enhancer.getPendingPick();
        if (batal || items.length === 0) return;
        // Proses bisa mati saat pemilih di depan (MIUI). Kalau yang diselamatkan
        // lebih dari satu foto, lanjutkan ke antrian batch, bukan editor tunggal.
        if (items.length > 1) {
          siapkanBatch(items);
          router.push("/batch");
          return;
        }
        setPhoto(items[0]);
        router.push("/editor");
      } catch {
        // Diamkan: tidak ada yang tertunda.
      }
    })();
    return () => {
      batal = true;
    };
  }, [router, setPhoto, siapkanBatch]);

  // Video yang sedang jalan atau terhenti: beri jalan pintas ke layarnya,
  // supaya proses yang berhenti tidak "hilang" tanpa jejak.
  useEffect(() => {
    if (!isNative()) return;
    let batal = false;
    async function cek() {
      try {
        const s = await Enhancer.getVideoStatus();
        if (batal) return;
        if (s?.aktif || s?.bisaLanjut) {
          setVideo({ aktif: !!s.aktif, percent: s.percent ?? 0 });
        } else {
          setVideo(null);
        }
      } catch {
        /* diamkan */
      }
    }
    cek();
    const t = setInterval(cek, 4000);
    const onVis = () => {
      if (document.visibilityState === "visible") cek();
    };
    document.addEventListener("visibilitychange", onVis);
    return () => {
      batal = true;
      clearInterval(t);
      document.removeEventListener("visibilitychange", onVis);
    };
  }, []);

  // Onboarding sekali jalan (TASKS 1.3): tampilkan sebelum apa pun yang lain.
  useEffect(() => {
    if (!bacaOnboarding()) router.replace("/mulai");
  }, [router]);

  // Tiga hasil terakhir untuk bagian "Terbaru" (PRD FR-08).
  useEffect(() => {
    if (!isNative()) return;
    let batal = false;
    (async () => {
      try {
        const { items } = await Enhancer.listHistory();
        if (!batal) setTerbaru(items.slice(0, 3));
      } catch {
        // Riwayat belum ada — biarkan bagian kosong.
      }
    })();
    return () => {
      batal = true;
    };
  }, []);

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

  /** Batch (PRD FR-11): pilih beberapa foto, lalu atur di layar Beberapa foto. */
  async function pilihBanyak() {
    setNotice(null);
    if (!isNative()) {
      setNotice("Pemilih foto baru aktif di aplikasi Android.");
      return;
    }
    setBusy(true);
    try {
      const { items, skipped } = await Enhancer.pickMedia({ type: "image", multiple: true });
      if (items.length === 0) return;
      siapkanBatch(items);
      if (skipped && skipped > 0) {
        setNotice(`${skipped} foto dilewati karena tidak bisa dibaca.`);
      }
      router.push("/batch");
    } catch (e) {
      const err = e as { code?: string; message?: string };
      const code = err.code ?? "";
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
            href="/pengaturan"
            aria-label="Pengaturan"
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
          <h1 className="text-title">Selamat datang di Arulume</h1>
          <p className="text-body text-text-2">
            Perjelas foto dan video lama, semuanya di HP-mu.
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
          onClick={pilihBanyak}
          disabled={busy}
          className="text-caption text-accent-text underline-offset-4 transition-opacity active:opacity-70 disabled:opacity-60"
        >
          Pilih beberapa foto sekaligus
        </button>

        {video ? (
          <Link
            href="/video"
            className="flex w-full items-center justify-between rounded-md border border-accent bg-surface px-4 py-3 transition-colors active:bg-surface-2"
          >
            <span className="flex items-center gap-2.5">
              <IconVideo size={20} className="text-accent-text" />
              <span className="flex flex-col text-left">
                <span className="text-body text-text">
                  {video.aktif ? "Video sedang diproses" : "Video terhenti"}
                </span>
                <span className="text-caption text-text-2">
                  {video.percent}% selesai ·{" "}
                  {video.aktif ? "ketuk untuk melihat" : "ketuk untuk melanjutkan"}
                </span>
              </span>
            </span>
            <IconChevronRight size={18} className="text-text-2" />
          </Link>
        ) : null}

        <Link
          href="/video"
          className="flex h-[52px] w-full items-center justify-between rounded-md border border-border bg-surface px-4 transition-colors active:bg-surface-2"
        >
          <span className="flex items-center gap-2.5">
            <IconVideo size={20} className="text-text-2" />
            <span className="text-body text-text">Pilih Video</span>
          </span>
          <IconChevronRight size={18} className="text-text-2" />
        </Link>

        {notice ? (
          <p
            role="status"
            className="rounded-sm bg-surface-2 px-3 py-2 text-caption text-text-2"
          >
            {notice}
          </p>
        ) : null}
      </section>

      {/* Terbaru — tiga hasil terakhir (PRD FR-08) */}
      <section className="flex flex-col gap-2 pb-8">
        <div className="flex items-center justify-between">
          <h2 className="text-caption uppercase tracking-wide text-text-2">
            Terbaru
          </h2>
          {terbaru.length > 0 ? (
            <Link href="/riwayat" className="text-caption text-accent-text">
              Lihat semua
            </Link>
          ) : null}
        </div>

        {terbaru.length === 0 ? (
          <div className="flex items-center gap-3 rounded-md border border-border bg-surface px-3 py-3">
            <Mascot mood="mengendap" size={40} className="text-text-2" />
            <div className="flex flex-col">
              <span className="text-body text-text">Belum ada cerita</span>
              <span className="text-caption text-text-2">
                Foto yang diproses akan muncul di sini.
              </span>
            </div>
          </div>
        ) : (
          <div className="grid grid-cols-3 gap-2.5">
            {terbaru.map((e) => (
              <Link
                key={e.id}
                href={`/riwayat/lihat?id=${encodeURIComponent(e.id)}`}
                className="relative flex aspect-square overflow-hidden rounded-md border border-border bg-surface-2"
              >
                {e.hasResult ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img
                    src={Capacitor.convertFileSrc(e.afterPreviewPath)}
                    alt={`Hasil ${e.scale} kali`}
                    className="size-full object-cover"
                  />
                ) : (
                  <span className="flex size-full items-center justify-center px-2 text-center text-caption text-text-2">
                    sudah dibersihkan
                  </span>
                )}
                <span className="absolute left-1.5 top-1.5 rounded-full bg-bg/80 px-1.5 py-0.5 text-caption text-text">
                  {e.scale}×
                </span>
              </Link>
            ))}
          </div>
        )}
      </section>
    </main>
  );
}
