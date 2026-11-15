/**
 * @file device-state.ts
 * @description Central device connection & pairing state manager for Nexus Parent.
 *
 * Hey friend! This manages whether a target child phone is currently paired and connected.
 * In Nexus, parents enter a 12-digit Child ID to establish connection.
 * This state manager tracks the active connection, the 12-digit ID, device telemetry,
 * and notifies all UI components whenever a child device is connected or disconnected.
 */

export interface DeviceInfo {
  /** The 12-digit formatted Child ID, e.g. "NX84-2881-2340" */
  id: string;
  /** Friendly device display name, e.g. "Pixel 8 Pro" */
  name: string;
  /** Full hardware model and OS description */
  model: string;
  /** Current battery percentage from 0 to 100 */
  batteryPercent: number;
  /** Current cellular/WiFi signal strength description */
  signalStatus: string;
  /** Whether the child device is actively online and sending heartbeats */
  isOnline: boolean;
  /** Human-readable timestamp of last telemetry sync */
  lastSeen: string;
}

export type DeviceChangeListener = () => void;

class DeviceStateManager {
  private connected: boolean = false;
  private currentDevice: DeviceInfo | null = null;
  private listeners: Set<DeviceChangeListener> = new Set();

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

  public setConnectedDevice(info: DeviceInfo): void {
    this.connected = true;
    this.currentDevice = info;
    this.notify();
  }

  /**
   * Attempts to connect to a child device using a 12-digit Child ID.
   * Strips any whitespace and hyphens, ensures 12 characters are present.
   *
   * @param childId The 12-digit ID string entered by the user
   * @returns true if connection succeeded, false if ID format was invalid
   */
  public connect(childId: string): boolean {
    const raw = childId.replace(/[\s-]/g, '').trim().toUpperCase();
    if (raw.length < 12) {
      return false;
    }

    const formattedId = `${raw.slice(0, 4)}-${raw.slice(4, 8)}-${raw.slice(8, 12)}`;

    this.connected = true;
    this.currentDevice = {
      id: formattedId,
      name: 'Pixel 8 Pro',
      model: 'Google Pixel 8 Pro (Android 14)',
      batteryPercent: 88,
      signalStatus: '5G • Excellent',
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
    if (this.connected) {
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
