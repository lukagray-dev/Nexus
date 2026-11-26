/**
 * @file device-state.ts
 * @description Central device connection & pairing state manager for Nexus Parent.
 *
 * Hey friend! This manages whether a target child phone is currently paired and connected.
 * In Nexus, parents enter a 12-digit Child ID to establish connection.
 * This state manager connects to our WebRtcManager and SignalingClient:
 * - Tracks the active connection, the 12-digit ID, and expanded hardware/posture telemetry.
 * - Caches the last connected device's telemetry snapshot in localStorage so Paired Devices
 *   can display grayed-out details even when disconnected or after an app restart.
 * - Notifies all UI components whenever a child device connects, disconnects, or updates telemetry.
 */

import { webrtcManager } from '../webrtc/webrtc-manager.js';
import { reconnectManager } from './reconnect-manager.js';

export interface DeviceTelemetryUpdate {
  batteryPercent?: number;
  isCharging?: boolean;
  networkState?: string;
  deviceName?: string;
  model?: string;
  manufacturer?: string;
  androidVersion?: string;
  freeStorageBytes?: number;
  totalStorageBytes?: number;
  sdkInt?: number;
  brand?: string;
  appVersion?: string;
  chargingSource?: string;
  batteryTemperatureC?: number;
  isPowerSaveMode?: boolean;
  batteryOptimizationIgnored?: boolean;
  totalRamBytes?: number;
  availableRamBytes?: number;
  isScreenOn?: boolean;
  screenResolution?: string;
  ringerMode?: string;
  simCarrier?: string;
  deviceUptimeMs?: number;
  isStealthMode?: boolean;
  isAppLockEnabled?: boolean;
  grantedPermissionsCount?: number;
  totalPermissionsCount?: number;
}

/**
 * Persisted snapshot of the most recent telemetry received from a paired child device.
 */
export interface LastDeviceTelemetrySnapshot {
  batteryPercent?: number;
  isCharging?: boolean;
  networkState?: string;
  androidVersion?: string;
  freeStorageBytes?: number;
  totalStorageBytes?: number;
  storageUsageText?: string;
  sdkInt?: number;
  brand?: string;
  appVersion?: string;
  chargingSource?: string;
  batteryTemperatureC?: number;
  isPowerSaveMode?: boolean;
  batteryOptimizationIgnored?: boolean;
  totalRamBytes?: number;
  availableRamBytes?: number;
  ramUsageText?: string;
  isScreenOn?: boolean;
  screenResolution?: string;
  ringerMode?: string;
  simCarrier?: string;
  deviceUptimeMs?: number;
  isStealthMode?: boolean;
  isAppLockEnabled?: boolean;
  grantedPermissionsCount?: number;
  totalPermissionsCount?: number;
}

/**
 * Metadata record for a previously connected child device saved in local storage.
 */
export interface LastConnectedDevice {
  /** Raw unformatted 12-digit numeric identifier, e.g. "482194021104" */
  rawId: string;
  /** Visually chunked 12-digit string, e.g. "4821 9402 1104" */
  formattedId: string;
  /** Friendly device name, e.g. "Pixel 8 Pro" or "Lucas Galaxy S24" */
  name?: string;
  /** Hardware model identifier, e.g. "Google Pixel 8 Pro (Android 14)" */
  model?: string;
  /** Timestamp when the device was last connected */
  lastConnectedAt?: number;
  /** Cached telemetry snapshot for offline / grayed-out Paired Devices inspection */
  lastTelemetry?: LastDeviceTelemetrySnapshot;
}

export interface DeviceInfo {
  /** The 12-digit formatted Child ID, e.g. "4821 9402 1104" */
  id: string;
  /** Raw unformatted 12-digit ID string */
  rawId?: string;
  /** Friendly device display name, e.g. "Lucas Galaxy S24" or "Pixel 8 Pro" */
  name: string;
  /** Full hardware model description, e.g. "Samsung SM-S928B" */
  model: string;
  /** Android OS release version, e.g. "14" */
  androidVersion?: string;
  /** Android SDK API level, e.g. 34 */
  sdkInt?: number;
  /** Hardware brand, e.g. "samsung" */
  brand?: string;
  /** Installed Nexus Child app version, e.g. "0.1.0-alpha" */
  appVersion?: string;
  /** Current battery percentage from 0 to 100 */
  batteryPercent: number;
  /** Whether the device is currently charging */
  isCharging?: boolean;
  /** Active charging source ("AC", "USB", "Wireless", "Plugged", or "Battery") */
  chargingSource?: string;
  /** Battery temperature in degrees Celsius */
  batteryTemperatureC?: number;
  /** Whether Android Battery Saver is currently active */
  isPowerSaveMode?: boolean;
  /** Whether Nexus Child is exempted from Android Doze / Battery Optimization */
  batteryOptimizationIgnored?: boolean;
  /** Network connection type: "WiFi", "Mobile Data", or "Offline" */
  networkState?: string;
  /** Current cellular/WiFi signal strength description */
  signalStatus: string;
  /** Whether the child device is actively online and sending heartbeats */
  isOnline: boolean;
  /** Human-readable timestamp of last telemetry sync */
  lastSeen: string;
  /** Free internal storage in bytes */
  freeStorageBytes?: number;
  /** Total internal storage in bytes */
  totalStorageBytes?: number;
  /** Formatted storage status, e.g. "60.0 GB free of 238.4 GB" */
  storageUsageText?: string;
  /** Available system RAM in bytes */
  availableRamBytes?: number;
  /** Total system RAM in bytes */
  totalRamBytes?: number;
  /** Formatted RAM status, e.g. "4.2 GB free / 8.0 GB" */
  ramUsageText?: string;
  /** Whether the child screen is currently awake / interactive */
  isScreenOn?: boolean;
  /** Physical display resolution, e.g. "1080x2340" */
  screenResolution?: string;
  /** Active ringer profile ("Normal", "Vibrate", "Silent") */
  ringerMode?: string;
  /** Active SIM card / telecom carrier summary */
  simCarrier?: string;
  /** Milliseconds elapsed since device boot */
  deviceUptimeMs?: number;
  /** Whether the child app launcher icon is hidden (Stealth Mode active) */
  isStealthMode?: boolean;
  /** Whether App Lock PIN guard is active on the child app */
  isAppLockEnabled?: boolean;
  /** Number of granted permissions out of total managed permissions */
  grantedPermissionsCount?: number;
  totalPermissionsCount?: number;
}

export type DeviceChangeListener = () => void;
export type DeviceTelemetryListener = (device: DeviceInfo) => void;
export type UnexpectedDisconnectListener = (device: DeviceInfo) => void;

class DeviceStateManager {
  private connected: boolean = false;
  private currentDevice: DeviceInfo | null = null;
  private lastDevice: LastConnectedDevice | null = null;
  private deviceHistory: LastConnectedDevice[] = [];
  private readonly LAST_DEVICE_STORAGE_KEY = 'nexus_last_connected_child_device';
  private readonly PAIRED_DEVICES_HISTORY_STORAGE_KEY = 'nexus_paired_devices_history_v1';
  private listeners: Set<DeviceChangeListener> = new Set();
  private telemetryListeners: Set<DeviceTelemetryListener> = new Set();
  private unexpectedDisconnectListeners: Set<UnexpectedDisconnectListener> = new Set();
  /** Tracks whether the disconnection was intentionally triggered by the user */
  private isManualDisconnect: boolean = false;
  private memoryStorage: Map<string, string> = new Map();

  constructor() {
    this.loadLastConnectedDevice();
    this.loadDeviceHistory();
    this.attachReconnectHandlers();

    webrtcManager.onConnectionStateChange((state) => {
      console.log('[DeviceState] WebRTC connection state changed to:', state);
      if (state === 'disconnected' || state === 'failed' || state === 'closed') {
        const wasConnected = this.connected;
        const target = this.currentDevice;

        const shouldAutoReconnect = wasConnected && target && !this.isManualDisconnect;
        if (shouldAutoReconnect && target) {
          console.log('[DeviceState] Unexpected connection drop detected. Triggering auto-reconnect...');
          const disconnectedSnapshot = { ...target };
          const cleanRaw = target.rawId ? target.rawId.replace(/\D/g, '') : target.id.replace(/\D/g, '');
          if (cleanRaw.length === 12) {
            reconnectManager.start({
              rawId: cleanRaw,
              formattedId: target.id,
              name: target.name,
              model: target.model,
            });
          }
          this.notifyUnexpectedDisconnect(disconnectedSnapshot);
        }

        if (this.connected) {
          console.log('[DeviceState] Connection lost. Setting connected state to false.');
          this.connected = false;
          if (!reconnectManager.isReconnecting()) {
            this.currentDevice = null;
          }
          this.notify();
        }

        this.isManualDisconnect = false;
      } else if (state === 'connected') {
        this.isManualDisconnect = false;
        reconnectManager.handleSuccess();
        if (!this.connected && this.currentDevice) {
          this.connected = true;
          this.notify();
        }
      }
    });

    // Listen for live hardware telemetry updates incoming over the DataChannel
    webrtcManager.onMessage((data) => {
      if (typeof data !== 'string') return;
      try {
        const packet = JSON.parse(data);
        if (packet && packet.type === 'DEVICE_TELEMETRY') {
          console.log('[DeviceState] Live telemetry packet received from child:', packet);
          this.updateTelemetry({
            batteryPercent: packet.batteryPercent,
            isCharging: packet.isCharging,
            networkState: packet.networkState,
            deviceName: packet.deviceName,
            model: packet.model,
            manufacturer: packet.manufacturer,
            androidVersion: packet.androidVersion,
            freeStorageBytes: packet.freeStorageBytes,
            totalStorageBytes: packet.totalStorageBytes,
            sdkInt: packet.sdkInt,
            brand: packet.brand,
            appVersion: packet.appVersion,
            chargingSource: packet.chargingSource,
            batteryTemperatureC: packet.batteryTemperatureC,
            isPowerSaveMode: packet.isPowerSaveMode,
            batteryOptimizationIgnored: packet.batteryOptimizationIgnored,
            totalRamBytes: packet.totalRamBytes,
            availableRamBytes: packet.availableRamBytes,
            isScreenOn: packet.isScreenOn,
            screenResolution: packet.screenResolution,
            ringerMode: packet.ringerMode,
            simCarrier: packet.simCarrier,
            deviceUptimeMs: packet.deviceUptimeMs,
            isStealthMode: packet.isStealthMode,
            isAppLockEnabled: packet.isAppLockEnabled,
            grantedPermissionsCount: packet.grantedPermissionsCount,
            totalPermissionsCount: packet.totalPermissionsCount,
          });
        }
      } catch {
        // Plain text or unformatted command
      }
    });
  }

  private getStorageItem(key: string): string | null {
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        return window.localStorage.getItem(key);
      }
    } catch (error) {
      console.warn('[DeviceState] localStorage read error, falling back to memory:', error);
    }
    return this.memoryStorage.get(key) ?? null;
  }

  private setStorageItem(key: string, value: string): void {
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        window.localStorage.setItem(key, value);
      }
    } catch (error) {
      console.warn('[DeviceState] localStorage write error, falling back to memory:', error);
    }
    this.memoryStorage.set(key, value);
  }

  private removeStorageItem(key: string): void {
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        window.localStorage.removeItem(key);
      }
    } catch (error) {
      console.warn('[DeviceState] localStorage remove error, falling back to memory:', error);
    }
    this.memoryStorage.delete(key);
  }

  /**
   * Builds a persistable telemetry snapshot from a live `DeviceInfo` record.
   */
  private buildTelemetrySnapshot(info: DeviceInfo): LastDeviceTelemetrySnapshot {
    return {
      batteryPercent: info.batteryPercent,
      isCharging: info.isCharging,
      networkState: info.networkState,
      androidVersion: info.androidVersion,
      freeStorageBytes: info.freeStorageBytes,
      totalStorageBytes: info.totalStorageBytes,
      storageUsageText: info.storageUsageText,
      sdkInt: info.sdkInt,
      brand: info.brand,
      appVersion: info.appVersion,
      chargingSource: info.chargingSource,
      batteryTemperatureC: info.batteryTemperatureC,
      isPowerSaveMode: info.isPowerSaveMode,
      batteryOptimizationIgnored: info.batteryOptimizationIgnored,
      totalRamBytes: info.totalRamBytes,
      availableRamBytes: info.availableRamBytes,
      ramUsageText: info.ramUsageText,
      isScreenOn: info.isScreenOn,
      screenResolution: info.screenResolution,
      ringerMode: info.ringerMode,
      simCarrier: info.simCarrier,
      deviceUptimeMs: info.deviceUptimeMs,
      isStealthMode: info.isStealthMode,
      isAppLockEnabled: info.isAppLockEnabled,
      grantedPermissionsCount: info.grantedPermissionsCount,
      totalPermissionsCount: info.totalPermissionsCount,
    };
  }

  public loadLastConnectedDevice(): LastConnectedDevice | null {
    try {
      const serialized = this.getStorageItem(this.LAST_DEVICE_STORAGE_KEY);
      if (serialized) {
        const parsed = JSON.parse(serialized) as LastConnectedDevice;
        if (parsed && typeof parsed.rawId === 'string' && parsed.rawId.length === 12) {
          this.lastDevice = parsed;
          return this.lastDevice;
        }
      }
    } catch (err) {
      console.warn('[DeviceState] Could not parse previously connected device from storage:', err);
    }
    this.lastDevice = null;
    return null;
  }

  /**
   * Loads the full list of previously connected child devices from persistent storage,
   * seeding from `lastDevice` if upgrading from a single-device storage layout.
   */
  public loadDeviceHistory(): LastConnectedDevice[] {
    try {
      const serialized = this.getStorageItem(this.PAIRED_DEVICES_HISTORY_STORAGE_KEY);
      if (serialized) {
        const parsed = JSON.parse(serialized) as unknown;
        if (Array.isArray(parsed)) {
          this.deviceHistory = parsed.filter(
            (item): item is LastConnectedDevice =>
              Boolean(item) &&
              typeof (item as LastConnectedDevice).rawId === 'string' &&
              (item as LastConnectedDevice).rawId.length === 12
          );
          return this.getPairedDevicesHistory();
        }
      }
    } catch (err) {
      console.warn('[DeviceState] Could not parse paired devices history from storage:', err);
    }

    this.deviceHistory = this.lastDevice ? [this.lastDevice] : [];
    return this.getPairedDevicesHistory();
  }

  /**
   * Inserts or updates a device record in the persistent paired devices history.
   */
  private upsertDeviceHistory(device: LastConnectedDevice): void {
    const existing = this.deviceHistory.find((entry) => entry.rawId === device.rawId);
    const merged: LastConnectedDevice = {
      ...existing,
      ...device,
      lastTelemetry: device.lastTelemetry ?? existing?.lastTelemetry,
    };
    this.deviceHistory = [
      merged,
      ...this.deviceHistory.filter((entry) => entry.rawId !== device.rawId),
    ];
    try {
      this.setStorageItem(
        this.PAIRED_DEVICES_HISTORY_STORAGE_KEY,
        JSON.stringify(this.deviceHistory)
      );
    } catch (err) {
      console.warn('[DeviceState] Failed to persist paired devices history:', err);
    }
  }

  /**
   * Returns all previously connected child devices ordered with any active device first,
   * then sorted by most recent connection timestamp descending.
   */
  public getPairedDevicesHistory(): LastConnectedDevice[] {
    const activeRawId = this.connected ? this.currentDevice?.rawId : undefined;
    return [...this.deviceHistory].sort((a, b) => {
      if (activeRawId && a.rawId === activeRawId) return -1;
      if (activeRawId && b.rawId === activeRawId) return 1;
      return (b.lastConnectedAt ?? 0) - (a.lastConnectedAt ?? 0);
    });
  }

  /**
   * Finds a specific device record from history (or `lastDevice`) by its 12-digit ID.
   */
  public getPairedDeviceRecord(rawId: string): LastConnectedDevice | null {
    const cleanRaw = rawId.replace(/\D/g, '');
    return (
      this.deviceHistory.find((entry) => entry.rawId === cleanRaw) ||
      (this.lastDevice?.rawId === cleanRaw ? this.lastDevice : null)
    );
  }

  public getLastConnectedDevice(): LastConnectedDevice | null {
    return this.lastDevice;
  }

  public saveLastConnectedDevice(device: LastConnectedDevice): void {
    const existing = this.getPairedDeviceRecord(device.rawId);
    const mergedDevice: LastConnectedDevice = {
      ...existing,
      ...device,
      lastTelemetry: device.lastTelemetry ?? existing?.lastTelemetry,
    };
    this.lastDevice = mergedDevice;
    this.upsertDeviceHistory(mergedDevice);
    try {
      this.setStorageItem(this.LAST_DEVICE_STORAGE_KEY, JSON.stringify(mergedDevice));
    } catch (err) {
      console.warn('[DeviceState] Failed to persist last connected device to storage:', err);
    }
    this.notify();
  }

  public clearLastConnectedDevice(): void {
    this.lastDevice = null;
    this.removeStorageItem(this.LAST_DEVICE_STORAGE_KEY);
    this.notify();
  }

  /**
   * Clears both the last-connected shortcut and the full paired devices history.
   */
  public clearPairedDevicesHistory(): void {
    this.lastDevice = null;
    this.deviceHistory = [];
    this.removeStorageItem(this.LAST_DEVICE_STORAGE_KEY);
    this.removeStorageItem(this.PAIRED_DEVICES_HISTORY_STORAGE_KEY);
    this.notify();
  }

  public isConnected(): boolean {
    return this.connected;
  }

  public getConnectedDevice(): DeviceInfo | null {
    return this.currentDevice;
  }

  public setConnectedDevice(info: DeviceInfo): void {
    this.connected = true;
    const cleanRaw = info.rawId ? info.rawId.replace(/\D/g, '') : info.id.replace(/\D/g, '');
    this.currentDevice = {
      ...info,
      rawId: cleanRaw.length === 12 ? cleanRaw : info.rawId,
    };

    if (cleanRaw.length === 12) {
      this.saveLastConnectedDevice({
        rawId: cleanRaw,
        formattedId: this.formatId(cleanRaw),
        name: info.name,
        model: info.model,
        lastConnectedAt: Date.now(),
        lastTelemetry: this.buildTelemetrySnapshot(this.currentDevice),
      });
    }

    this.notify();
  }

  public isStealthMode(): boolean {
    return this.currentDevice?.isStealthMode === true;
  }

  public setStealthMode(isStealth: boolean): void {
    if (!this.currentDevice) return;
    if (this.currentDevice.isStealthMode !== isStealth) {
      this.currentDevice.isStealthMode = isStealth;
      this.syncCachedLastDevice();
      this.notify();
    }
  }

  public updateTelemetry(telemetry: DeviceTelemetryUpdate): void {
    if (!this.currentDevice) return;

    if (telemetry.batteryPercent !== undefined) {
      this.currentDevice.batteryPercent = telemetry.batteryPercent;
    }
    if (telemetry.isCharging !== undefined) {
      this.currentDevice.isCharging = telemetry.isCharging;
    }
    if (telemetry.networkState !== undefined) {
      this.currentDevice.networkState = telemetry.networkState;
      this.currentDevice.signalStatus = telemetry.networkState;
    }
    if (telemetry.androidVersion) {
      this.currentDevice.androidVersion = telemetry.androidVersion;
    }
    if (telemetry.deviceName) {
      this.currentDevice.name = telemetry.deviceName;
    } else if (telemetry.model) {
      this.currentDevice.name = telemetry.model;
    }
    if (telemetry.model) {
      const mfg = telemetry.manufacturer ? `${telemetry.manufacturer} ` : '';
      this.currentDevice.model = `${mfg}${telemetry.model}`.trim();
    }
    if (telemetry.freeStorageBytes !== undefined && telemetry.totalStorageBytes !== undefined) {
      this.currentDevice.freeStorageBytes = telemetry.freeStorageBytes;
      this.currentDevice.totalStorageBytes = telemetry.totalStorageBytes;
      this.currentDevice.storageUsageText = this.formatStorage(
        telemetry.freeStorageBytes,
        telemetry.totalStorageBytes
      );
    }
    if (telemetry.sdkInt !== undefined) {
      this.currentDevice.sdkInt = telemetry.sdkInt;
    }
    if (telemetry.brand !== undefined) {
      this.currentDevice.brand = telemetry.brand;
    }
    if (telemetry.appVersion !== undefined) {
      this.currentDevice.appVersion = telemetry.appVersion;
    }
    if (telemetry.chargingSource !== undefined) {
      this.currentDevice.chargingSource = telemetry.chargingSource;
    }
    if (telemetry.batteryTemperatureC !== undefined) {
      this.currentDevice.batteryTemperatureC = telemetry.batteryTemperatureC;
    }
    if (telemetry.isPowerSaveMode !== undefined) {
      this.currentDevice.isPowerSaveMode = telemetry.isPowerSaveMode;
    }
    if (telemetry.batteryOptimizationIgnored !== undefined) {
      this.currentDevice.batteryOptimizationIgnored = telemetry.batteryOptimizationIgnored;
    }
    if (telemetry.totalRamBytes !== undefined && telemetry.availableRamBytes !== undefined) {
      this.currentDevice.totalRamBytes = telemetry.totalRamBytes;
      this.currentDevice.availableRamBytes = telemetry.availableRamBytes;
      this.currentDevice.ramUsageText = this.formatRam(
        telemetry.availableRamBytes,
        telemetry.totalRamBytes
      );
    }
    if (telemetry.isScreenOn !== undefined) {
      this.currentDevice.isScreenOn = telemetry.isScreenOn;
    }
    if (telemetry.screenResolution !== undefined) {
      this.currentDevice.screenResolution = telemetry.screenResolution;
    }
    if (telemetry.ringerMode !== undefined) {
      this.currentDevice.ringerMode = telemetry.ringerMode;
    }
    if (telemetry.simCarrier !== undefined) {
      this.currentDevice.simCarrier = telemetry.simCarrier;
    }
    if (telemetry.deviceUptimeMs !== undefined) {
      this.currentDevice.deviceUptimeMs = telemetry.deviceUptimeMs;
    }
    if (telemetry.isStealthMode !== undefined) {
      this.currentDevice.isStealthMode = telemetry.isStealthMode;
    }
    if (telemetry.isAppLockEnabled !== undefined) {
      this.currentDevice.isAppLockEnabled = telemetry.isAppLockEnabled;
    }
    if (telemetry.grantedPermissionsCount !== undefined) {
      this.currentDevice.grantedPermissionsCount = telemetry.grantedPermissionsCount;
    }
    if (telemetry.totalPermissionsCount !== undefined) {
      this.currentDevice.totalPermissionsCount = telemetry.totalPermissionsCount;
    }

    this.currentDevice.lastSeen = 'Just now';
    this.syncCachedLastDevice();
    this.notify();
    this.notifyTelemetryUpdate({ ...this.currentDevice });
  }

  /**
   * Synchronizes the cached `lastDevice` record with `currentDevice` telemetry.
   */
  private syncCachedLastDevice(): void {
    if (!this.currentDevice) return;
    const rawId =
      this.currentDevice.rawId || this.currentDevice.id.replace(/\D/g, '');
    if (rawId.length !== 12) return;

    this.lastDevice = {
      rawId,
      formattedId: this.formatId(rawId),
      name: this.currentDevice.name,
      model: this.currentDevice.model,
      lastConnectedAt: Date.now(),
      lastTelemetry: this.buildTelemetrySnapshot(this.currentDevice),
    };
    this.upsertDeviceHistory(this.lastDevice);
    this.setStorageItem(this.LAST_DEVICE_STORAGE_KEY, JSON.stringify(this.lastDevice));
  }

  /**
   * Formats internal storage metrics into a human-friendly string (e.g. "60.0 GB free of 238.4 GB").
   */
  public formatStorage(freeBytes: number, totalBytes: number): string {
    if (!totalBytes || totalBytes <= 0) return 'Unavailable';
    const toGB = (bytes: number) => (bytes / (1024 * 1024 * 1024)).toFixed(1);
    return `${toGB(freeBytes)} GB free of ${toGB(totalBytes)} GB`;
  }

  /**
   * Formats RAM metrics into a readable GB string (e.g. "4.2 GB free / 8.0 GB").
   */
  public formatRam(availableBytes: number, totalBytes: number): string {
    if (!totalBytes || totalBytes <= 0) return 'Unavailable';
    const toGB = (bytes: number) => (bytes / (1024 * 1024 * 1024)).toFixed(1);
    return `${toGB(availableBytes)} GB free / ${toGB(totalBytes)} GB`;
  }

  /**
   * Formats device uptime milliseconds into compact duration text (e.g. "2d 4h 15m" or "14h 22m").
   */
  public formatUptime(uptimeMs: number): string {
    if (!uptimeMs || uptimeMs <= 0) return 'Unavailable';
    const totalMinutes = Math.floor(uptimeMs / 60000);
    if (totalMinutes < 1) return '< 1m';
    const days = Math.floor(totalMinutes / (60 * 24));
    const hours = Math.floor((totalMinutes % (60 * 24)) / 60);
    const minutes = totalMinutes % 60;
    if (days > 0) return `${days}d ${hours}h ${minutes}m`;
    if (hours > 0) return `${hours}h ${minutes}m`;
    return `${minutes}m`;
  }

  public formatId(rawId: string): string {
    const digits = rawId.replace(/\D/g, '').slice(0, 12);
    if (digits.length === 12) {
      return `${digits.slice(0, 4)} ${digits.slice(4, 8)} ${digits.slice(8, 12)}`;
    }
    return rawId;
  }

  public async connectLive(
    childId: string,
    onProgress?: (statusText: string) => void
  ): Promise<boolean> {
    const raw = childId.replace(/[\s-]/g, '').trim();
    if (raw.length < 12) {
      return false;
    }

    this.isManualDisconnect = false;
    onProgress?.('Authenticating session...');
    const formattedId = this.formatId(raw);

    try {
      onProgress?.('Verifying child device presence...');
      await webrtcManager.connect(raw);

      onProgress?.('Establishing peer connection...');

      const cachedForSameDevice = this.getPairedDeviceRecord(raw);
      const cachedTel = cachedForSameDevice?.lastTelemetry;

      this.connected = true;
      this.currentDevice = {
        id: formattedId,
        rawId: raw,
        name: cachedForSameDevice?.name || 'Android Device',
        model: cachedForSameDevice?.model || 'Android Device',
        batteryPercent: cachedTel?.batteryPercent ?? 100,
        isCharging: cachedTel?.isCharging ?? false,
        networkState: cachedTel?.networkState ?? 'WiFi',
        signalStatus: cachedTel?.networkState ?? 'WiFi',
        androidVersion: cachedTel?.androidVersion,
        freeStorageBytes: cachedTel?.freeStorageBytes,
        totalStorageBytes: cachedTel?.totalStorageBytes,
        storageUsageText: cachedTel?.storageUsageText,
        sdkInt: cachedTel?.sdkInt,
        brand: cachedTel?.brand,
        appVersion: cachedTel?.appVersion,
        chargingSource: cachedTel?.chargingSource,
        batteryTemperatureC: cachedTel?.batteryTemperatureC,
        isPowerSaveMode: cachedTel?.isPowerSaveMode,
        batteryOptimizationIgnored: cachedTel?.batteryOptimizationIgnored,
        totalRamBytes: cachedTel?.totalRamBytes,
        availableRamBytes: cachedTel?.availableRamBytes,
        ramUsageText: cachedTel?.ramUsageText,
        isScreenOn: cachedTel?.isScreenOn,
        screenResolution: cachedTel?.screenResolution,
        ringerMode: cachedTel?.ringerMode,
        simCarrier: cachedTel?.simCarrier,
        deviceUptimeMs: cachedTel?.deviceUptimeMs,
        isStealthMode: cachedTel?.isStealthMode,
        isAppLockEnabled: cachedTel?.isAppLockEnabled,
        grantedPermissionsCount: cachedTel?.grantedPermissionsCount,
        totalPermissionsCount: cachedTel?.totalPermissionsCount,
        isOnline: true,
        lastSeen: 'Just now',
      };

      this.saveLastConnectedDevice({
        rawId: raw,
        formattedId,
        name: this.currentDevice.name,
        model: this.currentDevice.model,
        lastConnectedAt: Date.now(),
        lastTelemetry: cachedTel,
      });

      reconnectManager.handleSuccess();
      this.notify();
      return true;
    } catch (error) {
      console.error('[DeviceState] Connection attempt failed:', error);
      this.connected = false;
      if (!reconnectManager.isReconnecting()) {
        this.currentDevice = null;
      }
      this.notify();
      throw error;
    }
  }

  /**
   * Synchronous mock/local connect helper for testing purposes.
   */
  public connect(childId: string): boolean {
    const raw = childId.replace(/[\s-]/g, '').trim();
    if (raw.length < 12) {
      return false;
    }

    this.isManualDisconnect = false;
    const formattedId = this.formatId(raw);

    this.connected = true;
    this.currentDevice = {
      id: formattedId,
      rawId: raw,
      name: 'Pixel 8 Pro',
      model: 'Google Pixel 8 Pro (Android 14)',
      batteryPercent: 88,
      signalStatus: 'WebRTC Encrypted • Live P2P',
      isOnline: true,
      lastSeen: 'Just now',
    };

    this.saveLastConnectedDevice({
      rawId: raw,
      formattedId,
      name: 'Pixel 8 Pro',
      model: 'Google Pixel 8 Pro (Android 14)',
      lastConnectedAt: Date.now(),
      lastTelemetry: this.buildTelemetrySnapshot(this.currentDevice),
    });

    reconnectManager.handleSuccess();
    this.notify();
    return true;
  }

  public disconnect(): void {
    if (this.connected || this.currentDevice || reconnectManager.isReconnecting()) {
      console.log('[DeviceState] Disconnecting from active child device (manual)...');
      this.isManualDisconnect = true;
      reconnectManager.cancel();
      webrtcManager.disconnect();
      this.connected = false;
      this.currentDevice = null;
      this.notify();
    }
  }

  public cancelReconnection(): void {
    console.log('[DeviceState] Cancelling active reconnection sequence...');
    reconnectManager.cancel();
    this.connected = false;
    this.currentDevice = null;
    this.notify();
  }

  public attachReconnectHandlers(): void {
    reconnectManager.setConnectHandler(async (rawId) => {
      return await this.connectLive(rawId);
    });

    reconnectManager.onCancel(() => {
      console.log('[DeviceState] Reconnection sequence cancelled. Transitioning to empty state.');
      this.connected = false;
      this.currentDevice = null;
      this.notify();
    });

    reconnectManager.onExhausted(() => {
      console.log('[DeviceState] All 9 reconnection attempts exhausted. Transitioning to empty state.');
      this.connected = false;
      this.currentDevice = null;
      this.notify();
    });

    reconnectManager.subscribe(() => {
      if (!this.connected && !reconnectManager.isReconnecting() && !reconnectManager.getState().isActive) {
        if (this.currentDevice !== null) {
          this.currentDevice = null;
        }
      }
      this.notify();
    });
  }

  public isReconnecting(): boolean {
    return reconnectManager.isReconnecting();
  }

  public subscribe(listener: DeviceChangeListener): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  /**
   * Subscribes to live telemetry updates received via `updateTelemetry()`.
   */
  public onTelemetryUpdate(listener: DeviceTelemetryListener): () => void {
    this.telemetryListeners.add(listener);
    return () => {
      this.telemetryListeners.delete(listener);
    };
  }

  /**
   * Subscribes to unexpected WebRTC disconnections (never fires on manual `disconnect()`).
   */
  public onUnexpectedDisconnect(listener: UnexpectedDisconnectListener): () => void {
    this.unexpectedDisconnectListeners.add(listener);
    return () => {
      this.unexpectedDisconnectListeners.delete(listener);
    };
  }

  private notify(): void {
    for (const listener of this.listeners) {
      listener();
    }
  }

  private notifyTelemetryUpdate(device: DeviceInfo): void {
    for (const listener of this.telemetryListeners) {
      try {
        listener(device);
      } catch (err) {
        console.error('[DeviceState] Error in telemetry listener:', err);
      }
    }
  }

  private notifyUnexpectedDisconnect(device: DeviceInfo): void {
    for (const listener of this.unexpectedDisconnectListeners) {
      try {
        listener(device);
      } catch (err) {
        console.error('[DeviceState] Error in unexpected-disconnect listener:', err);
      }
    }
  }
}

export const deviceState = new DeviceStateManager();
