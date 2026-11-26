/**
 * @file types.ts
 * @description Protocol definitions, state models, and constants for Nexus Parent Screen monitoring.
 *
 * Hey friend! This file provides the single source of truth for screen surveillance:
 * 1. ScreenState: Tracks player lifecycle (IDLE, STARTING, STREAMING, STOPPING).
 * 2. Control Commands: Sent from Parent to Child over the DataChannel.
 * 3. Responses: Sent from Child to Parent acknowledging streaming state changes.
 * 4. Audio Header: Prefix for internal system Opus audio frames sent over DataChannel.
 */

/**
 * Screen monitoring player lifecycle states:
 * - IDLE: Feed is paused/stopped; play button is visible.
 * - STARTING: SCREEN_ON command dispatched; loading spinner active awaiting child confirmation.
 * - STREAMING: Live screen stream active; pause button is visible; live timer & DVR active.
 * - STOPPING: SCREEN_OFF command dispatched; loading spinner active awaiting child confirmation.
 */
export type ScreenState = 'IDLE' | 'STARTING' | 'STREAMING' | 'STOPPING';
export type ScreenQualitySetting = '1080p' | '720p' | '540p';

// DataChannel Screen Control Commands (Parent -> Child)
export const CMD_SCREEN_ON = 'SCREEN_ON';
export const CMD_SCREEN_OFF = 'SCREEN_OFF';
export const CMD_SCREEN_QUALITY_PREFIX = 'SCREEN_QUALITY:';

// DataChannel Screen Responses (Child -> Parent)
export const RESP_SCREEN_STARTED = 'SCREEN_STARTED';
export const RESP_SCREEN_STOPPED = 'SCREEN_STOPPED';
export const RESP_SCREEN_ERROR_PREFIX = 'SCREEN_ERROR: ';

// Internal System Audio DataChannel packet prefix
export const HEADER_OPUS_AUDIO = 'OPUS_AUDIO:';

/**
 * Builds the parameterized `SCREEN_ON` command for the configured quality preset.
 */
export function buildScreenOnCommand(quality: ScreenQualitySetting): string {
  return `${CMD_SCREEN_ON}:${quality}`;
}

/**
 * Builds the live `SCREEN_QUALITY` update command for active streaming sessions.
 */
export function buildScreenQualityCommand(quality: ScreenQualitySetting): string {
  return `${CMD_SCREEN_QUALITY_PREFIX}${quality}`;
}
