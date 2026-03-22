<a id="top"></a>

<p align="center">
  <img src="assets/nexus.png" alt="Nexus Logo" width="170" />
</p>

<h1 align="center">Nexus</h1>

<p align="center">
  <b>Parental Control Platform</b><br/>
  Child Agent + Parent Dashboards + Email Service
</p>

<p align="center">
  <img src="https://img.shields.io/badge/License-MIT-f5b301?style=for-the-badge" alt="MIT License" />
  <img src="https://img.shields.io/badge/Android-10%2B-34A853?style=for-the-badge&logo=android&logoColor=white" alt="Android 10+" />
  <img src="https://img.shields.io/badge/Electron-28%2B-47848F?style=for-the-badge&logo=electron&logoColor=white" alt="Electron 28+" />
  <img src="https://img.shields.io/badge/Node.js-18%2B-339933?style=for-the-badge&logo=node.js&logoColor=white" alt="Node.js 18+" />
</p>

<p align="center">
  <a href="#overview"><b>Overview</b></a> •
  <a href="#monorepo-layout"><b>Layout</b></a> •
  <a href="#tech-stack"><b>Tech Stack</b></a> •
  <a href="#quick-start"><b>Quick Start</b></a> •
  <a href="#community"><b>Community</b></a>
</p>

---

## Overview

Nexus is a multi-application parental control system composed of:

- A child-side Android agent
- Parent dashboards for Android and desktop
- An OTP/password-reset email service
- A branded landing site

> [!IMPORTANT]
> Nexus must be used only on devices you lawfully own/manage and in compliance with local laws and consent requirements.

## Monorepo Layout

```text
Nexus/
|-- Nexus Child/android/           # Android child-side agent (Kotlin)
|-- Nexus Parent/mobile/android/   # Android parent dashboard (Kotlin + XML)
|-- Nexus Parent/desktop/          # Desktop parent dashboard (Electron)
|-- e-mail/                        # OTP + password reset service (Node.js/Express)
|-- landing/                       # Landing page (HTML/CSS/JS)
|-- assets/                        # Shared media and branding assets
`-- .github/                       # Repo metadata
```

## Highlighted Capabilities

- Real-time parent-child signaling via Firebase Realtime Database
- WebRTC media/control sessions between parent and child components
- Parent monitoring interfaces on Android and desktop
- Email verification and password recovery workflows
- Static marketing/landing experience for distribution and onboarding

## Tech Stack

| Area | Stack |
| --- | --- |
| Child App | Kotlin, Android SDK, Gradle, Firebase Realtime Database, WebRTC |
| Parent Android App | Kotlin, XML UI, Gradle, Firebase, WebRTC |
| Parent Desktop App | Electron, Node.js, HTML, CSS, JavaScript |
| Email Service | Node.js, Express, Firebase Admin SDK, Brevo API |
| Landing Site | HTML5, CSS3, JavaScript, Three.js, GSAP |

## Quick Start

### 1) Clone

```bash
git clone https://github.com/soumo-git/Nexus.git
cd Nexus
```

### 2) Parent Desktop

```bash
cd "Nexus Parent/desktop"
npm install
npm run dev
```

### 3) Parent Android

```bash
cd "Nexus Parent/mobile/android"
./gradlew assembleDebug
```

### 4) Child Android

```bash
cd "Nexus Child/android"
./gradlew assembleDebug
```

### 5) Email Service

```bash
cd "e-mail"
npm install
cp env.example .env
npm run dev
```

### 6) Landing Site

Open `landing/index.html` in a browser, or deploy the directory as a static site.

## Configuration Notes

- Release history and transparency are tracked in `CHANGELOG.md`.
- `google-services.json` is required in Android modules.
- Email service credentials must be supplied via environment variables.
- Never commit secrets, private keys, or service-account files.

## Module Docs

- `Nexus Child/android/README.md`
- `Nexus Parent/mobile/android/README.md`
- `Nexus Parent/desktop/README.md`
- `e-mail/README.md`
- `landing/README.md`

## Community

- Contribution guide: `CONTRIBUTING.md`
- Code of conduct: `CODE_OF_CONDUCT.md`
- Security policy: `SECURITY.md`
- License: `LICENSE` (MIT)

---

<p align="center">
  <b>Nexus</b> • Responsible parental control tooling
</p>

<p align="center">
  Maintained by <b>Soumo Mukherjee</b> • <a href="mailto:soumom764@gmail.com">soumom764@gmail.com</a>
</p>

<p align="center">
  <a href="#top">Back to top</a>
</p>
