"use client";

/**
 * Layar Hasil — Docs/design.md bagian 7.4.
 *
 * PRD FR-06 (revisi 2026-10-31): hasil TIDAK otomatis masuk galeri. Di sini user
 * melihat perbandingan sebelum/sesudah (bisa digeser dan diperbesar), baru
 * menekan Simpan kalau sudah yakin. Foto asli tidak pernah diubah.
 */
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Capacitor } from "@capacitor/core";
import { Mascot, MASCOT_ROLE } from "@/components/mascot";
import { IconArrowLeft, IconCheck, IconDownload } from "@/components/icons";
import { CompareView } from "@/components/compare";
import { Enhancer, type SavedResult } from "@/plugins/enhancer";
import { pesanError, useJobStore } from "@/lib/job-store";

export default function Hasil() {
  const router = useRouter();
  const { result, photo, reset, setResult } = useJobStore();
  const [saved, setSaved] = useState<SavedResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    if (!result) router.replace("/");
  }, [result, router]);

  if (!result) return null;

  const hasil = result;
  const sebelum = Capacitor.convertFileSrc(
    photo?.previewPath || hasil.beforePreviewPath,
  );
  const sesudah = Capacitor.convertFileSrc(hasil.afterPreviewPath);
  const ratio = hasil.outputWidth / Math.max(1, hasil.outputHeight);
  const detik = (hasil.durationMs / 1000).toFixed(1);
  const uriTersimpan = saved?.uri ?? (hasil.outputUri || null);

  async function simpan() {
    if (busy || saved) return;
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const s = await Enhancer.saveToGallery({ path: hasil.resultPath, quality: 95 });
      setSaved(s);
      setResult({ ...hasil, outputUri: s.uri });
      setNotice("Tersimpan di Pictures/Arulume.");
    } catch (e) {
      setError(pesanError((e as { code?: string }).code ?? "E_SAVE_FAILED"));
    } finally {
      setBusy(false);
    }
  }

  async function bukaGaleri() {
    if (!uriTersimpan) return;
    setNotice(null);
    try {
      await Enhancer.openInGallery({ uri: uriTersimpan });
    } catch {
      setNotice("Tidak ada aplikasi galeri di perangkat ini.");
    }
  }

  async function bagikan() {
    if (!uriTersimpan) return;
    setNotice(null);
    try {
      await Enhancer.shareResult({ uri: uriTersimpan, mime: "image/jpeg" });
    } catch {
      setNotice("Tidak bisa membagikan hasil.");
    }
  }

  function selesai() {
    reset();
    router.replace("/");
  }

  return (
    <main className="mx-auto flex min-h-dvh w-full max-w-[480px] flex-col px-5">
      <header className="flex h-16 items-center gap-1">
        <button
          type="button"
          onClick={selesai}
          aria-label="Kembali ke Beranda"
          className="-ml-3 flex size-12 items-center justify-center rounded-full text-text-2 transition-colors hover:text-text"
        >
          <IconArrowLeft />
        </button>
        <span className="text-heading">Hasil</span>
      </header>

      <section className="flex flex-col gap-3">
        <CompareView before={sebelum} after={sesudah} ratio={ratio} />

        <div className="flex items-start gap-3 rounded-md border border-border bg-surface px-4 py-3">
          <span className={`mt-0.5 ${saved ? "text-success" : "text-text-2"}`}>
            {saved ? <IconCheck size={20} /> : <IconDownload size={20} />}
          </span>
          <div className="flex flex-col gap-0.5">
            <span className="text-body text-text">
              {saved ? "Tersimpan di Galeri" : "Belum disimpan"}
            </span>
            <span className="text-caption text-text-2">
              {saved ? "Pictures/Arulume" : "Simpan dulu kalau sudah cocok"} ·{" "}
              {hasil.outputWidth} × {hasil.outputHeight} · {detik} dtk
            </span>
            {photo ? (
              <span className="text-caption text-text-2">
                Asal {photo.width} × {photo.height}
              </span>
            ) : null}
          </div>
        </div>

        {error ? (
          <p role="alert" className="rounded-sm bg-surface-2 px-3 py-2 text-caption text-danger">
            {error}
          </p>
        ) : null}

        {notice ? (
          <p role="status" className="rounded-sm bg-surface-2 px-3 py-2 text-caption text-text-2">
            {notice}
          </p>
        ) : null}
      </section>

      <div className="flex-1" />

      <section className="flex flex-col gap-3 pb-6 pt-4">
        {saved ? (
          <button
            type="button"
            onClick={bukaGaleri}
            className="flex h-14 w-full items-center justify-center gap-2 rounded-md bg-accent text-on-accent transition-opacity active:opacity-90"
          >
            <span className="text-heading">Buka di Galeri</span>
          </button>
        ) : (
          <button
            type="button"
            onClick={simpan}
            disabled={busy}
            className="flex h-14 w-full items-center justify-center gap-2.5 rounded-md bg-accent text-on-accent transition-opacity active:opacity-90 disabled:opacity-60"
          >
            <IconDownload size={20} />
            <span className="text-heading">{busy ? "Menyimpan…" : "Simpan ke Galeri"}</span>
          </button>
        )}

        <div className="grid grid-cols-2 gap-3">
          <button
            type="button"
            onClick={bagikan}
            disabled={!uriTersimpan}
            className="flex h-[52px] items-center justify-center rounded-md border border-border bg-surface text-body text-text disabled:opacity-40"
          >
            Bagikan
          </button>
          <button
            type="button"
            onClick={selesai}
            className="flex h-[52px] items-center justify-center rounded-md border border-border bg-surface text-body text-text"
          >
            Selesai
          </button>
        </div>

        <p className="flex items-center justify-center gap-1.5 text-caption text-text-2">
          <Mascot mood={MASCOT_ROLE.berbinar} size={18} />
          Foto aslimu tidak diubah.
        </p>
      </section>
    </main>
  );
}
