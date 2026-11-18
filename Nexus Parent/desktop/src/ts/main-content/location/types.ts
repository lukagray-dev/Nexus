/**
 * @file types.ts
 * @description Type definitions, DataChannel command contracts, and telemetry formatting helpers
 * for the Nexus Parent Geolocation Tracking system.
 *
 * Hey friend! Here are the core contracts that keep Desktop Parent and Android Child in sync:
 * 1. Control Commands:
 *    - LOCATION_ON: Requests child device to begin streaming GPS coordinates.
 *    - LOCATION_OFF: Requests child device to halt GPS hardware listeners.
 * 2. Confirmation Responses:
 *    - LOCATION_STARTED: Acknowledges that GPS is active and coordinates will begin flowing.
 *    - LOCATION_STOPPED: Acknowledges that GPS is powered down.
 *    - LOCATION_ERROR: <reason>: Acknowledges a failure (e.g. permission missing).
 * 3. Telemetry Stream (JSON):
 *    - LOCATION_UPDATE: Carries latitude, longitude, accuracy, speed, altitude, and timestamp.
 */

// DataChannel Protocol Commands (Parent -> Child)
export const CMD_LOCATION_ON = 'LOCATION_ON';
export const CMD_LOCATION_OFF = 'LOCATION_OFF';
export const ALIAS_LOCATE_CHILD = 'LOCATE_CHILD';
export const ALIAS_LOCATE_CHILD_STOP = 'LOCATE_CHILD_STOP';

// DataChannel Protocol Confirmations (Child -> Parent)
export const RESP_LOCATION_STARTED = 'LOCATION_STARTED';
export const RESP_LOCATION_STOPPED = 'LOCATION_STOPPED';
export const RESP_LOCATION_ERROR_PREFIX = 'LOCATION_ERROR: ';

// DataChannel Telemetry Packet Identifier
export const TYPE_LOCATION_UPDATE = 'LOCATION_UPDATE';

/**
 * UI State machine for Location Tracking view:
 * - IDLE: Tracking is stopped. Start button displays play icon.
 * - STARTING: Parent dispatched LOCATION_ON; button shows animated loading spinner.
 * - TRACKING: Child confirmed LOCATION_STARTED; map is actively updating, button shows pause/stop icon.
 * - STOPPING: Parent dispatched LOCATION_OFF; button shows animated loading spinner.
 */
export type LocationTrackingState = 'IDLE' | 'STARTING' | 'TRACKING' | 'STOPPING';

/**
 * Raw telemetry payload received over WebRTC DataChannel.
 */
export interface LocationUpdatePayload {
  type: string;
  coords?: [number, number];
  latitude: number;
  longitude: number;
  accuracy: number;
  altitude?: number;
  speed?: number;
  bearing?: number;
  timestamp: number;
}

/**
 * Processed telemetry data model stored in memory.
 */
export interface LocationTelemetryData {
  latitude: number;
  longitude: number;
  accuracy: number;
  altitude: number;
  speed: number;
  bearing: number;
  timestamp: number;
  address?: string;
}

/**
 * Formats decimal degrees into clean tactical cardinal coordinates (e.g. 22.5726° N, 88.3639° E).
 */
export function formatCoordinates(lat: number, lng: number): string {
  const latDir = lat >= 0 ? 'N' : 'S';
  const lngDir = lng >= 0 ? 'E' : 'W';
  return `${Math.abs(lat).toFixed(6)}° ${latDir}, ${Math.abs(lng).toFixed(6)}° ${lngDir}`;
}

/**
 * Formats device velocity in km/h.
 * 0 or negative values return a stationary indicator.
 */
export function formatSpeed(speedMps?: number): string {
  if (!speedMps || speedMps <= 0.2) {
    return '0.0 km/h (Stationary)';
  }
  const kmh = speedMps * 3.6;
  return `${kmh.toFixed(1)} km/h`;
}

/**
 * Formats GPS accuracy margin in meters.
 */
export function formatAccuracy(accuracyMeters?: number): string {
  if (!accuracyMeters || accuracyMeters <= 0) {
    return '±0 m (High Precision)';
  }
  return `±${accuracyMeters.toFixed(1)} m`;
}

/**
 * Formats elapsed time since last position ping into human-readable text.
 */
export function formatRelativeTime(timestampMs: number): string {
  const now = Date.now();
  const diffSec = Math.max(0, Math.floor((now - timestampMs) / 1000));

  if (diffSec < 2) return 'Just now';
  if (diffSec < 60) return `${diffSec} seconds ago`;
  const diffMin = Math.floor(diffSec / 60);
  if (diffMin < 60) return `${diffMin}m ago`;
  const diffHours = Math.floor(diffMin / 60);
  return `${diffHours}h ago`;
}
