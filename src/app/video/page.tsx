"use client";

/**
 * Layar Video — Docs/design.md & Docs/TASKS.md butir 2.2.
 *
 * Alurnya sama seperti foto: pilih → atur → proses → lihat hasil → simpan
 * manual. Bedanya, kerja berat berjalan di layanan latar (`EnhancerService`)
 * supaya user boleh menutup layar; progres tetap dikirim ke sini lewat event
 * `progress` / `videoDone`.
 */
import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Capacitor } from "@capacitor/core";
import type { PluginListenerHandle } from "@capacitor/core";
import { Mascot } from "@/components/mascot";
import { IconArrowLeft, IconDownload, IconVideo } from "@/components/icons";
import {
  Enhancer,
  isNative,
  type PickedMedia,
  type VideoDoneEvent,
} from "@/plugins/enhancer";

type Tahap = "pilih" | "atur" | "proses" | "terhenti" | "selesai";

const UKURAN = [
  { sisi: 1280, label: "720p", ket: "Ringan, cepat" },
  { sisi: 1920, label: "1080p", ket: "Paling tajam" },
] as const;

function durasi(ms: number): string {
  const total = Math.round(ms / 1000);
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${m}:${String(s).padStart(2, "0")}`;
}

function ukuranByte(b: number): string {
  if (b >= 1024 * 1024) return `${(b / (1024 * 1024)).toFixed(1)} MB`;
  return `${Math.max(1, Math.round(b / 1024))} KB`;
}

export default function Video() {
  const router = useRouter();

  const [tahap, setTahap] = useState<Tahap>("pilih");
  const [klip, setKlip] = useState<PickedMedia | null>(null);
  const [sisi, setSisi] = useState<1280 | 1920>(1280);
  const [tajam, setTajam] = useState(true);
  const [persen, setPersen] = useState(0);
  const [frame, setFrame] = useState(0);
  const [totalFrame, setTotalFrame] = useState(0);
  const [hasil, setHasil] = useState<VideoDoneEvent | null>(null);
  const [tersimpan, setTersimpan] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [sibuk, setSibuk] = useState(false);
  /** Ada job terhenti di disk yang bisa diteruskan. */
  const [bisaLanjut, setBisaLanjut] = useState(false);

  const jobIdRef = useRef<string | null>(null);
  const handles = useRef<PluginListenerHandle[]>([]);

  const lepas = useCallback(async () => {
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

  const pasang = useCallback(async () => {
    await lepas();
    handles.current = [
      await Enhancer.addListener("progress", (e) => {
        if (e.kind !== "video") return;
        if (jobIdRef.current && e.jobId !== jobIdRef.current) return;
        jobIdRef.current = e.jobId;
        setTahap("proses");
        setPersen(e.percent);
        setFrame(e.frame ?? 0);
        setTotalFrame(e.totalFrame ?? 0);
      }),
      await Enhancer.addListener("videoDone", async (e) => {
        if (jobIdRef.current && e.jobId !== jobIdRef.current) return;
        await lepas();
        setHasil(e);
        setPersen(100);
        setSibuk(false);
        setTahap("selesai");
      }),
      await Enhancer.addListener("videoFailed", async (e) => {
        if (jobIdRef.current && e.jobId !== jobIdRef.current) return;
        await lepas();
        setSibuk(false);
        setPersen(e.percent ?? 0);
        setFrame(e.frame ?? 0);
        setTotalFrame(e.totalFrame ?? 0);
        // Kalau masih bisa dilanjutkan, jangan buang pekerjaan: tampilkan
        // layar "terhenti" dengan tombol Lanjutkan.
        if (e.bisaLanjut) {
          setBisaLanjut(true);
          setTahap("terhenti");
          setError(null);
          return;
        }
        setBisaLanjut(false);
        setTahap(klip ? "atur" : "pilih");
        setError(
          e.code === "E_CANCELLED"
            ? "Proses dihentikan. Video asli tidak berubah."
            : e.message || "Video gagal diproses.",
        );
      }),
    ];
  }, [klip, lepas]);

  useEffect(() => {
    return () => {
      void lepas();
    };
  }, [lepas]);

  /** Pulihkan keadaan kalau layar dibuka ulang saat job masih hidup. */
  useEffect(() => {
    if (!isNative()) return;
    let hidup = true;
    (async () => {
      try {
        const s = await Enhancer.getVideoStatus();
        if (!hidup || !s.jobId) return;
        setPersen(s.percent ?? 0);
        setFrame(s.frame ?? 0);
        setTotalFrame(s.totalFrame ?? 0);
        if (s.aktif) {
          jobIdRef.current = s.jobId;
          setTahap("proses");
          await pasang();
        } else if (s.bisaLanjut) {
          // Job terhenti (mis. aplikasi ditutup paksa): tawarkan lanjutkan.
          setBisaLanjut(true);
          setTahap("terhenti");
        }
      } catch {
        /* tidak ada job sebelumnya */
      }
    })();
    return () => {
      hidup = false;
    };
    // pasang sengaja tidak masuk daftar: hanya untuk pemulihan awal
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function pilihKlip() {
    setError(null);
    if (!isNative()) {
      setError("Pemilih video baru aktif di aplikasi Android.");
      return;
    }
    setSibuk(true);
    try {
      const { items } = await Enhancer.pickMedia({ type: "video", multiple: false });
      const satu = items[0];
      if (!satu) return;
      setKlip(satu);
      setHasil(null);
      setTersimpan(null);
      // Video kecil (≤ 640 px) tetap dinaikkan ke 720p; yang sudah besar
      // ditawarkan 1080p supaya tidak turun kualitas.
      setSisi(Math.max(satu.width, satu.height) >= 1600 ? 1920 : 1280);
      setTahap("atur");
    } catch (e) {
      const err = e as { code?: string; message?: string };
      if (err.code !== "E_PICK_CANCELLED") {
        setError(
          err.code === "E_UNSUPPORTED_FORMAT"
            ? "Format video ini belum didukung. Coba MP4 biasa."
            : `Gagal memilih video (${err.code ?? "E_UNKNOWN"}).`,
        );
      }
    } finally {
      setSibuk(false);
    }
  }

  async function mulai() {
    if (!klip || sibuk) return;
    setError(null);
    setSibuk(true);
    setPersen(0);
    setFrame(0);
    setTotalFrame(0);
    setBisaLanjut(false);
    await pasang();
    try {
      const { jobId } = await Enhancer.prosesVideo({
        uri: klip.uri,
        target: sisi,
        tajam,
      });
      jobIdRef.current = jobId;
      setTahap("proses");
    } catch (e) {
      await lepas();
      setSibuk(false);
      const code = (e as { code?: string }).code ?? "E_UNKNOWN";
      setError(`Tidak bisa memulai proses video (${code}).`);
    }
  }

  /** Jeda: kerja berhenti, potongan disimpan, bisa dilanjutkan kapan saja. */
  async function hentikan() {
    setSibuk(true);
    try {
      await Enhancer.hentikanVideo();
    } catch {
      /* biarkan selesai sendiri */
    } finally {
      setSibuk(false);
    }
  }

  /** Teruskan job yang terhenti dari frame terakhir yang tercatat. */
  async function lanjutkan() {
    if (sibuk) return;
    setError(null);
    setSibuk(true);
    await pasang();
    try {
      const r = await Enhancer.lanjutkanVideo();
      jobIdRef.current = r.jobId;
      setBisaLanjut(false);
      setPersen(r.percent ?? 0);
      setFrame(r.frame ?? 0);
      setTotalFrame(r.totalFrame ?? 0);
      setTahap("proses");
    } catch (e) {
      await lepas();
      const code = (e as { code?: string }).code ?? "E_UNKNOWN";
      setError(
        code === "E_TIDAK_ADA"
          ? "Tidak ada proses yang bisa dilanjutkan."
          : `Gagal melanjutkan (${code}).`,
      );
    } finally {
      setSibuk(false);
    }
  }

  async function simpan() {
    if (!hasil) return;
    setError(null);
    setSibuk(true);
    try {
      const r = await Enhancer.saveVideoToGallery({ path: hasil.path });
      setTersimpan(r.uri);
    } catch (e) {
      const code = (e as { code?: string }).code ?? "E_UNKNOWN";
      setError(
        code === "E_SAVE_FAILED"
          ? "Gagal menyimpan ke galeri. Pastikan ruang penyimpanan cukup."
          : `Gagal menyimpan (${code}).`,
      );
    } finally {
      setSibuk(false);
    }
  }

  async function bagikan() {
    if (!tersimpan) return;
    try {
      await Enhancer.shareResult({ uri: tersimpan, mime: "video/mp4" });
    } catch {
      setError("Belum bisa membagikan. Coba dari aplikasi Galeri.");
    }
  }

  const sumberVideo = hasil ? Capacitor.convertFileSrc(hasil.path) : null;
  const potongan = klip?.previewPath ? Capacitor.convertFileSrc(klip.previewPath) : null;
  const lamaProses = hasil ? Math.max(1, Math.round(hasil.ms / 1000)) : 0;

  return (
    <main className="mx-auto flex min-h-dvh w-full max-w-[480px] flex-col px-5">
      <header className="flex h-16 items-center gap-1">
        <button
          type="button"
          onClick={() => router.replace("/")}
          aria-label="Kembali"
          className="-ml-3 flex size-12 items-center justify-center rounded-full text-text-2 transition-colors hover:text-text"
        >
          <IconArrowLeft />
        </button>
        <span className="text-heading">
          {tahap === "selesai"
            ? "Hasil video"
            : tahap === "terhenti"
              ? "Ekstrak video"
              : "Perjelas video"}
        </span>
      </header>

      {tahap === "pilih" ? (
        <section className="flex flex-1 flex-col items-center justify-center gap-4 py-8 text-center">
          <Mascot mood="mengintip" size={140} animate />
          <div className="flex flex-col gap-1.5">
            <h1 className="text-title">Pilih video dulu</h1>
            <p className="text-body text-text-2">
              Video pendek paling enak. Hasilnya jadi HD, jadi tidak pecah saat
              diunggah ke WhatsApp atau Instagram.
            </p>
          </div>
          <button
            type="button"
            onClick={pilihKlip}
            disabled={sibuk}
            className="mt-2 flex h-14 w-full items-center justify-center gap-2.5 rounded-md bg-accent text-on-accent transition-opacity active:opacity-90 disabled:opacity-60"
          >
            <IconVideo size={22} />
            <span className="text-heading">Pilih Video</span>
          </button>
          {error ? (
            <p role="alert" className="text-caption text-danger">
              {error}
            </p>
          ) : null}
        </section>
      ) : null}

      {klip && tahap !== "pilih" ? (
        <section className="flex flex-col gap-3">
          {tahap === "selesai" && sumberVideo ? (
            <div className="overflow-hidden rounded-lg border border-border bg-black">
              {/* eslint-disable-next-line jsx-a11y/media-has-caption */}
              <video
                src={sumberVideo}
                controls
                playsInline
                className="max-h-[52vh] w-full bg-black object-contain"
              />
            </div>
          ) : (
            <div className="relative overflow-hidden rounded-lg border border-border bg-surface-2">
              {potongan ? (
                // eslint-disable-next-line @next/next/no-img-element -- berkas lokal WebView
                <img
                  src={potongan}
                  alt="Cuplikan video yang dipilih"
                  className="max-h-[42vh] w-full object-contain"
                />
              ) : (
                <div className="flex h-44 items-center justify-center text-caption text-text-2">
                  Cuplikan tidak tersedia
                </div>
              )}
              <span className="absolute bottom-2 right-2 rounded-full bg-bg/85 px-2 py-0.5 text-caption text-text">
                {durasi(klip.durationMs ?? 0)}
              </span>
            </div>
          )}

          <div className="flex flex-col gap-1 rounded-md border border-border bg-surface px-4 py-3">
            <span className="truncate text-body text-text">{klip.name}</span>
            <span className="text-caption text-text-2">
              {klip.width} × {klip.height} · {ukuranByte(klip.sizeBytes)}
              {hasil ? ` → ${hasil.width} × ${hasil.height} · ${ukuranByte(hasil.bytes)}` : ""}
            </span>
          </div>
        </section>
      ) : null}

      {tahap === "atur" ? (
        <section className="flex flex-col gap-3 pt-4">
          <div className="flex flex-col gap-2 rounded-md border border-border bg-surface px-4 py-3">
            <span className="text-caption uppercase tracking-wide text-text-2">
              Ukuran hasil
            </span>
            <div role="radiogroup" aria-label="Ukuran hasil" className="flex gap-2">
              {UKURAN.map((u) => (
                <button
                  key={u.sisi}
                  type="button"
                  role="radio"
                  aria-checked={sisi === u.sisi}
                  onClick={() => setSisi(u.sisi)}
                  className={`flex flex-1 flex-col items-start rounded-sm border px-3 py-2 text-left transition-colors ${
                    sisi === u.sisi
                      ? "border-accent bg-surface-2"
                      : "border-border bg-surface"
                  }`}
                >
                  <span className="text-body text-text">{u.label}</span>
                  <span className="text-caption text-text-2">{u.ket}</span>
                </button>
              ))}
            </div>
          </div>

          <label className="flex items-center justify-between rounded-md border border-border bg-surface px-4 py-3">
            <span className="flex flex-col">
              <span className="text-body text-text">Pertajam detail</span>
              <span className="text-caption text-text-2">
                Sedikit lebih tegas, tidak mengubah warna.
              </span>
            </span>
            <input
              type="checkbox"
              checked={tajam}
              onChange={(e) => setTajam(e.target.checked)}
              className="size-5 accent-[var(--color-accent)]"
            />
          </label>

          <p className="text-caption text-text-2">
            Proses berjalan di HP-mu. Layar boleh ditinggal atau ditutup —
            pekerjaan tetap lanjut dan hasilnya muncul di Riwayat.
          </p>

          {error ? (
            <p role="alert" className="rounded-sm bg-surface-2 px-3 py-2 text-caption text-danger">
              {error}
            </p>
          ) : null}
        </section>
      ) : null}

      {tahap === "proses" ? (
        <section className="flex flex-col gap-2 rounded-md border border-border bg-surface px-4 py-3">
          <div className="flex items-center justify-between text-caption text-text-2">
            <span>{persen < 100 ? "Memperjelas video" : "Merapikan hasil"}</span>
            <span>{persen}%</span>
          </div>
          <div
            role="progressbar"
            aria-valuenow={persen}
            aria-valuemin={0}
            aria-valuemax={100}
            className="h-2 w-full overflow-hidden rounded-full bg-surface-2"
          >
            <div
              className="h-full rounded-full bg-accent transition-[width] duration-500"
              style={{ width: `${Math.max(4, persen)}%` }}
            />
          </div>
          <span className="text-caption text-text-2">
            {totalFrame > 0
              ? `Frame ${frame} dari ${totalFrame}.`
              : "Menyiapkan frame pertama…"}{" "}
            Video lebih panjang berarti lebih lama. Layar boleh ditutup —
            kalau proses berhenti, bisa dilanjutkan dari titik terakhir.
          </span>
        </section>
      ) : null}

      {tahap === "terhenti" ? (
        <section className="flex flex-col gap-3 rounded-md border border-border bg-surface px-4 py-4">
          <div className="flex items-center justify-between text-caption text-text-2">
            <span>Ekstrak video terhenti</span>
            <span>{persen}%</span>
          </div>
          <div
            role="progressbar"
            aria-valuenow={persen}
            aria-valuemin={0}
            aria-valuemax={100}
            className="h-2 w-full overflow-hidden rounded-full bg-surface-2"
          >
            <div
              className="h-full rounded-full bg-accent"
              style={{ width: `${Math.max(4, persen)}%` }}
            />
          </div>
          <p className="text-caption text-text-2">
            {totalFrame > 0
              ? `Sudah ${frame} dari ${totalFrame} frame. `
              : ""}
            Pekerjaan disimpan di HP-mu. Ketuk Lanjutkan untuk meneruskan dari
            titik terakhir — tidak mengulang dari awal.
          </p>
        </section>
      ) : null}

      {tahap === "selesai" && hasil ? (
        <section className="flex flex-col gap-2 pt-3">
          <p className="text-caption text-text-2">
            Selesai dalam {lamaProses} detik · {hasil.frames} frame. Simpan ke
            galeri supaya bisa dibagikan ke WhatsApp atau Instagram.
          </p>
          {tersimpan ? (
            <p role="status" className="rounded-sm bg-surface-2 px-3 py-2 text-caption text-text-2">
              Tersimpan di Movies/Arulume.
            </p>
          ) : null}
          {error ? (
            <p role="alert" className="rounded-sm bg-surface-2 px-3 py-2 text-caption text-danger">
              {error}
            </p>
          ) : null}
        </section>
      ) : null}

      <div className="flex-1" />

      <section className="flex flex-col gap-3 pb-6 pt-4">
        {tahap === "atur" ? (
          <button
            type="button"
            onClick={mulai}
            disabled={sibuk}
            className="flex h-14 w-full items-center justify-center rounded-md bg-accent text-heading text-on-accent transition-opacity active:opacity-90 disabled:opacity-60"
          >
            Perjelas Video
          </button>
        ) : null}

        {tahap === "proses" ? (
          <button
            type="button"
            onClick={hentikan}
            disabled={sibuk}
            className="flex h-14 w-full items-center justify-center rounded-md border border-border bg-surface text-heading text-text transition-opacity active:opacity-90 disabled:opacity-60"
          >
            Jeda dulu
          </button>
        ) : null}

        {tahap === "terhenti" ? (
          <>
            <button
              type="button"
              onClick={lanjutkan}
              disabled={sibuk}
              className="flex h-14 w-full items-center justify-center rounded-md bg-accent text-heading text-on-accent transition-opacity active:opacity-90 disabled:opacity-60"
            >
              Lanjutkan
            </button>
            <button
              type="button"
              onClick={pilihKlip}
              className="text-caption text-accent-text underline-offset-4"
            >
              Mulai video baru
            </button>
          </>
        ) : null}

        {tahap === "selesai" ? (
          <>
            {tersimpan ? (
              <button
                type="button"
                onClick={bagikan}
                className="flex h-14 w-full items-center justify-center rounded-md bg-accent text-heading text-on-accent"
              >
                Bagikan
              </button>
            ) : (
              <button
                type="button"
                onClick={simpan}
                disabled={sibuk}
                className="flex h-14 w-full items-center justify-center gap-2.5 rounded-md bg-accent text-heading text-on-accent transition-opacity active:opacity-90 disabled:opacity-60"
              >
                <IconDownload size={20} />
                Simpan ke Galeri
              </button>
            )}
            <button
              type="button"
              onClick={pilihKlip}
              className="text-caption text-accent-text underline-offset-4"
            >
              Perjelas video lain
            </button>
          </>
        ) : null}
      </section>
    </main>
  );
}
