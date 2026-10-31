"use client";

/**
 * Layar Editor — Docs/design.md bagian 7.2.
 *
 * Fase 1 sengaja hanya menawarkan satu pilihan yang sudah terbukti di perangkat
 * (2×, mesin CPU, tile 256). Preset lain menunggu hasil optimasi benchmark §5:
 * di Redmi Note 8, 1024² sudah butuh 33 detik dan 4× empat kali lipat lebih berat.
 */
import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Capacitor } from "@capacitor/core";
import type { PluginListenerHandle } from "@capacitor/core";
import { Mascot, MASCOT_ROLE } from "@/components/mascot";
import { IconArrowLeft } from "@/components/icons";
import { Enhancer, isNative } from "@/plugins/enhancer";
import { pesanError, useJobStore } from "@/lib/job-store";
import { bacaFormat, bacaSkalaBawaan, type Format } from "@/lib/settings";

const STAGE_LABEL: Record<string, string> = {
  decoding: "Membaca foto",
  enhancing: "Memperbesar",
  encoding: "Menyiapkan hasil",
  saving: "Menyiapkan hasil",
};

export default function Editor() {
  const router = useRouter();
  const { photo, setResult, setFailure } = useJobStore();

  const [percent, setPercent] = useState(0);
  const [stage, setStage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [skala, setSkala] = useState<2 | 4>(2);
  const [format, setFormat] = useState<Format>("jpeg");

  // Skala bawaan dari Pengaturan (PRD FR-09); dibaca setelah mount supaya
  // ekspor statis tidak menyentuh localStorage di server.
  useEffect(() => {
    setSkala(bacaSkalaBawaan());
    setFormat(bacaFormat());
  }, []);

  const jobIdRef = useRef<string | null>(null);
  const handles = useRef<PluginListenerHandle[]>([]);

  // Kalau layar ini dibuka tanpa foto (mis. setelah proses mati), kembali ke Beranda.
  useEffect(() => {
    if (!photo) router.replace("/");
  }, [photo, router]);

  const lepasListener = useCallback(async () => {
    const list = handles.current;
    handles.current = [];
    for (const h of list) {
      try {
        await h.remove();
      } catch {
        /* sudah lepas */
      }
    }
  }, []);

  useEffect(() => {
    return () => {
      void lepasListener();
    };
  }, [lepasListener]);

  /** Memasang tiga listener job. Dipakai saat memulai dan saat memulihkan. */
  const pasangListener = useCallback(async () => {
    handles.current = [
      await Enhancer.addListener("progress", (e) => {
        setPercent(e.percent);
        setStage(e.stage);
      }),
      await Enhancer.addListener("completed", async (e) => {
        await lepasListener();
        setResult(e);
        setBusy(false);
        router.push("/hasil");
      }),
      await Enhancer.addListener("failed", async (e) => {
        await lepasListener();
        setFailure(e);
        setBusy(false);
        setStage(null);
        setError(pesanError(e.code));
      }),
    ];
  }, [lepasListener, router, setFailure, setResult]);

  /**
   * Pemulihan: proses berjalan di layanan latar, jadi layar ini bisa dibuka
   * ulang saat job masih hidup (atau sudah selesai) tanpa kehilangan progres.
   */
  useEffect(() => {
    if (!photo || !isNative()) return;
    let dibatalkan = false;
    (async () => {
      try {
        const s = await Enhancer.getJobStatus();
        if (dibatalkan || !s.jobId) return;
        if (s.aktif) {
          jobIdRef.current = s.jobId;
          setBusy(true);
          setStage(s.stage ?? "decoding");
          setPercent(s.percent ?? 0);
          await pasangListener();
        } else if (s.stage === "done") {
          router.replace(`/riwayat/lihat?id=${encodeURIComponent(s.jobId)}`);
        }
      } catch {
        /* tidak ada job sebelumnya */
      }
    })();
    return () => {
      dibatalkan = true;
    };
  }, [photo, pasangListener, router]);

  async function proses() {
    if (!photo || busy) return;
    setError(null);
    setBusy(true);
    setPercent(0);
    setStage("decoding");

    // Listener dipasang SEBELUM job dimulai supaya tidak ada event yang terlewat.
    await pasangListener();

    // Notifikasi progres butuh izin di Android 13+. Ditolak pun proses tetap
    // jalan; hanya bar di notifikasi yang tidak muncul.
    if (isNative()) {
      try {
        await Enhancer.mintaIzinNotifikasi();
      } catch {
        /* izin opsional */
      }
    }

    try {
      const { jobId } = await Enhancer.startEnhanceImage({
        uri: photo.uri,
        model: "",
        scale: skala,
        tileSize: 0,
        useGpu: false,
        keepExif: true,
        output: { format, quality: 95, target: "gallery" },
        maxOutputLongSide: 2048,
        autoDownscaleInput: true,
      });
      jobIdRef.current = jobId;
    } catch (e) {
      await lepasListener();
      const code = (e as { code?: string }).code ?? "E_UNKNOWN";
      setBusy(false);
      setStage(null);
      setError(pesanError(code));
    }
  }

  async function batalkan() {
    const jobId = jobIdRef.current;
    if (!jobId) return;
    try {
      await Enhancer.cancelJob({ jobId });
    } catch {
      /* biarkan job selesai sendiri */
    }
  }

  if (!photo) return null;

  const preview = photo.previewPath ? Capacitor.convertFileSrc(photo.previewPath) : null;
  const judul = busy ? (STAGE_LABEL[stage ?? ""] ?? "Memproses") : "Siap diperbesar";

  // Sama dengan plugin: maxOutputLongSide 2048 ÷ skala = px sisi terpanjang sumber.
  const MASUK = Math.round(2048 / skala);
  const k = Math.min(1, MASUK / Math.max(photo.width, photo.height));
  const keluaran = {
    w: Math.round(photo.width * k) * skala,
    h: Math.round(photo.height * k) * skala,
  };

  return (
    <main className="mx-auto flex min-h-dvh w-full max-w-[480px] flex-col px-5">
      <header className="flex h-16 items-center gap-1">
        <button
          type="button"
          onClick={() => router.back()}
          aria-label="Kembali"
          className="-ml-3 flex size-12 items-center justify-center rounded-full text-text-2 transition-colors hover:text-text"
        >
          <IconArrowLeft />
        </button>
        <span className="text-heading">{judul}</span>
      </header>

      {/* Foto terpilih */}
      <section className="flex flex-col gap-3">
        <div className="overflow-hidden rounded-lg border border-border bg-surface-2">
          {preview ? (
            // eslint-disable-next-line @next/next/no-img-element -- berkas lokal WebView, bukan aset Next
            <img
              src={preview}
              alt="Foto yang dipilih"
              className="max-h-[46vh] w-full object-contain"
            />
          ) : (
            <div className="flex h-48 items-center justify-center text-caption text-text-2">
              Pratinjau tidak tersedia
            </div>
          )}
        </div>

        <div className="flex items-center justify-between rounded-md border border-border bg-surface px-4 py-3">
          <div className="flex flex-col">
            <span className="text-body text-text">
              {photo.width} × {photo.height}
            </span>
            <span className="text-caption text-text-2">
              Diproses pada {MASUK} px sisi terpanjang → {keluaran.w} × {keluaran.h}
            </span>
            {k < 1 ? (
              <span className="text-caption text-warning">
                Foto dikecilkan dulu supaya hasil pas 2048 px.
              </span>
            ) : null}
          </div>
          <div
            role="radiogroup"
            aria-label="Ukuran hasil"
            className="flex rounded-full border border-border bg-surface-2 p-0.5"
          >
            {([2, 4] as const).map((n) => (
              <button
                key={n}
                type="button"
                role="radio"
                aria-checked={skala === n}
                disabled={busy}
                onClick={() => setSkala(n)}
                className={`h-8 rounded-full px-3.5 text-caption transition-colors disabled:opacity-50 ${
                  skala === n
                    ? "bg-accent font-semibold text-on-accent"
                    : "text-text-2"
                }`}
              >
                {n}×
              </button>
            ))}
          </div>
        </div>

        {busy ? (
          <div className="flex flex-col gap-2 rounded-md border border-border bg-surface px-4 py-3">
            <div className="flex items-center justify-between text-caption text-text-2">
              <span>{STAGE_LABEL[stage ?? ""] ?? "Memproses"}</span>
              <span>{percent}%</span>
            </div>
            <div
              role="progressbar"
              aria-valuenow={percent}
              aria-valuemin={0}
              aria-valuemax={100}
              className="h-2 w-full overflow-hidden rounded-full bg-surface-2"
            >
              <div
                className="h-full rounded-full bg-accent transition-[width] duration-500"
                style={{ width: `${Math.max(4, percent)}%` }}
              />
            </div>
            <span className="text-caption text-text-2">
              Foto besar bisa butuh puluhan detik. Layar boleh ditinggal, proses tetap jalan.
            </span>
          </div>
        ) : null}

        {error ? (
          <p role="alert" className="rounded-sm bg-surface-2 px-3 py-2 text-caption text-danger">
            {error}
          </p>
        ) : null}
      </section>

      <div className="flex-1" />

      <section className="flex flex-col gap-3 pb-6 pt-4">
        {busy ? (
          <button
            type="button"
            onClick={batalkan}
            className="flex h-14 w-full items-center justify-center rounded-md border border-border bg-surface text-heading text-text"
          >
            Batalkan
          </button>
        ) : (
          <button
            type="button"
            onClick={proses}
            disabled={!isNative()}
            className="flex h-14 w-full items-center justify-center gap-2.5 rounded-md bg-accent text-on-accent transition-opacity active:opacity-90 disabled:opacity-60"
          >
            <span className="text-heading">Perbesar Sekarang</span>
          </button>
        )}

        {!isNative() ? (
          <p className="text-center text-caption text-text-2">
            Pemrosesan hanya berjalan di aplikasi Android.
          </p>
        ) : (
          <p className="flex items-center justify-center gap-1.5 text-center text-caption text-text-2">
            <Mascot mood={MASCOT_ROLE.jejak} size={18} />
            Diproses di HP ini, tanpa unggah.
          </p>
        )}
      </section>
    </main>
  );
}
