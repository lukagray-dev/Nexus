/**
 * @file types.ts
 * @description Shared domain models and data interfaces for Nexus Parent Desktop.
 *
 * Hey friend! Defining strict, modular types prevents runtime errors and guarantees
 * that all monitoring views, control panels, and signaling states stay perfectly synchronized.
 */

export type DeviceStatus = 'online' | 'offline' | 'idle' | 'alert';

export interface DeviceSummary {
  id: string;
  name: string;
  model: string;
  status: DeviceStatus;
  batteryLevel: number;
  lastPing: string;
}

export interface SystemMetrics {
  totalMonitoredDevices: number;
  activeSessions: number;
  activeAlerts: number;
  networkLatencyMs: number;
}

/**
 * High-level connection state of the WebRTC signaling manager.
 */
export type SignalingState =
  | 'disconnected'
  | 'authenticating'
  | 'checking-device'
  | 'signaling'
  | 'connected'
  | 'error';

/**
 * Status event payload dispatched to listeners and renderer.
 */
export interface SignalingStatusEvent {
  state: SignalingState;
  message?: string;
}

/**
 * Child device metadata received from Firebase or WebRTC signaling.
 */
export interface ChildDeviceRecord {
  status: string;
  createdAt?: number;
  lastSeen?: number;
  manufacturer?: string;
  productName?: string;
  model?: string;
  device?: string;
  brand?: string;
  sdkInt?: number;
  securityPatch?: string;
  androidVersion?: string;
}
