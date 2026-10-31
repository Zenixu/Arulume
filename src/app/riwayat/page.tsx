"use client";

/**
 * Layar Riwayat — Docs/design.md bagian 7.5, PRD FR-08.
 *
 * Grid dua kolom, ketuk untuk melihat lagi, tekan lama untuk memilih lalu
 * menghapus entri. Menghapus entri TIDAK menghapus foto di galeri.
 */
import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Capacitor } from "@capacitor/core";
import { Mascot } from "@/components/mascot";
import { IconArrowLeft, IconClock, IconTrash } from "@/components/icons";
import { Enhancer, isNative, type HistoryEntry } from "@/plugins/enhancer";

function waktu(ms: number): string {
  try {
    return new Intl.DateTimeFormat("id-ID", {
      day: "numeric",
      month: "short",
      hour: "2-digit",
      minute: "2-digit",
    }).format(new Date(ms));
  } catch {
    return "";
  }
}

export default function Riwayat() {
  const router = useRouter();
  const [items, setItems] = useState<HistoryEntry[] | null>(null);
  const [pilih, setPilih] = useState<string[]>([]);
  const [notice, setNotice] = useState<string | null>(null);
  const [konfirmasiSemua, setKonfirmasiSemua] = useState(false);
  const tekanTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const modePilih = pilih.length > 0;

  const muat = useCallback(async () => {
    if (!isNative()) {
      setItems([]);
      return;
    }
    try {
      const { items } = await Enhancer.listHistory();
      setItems(items);
    } catch {
      setItems([]);
      setNotice("Riwayat tidak bisa dibaca.");
    }
  }, []);

  useEffect(() => {
    muat();
  }, [muat]);

  function mulaiTekan(id: string) {
    batalTekan();
    tekanTimer.current = setTimeout(() => {
      setPilih([id]);
    }, 500);
  }

  function batalTekan() {
    if (tekanTimer.current) {
      clearTimeout(tekanTimer.current);
      tekanTimer.current = null;
    }
  }

  function ketuk(e: HistoryEntry) {
    if (modePilih) {
      setPilih((p) =>
        p.includes(e.id) ? p.filter((x) => x !== e.id) : [...p, e.id],
      );
      return;
    }
    router.push(`/riwayat/lihat?id=${encodeURIComponent(e.id)}`);
  }

  async function hapusSemua() {
    if (!konfirmasiSemua) {
      setKonfirmasiSemua(true);
      return;
    }
    setKonfirmasiSemua(false);
    try {
      await Enhancer.clearHistory();
      await muat();
      setNotice("Semua entri dihapus. Foto di galeri tetap ada.");
    } catch {
      setNotice("Gagal menghapus riwayat.");
    }
  }

  async function hapusTerpilih() {
    const jumlah = pilih.length;
    setPilih([]);
    setNotice(null);
    try {
      for (const id of pilih) {
        await Enhancer.deleteHistory({ id });
      }
      await muat();
      setNotice(`${jumlah} entri dihapus dari riwayat. Foto di galeri tetap ada.`);
    } catch {
      setNotice("Gagal menghapus entri.");
    }
  }

  return (
    <main className="mx-auto flex min-h-dvh w-full max-w-[480px] flex-col px-5">
      <header className="flex h-16 items-center justify-between">
        <button
          type="button"
          onClick={() => (modePilih ? setPilih([]) : router.replace("/"))}
          aria-label={modePilih ? "Batal memilih" : "Kembali ke Beranda"}
          className="-ml-2 flex size-12 items-center justify-center rounded-full text-text transition-colors active:bg-surface-2"
        >
          <IconArrowLeft />
        </button>
        <h1 className="text-heading">
          {modePilih ? `${pilih.length} dipilih` : "Riwayat"}
        </h1>
        <div className="flex size-12 items-center justify-center">
          {modePilih ? (
            <button
              type="button"
              onClick={hapusTerpilih}
              aria-label="Hapus entri terpilih"
              className="flex size-12 items-center justify-center rounded-full text-danger transition-colors active:bg-surface-2"
            >
              <IconTrash />
            </button>
          ) : null}
        </div>
      </header>

      {notice ? (
        <p
          role="status"
          className="mb-3 rounded-sm bg-surface-2 px-3 py-2 text-caption text-text-2"
        >
          {notice}
        </p>
      ) : null}

      {items === null ? (
        <p className="py-10 text-center text-body text-text-2">Memuat…</p>
      ) : items.length === 0 ? (
        <section className="flex flex-1 flex-col items-center justify-center gap-4 pb-16 text-center">
          <Mascot mood="mengendap" size={120} />
          <div className="flex flex-col gap-1">
            <span className="text-heading">Belum ada cerita</span>
            <span className="text-body text-text-2">
              Foto yang kamu proses akan muncul di sini.
            </span>
          </div>
          <button
            type="button"
            onClick={() => router.replace("/")}
            className="mt-2 flex h-12 items-center justify-center rounded-md bg-accent px-5 text-body text-on-accent"
          >
            Pilih foto
          </button>
        </section>
      ) : (
        <ul className="grid grid-cols-2 gap-3 pb-8">
          {items.map((e) => {
            const terpilih = pilih.includes(e.id);
            return (
              <li key={e.id}>
                <button
                  type="button"
                  onClick={() => ketuk(e)}
                  onPointerDown={() => mulaiTekan(e.id)}
                  onPointerUp={batalTekan}
                  onPointerLeave={batalTekan}
                  onContextMenu={(ev) => {
                    ev.preventDefault();
                    setPilih([e.id]);
                  }}
                  className={`flex w-full flex-col overflow-hidden rounded-md border text-left transition-colors ${
                    terpilih
                      ? "border-accent bg-surface-2"
                      : "border-border bg-surface"
                  }`}
                >
                  <span className="relative block aspect-square w-full bg-surface-2">
                    {e.hasResult ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img
                        src={Capacitor.convertFileSrc(e.afterPreviewPath)}
                        alt={`Hasil ${e.outWidth} kali ${e.outHeight}`}
                        className="size-full object-cover"
                      />
                    ) : (
                      <span className="flex size-full items-center justify-center text-caption text-text-2">
                        Berkas sudah dibersihkan
                      </span>
                    )}
                    <span className="absolute left-2 top-2 rounded-full bg-bg/80 px-2 py-0.5 text-caption text-text">
                      {e.scale}×
                    </span>
                    {e.savedUri ? (
                      <span className="absolute right-2 top-2 rounded-full bg-success/90 px-2 py-0.5 text-caption text-bg">
                        Tersimpan
                      </span>
                    ) : null}
                    {terpilih ? (
                      <span className="absolute inset-0 border-2 border-accent" />
                    ) : null}
                  </span>
                  <span className="flex flex-col gap-0.5 px-2.5 py-2">
                    <span className="text-caption text-text">{waktu(e.createdAt)}</span>
                    <span className="flex items-center gap-1 text-caption text-text-2">
                      <IconClock size={13} />
                      {(e.durationMs / 1000).toFixed(1)} dtk · {e.outWidth}×
                      {e.outHeight}
                    </span>
                  </span>
                </button>
              </li>
            );
          })}
        </ul>
      )}

      {items && items.length > 0 && !modePilih ? (
        <div className="pb-8">
          <button
            type="button"
            onClick={hapusSemua}
            className={`flex h-12 w-full items-center justify-center rounded-md border text-body transition-colors ${
              konfirmasiSemua
                ? "border-danger bg-danger/10 text-danger"
                : "border-border bg-surface text-text-2"
            }`}
          >
            {konfirmasiSemua
              ? "Tekan lagi untuk hapus semua"
              : "Hapus semua riwayat"}
          </button>
        </div>
      ) : null}
    </main>
  );
}
