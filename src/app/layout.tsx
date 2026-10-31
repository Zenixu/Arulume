import type { Metadata, Viewport } from "next";
import localFont from "next/font/local";
import "./globals.css";
import { BackHandler } from "@/components/back-handler";

/**
 * Font dibundel lokal — tidak boleh memuat dari CDN/jaringan (AGENTS.md aturan 1).
 * Berkas: src/app/fonts/plus-jakarta-sans-*.woff2 (variable, wght 400-800).
 */
const jakarta = localFont({
  src: [
    {
      path: "./fonts/plus-jakarta-sans-latin.woff2",
      weight: "400 800",
      style: "normal",
    },
    {
      path: "./fonts/plus-jakarta-sans-latin-ext.woff2",
      weight: "400 800",
      style: "normal",
    },
  ],
  variable: "--font-jakarta",
  display: "swap",
  fallback: ["system-ui", "sans-serif"],
});

export const metadata: Metadata = {
  title: "Arulume",
  description: "Perjelas foto dan video lama, semuanya di HP tanpa internet.",
  applicationName: "Arulume",
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  maximumScale: 1,
  userScalable: false,
  viewportFit: "cover",
  themeColor: "#111214",
};

/**
 * Tema: gelap sebagai default. Skrip ini hanya menerapkan pilihan tersimpan
 * (mode terang) sebelum paint supaya tidak ada kedipan.
 */
const themeScript = `
try {
  var t = localStorage.getItem("arulume.theme");
  if (t === "system") {
    t = window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
  }
  if (t === "light" || t === "dark") document.documentElement.dataset.theme = t;
} catch (e) {}
`;

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="id" data-theme="dark" className={`${jakarta.variable} h-full`}>
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeScript }} />
      </head>
      <body className="min-h-full bg-bg text-text antialiased">
        <BackHandler />
        {children}
      </body>
    </html>
  );
}
