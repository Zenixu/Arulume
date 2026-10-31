import type { NextConfig } from "next";
import { fileURLToPath } from "node:url";

const projectRoot = fileURLToPath(new URL(".", import.meta.url));

/**
 * Konfigurasi wajib untuk Capacitor (Docs/BUILD.md bagian 2.1):
 * - output: 'export'      → hanya file statis, tidak ada server
 * - images.unoptimized    → optimasi gambar butuh server, dilarang
 * - trailingSlash         → URL /path/ konsisten saat dibuka dari file lokal
 *
 * JANGAN memakai fitur yang butuh server (API routes, SSR, middleware).
 */
const nextConfig: NextConfig = {
  output: "export",
  images: { unoptimized: true },
  trailingSlash: true,
  turbopack: {
    // Batasi root ke folder proyek, supaya Turbopack tidak memindai direktori home.
    root: projectRoot,
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default nextConfig;
