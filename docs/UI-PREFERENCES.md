# UI Preferences & Design System

This document specifies the exact visual, structural, and behavioral standards for the Nexus Parent user interface. All AI agents and contributors must strictly adhere to these rules.

## 1. Aesthetic: Strict Monochrome Tactical Dark Theme

Nexus uses a high-contrast, distraction-free Black & White industrial theme:

- **Background Palette**:
  - Primary Canvas: #000000 / #0a0a0a
  - Sidebars (Left & Right): #121212 (Both sidebars must match)
  - Cards & Floating Panels: #181818 / #202020
- **Text & Accents**:
  - Primary Headings & Active Text: #ffffff
  - Secondary & Muted Labels: #888888 / rgba(255, 255, 255, 0.5)
  - Active Accents: Pure White (#ffffff) or functional states (Live Red: `#ef4444`, Live Green: #22c55e)
  - Translucent Borders: rgba(255, 255, 255, 0.08) to rgba(255, 255, 255, 0.15)

## 2. Absolute No-Emoji Policy

- **NEVER use emojis anywhere** in the UI, code comments, notifications, or terminal outputs.
- Emojis render inconsistently across operating systems and degrade the industrial surveillance aesthetic.
- **Always use SVGs of Lucide**:
  - Desktop: Vector SVGs located in `desktop/src/assets/icons/`
  - Android: Native Vector Drawables in `res/drawable/`
