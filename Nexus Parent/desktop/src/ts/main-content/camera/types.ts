/**
 * @file types.ts
 * @description Protocol definitions, state types, and constants for Nexus Parent Camera monitoring.
 *
 * Hey friend! This file acts as the single source of truth for all camera commands,
 * responses, and state models:
 * 1. CameraState: Represents the lifecycle state of the play/pause button and live feed.
 * 2. CameraLens: 'Rear' or 'Front' hardware orientation.
 * 3. Command Constants: The exact strings sent across the WebRTC DataChannel ('nexus-control').
 */

/**
 * Camera player lifecycle states:
 * - IDLE: Feed is paused/stopped; play button is visible.
 * - STARTING: CAMERA_ON command dispatched; loading spinner active awaiting child confirmation.
 * - STREAMING: Live video feed active; pause button is visible; live timer & DVR active.
 * - STOPPING: CAMERA_OFF command dispatched; loading spinner active awaiting child confirmation.
 */
export type CameraState = 'IDLE' | 'STARTING' | 'STREAMING' | 'STOPPING';

/**
 * Camera lens orientation:
 * - 'Rear': Main back-facing camera lens (default upon activation).
 * - 'Front': Selfie front-facing camera lens.
 */
export type CameraLens = 'Rear' | 'Front';
export type PreferredCameraLensSetting = 'rear' | 'front';

export const EVENT_CAMERA_PREFERRED_LENS_CHANGED = 'nexus-camera-preferred-lens-changed';

// DataChannel Camera Control Commands (Parent -> Child)
export const CMD_CAMERA_ON = 'CAMERA_ON';
export const CMD_CAMERA_OFF = 'CAMERA_OFF';
export const CMD_CAMERA_SWITCH = 'CAMERA_SWITCH';

// DataChannel Camera Responses (Child -> Parent)
export const RESP_CAMERA_STARTED = 'CAMERA_STARTED';
export const RESP_CAMERA_STOPPED = 'CAMERA_STOPPED';
export const RESP_CAMERA_SWITCHED_PREFIX = 'CAMERA_SWITCHED';
export const RESP_CAMERA_ERROR_PREFIX = 'CAMERA_ERROR: ';

/**
 * Maps Settings lens value (`'rear' | 'front'`) to UI display label (`'Rear' | 'Front'`).
 */
export function toCameraLensLabel(setting: PreferredCameraLensSetting): CameraLens {
  return setting === 'front' ? 'Front' : 'Rear';
}

/**
 * Builds the parameterized `CAMERA_ON` command targeting the requested lens.
 */
export function buildCameraOnCommand(lens: PreferredCameraLensSetting | CameraLens): string {
  const normalized = lens.toLowerCase() === 'front' ? 'FRONT' : 'REAR';
  return `${CMD_CAMERA_ON}:${normalized}`;
}
