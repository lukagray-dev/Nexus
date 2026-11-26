/**
 * @file rolling-dvr.ts
 * @description Shared YouTube-Style Live Stream DVR & Clip Export Engine for Nexus Parent.
 *
 * Hey friend! Both Camera Feed and Screen Feed record a rolling in-memory buffer of
 * live WebRTC stream chunks so parents can rewind and scrub backwards like YouTube
 * without interrupting the live feed. Centralizing the DVR state machine here:
 * 1. Applies `dvrBufferDuration` (`15m` / `30m` / `60m`) dynamically, including live resizing
 *    if the user changes retention depth while a stream is running.
 * 2. Applies `dvrClipFormat` (`mp4` / `webm`) when selecting `MediaRecorder` MIME types
 *    (falling back gracefully to WebM if the host WebView2 build lacks MP4 recording).
 * 3. Exports the recorded DVR buffer as a playable video clip using the universal
 *    `saveBlobWithUniversalSettings()` pipeline (respecting `downloadsDirectory` & `askBeforeDownload`).
 */

import { settingsState } from '../settings/sidebar/state.js';
import { saveBlobWithUniversalSettings } from '../main-content/storage/download-manager.js';

export type DvrBufferDurationSetting = '15m' | '30m' | '60m';
export type DvrClipFormatSetting = 'mp4' | 'webm';

export interface RollingDvrProgressState {
  isLive: boolean;
  currentSeconds: number;
  totalSeconds: number;
  progressPercent: number;
  bufferStartSeconds: number;
}

export type RollingDvrTickCallback = (state: RollingDvrProgressState) => void;

export interface DvrMimeSelection {
  mimeType: string;
  container: DvrClipFormatSetting;
  usedFallback: boolean;
}

export interface DvrClipExportResult {
  saved: boolean;
  fileName?: string;
  path?: string;
  container: DvrClipFormatSetting;
  usedFallbackContainer: boolean;
}

interface DvrChunk {
  blob: Blob;
  startMs: number;
  endMs: number;
}

const MP4_MIME_CANDIDATES = [
  'video/mp4;codecs=avc1.42E01E,mp4a.40.2',
  'video/mp4;codecs=avc1',
  'video/mp4',
] as const;

const WEBM_MIME_CANDIDATES = [
  'video/webm;codecs=vp8',
  'video/webm;codecs=vp9',
  'video/webm;codecs=h264',
  'video/webm',
] as const;

/**
 * Converts the `dvrBufferDuration` setting (`'15m' | '30m' | '60m'`) into milliseconds.
 */
export function resolveDvrBufferMs(duration: DvrBufferDurationSetting): number {
  switch (duration) {
    case '15m':
      return 15 * 60 * 1000;
    case '60m':
      return 60 * 60 * 1000;
    case '30m':
    default:
      return 30 * 60 * 1000;
  }
}

/**
 * Selects the best supported `MediaRecorder` MIME type for the chosen export container.
 * Falls back cleanly to WebM if the host WebView2 runtime does not support MP4 recording.
 */
export function resolveDvrMimeType(
  preferredFormat: DvrClipFormatSetting,
  isTypeSupportedFn?: (mime: string) => boolean
): DvrMimeSelection {
  const checker =
    isTypeSupportedFn ||
    (typeof MediaRecorder !== 'undefined' && typeof MediaRecorder.isTypeSupported === 'function'
      ? (m: string) => MediaRecorder.isTypeSupported(m)
      : undefined);

  if (preferredFormat === 'mp4') {
    if (checker) {
      for (const candidate of MP4_MIME_CANDIDATES) {
        if (checker(candidate)) {
          return { mimeType: candidate, container: 'mp4', usedFallback: false };
        }
      }
    }
    // WebView2 / Chromium without MP4 MediaRecorder support -> fall back cleanly to WebM
    const webmChoice = resolveDvrMimeType('webm', checker);
    return {
      mimeType: webmChoice.mimeType,
      container: 'webm',
      usedFallback: true,
    };
  }

  if (checker) {
    for (const candidate of WEBM_MIME_CANDIDATES) {
      if (checker(candidate)) {
        return { mimeType: candidate, container: 'webm', usedFallback: false };
      }
    }
  }

  return {
    mimeType: 'video/webm;codecs=vp8',
    container: 'webm',
    usedFallback: false,
  };
}

/**
 * Builds a deterministic, filesystem-safe timestamped clip filename.
 */
export function buildDvrClipFileName(
  feedLabel: 'camera' | 'screen',
  container: DvrClipFormatSetting,
  now: Date = new Date()
): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  const stamp = [
    now.getFullYear(),
    pad(now.getMonth() + 1),
    pad(now.getDate()),
    '-',
    pad(now.getHours()),
    pad(now.getMinutes()),
    pad(now.getSeconds()),
  ].join('');
  return `nexus-${feedLabel}-${stamp}.${container}`;
}

export class RollingDvrManager {
  private static readonly CHUNK_INTERVAL_MS = 1000;

  private readonly logPrefix: string;
  private readonly feedLabel: 'camera' | 'screen';
  private readonly fractionalTotalSeconds: boolean;

  private maxBufferMs: number;
  private mediaRecorder: MediaRecorder | null = null;
  private chunks: DvrChunk[] = [];
  private sessionStartWallTime = 0;
  private isLiveState = true;
  private currentPlaybackSeconds = 0;
  private dvrBlobUrl: string | null = null;
  private tickInterval: ReturnType<typeof setInterval> | null = null;
  private onTickCallbacks: Set<RollingDvrTickCallback> = new Set();
  private mimeType = 'video/webm;codecs=vp8';
  private activeContainer: DvrClipFormatSetting = 'webm';
  private usedContainerFallback = false;
  private unsubscribeSettings: (() => void) | null = null;

  constructor(options: {
    logPrefix: string;
    feedLabel: 'camera' | 'screen';
    fractionalTotalSeconds?: boolean;
  }) {
    this.logPrefix = options.logPrefix;
    this.feedLabel = options.feedLabel;
    this.fractionalTotalSeconds = Boolean(options.fractionalTotalSeconds);
    this.maxBufferMs = resolveDvrBufferMs(settingsState.getValue('dvrBufferDuration'));
    this.refreshMimeSelection();
  }

  /**
   * Refreshes the target MediaRecorder MIME selection from `dvrClipFormat`.
   */
  public refreshMimeSelection(): DvrMimeSelection {
    const preferred = settingsState.getValue('dvrClipFormat');
    const selection = resolveDvrMimeType(preferred);
    this.mimeType = selection.mimeType;
    this.activeContainer = selection.container;
    this.usedContainerFallback = selection.usedFallback;
    return selection;
  }

  /**
   * Returns the configured maximum buffer duration in milliseconds.
   */
  public getMaxBufferMs(): number {
    return this.maxBufferMs;
  }

  /**
   * Updates the rolling buffer retention window dynamically (e.g. if changed mid-stream).
   */
  public setMaxBufferMs(nextMaxMs: number): void {
    this.maxBufferMs = Math.max(1000, nextMaxMs);
    if (this.sessionStartWallTime > 0 && this.chunks.length > 0) {
      const elapsedMs = Date.now() - this.sessionStartWallTime;
      this.pruneOldChunks(elapsedMs);
      this.notifyTick();
    }
  }

  /**
   * Returns true if recorded chunks exist for exporting.
   */
  public hasRecordedChunks(): boolean {
    return this.chunks.length > 0;
  }

  /**
   * Appends a synthetic or recorded chunk (useful for unit tests and direct chunk pipelines).
   */
  public pushChunkForTesting(blob: Blob, startMs: number, endMs: number): void {
    if (this.sessionStartWallTime === 0) {
      this.sessionStartWallTime = Date.now() - endMs;
    }
    this.chunks.push({ blob, startMs, endMs });
    this.pruneOldChunks(endMs);
  }

  /**
   * Starts background recording and DVR timeline tracking for the given live stream.
   */
  public start(stream: MediaStream): boolean {
    if (this.mediaRecorder && this.mediaRecorder.state !== 'inactive') {
      return true;
    }

    this.stop();

    if (!stream || stream.getVideoTracks().length === 0) {
      console.warn(`[${this.logPrefix}] Cannot start DVR: No video tracks in stream.`);
      return false;
    }

    this.maxBufferMs = resolveDvrBufferMs(settingsState.getValue('dvrBufferDuration'));
    this.refreshMimeSelection();

    // React live if the user adjusts Rolling DVR Memory Retention while streaming
    this.unsubscribeSettings = settingsState.onValuesChange((values) => {
      const nextMs = resolveDvrBufferMs(values.dvrBufferDuration);
      if (nextMs !== this.maxBufferMs) {
        console.log(`[${this.logPrefix}] Updating live DVR buffer retention to ${values.dvrBufferDuration}.`);
        this.setMaxBufferMs(nextMs);
      }
    });

    try {
      console.log(
        `[${this.logPrefix}] Starting rolling DVR buffer (MIME=${this.mimeType}, retention=${this.maxBufferMs / 60000}m)`
      );
      this.sessionStartWallTime = Date.now();
      this.chunks = [];
      this.isLiveState = true;
      this.currentPlaybackSeconds = 0;

      const recorder = new MediaRecorder(stream, { mimeType: this.mimeType });
      this.mediaRecorder = recorder;

      let chunkStartTime = 0;

      recorder.ondataavailable = (event: BlobEvent) => {
        if (event.data && event.data.size > 0) {
          const nowMs = Date.now() - this.sessionStartWallTime;
          this.chunks.push({
            blob: event.data,
            startMs: chunkStartTime,
            endMs: nowMs,
          });
          chunkStartTime = nowMs;
          this.pruneOldChunks(nowMs);
        }
      };

      recorder.onerror = (err) => {
        console.error(`[${this.logPrefix}] MediaRecorder error:`, err);
      };

      recorder.start(RollingDvrManager.CHUNK_INTERVAL_MS);

      this.tickInterval = setInterval(() => {
        this.notifyTick();
      }, 500);

      console.log(`[${this.logPrefix}] Background DVR recorder successfully active.`);
      return true;
    } catch (err) {
      console.error(`[${this.logPrefix}] Failed to start MediaRecorder:`, err);
      this.unsubscribeSettings?.();
      this.unsubscribeSettings = null;
      return false;
    }
  }

  /**
   * Evicts chunks older than `this.maxBufferMs` from the front of the queue (FIFO).
   */
  public pruneOldChunks(currentElapsedMs: number): void {
    const cutoffTime = currentElapsedMs - this.maxBufferMs;
    if (cutoffTime <= 0) return;

    while (this.chunks.length > 1 && this.chunks[0].endMs < cutoffTime) {
      this.chunks.shift();
    }
  }

  /**
   * Compiles the currently retained rolling DVR buffer and saves it to disk using
   * the user's `downloadsDirectory` / `askBeforeDownload` settings.
   */
  public async exportRecordedClip(onToast?: (message: string) => void): Promise<DvrClipExportResult> {
    if (this.chunks.length === 0) {
      onToast?.('No recorded DVR footage available to export yet');
      return {
        saved: false,
        container: this.activeContainer,
        usedFallbackContainer: this.usedContainerFallback,
      };
    }

    const compiledBlob = new Blob(
      this.chunks.map((c) => c.blob),
      { type: this.mimeType }
    );
    const fileName = buildDvrClipFileName(this.feedLabel, this.activeContainer);

    if (this.usedContainerFallback) {
      onToast?.('Saving clip as WebM (MP4 MediaRecorder not supported by this WebView2 runtime)');
    }

    const result = await saveBlobWithUniversalSettings({
      blob: compiledBlob,
      fileName,
      mimeType: this.mimeType,
      onToast,
    });

    return {
      saved: result.saved,
      fileName,
      path: result.path,
      container: this.activeContainer,
      usedFallbackContainer: this.usedContainerFallback,
    };
  }

  /**
   * Seeks to a historical timestamp (seconds from session start) and configures the DVR video element.
   */
  public seekTo(targetSeconds: number, dvrVideoElement: HTMLVideoElement): void {
    const totalSeconds = this.getTotalDurationSeconds();
    const clampedTarget = Math.max(0, Math.min(targetSeconds, totalSeconds));

    if (clampedTarget >= totalSeconds - 2) {
      this.goLive(dvrVideoElement);
      return;
    }

    this.isLiveState = false;
    this.currentPlaybackSeconds = clampedTarget;

    if (this.chunks.length === 0) {
      console.warn(`[${this.logPrefix}] Cannot seek: Buffer has no recorded chunks yet.`);
      return;
    }

    try {
      const allBlobs = this.chunks.map((c) => c.blob);
      const compiledBlob = new Blob(allBlobs, { type: this.mimeType });

      if (this.dvrBlobUrl) {
        URL.revokeObjectURL(this.dvrBlobUrl);
        this.dvrBlobUrl = null;
      }

      const url = URL.createObjectURL(compiledBlob);
      this.dvrBlobUrl = url;

      const bufferStartSec = this.getBufferStartSeconds();
      const relativeOffset = Math.max(0, clampedTarget - bufferStartSec);

      console.log(
        `[${this.logPrefix}] Seeking to ${clampedTarget.toFixed(1)}s (offset ${relativeOffset.toFixed(1)}s in ${allBlobs.length} chunks)...`
      );

      dvrVideoElement.src = url;
      dvrVideoElement.onloadedmetadata = () => {
        dvrVideoElement.currentTime = relativeOffset;
        dvrVideoElement.play().catch((err) => {
          console.warn(`[${this.logPrefix}] DVR video play prevented:`, err);
        });
      };

      dvrVideoElement.ontimeupdate = () => {
        if (!this.isLiveState) {
          this.currentPlaybackSeconds = bufferStartSec + dvrVideoElement.currentTime;
          this.notifyTick();
        }
      };

      dvrVideoElement.onended = () => {
        console.log(`[${this.logPrefix}] Reached end of recorded buffer. Returning to Live...`);
        this.goLive(dvrVideoElement);
      };

      this.notifyTick();
    } catch (err) {
      console.error(`[${this.logPrefix}] Failed to load time-shifted video chunk:`, err);
    }
  }

  /**
   * Snaps the player back to real-time live feed.
   */
  public goLive(dvrVideoElement?: HTMLVideoElement): void {
    console.log(`[${this.logPrefix}] goLive() invoked. Returning to real-time feed...`);
    this.isLiveState = true;
    this.currentPlaybackSeconds = this.getTotalDurationSeconds();

    if (dvrVideoElement) {
      dvrVideoElement.pause();
      dvrVideoElement.removeAttribute('src');
      dvrVideoElement.load();
    }

    if (this.dvrBlobUrl) {
      URL.revokeObjectURL(this.dvrBlobUrl);
      this.dvrBlobUrl = null;
    }

    this.notifyTick();
  }

  public isLive(): boolean {
    return this.isLiveState;
  }

  public getTotalDurationSeconds(): number {
    if (this.sessionStartWallTime === 0) return 0;
    const elapsedSec = (Date.now() - this.sessionStartWallTime) / 1000;
    return this.fractionalTotalSeconds ? Math.max(0, elapsedSec) : Math.max(0, Math.floor(elapsedSec));
  }

  public getBufferStartSeconds(): number {
    if (this.chunks.length === 0) return 0;
    const startSec = this.chunks[0].startMs / 1000;
    return this.fractionalTotalSeconds ? Math.max(0, startSec) : Math.max(0, Math.floor(startSec));
  }

  public getCurrentPlaybackSeconds(): number {
    if (this.isLiveState) {
      return this.getTotalDurationSeconds();
    }
    return this.currentPlaybackSeconds;
  }

  public getChunkCount(): number {
    return this.chunks.length;
  }

  public onTick(callback: RollingDvrTickCallback): () => void {
    this.onTickCallbacks.add(callback);
    return () => this.onTickCallbacks.delete(callback);
  }

  private notifyTick(): void {
    const total = this.getTotalDurationSeconds();
    const current = this.getCurrentPlaybackSeconds();
    const bufferStart = this.getBufferStartSeconds();
    const progressPercent = total > 0 ? Math.min(100, Math.max(0, (current / total) * 100)) : 100;

    const state: RollingDvrProgressState = {
      isLive: this.isLiveState,
      currentSeconds: current,
      totalSeconds: total,
      progressPercent,
      bufferStartSeconds: bufferStart,
    };

    for (const callback of this.onTickCallbacks) {
      callback(state);
    }
  }

  /**
   * Stops recording, clears buffers, unsubscribes from settings, and releases URLs.
   */
  public stop(): void {
    if (this.unsubscribeSettings) {
      this.unsubscribeSettings();
      this.unsubscribeSettings = null;
    }

    if (this.tickInterval) {
      clearInterval(this.tickInterval);
      this.tickInterval = null;
    }

    if (this.mediaRecorder && this.mediaRecorder.state !== 'inactive') {
      try {
        this.mediaRecorder.stop();
      } catch (e) {
        console.warn(`[${this.logPrefix}] Warning stopping MediaRecorder:`, e);
      }
    }
    this.mediaRecorder = null;

    if (this.dvrBlobUrl) {
      URL.revokeObjectURL(this.dvrBlobUrl);
      this.dvrBlobUrl = null;
    }

    this.chunks = [];
    this.sessionStartWallTime = 0;
    this.isLiveState = true;
    this.currentPlaybackSeconds = 0;
  }
}
