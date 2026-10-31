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
import { Enhancer, isNative, type UpdateCheck } from "@/plugins/enhancer";
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

/** Cadangan bila versi tidak terbaca dari paket (mis. saat pratinjau web). */
const VERSI_WEB = "0.1.2";

/** Halaman rilis resmi — dibuka di browser, bukan diunduh di dalam app. */
const TAUTAN_RILIS = "https://github.com/Zenixu/Arulume/releases/latest";

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
  const [versi, setVersi] = useState(VERSI_WEB);
  const [upCek, setUpCek] = useState<UpdateCheck | null>(null);
  const [upBusy, setUpBusy] = useState(false);
  const [upPersen, setUpPersen] = useState<number | null>(null);
  const [upPath, setUpPath] = useState<string | null>(null);

  useEffect(() => {
    setTema(bacaTema());
    setSkala(bacaSkalaBawaan());
    setFormat(bacaFormat());
  }, []);

  // Versi dibaca dari paket yang terpasang, supaya angka di layar tidak
  // pernah berbeda dengan APK yang benar-benar dipakai.
  useEffect(() => {
    if (!isNative()) return;
    let batal = false;
    (async () => {
      try {
        const v = await Enhancer.versiApp();
        if (!batal && v?.nama) setVersi(v.nama);
      } catch {
        /* biarkan nilai cadangan */
      }
    })();
    return () => {
      batal = true;
    };
  }, []);

  /** Cek versi terbaru langsung dari dalam aplikasi (tanpa keluar). */
  async function periksaUpdate() {
    if (upBusy) return;
    setUpBusy(true);
    setUpPath(null);
    setUpPersen(null);
    setUpCek(null);
    setNotice(null);
    try {
      if (!isNative()) {
        setNotice("Pembaruan hanya bisa diperiksa dari aplikasi Android.");
        return;
      }
      const hasil = await Enhancer.cekUpdate();
      setUpCek(hasil);
      if (hasil.versiSekarang) setVersi(hasil.versiSekarang);
    } catch {
      setUpCek({
        versiSekarang: versi,
        adaBaru: false,
        galat: "Tidak bisa memeriksa pembaruan.",
      });
    } finally {
      setUpBusy(false);
    }
  }

  /** Unduh APK pembaruan dengan progres, di dalam aplikasi. */
  async function unduhPembaruan() {
    if (upBusy || !upCek?.url) return;
    setUpBusy(true);
    setUpPersen(0);
    setNotice(null);
    const telinga = await Enhancer.addListener("updateProgress", (e) =>
      setUpPersen(e.percent ?? 0),
    );
    try {
      const r = await Enhancer.unduhUpdate({
        url: upCek.url,
        nama: upCek.namaBerkas,
        ukuran: upCek.ukuran,
      });
      setUpPath(r.path);
      setNotice("Unduhan selesai. Ketuk Pasang sekarang.");
    } catch {
      setNotice("Unduhan gagal. Periksa koneksi lalu coba lagi.");
    } finally {
      await telinga.remove();
      setUpBusy(false);
      setUpPersen(null);
    }
  }

  /** Buka pemasang Android untuk APK yang sudah diunduh. */
  async function pasangPembaruan() {
    if (!upPath) return;
    try {
      await Enhancer.pasangUpdate({ path: upPath });
      setNotice(
        "Ikuti petunjuk pemasang. Bila diminta, izinkan Arulume memasang aplikasi.",
      );
    } catch {
      setNotice("Pemasang tidak bisa dibuka. Coba lagi.");
    }
  }

  /** Jalan cadangan: buka halaman rilis di browser. */
  async function bukaRilis() {
    try {
      await Enhancer.bukaHalamanRilis({ url: TAUTAN_RILIS });
    } catch {
      setNotice("Tidak ada browser untuk membuka halaman rilis.");
    }
  }

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
            Pembaruan
          </h2>
          <button
            type="button"
            onClick={periksaUpdate}
            disabled={upBusy}
            className="flex h-12 items-center justify-between rounded-md border border-border bg-surface px-4 text-body text-text disabled:opacity-60"
          >
            <span>{upBusy ? "Memeriksa…" : "Periksa update"}</span>
            <span className="text-caption text-text-2">v{versi}</span>
          </button>

          {upCek && !upCek.galat && !upCek.adaBaru ? (
            <p role="status" className="text-caption text-text-2">
              Kamu sudah memakai versi terbaru (v{upCek.versiSekarang}).
            </p>
          ) : null}

          {upCek?.galat ? (
            <div className="flex flex-col gap-1 rounded-sm bg-surface-2 px-3 py-2">
              <p role="status" className="text-caption text-text-2">
                {upCek.galat}
              </p>
              <button
                type="button"
                onClick={bukaRilis}
                className="self-start text-caption text-accent-text underline-offset-4"
              >
                Buka halaman rilis di browser
              </button>
            </div>
          ) : null}

          {upCek?.adaBaru ? (
            <div className="flex flex-col gap-3 rounded-md border border-accent bg-surface px-4 py-3">
              <div className="flex flex-col gap-0.5">
                <span className="text-body text-text">
                  Versi {upCek.versiTerbaru} tersedia
                </span>
                <span className="text-caption text-text-2">
                  {upCek.ukuran
                    ? `${(upCek.ukuran / (1024 * 1024)).toFixed(1)} MB · pemasangan tidak menghapus data`
                    : "Pemasangan tidak menghapus data"}
                </span>
              </div>

              {upPersen !== null ? (
                <div
                  role="progressbar"
                  aria-valuenow={upPersen}
                  aria-valuemin={0}
                  aria-valuemax={100}
                  className="h-2 w-full overflow-hidden rounded-full bg-surface-2"
                >
                  <div
                    className="h-full rounded-full bg-accent transition-[width]"
                    style={{ width: `${Math.max(3, upPersen)}%` }}
                  />
                </div>
              ) : null}

              {upPath ? (
                <button
                  type="button"
                  onClick={pasangPembaruan}
                  className="flex h-12 w-full items-center justify-center rounded-md bg-accent text-body text-on-accent transition-opacity active:opacity-90"
                >
                  Pasang sekarang
                </button>
              ) : (
                <button
                  type="button"
                  onClick={unduhPembaruan}
                  disabled={upBusy}
                  className="flex h-12 w-full items-center justify-center rounded-md bg-accent text-body text-on-accent transition-opacity active:opacity-90 disabled:opacity-60"
                >
                  {upBusy
                    ? `Mengunduh ${upPersen ?? 0}%`
                    : "Unduh pembaruan"}
                </button>
              )}
            </div>
          ) : null}

          <p className="text-caption text-text-2">
            Diperiksa langsung di dalam aplikasi. Satu-satunya pemakaian
            internet adalah memeriksa dan mengunduh pembaruan dari halaman rilis
            resmi Arulume — tidak ada analitik, pelacakan, atau unggahan data.
            Pemrosesan foto dan video tetap sepenuhnya di HP ini.
          </p>
        </section>

        <section className="flex flex-col gap-2">
          <h2 className="text-caption uppercase tracking-wide text-text-2">
            Tentang &amp; lisensi
          </h2>
          <div className="flex flex-col gap-2 rounded-md border border-border bg-surface px-4 py-3 text-caption text-text-2">
            <div className="flex justify-between">
              <span className="text-text">Arulume</span>
              <span>versi {versi}</span>
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
