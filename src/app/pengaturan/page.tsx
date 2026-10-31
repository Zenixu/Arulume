"use client";

/**
 * Layar Pengaturan — Docs/design.md bagian 7.6, PRD FR-09.
 *
 * Semua pilihan disimpan di perangkat (`localStorage`). Tidak ada akun, tidak
 * ada telemetri: aplikasi ini tidak punya izin internet.
 */
import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Mascot, MASCOT_ROLE } from "@/components/mascot";
import { IconArrowLeft, IconChevronRight } from "@/components/icons";
import { Enhancer, isNative } from "@/plugins/enhancer";
import {
  bacaFormat,
  bacaSkalaBawaan,
  bacaTema,
  KETERANGAN_FORMAT,
  LABEL_FORMAT,
  LABEL_TEMA,
  simpanFormat,
  simpanSkalaBawaan,
  terapkanTema,
  type Format,
  type Tema,
} from "@/lib/settings";

const VERSI = "0.0.1"; // samakan dengan versionName di android/app/build.gradle

function Segmen<T extends string | number>({
  nilai,
  opsi,
  onPilih,
}: {
  nilai: T;
  opsi: { nilai: T; label: string }[];
  onPilih: (v: T) => void;
}) {
  return (
    <div className="flex gap-1 rounded-md border border-border bg-surface-2 p-1">
      {opsi.map((o) => {
        const aktif = o.nilai === nilai;
        return (
          <button
            key={String(o.nilai)}
            type="button"
            onClick={() => onPilih(o.nilai)}
            aria-pressed={aktif}
            className={`flex h-10 flex-1 items-center justify-center rounded-sm text-caption transition-colors ${
              aktif ? "bg-accent text-on-accent" : "text-text-2"
            }`}
          >
            {o.label}
          </button>
        );
      })}
    </div>
  );
}

export default function Pengaturan() {
  const router = useRouter();
  const [tema, setTema] = useState<Tema>("gelap");
  const [skala, setSkala] = useState<2 | 4>(2);
  const [format, setFormat] = useState<Format>("jpeg");
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    setTema(bacaTema());
    setSkala(bacaSkalaBawaan());
    setFormat(bacaFormat());
  }, []);

  function gantiTema(t: Tema) {
    setTema(t);
    terapkanTema(t);
  }

  function gantiSkala(s: 2 | 4) {
    setSkala(s);
    simpanSkalaBawaan(s);
  }

  function gantiFormat(f: Format) {
    setFormat(f);
    simpanFormat(f);
  }

  async function hapusCache() {
    if (busy) return;
    setBusy(true);
    setNotice(null);
    try {
      if (!isNative()) {
        setNotice("Hapus cache hanya berlaku di aplikasi Android.");
        return;
      }
      const { freedBytes } = await Enhancer.clearCache();
      const mb = freedBytes / (1024 * 1024);
      setNotice(
        mb >= 0.1
          ? `Cache dibersihkan, ${mb.toFixed(1)} MB kembali.`
          : "Cache sudah bersih.",
      );
    } catch {
      setNotice("Gagal membersihkan cache.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="mx-auto flex min-h-dvh w-full max-w-[480px] flex-col px-5">
      <header className="flex h-16 items-center gap-1">
        <button
          type="button"
          onClick={() => router.replace("/")}
          aria-label="Kembali ke Beranda"
          className="-ml-2 flex size-12 items-center justify-center rounded-full text-text transition-colors active:bg-surface-2"
        >
          <IconArrowLeft />
        </button>
        <h1 className="text-heading">Pengaturan</h1>
      </header>

      <div className="flex flex-col gap-6 pb-8">
        <section className="flex flex-col gap-2">
          <h2 className="text-caption uppercase tracking-wide text-text-2">Tema</h2>
          <Segmen<Tema>
            nilai={tema}
            onPilih={gantiTema}
            opsi={[
              { nilai: "gelap", label: LABEL_TEMA.gelap },
              { nilai: "terang", label: LABEL_TEMA.terang },
              { nilai: "sistem", label: LABEL_TEMA.sistem },
            ]}
          />
        </section>

        <section className="flex flex-col gap-2">
          <h2 className="text-caption uppercase tracking-wide text-text-2">
            Skala bawaan
          </h2>
          <Segmen<2 | 4>
            nilai={skala}
            onPilih={gantiSkala}
            opsi={[
              { nilai: 2, label: "2× lebih besar" },
              { nilai: 4, label: "4× lebih besar" },
            ]}
          />
          <p className="text-caption text-text-2">
            Dipakai sebagai pilihan awal di layar Editor. Kamu tetap bisa
            mengubahnya tiap foto.
          </p>
        </section>

        <section className="flex flex-col gap-2">
          <h2 className="text-caption uppercase tracking-wide text-text-2">
            Format hasil
          </h2>
          <Segmen<Format>
            nilai={format}
            onPilih={gantiFormat}
            opsi={[
              { nilai: "jpeg", label: LABEL_FORMAT.jpeg },
              { nilai: "png", label: LABEL_FORMAT.png },
              { nilai: "webp", label: LABEL_FORMAT.webp },
            ]}
          />
          <p className="text-caption text-text-2">{KETERANGAN_FORMAT[format]}</p>
        </section>

        <section className="flex flex-col gap-2">
          <h2 className="text-caption uppercase tracking-wide text-text-2">
            Penyimpanan
          </h2>
          <button
            type="button"
            onClick={hapusCache}
            disabled={busy}
            className="flex h-12 items-center justify-between rounded-md border border-border bg-surface px-4 text-body text-text disabled:opacity-60"
          >
            <span>{busy ? "Membersihkan…" : "Hapus cache hasil"}</span>
            <span className="text-caption text-text-2">aman</span>
          </button>
          <p className="text-caption text-text-2">
            Foto di galeri tidak ikut terhapus. Hanya berkas sementara di dalam
            aplikasi.
          </p>
        </section>

        {notice ? (
          <p
            role="status"
            className="rounded-sm bg-surface-2 px-3 py-2 text-caption text-text-2"
          >
            {notice}
          </p>
        ) : null}

        <section className="flex flex-col gap-2">
          <h2 className="text-caption uppercase tracking-wide text-text-2">
            Lainnya
          </h2>
          <Link
            href="/uji"
            className="flex h-12 items-center justify-between rounded-md border border-border bg-surface px-4 text-body text-text"
          >
            <span>Info perangkat</span>
            <IconChevronRight size={18} className="text-text-2" />
          </Link>
        </section>

        <section className="flex flex-col gap-2">
          <h2 className="text-caption uppercase tracking-wide text-text-2">
            Tentang &amp; lisensi
          </h2>
          <div className="flex flex-col gap-2 rounded-md border border-border bg-surface px-4 py-3 text-caption text-text-2">
            <div className="flex justify-between">
              <span className="text-text">Arulume</span>
              <span>versi {VERSI}</span>
            </div>
            <div className="flex justify-between">
              <span>Mesin</span>
              <span>ncnn (BSD-3-Clause)</span>
            </div>
            <div className="flex justify-between">
              <span>Model</span>
              <span>realesr-animevideov3 (BSD-3-Clause)</span>
            </div>
            <p className="pt-1">
              Semua pemrosesan terjadi di HP ini. Tidak ada unggahan, akun, atau
              pelacakan. Daftar lengkap komponen pihak ketiga ada di
              <span className="text-text"> THIRD_PARTY.md</span> di repositori.
            </p>
          </div>
        </section>

        <p className="flex items-center justify-center gap-1.5 pb-2 text-caption text-text-2">
          <Mascot mood={MASCOT_ROLE.netral} size={18} />
          Aruthtale
        </p>
      </div>
    </main>
  );
}
