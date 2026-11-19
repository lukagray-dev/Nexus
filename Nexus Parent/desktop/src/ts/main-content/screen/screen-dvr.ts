/**
 * @file screen-dvr.ts
 * @description YouTube-Style Live Stream DVR and Rolling Time-Shift Buffer for Nexus Parent Screen Monitoring.
 *
 * Hey friend! Here is how the YouTube-style Live Screen DVR functions:
 * 1. While the screen stream is live, the user sees the real-time WebRTC feed (0ms delay).
 * 2. In the background, ScreenDvrManager continuously captures 1-second WebM chunks using MediaRecorder.
 * 3. Chunks are stored in a 30-minute rolling buffer (FIFO eviction) to cap memory usage (~80-120MB max).
 * 4. When the user drags the timeline scrubber backwards:
 *    - The live screen continues recording in the background without missing a single frame!
 *    - The UI switches to the secondary DVR video surface, rendering past footage from that exact second.
 *    - The Live indicator dot turns dim to show the user is watching past recording.
 * 5. When the user clicks the "Live" button or scrubs back to the right edge (>= 98%):
 *    - The DVR surface is hidden and the live WebRTC surface is restored.
 *    - The Live dot pulses red again and the scrubber docks at 100%!
 */

export interface ScreenDvrProgressState {
  isLive: boolean;
  currentSeconds: number;
  totalSeconds: number;
  progressPercent: number;
  bufferStartSeconds: number;
}

export type ScreenDvrTickCallback = (state: ScreenDvrProgressState) => void;

interface DvrChunk {
  blob: Blob;
  startMs: number;
  endMs: number;
}

export class ScreenDvrManager {
  // Maximum rolling buffer duration: 30 minutes in milliseconds
  private static readonly MAX_BUFFER_MS = 30 * 60 * 1000;
  private static readonly CHUNK_INTERVAL_MS = 1000;

  private mediaRecorder: MediaRecorder | null = null;
  private chunks: DvrChunk[] = [];
  private sessionStartWallTime = 0;
  private isLiveState = true;
  private currentPlaybackSeconds = 0;
  private dvrBlobUrl: string | null = null;
  private tickInterval: ReturnType<typeof setInterval> | null = null;
  private onTickCallbacks: Set<ScreenDvrTickCallback> = new Set();
  private mimeType = 'video/webm;codecs=vp8';

  constructor() {
    this.detectSupportedMimeType();
  }

  /**
   * Detects the optimal hardware-supported WebM video MIME type in Chromium/WebView2.
   */
  private detectSupportedMimeType(): void {
    const candidates = [
      'video/webm;codecs=vp8',
      'video/webm;codecs=vp9',
      'video/webm;codecs=h264',
      'video/webm'
    ];

    if (typeof MediaRecorder !== 'undefined') {
      for (const candidate of candidates) {
        if (MediaRecorder.isTypeSupported(candidate)) {
          this.mimeType = candidate;
          break;
        }
      }
    }
  }

  /**
   * Starts background recording and DVR timeline tracking for the given live stream.
   *
   * @param stream Active WebRTC MediaStream from child device.
   */
  public start(stream: MediaStream): boolean {
    if (this.mediaRecorder && this.mediaRecorder.state !== 'inactive') {
      return true; // Already actively recording this stream
    }

    this.stop();

    if (!stream || stream.getVideoTracks().length === 0) {
      console.warn('[ScreenDVR] Cannot start DVR: No video tracks in stream.');
      return false;
    }

    try {
      console.log('[ScreenDVR] Starting rolling DVR buffer with MIME:', this.mimeType);
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
            endMs: nowMs
          });
          chunkStartTime = nowMs;

          // Enforce 30-minute FIFO buffer eviction
          this.pruneOldChunks(nowMs);
        }
      };

      recorder.onerror = (err) => {
        console.error('[ScreenDVR] MediaRecorder error:', err);
      };

      // Record chunks every 1 second
      recorder.start(ScreenDvrManager.CHUNK_INTERVAL_MS);

      // Start 500ms timeline update ticker
      this.tickInterval = setInterval(() => {
        this.notifyTick();
      }, 500);

      console.log('[ScreenDVR] Background DVR recorder successfully active.');
      return true;
    } catch (err) {
      console.error('[ScreenDVR] Failed to start MediaRecorder:', err);
      return false;
    }
  }

  /**
   * Evicts chunks older than the 30-minute window from the front of the queue.
   */
  private pruneOldChunks(currentElapsedMs: number): void {
    const cutoffTime = currentElapsedMs - ScreenDvrManager.MAX_BUFFER_MS;
    if (cutoffTime <= 0) return;

    while (this.chunks.length > 1 && this.chunks[0].endMs < cutoffTime) {
      this.chunks.shift();
    }
  }

  /**
   * Seeks to a historical timestamp (seconds from session start) and configures the DVR video element.
   *
   * @param targetSeconds Desired playback position in seconds.
   * @param dvrVideoElement HTML5 <video> element dedicated to DVR time-shift playback.
   */
  public seekTo(targetSeconds: number, dvrVideoElement: HTMLVideoElement): void {
    const totalSeconds = this.getTotalDurationSeconds();
    const clampedTarget = Math.max(0, Math.min(targetSeconds, totalSeconds));

    // If seeking to within 2 seconds of the live edge, jump back to Live immediately
    if (clampedTarget >= totalSeconds - 2) {
      this.goLive(dvrVideoElement);
      return;
    }

    this.isLiveState = false;
    this.currentPlaybackSeconds = clampedTarget;

    if (this.chunks.length === 0) {
      console.warn('[ScreenDVR] Cannot seek: Buffer has no recorded chunks yet.');
      return;
    }

    try {
      // Compile all recorded chunks up to current moment into a single playable WebM Blob
      const allBlobs = this.chunks.map((c) => c.blob);
      const compiledBlob = new Blob(allBlobs, { type: this.mimeType });

      // Revoke previous Blob URL to prevent memory accumulation
      if (this.dvrBlobUrl) {
        URL.revokeObjectURL(this.dvrBlobUrl);
        this.dvrBlobUrl = null;
      }

      const url = URL.createObjectURL(compiledBlob);
      this.dvrBlobUrl = url;

      // Calculate offset relative to the buffer start
      const bufferStartSec = this.getBufferStartSeconds();
      const relativeOffset = Math.max(0, clampedTarget - bufferStartSec);

      console.log(`[ScreenDVR] Seeking to ${clampedTarget.toFixed(1)}s (offset ${relativeOffset.toFixed(1)}s in ${allBlobs.length} chunks)...`);

      dvrVideoElement.src = url;
      dvrVideoElement.onloadedmetadata = () => {
        dvrVideoElement.currentTime = relativeOffset;
        dvrVideoElement.play().catch((err) => {
          console.warn('[ScreenDVR] DVR video play prevented:', err);
        });
      };

      dvrVideoElement.ontimeupdate = () => {
        if (!this.isLiveState) {
          this.currentPlaybackSeconds = bufferStartSec + dvrVideoElement.currentTime;
          this.notifyTick();
        }
      };

      dvrVideoElement.onended = () => {
        // Reached end of recorded buffer -> return to live
        console.log('[ScreenDVR] Reached end of recorded buffer. Returning to Live...');
        this.goLive(dvrVideoElement);
      };

      this.notifyTick();
    } catch (err) {
      console.error('[ScreenDVR] Failed to load time-shifted video chunk:', err);
    }
  }

  /**
   * Snaps the player back to real-time live feed.
   */
  public goLive(dvrVideoElement?: HTMLVideoElement): void {
    console.log('[ScreenDVR] goLive() invoked. Returning to real-time feed...');
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

  /**
   * Stops background DVR recording and cleans up all memory buffers.
   */
  public stop(): void {
    if (this.tickInterval) {
      clearInterval(this.tickInterval);
      this.tickInterval = null;
    }

    if (this.mediaRecorder && this.mediaRecorder.state !== 'inactive') {
      try {
        this.mediaRecorder.stop();
      } catch (e) {
        console.warn('[ScreenDVR] MediaRecorder stop error:', e);
      }
      this.mediaRecorder = null;
    }

    if (this.dvrBlobUrl) {
      URL.revokeObjectURL(this.dvrBlobUrl);
      this.dvrBlobUrl = null;
    }

    this.chunks = [];
    this.sessionStartWallTime = 0;
    this.isLiveState = true;
    this.currentPlaybackSeconds = 0;
  }

  /**
   * Subscribes to DVR timeline tick updates (fired every 500ms).
   */
  public onTick(callback: ScreenDvrTickCallback): () => void {
    this.onTickCallbacks.add(callback);
    return () => this.onTickCallbacks.delete(callback);
  }

  /**
   * Returns current elapsed stream duration in seconds.
   */
  public getTotalDurationSeconds(): number {
    if (this.sessionStartWallTime === 0) return 0;
    return (Date.now() - this.sessionStartWallTime) / 1000;
  }

  /**
   * Returns earliest available timestamp in buffer in seconds.
   */
  public getBufferStartSeconds(): number {
    if (this.chunks.length === 0) return 0;
    return this.chunks[0].startMs / 1000;
  }

  /**
   * Returns true if user is currently watching at the live edge.
   */
  public isLive(): boolean {
    return this.isLiveState;
  }

  /**
   * Fires tick callbacks with current progress metrics.
   */
  private notifyTick(): void {
    const total = this.getTotalDurationSeconds();
    const current = this.isLiveState ? total : this.currentPlaybackSeconds;
    const progressPercent = total > 0 ? Math.min(100, Math.max(0, (current / total) * 100)) : 100;
    const bufferStart = this.getBufferStartSeconds();

    const state: ScreenDvrProgressState = {
      isLive: this.isLiveState,
      currentSeconds: current,
      totalSeconds: total,
      progressPercent,
      bufferStartSeconds: bufferStart
    };

    for (const cb of this.onTickCallbacks) {
      cb(state);
    }
  }
}
