"use client";

/**
 * Tombol back Android — Docs/design.md bagian 5.4.
 *
 * Sebelumnya back langsung menutup aplikasi dari layar mana pun. Sekarang
 * `MainActivity` menanyakan dulu ke halaman ini: kalau bukan Beranda, kembali
 * ke Beranda; kalau sudah di Beranda, baru keluar.
 */
import { useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";

declare global {
  interface Window {
    arulumeBack?: () => boolean;
  }
}

export function BackHandler() {
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    window.arulumeBack = () => {
      if (pathname && pathname !== "/") {
        router.replace("/");
        return true;
      }
      return false;
    };
    return () => {
      window.arulumeBack = undefined;
    };
  }, [pathname, router]);

  return null;
}
