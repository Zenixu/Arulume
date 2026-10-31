<!-- ⚠️ LEGACY — JANGAN DIPAKAI -->
> **Status: legacy (diarsipkan).** Berkas ini adalah ekspor Stitch lama dengan token campuran
> (Material 3 + Arulume). Sumber kebenaran sekarang: `Docs/design.md`, dan ekspor aktif ada di
> `stitch/DESIGN.md`. Folder ini hanya disimpan sebagai riwayat.

---
name: Luminous Dark Utility
colors:
  surface: '#1B1C1F'
  surface-dim: '#121315'
  surface-bright: '#38393b'
  surface-container-lowest: '#0d0e10'
  surface-container-low: '#1b1c1e'
  surface-container: '#1f2022'
  surface-container-high: '#292a2c'
  surface-container-highest: '#343537'
  on-surface: '#e3e2e4'
  on-surface-variant: '#d4c4ae'
  inverse-surface: '#e3e2e4'
  inverse-on-surface: '#303032'
  outline: '#9c8f7b'
  outline-variant: '#504535'
  surface-tint: '#fabc41'
  primary: '#ffd999'
  on-primary: '#422d00'
  primary-container: '#f5b83d'
  on-primary-container: '#6a4a00'
  inverse-primary: '#7c5800'
  secondary: '#c6c6cb'
  on-secondary: '#2e3035'
  secondary-container: '#47494d'
  on-secondary-container: '#b7b8bd'
  tertiary: '#b8e4ff'
  on-tertiary: '#003549'
  tertiary-container: '#68cdff'
  on-tertiary-container: '#005674'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#ffdea8'
  primary-fixed-dim: '#fabc41'
  on-primary-fixed: '#271900'
  on-primary-fixed-variant: '#5e4200'
  secondary-fixed: '#e2e2e7'
  secondary-fixed-dim: '#c6c6cb'
  on-secondary-fixed: '#1a1c1f'
  on-secondary-fixed-variant: '#45474b'
  tertiary-fixed: '#c2e8ff'
  tertiary-fixed-dim: '#78d1ff'
  on-tertiary-fixed: '#001e2c'
  on-tertiary-fixed-variant: '#004d68'
  background: '#121315'
  on-background: '#e3e2e4'
  surface-variant: '#343537'
  surface-2: '#25272B'
  border: '#2F3136'
  text: '#F7F6F3'
  text-2: '#A4A7AE'
  accent-text: '#F5B83D'
  success: '#6FCF97'
  danger: '#FF6B6B'
  warning: '#F2994A'
typography:
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 26px
  body-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 15px
    fontWeight: '500'
    lineHeight: 22px
  body-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 15px
    fontWeight: '400'
    lineHeight: 22px
  label-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 15px
    fontWeight: '600'
    lineHeight: 22px
  caption-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  margin: 1rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
---

# Design Guide: Arulume

Panduan desain dan UI/UX untuk **Arulume**, aplikasi Android offline untuk memperjelas dan memperbesar foto, bagian dari brand **Aruthtale**.

Platform: Android, portrait, 360x800 dp
Kesan: Tenang, hangat, jernih, privat.

Token desain:
- Mode gelap default:
  - bg: #111214
  - surface: #1B1C1F
  - surface-2: #25272B
  - border: #2F3136
  - text: #F7F6F3
  - text-2: #A4A7AE
  - accent: #F5B83D
  - on-accent: #111214
  - accent-text: #F5B83D
  - success: #6FCF97
  - danger: #FF6B6B
  - warning: #F2994A

Tipografi:
- Font: Plus Jakarta Sans
- title: 24/32 700
- heading: 18/26 600
- body: 15/22 400-500
- caption: 12/16 500

Spasi & Bentuk:
- Radius: 12 / 16 / 24 dp
- Tombol utama: tinggi 56 dp, radius 16 dp, warna amber #F5B83D, teks gelap #111214
- Sentuh minimum: 48x48 dp
- Tanpa bayangan berat, flat surfaces, outline icons stroke 1.75-2px.
- Maskot: Kucing hitam siluet minimal dengan mata bulat putih.
