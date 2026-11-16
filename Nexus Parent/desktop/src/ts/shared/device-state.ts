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
}

export type DeviceChangeListener = () => void;

class DeviceStateManager {
  private connected: boolean = false;
  private currentDevice: DeviceInfo | null = null;
  private listeners: Set<DeviceChangeListener> = new Set();

  constructor() {
    // Listen for WebRTC state changes to automatically handle disconnections
    webrtcManager.onConnectionStateChange((state) => {
      console.log('[DeviceState] WebRTC connection state changed to:', state);
      if (state === 'disconnected' || state === 'failed' || state === 'closed') {
        if (this.connected) {
          console.log('[DeviceState] Connection lost. Resetting device state to disconnected.');
          this.connected = false;
          this.currentDevice = null;
          this.notify();
        }
      } else if (state === 'connected') {
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
    this.notify();
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

      this.notify();
      return true;
    } catch (error) {
      console.error('[DeviceState] Connection attempt failed:', error);
      this.connected = false;
      this.currentDevice = null;
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

    this.notify();
    return true;
  }

  /**
   * Disconnects the active child device and resets connection state back to the Enter Child ID screen.
   */
  public disconnect(): void {
    if (this.connected || this.currentDevice) {
      console.log('[DeviceState] Disconnecting from active child device...');
      webrtcManager.disconnect();
      this.connected = false;
      this.currentDevice = null;
      this.notify();
    }
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
