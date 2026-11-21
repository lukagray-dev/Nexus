/**
 * @file device-state.ts
 * @description Central device connection & pairing state manager for Nexus Parent.
 *
 * Hey friend! This manages whether a target child phone is currently paired and connected.
 * In Nexus, parents enter a 12-digit Child ID to establish connection.
 * This state manager connects to our WebRtcManager and SignalingClient:
 * - Tracks the active connection, the 12-digit ID, device telemetry, and battery/network metrics.
 * - Notifies all UI components whenever a child device is connected or disconnected.
 * - Listens for WebRTC connection dropouts so the UI returns to the pairing screen automatically.
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
}

/**
 * Metadata record for a previously connected child device saved in local storage.
 * Hey friend! This allows the desktop app to offer 1-click reconnect shortcuts
 * on the Enter Child ID page so parents don't have to re-type 12 digits every time!
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
  /** Current battery percentage from 0 to 100 */
  batteryPercent: number;
  /** Whether the device is currently charging */
  isCharging?: boolean;
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
  /** Whether the child app launcher icon is hidden (Stealth Mode active) */
  isStealthMode?: boolean;
}

export type DeviceChangeListener = () => void;

class DeviceStateManager {
  private connected: boolean = false;
  private currentDevice: DeviceInfo | null = null;
  private lastDevice: LastConnectedDevice | null = null;
  private readonly LAST_DEVICE_STORAGE_KEY = 'nexus_last_connected_child_device';
  private listeners: Set<DeviceChangeListener> = new Set();
  /** Tracks whether the disconnection was intentionally triggered by the user */
  private isManualDisconnect: boolean = false;

  /**
   * In-memory storage dictionary fallback.
   * Hey friend! In unit test environments (like Node.js test runner) or environments
   * where window.localStorage might be sandboxed or throw a SecurityError, this fallback
   * ensures our state manager remains completely rock-solid without throwing unhandled exceptions!
   */
  private memoryStorage: Map<string, string> = new Map();

  constructor() {
    // Hey friend! Preload previously connected child device from storage so
    // the pairing screen can instantly display the 1-click reconnect shortcut!
    this.loadLastConnectedDevice();

    // Wire ReconnectManager delegates and event listeners
    this.attachReconnectHandlers();

    // Listen for WebRTC state changes to automatically handle disconnections
    webrtcManager.onConnectionStateChange((state) => {
      console.log('[DeviceState] WebRTC connection state changed to:', state);
      if (state === 'disconnected' || state === 'failed' || state === 'closed') {
        const wasConnected = this.connected;
        const target = this.currentDevice;

        // Automatic Reconnection Trigger:
        // If the connection dropped suddenly due to network/child dropout (and was NOT
        // manually triggered by the parent), start auto-reconnecting up to 9 times FIRST
        // before clearing state flags or notifying subscribers!
        const shouldAutoReconnect = wasConnected && target && !this.isManualDisconnect;
        if (shouldAutoReconnect && target) {
          console.log('[DeviceState] Unexpected connection drop detected. Triggering auto-reconnect...');
          const cleanRaw = target.rawId ? target.rawId.replace(/\D/g, '') : target.id.replace(/\D/g, '');
          if (cleanRaw.length === 12) {
            reconnectManager.start({
              rawId: cleanRaw,
              formattedId: target.id,
              name: target.name,
              model: target.model
            });
          }
        }

        if (this.connected) {
          console.log('[DeviceState] Connection lost. Setting connected state to false.');
          this.connected = false;
          // Retain currentDevice during auto-reconnection so active views retain device context.
          // Only clear currentDevice if manual disconnect occurred or auto-reconnect is not active.
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
            totalStorageBytes: packet.totalStorageBytes
          });
        }
      } catch {
        // Plain text or unformatted command
      }
    });
  }

  /**
   * Safely reads a string value from local storage with graceful in-memory fallback.
   *
   * @param key Storage key name
   * @returns Stored string value or null if not found
   */
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

  /**
   * Safely writes a key-value string pair to local storage with graceful in-memory fallback.
   *
   * @param key Storage key name
   * @param value String value to store
   */
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

  /**
   * Safely removes a key-value entry from local storage with graceful in-memory fallback.
   *
   * @param key Storage key name
   */
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
   * Loads previously connected device information from persistent local storage.
   * Hey friend! If a valid record exists, we deserialize it so UI screens can render it instantly.
   *
   * @returns The restored LastConnectedDevice metadata or null if none stored
   */
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
   * Returns the cached metadata of the most recently connected child device, or null if none.
   */
  public getLastConnectedDevice(): LastConnectedDevice | null {
    return this.lastDevice;
  }

  /**
   * Saves metadata for the currently or previously connected child device to persistent local storage.
   * Hey friend! This enables parents to quickly connect to their child phone with a single click!
   *
   * @param device Metadata record of the child device
   */
  public saveLastConnectedDevice(device: LastConnectedDevice): void {
    this.lastDevice = device;
    try {
      this.setStorageItem(this.LAST_DEVICE_STORAGE_KEY, JSON.stringify(device));
    } catch (err) {
      console.warn('[DeviceState] Failed to persist last connected device to storage:', err);
    }
    this.notify();
  }

  /**
   * Clears the stored last connected child device from persistent local storage.
   */
  public clearLastConnectedDevice(): void {
    this.lastDevice = null;
    this.removeStorageItem(this.LAST_DEVICE_STORAGE_KEY);
    this.notify();
  }

  /**
   * Returns true if a child device is currently paired and connected.
   */
  public isConnected(): boolean {
    return this.connected;
  }

  /**
   * Returns telemetry information for the active connected device, or null if disconnected.
   */
  public getConnectedDevice(): DeviceInfo | null {
    return this.currentDevice;
  }

  /**
   * Sets the connected device directly and notifies subscribers.
   */
  public setConnectedDevice(info: DeviceInfo): void {
    this.connected = true;
    this.currentDevice = info;

    // Hey friend! Automatically persist the connected device record so
    // parents can easily reconnect next time without retyping!
    const cleanRaw = info.rawId ? info.rawId.replace(/\D/g, '') : info.id.replace(/\D/g, '');
    if (cleanRaw.length === 12) {
      this.saveLastConnectedDevice({
        rawId: cleanRaw,
        formattedId: this.formatId(cleanRaw),
        name: info.name,
        model: info.model,
        lastConnectedAt: Date.now()
      });
    }

    this.notify();
  }

  /**
   * Returns whether the connected child device is currently operating in stealth mode.
   */
  public isStealthMode(): boolean {
    return this.currentDevice?.isStealthMode === true;
  }

  /**
   * Updates the stealth mode state for the connected child device and notifies subscribers.
   */
  public setStealthMode(isStealth: boolean): void {
    if (!this.currentDevice) return;
    if (this.currentDevice.isStealthMode !== isStealth) {
      this.currentDevice.isStealthMode = isStealth;
      this.notify();
    }
  }

  /**
   * Updates real-time telemetry metrics received over the WebRTC DataChannel.
   */
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
    this.currentDevice.lastSeen = 'Just now';

    // Hey friend! If the connected device name or model was refined by the telemetry payload,
    // update our stored lastDevice record as well so the reconnect button displays the friendly phone name!
    if (this.lastDevice && this.currentDevice?.rawId && this.lastDevice.rawId === this.currentDevice.rawId) {
      this.lastDevice.name = this.currentDevice.name;
      this.lastDevice.model = this.currentDevice.model;
      this.setStorageItem(this.LAST_DEVICE_STORAGE_KEY, JSON.stringify(this.lastDevice));
    }

    this.notify();
  }

  /**
   * Formats internal storage metrics into a human-friendly string (e.g. "60.0 GB free of 238.4 GB").
   */
  public formatStorage(freeBytes: number, totalBytes: number): string {
    if (!totalBytes || totalBytes <= 0) return 'Unavailable';
    const toGB = (bytes: number) => (bytes / (1024 * 1024 * 1024)).toFixed(1);
    const freeGB = toGB(freeBytes);
    const totalGB = toGB(totalBytes);
    return `${freeGB} GB free of ${totalGB} GB`;
  }

  /**
   * Formats a raw 12-digit string into standard visual chunks: "XXXX XXXX XXXX".
   */
  public formatId(rawId: string): string {
    const digits = rawId.replace(/\D/g, '').slice(0, 12);
    if (digits.length === 12) {
      return `${digits.slice(0, 4)} ${digits.slice(4, 8)} ${digits.slice(8, 12)}`;
    }
    return rawId;
  }

  /**
   * Initiates the real WebRTC and Firebase connection to a child device using its 12-digit ID.
   *
   * @param childId The 12-digit ID string entered by the user
   * @param onProgress Optional callback to report detailed progress status to the UI
   * @returns true if connection succeeded, false if error occurred
   */
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

      this.connected = true;
      this.currentDevice = {
        id: formattedId,
        rawId: raw,
        name: 'Android Device',
        model: 'Android Device',
        batteryPercent: 100,
        isCharging: false,
        networkState: 'WiFi',
        signalStatus: 'WiFi',
        isOnline: true,
        lastSeen: 'Just now'
      };

      // Hey friend! Save the successfully connected child device to persistent storage!
      this.saveLastConnectedDevice({
        rawId: raw,
        formattedId: formattedId,
        name: 'Android Device',
        model: 'Android Device',
        lastConnectedAt: Date.now()
      });

      // Clear any pending reconnection attempts upon successful connection
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
      lastSeen: 'Just now'
    };

    // Hey friend! Save the connected device to persistent storage!
    this.saveLastConnectedDevice({
      rawId: raw,
      formattedId: formattedId,
      name: 'Pixel 8 Pro',
      model: 'Google Pixel 8 Pro (Android 14)',
      lastConnectedAt: Date.now()
    });

    reconnectManager.handleSuccess();
    this.notify();
    return true;
  }

  /**
   * Disconnects the active child device and resets connection state back to the Enter Child ID screen.
   * Marks this disconnection as an intentional user action so auto-reconnect is not triggered.
   */
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

  /**
   * Explicitly cancels any ongoing automated reconnection attempts and resets state.
   */
  public cancelReconnection(): void {
    console.log('[DeviceState] Cancelling active reconnection sequence...');
    reconnectManager.cancel();
    this.connected = false;
    this.currentDevice = null;
    this.notify();
  }

  /**
   * Wires ReconnectManager delegation handlers and lifecycle listeners.
   */
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

  /**
   * Returns whether an automated reconnection sequence is actively ongoing.
   */
  public isReconnecting(): boolean {
    return reconnectManager.isReconnecting();
  }

  /**
   * Subscribes a listener to connection state changes.
   * @returns An unsubscribe cleanup function
   */
  public subscribe(listener: DeviceChangeListener): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  private notify(): void {
    for (const listener of this.listeners) {
      listener();
    }
  }
}

export const deviceState = new DeviceStateManager();
