"use client";

/**
 * Layar Beberapa Foto — PRD FR-11.
 *
 * Foto dikerjakan berurutan di satu job native (layanan foreground), jadi proses
 * tetap lanjut walau layar ini ditinggal. Halaman ini hanya menampilkan keadaan
 * dan menyimpan hasil satu per satu — tidak ada foto yang masuk galeri sendiri.
 */
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Capacitor } from "@capacitor/core";
import { IconArrowLeft, IconCheck, IconDownload } from "@/components/icons";
import { Enhancer, type CompletedEvent } from "@/plugins/enhancer";
import { pesanError } from "@/lib/job-store";
import { ringkasBatch, useBatchStore } from "@/lib/batch-store";

const LABEL_STAGE: Record<string, string> = {
  decoding: "Membaca foto",
  enhancing: "Memperbesar",
  encoding: "Menyiapkan hasil",
  saving: "Menyimpan",
  done: "Selesai",
  failed: "Gagal",
};

export default function BatchPage() {
  const router = useRouter();
  const items = useBatchStore((s) => s.items);
  const jobId = useBatchStore((s) => s.jobId);
  const percent = useBatchStore((s) => s.percent);
  const stage = useBatchStore((s) => s.stage);
  const aktif = useBatchStore((s) => s.aktif);
  const gagalJob = useBatchStore((s) => s.gagalJob);
  const mulai = useBatchStore((s) => s.mulai);
  const progres = useBatchStore((s) => s.progres);
  const itemSelesai = useBatchStore((s) => s.itemSelesai);
  const itemGagal = useBatchStore((s) => s.itemGagal);
  const selesai = useBatchStore((s) => s.selesai);
  const gagal = useBatchStore((s) => s.gagal);
  const tandaiTersimpan = useBatchStore((s) => s.tandaiTersimpan);
  const reset = useBatchStore((s) => s.reset);

  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  const { selesai: jmlSelesai, gagal: jmlGagal, tersimpan } = ringkasBatch(items);

  // Kalau halaman dibuka ulang saat job masih jalan, sambungkan lagi ke progres.
  useEffect(() => {
    let batal = false;
    (async () => {
      try {
        const s = await Enhancer.getJobStatus();
        if (batal || !s.aktif || !s.jobId) return;
        mulai(s.jobId);
        if (typeof s.percent === "number") {
          progres(s.percent, (s.stage as never) ?? "decoding");
        }
      } catch {
        // Tidak ada job berjalan.
      }
    })();
    return () => {
      batal = true;
    };
    // Sengaja hanya sekali saat halaman dibuka.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Pendengar event job. Dipasang sekali; dilepas saat halaman ditutup.
  useEffect(() => {
    if (!jobId) return;
    const handles: { remove: () => Promise<void> }[] = [];
    let batal = false;

    (async () => {
      handles.push(
        await Enhancer.addListener("progress", (e) => {
          progres(e.percent, e.stage);
        }),
      );
      handles.push(
        await Enhancer.addListener("itemDone", (e) => {
          itemSelesai(e.index, e.entry as CompletedEvent);
        }),
      );
      handles.push(
        await Enhancer.addListener("itemFailed", (e) => {
          itemGagal(e.index, pesanError(e.code));
        }),
      );
      handles.push(
        await Enhancer.addListener("completed", (e) => {
          selesai(e.ok ?? 0, e.failed ?? 0, e.cancelled ?? false);
        }),
      );
      handles.push(
        await Enhancer.addListener("failed", (e) => {
          gagal(pesanError(e.code));
        }),
      );
      if (batal) {
        handles.forEach((h) => void h.remove());
      }
    })();

    return () => {
      batal = true;
      handles.forEach((h) => void h.remove());
    };
  }, [jobId, progres, itemSelesai, itemGagal, selesai, gagal]);

  async function mulaiProses() {
    if (busy || aktif) return;
    setBusy(true);
    setNotice(null);
    try {
      // Notifikasi progres butuh izin di Android 13+; ditolak pun proses tetap jalan.
      try {
        await Enhancer.mintaIzinNotifikasi();
      } catch {
        /* izin opsional */
      }
      const { jobId: id } = await Enhancer.startEnhanceBatch({
        uris: items.map((i) => i.media.uri),
        model: "",
        scale: 2,
        output: { format: "jpeg", quality: 95 },
      });
      mulai(id);
    } catch (e) {
      const code = (e as { code?: string }).code ?? "E_UNKNOWN";
      setNotice(
        code === "E_BUSY"
          ? "Masih ada proses lain. Tunggu sampai selesai."
          : pesanError(code),
      );
    } finally {
      setBusy(false);
    }
  }

  async function simpanSatu(index: number) {
    const it = items[index];
    if (!it?.entry || it.savedUri) return;
    try {
      const s = await Enhancer.saveToGallery({ path: it.entry.resultPath, quality: 95 });
      tandaiTersimpan(index, s.uri);
      setNotice("Tersimpan di Pictures/Arulume.");
    } catch (e) {
      setNotice(pesanError((e as { code?: string }).code ?? "E_SAVE_FAILED"));
    }
  }

  async function simpanSemua() {
    if (busy) return;
    setBusy(true);
    setNotice(null);
    let ok = 0;
    for (let i = 0; i < items.length; i++) {
      const it = items[i];
      if (it.state !== "selesai" || it.savedUri) continue;
      try {
        const s = await Enhancer.saveToGallery({ path: it.entry!.resultPath, quality: 95 });
        tandaiTersimpan(i, s.uri);
        ok++;
      } catch {
        // Lanjut ke foto berikutnya; ringkasan di bawah memberi tahu.
      }
    }
    setBusy(false);
    setNotice(
      ok > 0
        ? `${ok} foto tersimpan di Pictures/Arulume.`
        : "Tidak ada foto baru yang bisa disimpan.",
    );
  }

  function tutup() {
    if (aktif) {
      setNotice("Proses masih berjalan. Kamu boleh menutup layar ini — proses lanjut di latar.");
      return;
    }
    reset();
    router.replace("/");
  }

  const label = aktif
    ? `${LABEL_STAGE[stage ?? "decoding"] ?? "Memproses"}… ${percent}%`
    : jmlSelesai > 0
      ? `${jmlSelesai} foto siap disimpan`
      : "Siap diproses";

  return (
    <main className="mx-auto flex min-h-dvh w-full max-w-[480px] flex-col px-5">
      <header className="flex h-16 items-center gap-1">
        <button
          type="button"
          onClick={tutup}
          aria-label="Kembali ke Beranda"
          className="-ml-2 flex size-12 items-center justify-center rounded-full text-text-2 transition-colors hover:text-text"
        >
          <IconArrowLeft />
        </button>
        <h1 className="text-heading">Beberapa foto</h1>
      </header>

      <section className="flex flex-col gap-2 pb-4">
        <div className="flex items-center justify-between text-caption text-text-2">
          <span>
            {items.length} foto · 2× lebih besar
          </span>
          <span>{label}</span>
        </div>
        <div
          role="progressbar"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={percent}
          className="h-2 w-full overflow-hidden rounded-full bg-surface-2"
        >
          <div
            className="h-full rounded-full bg-accent transition-[width] duration-300"
            style={{ width: `${percent}%` }}
          />
        </div>
      </section>

      <section className="flex flex-1 flex-col gap-2">
        {items.map((it, i) => (
          <div
            key={`${it.media.uri}-${i}`}
            className="flex items-center gap-3 rounded-md border border-border bg-surface p-2.5"
          >
            <div className="relative size-14 shrink-0 overflow-hidden rounded-sm bg-surface-2">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img
                src={Capacitor.convertFileSrc(it.media.previewPath ?? "")}
                alt=""
                className="size-full object-cover"
              />
            </div>
            <div className="flex min-w-0 flex-1 flex-col">
              <span className="truncate text-body text-text">{it.media.name}</span>
              <span className="text-caption text-text-2">
                {it.state === "selesai"
                  ? it.savedUri
                    ? "Tersimpan di galeri"
                    : `${it.entry?.outputWidth}×${it.entry?.outputHeight} · belum disimpan`
                  : it.state === "gagal"
                    ? (it.pesan ?? "Gagal diproses")
                    : it.state === "proses"
                      ? "Sedang diproses…"
                      : "Menunggu"}
              </span>
            </div>
            {it.state === "selesai" ? (
              it.savedUri ? (
                <span className="flex size-10 items-center justify-center text-success">
                  <IconCheck size={20} />
                </span>
              ) : (
                <button
                  type="button"
                  onClick={() => void simpanSatu(i)}
                  aria-label={`Simpan ${it.media.name}`}
                  className="flex size-10 items-center justify-center rounded-full text-accent-text"
                >
                  <IconDownload size={20} />
                </button>
              )
            ) : null}
          </div>
        ))}
      </section>

      {gagalJob ? (
        <p role="status" className="pb-2 text-caption text-danger">
          {gagalJob}
        </p>
      ) : null}
      {notice ? (
        <p role="status" className="pb-2 text-caption text-text-2">
          {notice}
        </p>
      ) : null}

      <section className="flex flex-col gap-2 pb-6">
        {!aktif && jmlSelesai === 0 ? (
          <button
            type="button"
            onClick={() => void mulaiProses()}
            disabled={busy || items.length === 0}
            className="flex h-14 w-full items-center justify-center rounded-md bg-accent text-on-accent disabled:opacity-60"
          >
            <span className="text-heading">Mulai perbesar</span>
          </button>
        ) : null}

        {!aktif && jmlSelesai > 0 && tersimpan < jmlSelesai ? (
          <button
            type="button"
            onClick={() => void simpanSemua()}
            disabled={busy}
            className="flex h-14 w-full items-center justify-center gap-2 rounded-md bg-accent text-on-accent disabled:opacity-60"
          >
            <IconDownload size={20} />
            <span className="text-heading">
              Simpan semua ({jmlSelesai - tersimpan})
            </span>
          </button>
        ) : null}

        {!aktif && jmlGagal > 0 ? (
          <p className="text-center text-caption text-text-2">
            {jmlGagal} foto gagal diproses dan dilewati.
          </p>
        ) : null}

        {aktif ? (
          <p className="text-center text-caption text-text-2">
            Proses tetap berjalan walau kamu keluar dari layar ini.
          </p>
        ) : null}
      </section>
    </main>
  );
}
