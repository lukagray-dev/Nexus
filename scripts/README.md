# Nexus Installation Scripts

Quick start scripts for building and installing the Nexus mobile apps.

## Prerequisites

- **JDK 21** installed and in PATH
- **Android SDK** installed at `C:\Users\%USERNAME%\AppData\Local\Android\Sdk`
- **Android Studio** (optional, for manual SDK component installation)
- Connected Android device or emulator with USB debugging enabled

## Scripts

### 1. **Setup-Android-SDK.bat**
Prepares the Android SDK with required components (build-tools, platforms).

**First time setup:**
```batch
D:\Nexus\scripts\Setup-Android-SDK.bat
```

- Accepts SDK licenses automatically
- Installs/downloads required build tools and platform packages
- Takes 5-10 minutes on first run

### 2. **Install-Nexus-Child.bat**
Builds and installs the Nexus Child Android app.

```batch
D:\Nexus\scripts\Install-Nexus-Child.bat
```

**What it does:**
- Automatically runs SDK setup if needed (first time only)
- Accepts SDK licenses
- Builds debug APK via Gradle
- Installs APK on connected device/emulator

**Output:**
- APK: `D:\Nexus\Nexus Child\android\app\build\outputs\apk\debug\app-debug.apk`
- Package: `nexus.android.child`

### 3. **Install-Nexus-Parent.bat**
Builds and installs the Nexus Parent Android app.

```batch
D:\Nexus\scripts\Install-Nexus-Parent.bat
```

**What it does:**
- Automatically runs SDK setup if needed (first time only)
- Accepts SDK licenses
- Builds debug APK via Gradle
- Installs APK on connected device/emulator

**Output:**
- APK: `D:\Nexus\Nexus Parent\mobile\android\app\build\outputs\apk\debug\app-debug.apk`
- Package: `nexus.android.parent`

### 4. **Run-Parent-Desktop.bat**
Installs dependencies and runs the Nexus Parent Desktop app (Electron).

```batch
D:\Nexus\scripts\Run-Parent-Desktop.bat
```

**What it does:**
- Checks Node.js 20+ installation
- Installs npm dependencies
- Runs tests
- Launches Electron desktop app

**Prerequisites:**
- Node.js 20 or later

## Usage

### Quick Start (First Time)
```batch
cd D:\Nexus\scripts

REM Install Child Android app
Install-Nexus-Child.bat

REM Install Parent Android app
Install-Nexus-Parent.bat

REM Run Parent Desktop app
Run-Parent-Desktop.bat
```

### Subsequent Builds
```batch
REM Builds are cached, so subsequent runs are much faster
Install-Nexus-Child.bat
Install-Nexus-Parent.bat
```

## Troubleshooting

### "Java 21 not detected"
- Install JDK 21 from [oracle.com](https://www.oracle.com/java/technologies/downloads/#java21) or [adoptium.net](https://adoptium.net/)
- Add Java bin directory to PATH

### "No devices connected"
- Connect Android device via USB
- Enable USB debugging: Settings → Developer Options → USB Debugging
- Or launch an Android emulator in Android Studio

### Build fails with "LICENSE not accepted"
- Scripts automatically accept licenses
- If manual intervention needed: `C:\Users\%USERNAME%\AppData\Local\Android\Sdk\licenses\`

### APK installed but app doesn't launch
- Check device logs: `adb logcat` (if adb is in PATH)
- Ensure device meets minimum API requirements (Android 9+ for Child, 10+ for Parent)

### "Node.js not found" (Desktop app)
- Install Node.js 20+ from [nodejs.org](https://nodejs.org/)
- Restart terminal after installation

## Build Times

| Script | First Run | Subsequent |
|--------|-----------|-----------|
| Install-Nexus-Child | 5-10 min | 1-2 min |
| Install-Nexus-Parent | 5-10 min | 1-2 min |
| Run-Parent-Desktop | 2-3 min | 30 sec |

## File Locations

- **Child APK**: `D:\Nexus\Nexus Child\android\app\build\outputs\apk\debug\app-debug.apk`
- **Parent APK**: `D:\Nexus\Nexus Parent\mobile\android\app\build\outputs\apk\debug\app-debug.apk`
- **Desktop app**: Runs directly from Electron

## Manual Installation

If scripts don't work, you can install manually:

```batch
REM Child
cd D:\Nexus\Nexus Child\android
gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk

REM Parent Mobile
cd D:\Nexus\Nexus Parent\mobile\android
gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk

REM Parent Desktop
cd D:\Nexus\Nexus Parent\desktop
npm install
npm start
```
