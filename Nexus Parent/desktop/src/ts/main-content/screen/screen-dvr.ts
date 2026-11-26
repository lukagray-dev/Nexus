/**
 * @file screen-dvr.ts
 * @description YouTube-Style Live Stream DVR and Rolling Time-Shift Buffer for Nexus Parent Screen Monitoring.
 *
 * Delegates rolling chunk capture, dynamic retention resizing (`15m`/`30m`/`60m`),
 * and MP4/WebM clip export to the shared `RollingDvrManager`.
 */

import {
  RollingDvrManager,
  type RollingDvrProgressState,
  type RollingDvrTickCallback,
} from '../../shared/rolling-dvr.js';

export type ScreenDvrProgressState = RollingDvrProgressState;
export type ScreenDvrTickCallback = RollingDvrTickCallback;

export class ScreenDvrManager extends RollingDvrManager {
  constructor() {
    super({
      logPrefix: 'ScreenDVR',
      feedLabel: 'screen',
      fractionalTotalSeconds: true,
    });
  }
}
