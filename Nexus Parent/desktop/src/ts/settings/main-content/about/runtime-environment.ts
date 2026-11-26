/**
 * @file runtime-environment.ts
 * @description Live Parent System, WebView2 Runtime, and Local Directory Path inspection.
 *
 * Hey friend! Queries Rust `get_parent_system_info` (with browser fallback outside Tauri)
 * to show real OS, CPU architecture, Tauri v2, WebView2 runtime version, GPU acceleration,
 * and openable local Config, Data/Updates, and Downloads folders.
 */

import {
  ParentSystemInfo,
  getParentSystemInfo,
  isTauriRuntime,
  openLocalFolder,
} from '../../../shared/ipc.js';
import { showToast } from '../../../shared/toast.js';
import { settingsState } from '../../sidebar/state.js';
import { createSettingsGroup, escapeHtml } from '../helpers.js';

let cleanupRuntimeSubscriptions: (() => void) | null = null;

/**
 * Formats Rust `std::env::consts::{OS, ARCH}` values into clean human-readable labels.
 */
export function formatOsLabel(os: string | undefined, arch: string | undefined): string {
  const osMap: Record<string, string> = {
    windows: 'Windows',
    macos: 'macOS',
    linux: 'Linux',
  };
  const archMap: Record<string, string> = {
    x86_64: 'x64',
    aarch64: 'ARM64',
    x86: 'x86',
  };
  const rawOs = (os ?? '').trim();
  const rawArch = (arch ?? '').trim();
  const niceOs = osMap[rawOs.toLowerCase()] || rawOs || 'Unknown OS';
  const niceArch = archMap[rawArch.toLowerCase()] || rawArch || 'Unknown';
  return `${niceOs} (${niceArch})`;
}

/**
 * Extracts a concise WebView / Chromium version label when running outside Tauri.
 */
export function collectBrowserRuntimeFallback(): {
  osLabel: string;
  runtimeLabel: string;
  webviewLabel: string;
  cpuCores: number;
} {
  const ua = typeof navigator !== 'undefined' ? navigator.userAgent || '' : '';
  const platform = typeof navigator !== 'undefined' ? navigator.platform || 'Desktop' : 'Desktop';
  const cores = typeof navigator !== 'undefined' ? navigator.hardwareConcurrency || 1 : 1;

  const edgeMatch = ua.match(/Edg\/([\d.]+)/);
  const chromeMatch = ua.match(/Chrome\/([\d.]+)/);
  const webviewLabel = edgeMatch
    ? `WebView2 / Edge ${edgeMatch[1]}`
    : chromeMatch
    ? `Chromium ${chromeMatch[1]}`
    : 'OS WebView';

  return {
    osLabel: platform,
    runtimeLabel: 'Web Preview Mode',
    webviewLabel,
    cpuCores: cores,
  };
}

function buildPathRow(
  idPrefix: string,
  title: string,
  desc: string,
  initialPath: string | null,
  emptyLabel: string
): {
  row: HTMLElement;
  setPath: (nextPath: string | null, nextEmptyLabel?: string) => void;
} {
  const row = document.createElement('div');
  row.className = 'settings-row settings-about-path-row';

  row.innerHTML = `
    <div class="settings-row-label">
      <span class="settings-label-title">${escapeHtml(title)}</span>
      <span class="settings-label-desc">${escapeHtml(desc)}</span>
      <span id="${idPrefix}-val" class="settings-about-path-val"></span>
    </div>
    <div class="settings-device-select-group">
      <button type="button" id="${idPrefix}-copy" class="settings-btn-inline">Copy</button>
      <button type="button" id="${idPrefix}-open" class="settings-btn-inline">Open</button>
    </div>
  `;

  const pathEl = row.querySelector<HTMLElement>(`#${idPrefix}-val`);
  const copyBtn = row.querySelector<HTMLButtonElement>(`#${idPrefix}-copy`);
  const openBtn = row.querySelector<HTMLButtonElement>(`#${idPrefix}-open`);

  let currentPath: string | null = initialPath;

  const setPath = (nextPath: string | null, overrideEmptyLabel?: string): void => {
    currentPath = nextPath && nextPath.trim().length > 0 ? nextPath.trim() : null;
    if (pathEl) {
      pathEl.textContent = currentPath || overrideEmptyLabel || emptyLabel;
      pathEl.classList.toggle('is-muted', !currentPath);
    }
    if (copyBtn) copyBtn.disabled = !currentPath;
    if (openBtn) openBtn.disabled = !currentPath;
  };

  setPath(initialPath);

  copyBtn?.addEventListener('click', async () => {
    if (!currentPath) return;
    try {
      if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(currentPath);
        showToast(`${title} copied`);
      }
    } catch {
      showToast('Failed to copy path');
    }
  });

  openBtn?.addEventListener('click', async () => {
    if (!currentPath) return;
    const opened = await openLocalFolder(currentPath);
    if (!opened) {
      showToast('Folder is not created on disk yet or unavailable');
    }
  });

  return { row, setPath };
}

/**
 * Renders the Parent System & Runtime Environment group for the About page.
 */
export function renderRuntimeEnvironmentGroup(): HTMLElement {
  if (cleanupRuntimeSubscriptions) {
    cleanupRuntimeSubscriptions();
    cleanupRuntimeSubscriptions = null;
  }

  const fallback = collectBrowserRuntimeFallback();
  const gpuEnabled = settingsState.getValue('hardwareAcceleration');

  const grid = document.createElement('div');
  grid.className = 'settings-build-grid';
  grid.innerHTML = `
    <div class="settings-build-tile">
      <span class="settings-build-label">Host OS & Arch</span>
      <span id="about-os-val" class="settings-build-val">${escapeHtml(fallback.osLabel)}</span>
    </div>
    <div class="settings-build-tile">
      <span class="settings-build-label">Core Runtime</span>
      <span id="about-runtime-val" class="settings-build-val">${escapeHtml(fallback.runtimeLabel)}</span>
    </div>
    <div class="settings-build-tile">
      <span class="settings-build-label">WebView Engine</span>
      <span id="about-webview-val" class="settings-build-val">${escapeHtml(fallback.webviewLabel)}</span>
    </div>
    <div class="settings-build-tile">
      <span class="settings-build-label">CPU & GPU</span>
      <span id="about-cpu-gpu-val" class="settings-build-val">${fallback.cpuCores} Cores • GPU ${
        gpuEnabled ? 'On' : 'Off'
      }</span>
    </div>
  `;

  const configRow = buildPathRow(
    'about-path-config',
    'App Config Directory',
    'Stores window-settings.json (tray, startup, Always on Top, GPU flag)',
    null,
    isTauriRuntime() ? 'Resolving...' : 'Available in Tauri desktop runtime'
  );

  const dataRow = buildPathRow(
    'about-path-data',
    'Local Data & Staged Updates',
    'Stores staged GitHub release binaries and verification assets',
    null,
    isTauriRuntime() ? 'Resolving...' : 'Available in Tauri desktop runtime'
  );

  const resolveDownloadsDisplay = (): { path: string | null; emptyLabel: string } => {
    if (settingsState.getValue('askBeforeDownload')) {
      return { path: null, emptyLabel: 'Prompt every time (Save As dialog active)' };
    }
    const dir = settingsState.getValue('downloadsDirectory').trim();
    return {
      path: dir || null,
      emptyLabel: 'Not set yet (asks once on first download)',
    };
  };

  const initialDl = resolveDownloadsDisplay();
  const downloadsRow = buildPathRow(
    'about-path-downloads',
    'Downloads Directory',
    'Target folder for Monitor Storage file exports',
    initialDl.path,
    initialDl.emptyLabel
  );

  const applySystemInfo = (info: ParentSystemInfo | null): void => {
    if (!info || typeof info.tauriVersion !== 'string') return;
    const osEl = grid.querySelector<HTMLElement>('#about-os-val');
    const runtimeEl = grid.querySelector<HTMLElement>('#about-runtime-val');
    const webviewEl = grid.querySelector<HTMLElement>('#about-webview-val');
    const cpuGpuEl = grid.querySelector<HTMLElement>('#about-cpu-gpu-val');

    if (osEl) osEl.textContent = formatOsLabel(info.os, info.arch);
    if (runtimeEl) runtimeEl.textContent = `Tauri v${info.tauriVersion} (Rust)`;
    if (webviewEl) {
      webviewEl.textContent = info.webviewVersion
        ? `WebView2 ${info.webviewVersion}`
        : fallback.webviewLabel;
    }
    if (cpuGpuEl) {
      const accel = settingsState.getValue('hardwareAcceleration') ? 'On' : 'Off';
      const cores = typeof info.cpuCores === 'number' ? info.cpuCores : fallback.cpuCores;
      cpuGpuEl.textContent = `${cores} Cores • GPU ${accel}`;
    }

    configRow.setPath(info.configDir ?? null, 'Unavailable');
    dataRow.setPath(info.localDataDir ?? null, 'Unavailable');
  };

  if (isTauriRuntime()) {
    void getParentSystemInfo().then(applySystemInfo);
  }

  let disposed = false;
  let wasMounted = false;
  let unsubSettings: () => void = () => {};

  const onWorkspaceReset = () => syncReactiveFields();

  const dispose = (): void => {
    if (disposed) return;
    disposed = true;
    unsubSettings();
    if (typeof window !== 'undefined' && typeof window.removeEventListener === 'function') {
      window.removeEventListener('nexus-workspace-reset', onWorkspaceReset);
    }
  };

  function syncReactiveFields(): void {
    if (grid.isConnected === true) {
      wasMounted = true;
    } else if (wasMounted && grid.isConnected === false) {
      dispose();
      return;
    }
    if (disposed) return;
    const dl = resolveDownloadsDisplay();
    downloadsRow.setPath(dl.path, dl.emptyLabel);
    const cpuGpuEl = grid.querySelector<HTMLElement>('#about-cpu-gpu-val');
    if (cpuGpuEl) {
      const prefix = cpuGpuEl.textContent?.split('•')[0]?.trim() || `${fallback.cpuCores} Cores`;
      cpuGpuEl.textContent = `${prefix} • GPU ${
        settingsState.getValue('hardwareAcceleration') ? 'On' : 'Off'
      }`;
    }
  }

  unsubSettings = settingsState.onValuesChange(syncReactiveFields);
  if (typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
    window.addEventListener('nexus-workspace-reset', onWorkspaceReset);
  }
  cleanupRuntimeSubscriptions = dispose;

  return createSettingsGroup('SYSTEM & RUNTIME ENVIRONMENT', [
    grid,
    configRow.row,
    dataRow.row,
    downloadsRow.row,
  ]);
}
