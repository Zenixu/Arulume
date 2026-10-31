"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { IconArrowLeft, IconCheck } from "@/components/icons";
import {
  Enhancer,
  isNative,
  type DeviceCapabilities,
  type SelfTestResult,
} from "@/plugins/enhancer";

/**
 * Halaman uji Fase 0 (TASKS.md 0.3 & 0.5): menampilkan hasil getCapabilities()
 * dan menjalankan uji mesin native (ncnn + Vulkan) pada citra sintetis.
 * Bukan layar produk — dipakai untuk membuktikan plugin native bekerja.
 */
export default function HalamanUji() {
  const [cap, setCap] = useState<DeviceCapabilities | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<null | "gpu" | "cpu">(null);
  const [tests, setTests] = useState<SelfTestResult[]>([]);
  const [testError, setTestError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    async function load() {
      if (!isNative()) {
        setError(
          "Berjalan di browser, bukan di shell Android. Buka lewat aplikasi (Capacitor) untuk membaca data perangkat.",
        );
        setLoading(false);
        return;
      }
      try {
        const result = await Enhancer.getCapabilities();
        if (alive) setCap(result);
      } catch (e) {
        const err = e as { code?: string; message?: string };
        if (alive) setError(`${err.code ?? "E_UNKNOWN"}: ${err.message ?? "gagal"}`);
      } finally {
        if (alive) setLoading(false);
      }
    }
    void load();
    return () => {
      alive = false;
    };
  }, []);

  async function jalankan(vulkan: boolean) {
    setBusy(vulkan ? "gpu" : "cpu");
    setTestError(null);
    setTests([]);
    const hasil: SelfTestResult[] = [];
    try {
      for (const size of [256, 512, 1024]) {
        // Ukuran besar wajib dipotong: satu lintasan 1024² menghabiskan memori
        // di perangkat 3,5 GB (aplikasi pernah dimatikan sistem).
        const r = await Enhancer.runSelfTest({ vulkan, size, tile: size > 512 ? 256 : 0 });
        hasil.push(r);
        setTests([...hasil]);
      }
    } catch (e) {
      const err = e as { code?: string; message?: string };
      setTestError(`${err.code ?? "E_UNKNOWN"}: ${err.message ?? "gagal"}`);
    } finally {
      setBusy(null);
    }
  }

  const rows: Array<[string, string]> = cap
    ? [
        ["ABI", cap.abi],
        ["API Android", String(cap.androidApi)],
        ["RAM total", `${cap.ramTotalMb} MB`],
        ["Core CPU", String(cap.cpuCores)],
        ["Vulkan", cap.vulkanAvailable ? "tersedia" : "tidak tersedia"],
        ["GPU", cap.gpuName ?? "—"],
        ["Model terpasang", cap.models.length ? cap.models.map((m) => m.id).join(", ") : "belum ada"],
      ]
    : [];

  return (
    <main className="mx-auto flex min-h-dvh w-full max-w-[480px] flex-col px-5">
      <header className="flex h-16 items-center gap-2">
        <Link
          href="/"
          aria-label="Kembali"
          className="-ml-3 flex size-12 items-center justify-center rounded-full text-text-2 hover:text-text"
        >
          <IconArrowLeft />
        </Link>
        <h1 className="text-heading">Info perangkat</h1>
        <span className="ml-auto rounded-full bg-surface-2 px-2 py-0.5 text-caption text-text-2">
          Fase 0
        </span>
      </header>

      <section className="flex flex-col gap-3 pb-8">
        {loading ? (
          <p className="text-body text-text-2">Membaca kemampuan perangkat…</p>
        ) : null}

        {error ? (
          <div className="rounded-md border border-border bg-surface p-4">
            <p className="text-body text-text">Plugin belum bisa dibaca</p>
            <p className="mt-1 text-caption text-text-2">{error}</p>
          </div>
        ) : null}

        {cap ? (
          <div className="overflow-hidden rounded-md border border-border bg-surface">
            {rows.map(([label, value], i) => (
              <div
                key={label}
                className={`flex items-center justify-between gap-3 px-4 py-3 ${
                  i > 0 ? "border-t border-border" : ""
                }`}
              >
                <span className="text-body text-text-2">{label}</span>
                <span className="flex items-center gap-1.5 text-body text-text">
                  {label === "Vulkan" && cap.vulkanAvailable ? (
                    <IconCheck size={16} className="text-success" />
                  ) : null}
                  {value}
                </span>
              </div>
            ))}
          </div>
        ) : null}

        <p className="text-caption text-text-2">
          Data ini dipakai untuk mengisi tabel perangkat di Docs/benchmark-fase0.md.
        </p>

        {cap ? (
          <div className="mt-2 rounded-md border border-border bg-surface p-4">
            <p className="text-body text-text">Uji mesin</p>
            <p className="mt-1 text-caption text-text-2">
              Memperbesar citra uji 256×256, 512×512, dan 1024×1024 di dalam
              aplikasi, tanpa internet.
            </p>
            <div className="mt-3 flex gap-2">
              <button
                type="button"
                disabled={busy !== null}
                onClick={() => void jalankan(true)}
                className="rounded-full bg-accent px-4 py-2.5 text-body font-semibold text-on-accent disabled:opacity-60"
              >
                {busy === "gpu" ? "Menguji…" : "Uji GPU"}
              </button>
              <button
                type="button"
                disabled={busy !== null}
                onClick={() => void jalankan(false)}
                className="rounded-full border border-border bg-surface-2 px-4 py-2.5 text-body font-semibold text-text disabled:opacity-60"
              >
                {busy === "cpu" ? "Menguji…" : "Uji CPU"}
              </button>
            </div>

            {testError ? (
              <p className="mt-3 text-caption text-danger">{testError}</p>
            ) : null}

            {tests.length ? (
              <>
                <p className="mt-3 text-caption text-text-2">
                  {tests[0].vulkan ? "Mesin GPU (Vulkan)" : "Mesin CPU"}
                  {Math.max(...tests.map((t) => t.tile)) > 0
                    ? ` · 1024² dipotong ${Math.max(...tests.map((t) => t.tile))} px`
                    : " · satu lintasan"}
                </p>
                <table className="mt-2 w-full border-collapse">
                <thead>
                  <tr className="text-left">
                    <th className="text-caption font-normal text-text-2">Masuk</th>
                    <th className="text-caption font-normal text-text-2">Keluaran</th>
                    <th className="text-caption font-normal text-text-2">Muat</th>
                    <th className="text-caption font-normal text-text-2">Proses</th>
                  </tr>
                </thead>
                <tbody>
                  {tests.map((t) => (
                    <tr key={t.inputSize} className="border-t border-border">
                      <td className="py-1.5 text-caption text-text">
                        {t.inputSize}²
                      </td>
                      <td className="py-1.5 text-caption text-text">
                        {t.inputSize * (t.expectedWidth / t.inputSize)}²
                      </td>
                      <td className="py-1.5 text-caption text-text">{t.loadMs} ms</td>
                      <td className="py-1.5 text-caption text-text">{t.inferMs} ms</td>
                    </tr>
                  ))}
                </tbody>
                </table>
              </>
            ) : null}
          </div>
        ) : null}
      </section>
    </main>
  );
}
