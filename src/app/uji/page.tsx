"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { IconArrowLeft, IconCheck } from "@/components/icons";
import { Enhancer, isNative, type DeviceCapabilities } from "@/plugins/enhancer";

/**
 * Halaman uji Fase 0 (TASKS.md 0.3): menampilkan hasil getCapabilities().
 * Bukan layar produk — dipakai untuk membuktikan plugin native bekerja.
 */
export default function HalamanUji() {
  const [cap, setCap] = useState<DeviceCapabilities | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

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
      </section>
    </main>
  );
}
