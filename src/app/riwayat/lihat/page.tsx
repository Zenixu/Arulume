"use client";

/**
 * Lihat lagi satu entri Riwayat — pembanding sebelum/sesudah yang sama dengan
 * layar Hasil, tanpa harus memproses ulang.
 *
 * Kalau berkas cache sudah dipangkas (cache hanya menyimpan 3 hasil terbaru),
 * layar menawarkan membuka salinan di galeri. Foto di galeri tidak pernah
 * dihapus dari sini (PRD FR-08).
 */
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Capacitor } from "@capacitor/core";
import { IconArrowLeft, IconDownload, IconTrash } from "@/components/icons";
import { CompareView } from "@/components/compare";
import { Enhancer, isNative, type HistoryEntry } from "@/plugins/enhancer";

export default function LihatRiwayat() {
  const router = useRouter();
  const [id, setId] = useState<string | null>(null);
  const [entri, setEntri] = useState<HistoryEntry | null>(null);
  const [galat, setGalat] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [savedUri, setSavedUri] = useState("");

  // Dibaca dari location, bukan useSearchParams, supaya halaman ini tetap
  // bisa diekspor statis tanpa perlu Suspense.
  useEffect(() => {
    const q = new URLSearchParams(window.location.search).get("id");
    setId(q);
  }, []);

  useEffect(() => {
    if (!id) return;
    if (!isNative()) {
      setGalat("Riwayat hanya tersedia di aplikasi Android.");
      return;
    }
    (async () => {
      try {
        const e = await Enhancer.getHistory({ id });
        setEntri(e);
        setSavedUri(e.savedUri ?? "");
      } catch {
        setGalat("Entri ini sudah tidak ada di riwayat.");
      }
    })();
  }, [id]);

  async function simpan() {
    if (!entri || busy || savedUri) return;
    setBusy(true);
    setNotice(null);
    try {
      const s = await Enhancer.saveToGallery({ path: entri.resultPath, quality: 95 });
      setSavedUri(s.uri);
      setNotice("Tersimpan di Pictures/Arulume.");
    } catch {
      setNotice("Hasil gagal disimpan. Berkasnya mungkin sudah dibersihkan.");
    } finally {
      setBusy(false);
    }
  }

  async function bukaGaleri() {
    if (!savedUri) return;
    try {
      await Enhancer.openInGallery({ uri: savedUri });
    } catch {
      setNotice("Tidak ada aplikasi galeri di perangkat ini.");
    }
  }

  async function hapus() {
    if (!entri) return;
    try {
      await Enhancer.deleteHistory({ id: entri.id });
      router.replace("/riwayat");
    } catch {
      setNotice("Gagal menghapus entri.");
    }
  }

  if (galat) {
    return (
      <main className="mx-auto flex min-h-dvh w-full max-w-[480px] flex-col px-5">
        <header className="flex h-16 items-center">
          <button
            type="button"
            onClick={() => router.replace("/riwayat")}
            aria-label="Kembali ke Riwayat"
            className="-ml-2 flex size-12 items-center justify-center rounded-full text-text"
          >
            <IconArrowLeft />
          </button>
        </header>
        <p className="py-10 text-center text-body text-text-2">{galat}</p>
      </main>
    );
  }

  if (!entri) {
    return (
      <main className="mx-auto flex min-h-dvh w-full max-w-[480px] flex-col px-5">
        <p className="py-16 text-center text-body text-text-2">Memuat…</p>
      </main>
    );
  }

  const ratio = entri.outWidth / Math.max(1, entri.outHeight);
  const detik = (entri.durationMs / 1000).toFixed(1);

  return (
    <main className="mx-auto flex min-h-dvh w-full max-w-[480px] flex-col px-5">
      <header className="flex h-16 items-center justify-between">
        <button
          type="button"
          onClick={() => router.replace("/riwayat")}
          aria-label="Kembali ke Riwayat"
          className="-ml-2 flex size-12 items-center justify-center rounded-full text-text transition-colors active:bg-surface-2"
        >
          <IconArrowLeft />
        </button>
        <h1 className="text-heading">Hasil {entri.scale}×</h1>
        <button
          type="button"
          onClick={hapus}
          aria-label="Hapus dari riwayat"
          className="flex size-12 items-center justify-center rounded-full text-danger transition-colors active:bg-surface-2"
        >
          <IconTrash />
        </button>
      </header>

      {entri.hasResult ? (
        <CompareView
          before={Capacitor.convertFileSrc(entri.beforePreviewPath)}
          after={Capacitor.convertFileSrc(entri.afterPreviewPath)}
          ratio={ratio}
        />
      ) : (
        <div className="flex aspect-square w-full flex-col items-center justify-center gap-2 rounded-md border border-border bg-surface px-4 text-center">
          <span className="text-body text-text">Berkas hasil sudah dibersihkan</span>
          <span className="text-caption text-text-2">
            Aplikasi hanya menyimpan 3 hasil terbaru. Foto yang sudah kamu simpan
            tetap ada di galeri.
          </span>
        </div>
      )}

      <p className="mt-3 text-caption text-text-2">
        {entri.srcWidth}×{entri.srcHeight} → {entri.outWidth}×{entri.outHeight} ·{" "}
        {detik} dtk
      </p>

      {notice ? (
        <p role="status" className="mt-2 rounded-sm bg-surface-2 px-3 py-2 text-caption text-text-2">
          {notice}
        </p>
      ) : null}

      <div className="flex-1" />

      <section className="flex flex-col gap-3 pb-6 pt-4">
        {savedUri ? (
          <button
            type="button"
            onClick={bukaGaleri}
            className="flex h-14 w-full items-center justify-center rounded-md bg-accent text-on-accent"
          >
            <span className="text-heading">Buka di Galeri</span>
          </button>
        ) : (
          <button
            type="button"
            onClick={simpan}
            disabled={busy || !entri.hasResult}
            className="flex h-14 w-full items-center justify-center gap-2 rounded-md bg-accent text-on-accent disabled:opacity-60"
          >
            <IconDownload size={20} />
            <span className="text-heading">
              {busy ? "Menyimpan…" : "Simpan ke Galeri"}
            </span>
          </button>
        )}
      </section>
    </main>
  );
}
