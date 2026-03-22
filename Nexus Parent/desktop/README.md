<a id="top"></a>

<p align="center">
  <img src="../assets/nexus_parent.png" alt="Nexus Parent Logo" width="170" />
</p>

<h1 align="center">Nexus Parent Desktop</h1>

<p align="center">
  Electron desktop dashboard for real-time child-device monitoring and control
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Desktop-Electron-47848F?style=for-the-badge&logo=electron&logoColor=white" alt="Electron" />
  <img src="https://img.shields.io/badge/Node.js-18%2B-339933?style=for-the-badge&logo=node.js&logoColor=white" alt="Node.js 18+" />
  <img src="https://img.shields.io/badge/Auth-Supabase-3ECF8E?style=for-the-badge&logo=supabase&logoColor=white" alt="Supabase" />
  <img src="https://img.shields.io/badge/Realtime-Firebase%20%2B%20WebRTC-FFCA28?style=for-the-badge&logo=firebase&logoColor=black" alt="Firebase + WebRTC" />
</p>

<p align="center">
  <a href="#overview"><b>Overview</b></a> •
  <a href="#architecture"><b>Architecture</b></a> •
  <a href="#feature-modules-24"><b>Features</b></a> •
  <a href="#setup"><b>Setup</b></a> •
  <a href="#automation-scripts"><b>Scripts</b></a> •
  <a href="#build"><b>Build</b></a>
</p>

---

## Overview

Nexus Parent Desktop is the primary desktop control surface for the Nexus platform. It combines:

- Supabase-based parent authentication,
- Firebase signaling + WebRTC session setup,
- dynamic feature-window loading for monitoring/control modules,
- Electron IPC for native desktop controls and dialogs.

## Architecture

### Main Process

- `src/main.js` handles:
  - app lifecycle,
  - custom protocol deep links (`nexus://...`) for auth/reset flows,
  - frameless window creation,
  - window IPC handlers (`minimize`, `maximize`, `close`, file dialogs).

### Renderer App Shell

- `dist/js/app.js`: top-level app controller.
- `dist/js/managers/ui-manager.js`: screen transitions and top-bar/profile interactions.
- `dist/js/managers/window-manager.js`: draggable feature windows with z-index management.
- `dist/js/loaders/feature-loader.js`: dynamic HTML/CSS/JS feature module loader.

### Auth and Connection

- `dist/js/managers/auth/auth-manager.js`: Supabase auth state, verification deep-link handling, password reset flow.
- `dist/js/managers/connection-manager.js`: child ID validation, WebRTC manager orchestration, data-channel event dispatch.
- `dist/js/webrtc/webrtc-manager.js`: offer/answer, ICE exchange, track routing, renegotiation support.

## Feature Modules (24)

### Monitoring/Control Modules

- `camera`, `screen`, `mic`
- `location`, `sms`, `calllog`, `notifications`, `chats`, `keyboard`
- `gmail`, `files`, `wellbeing`, `applock`, `wallpaper`, `vibrateflash`

### Social Panels

- `whatsapp`, `facebook`, `instagram`, `messenger`, `snapchat`, `telegram`

### AI Panels

- `chatgpt`, `claude`, `grok`

### Command/Event Pattern

- Features send commands through `app.connectionManager.sendCommand(...)`.
- Connection manager translates data-channel responses into custom browser events (camera/screen/mic confirmations, data feeds, status updates).

## Project Layout

```text
desktop/
|-- src/main.js                 # Electron main process
|-- dist/index.html             # Renderer entry
|-- dist/js/app.js              # App orchestrator
|-- dist/js/managers/           # UI/window/auth/connection managers
|-- dist/js/webrtc/             # WebRTC + signaling orchestration
|-- dist/js/loaders/            # Dynamic component/feature loaders
|-- dist/js/features/           # 24 feature modules
|-- dist/js/ipc-bridge.js       # Renderer IPC helper
|-- package.json
`-- README.md
```

## Setup

### Prerequisites

- Node.js 18+
- npm

### Install and Run

```bash
cd "Nexus Parent/desktop"
npm install
npm run dev
```

Production-like local run:

```bash
npm start
```

## Automation Scripts

Project-level Windows batch scripts are available in:

- `D:\Nexus\scripts`

Desktop scripts:

- `parent-desktop-test.bat` -> runs `npm test -- --run`
- `parent-desktop-build-debug.bat` -> builds unpacked debug app (`electron-builder --win --dir`)
- `parent-desktop-build-release.bat` -> builds Windows release installer (`npm run build:win`)

Run from anywhere:

```bat
D:\Nexus\scripts\parent-desktop-test.bat
```

## Build

Windows installer build:

```bash
npm run build:win
```

`electron-builder` output is configured to `release/`.

## Configuration

- Firebase renderer config:
  - `dist/js/firebase-config.js`
- Supabase renderer config:
  - `dist/js/supabase-config.js`

If you deploy your own backend/project, update both files accordingly.

## Desktop UX Notes

- Frameless window with custom title-bar controls via IPC bridge.
- Feature windows are draggable, focus-aware, and independently closable.
- Keyboard shortcuts:
  - `F12`: toggle DevTools
  - `Ctrl+R`: reload renderer

## Troubleshooting

- Cannot connect to child: verify Firebase config and child online state.
- No stream/command response: inspect data-channel state in DevTools logs.
- Auth callback not handled: verify protocol registration and deep-link payload.

> [!IMPORTANT]
> Use only in lawful parental-control contexts with proper ownership, disclosure, and consent as required by applicable law.

---

<p align="center">
  Nexus Parent Desktop • Electron control console
</p>

<p align="center">
  <a href="#top">Back to top</a>
</p>
