/**
 * @file camera-dvr.ts
 * @description YouTube-Style Live Stream DVR and Rolling Time-Shift Buffer for Nexus Parent Camera.
 *
 * Delegates rolling chunk capture, dynamic retention resizing (`15m`/`30m`/`60m`),
 * and MP4/WebM clip export to the shared `RollingDvrManager`.
 */

import {
  RollingDvrManager,
  type RollingDvrProgressState,
  type RollingDvrTickCallback,
} from '../../shared/rolling-dvr.js';

export type DvrProgressState = RollingDvrProgressState;
export type DvrTickCallback = RollingDvrTickCallback;

export class CameraDvrManager extends RollingDvrManager {
  constructor() {
    super({
      logPrefix: 'CameraDVR',
      feedLabel: 'camera',
      fractionalTotalSeconds: false,
    });
  }
}
