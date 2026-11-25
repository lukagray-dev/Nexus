/**
 * @file opus-audio-player.ts
 * @description Real-time low-latency Opus audio player for internal system audio streaming.
 *
 * Hey friend! Here is how internal system audio playback works:
 * 1. The Android Child captures internal app/game audio via AudioPlaybackCapture (48kHz Mono 16-bit PCM).
 * 2. Android hardware MediaCodec encodes 20ms frames into raw Opus packets.
 * 3. Packets are streamed over DataChannel prefixed with "OPUS_AUDIO:<base64>".
 * 4. This OpusAudioPlayer decodes base64 into binary Uint8Array frames.
 * 5. Uses the WebCodecs AudioDecoder (hardware-accelerated in Chromium / WebView2)
 *    to decode raw Opus packets directly into PCM AudioData.
 * 6. Schedules the decoded audio frames with microsecond precision onto an AudioContext
 *    pipeline with GainNode volume and mute controls, ensuring zero stutter and low latency!
 */

import { settingsState } from '../../settings/sidebar/state.js';
import {
  EVENT_AUDIO_OUTPUT_CHANGED,
  applyAudioOutputDevice,
} from '../mic/audio-device-service.js';

export class OpusAudioPlayer {
  private audioContext: AudioContext | null = null;
  private gainNode: GainNode | null = null;
  private decoder: AudioDecoder | null = null;
  private nextPlayTime = 0;
  private isMuted = false;
  private volume = 1.0;
  private isRunning = false;
  private packetCount = 0;
  private outputChangeListener: ((event: Event) => void) | null = null;

  constructor() {
    this.initPipeline();
  }

  /**
   * Initializes the AudioContext and WebCodecs AudioDecoder pipeline.
   */
  private initPipeline(): void {
    if (typeof window === 'undefined') return;

    try {
      const AudioCtxClass = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      if (AudioCtxClass) {
        this.audioContext = new AudioCtxClass({ sampleRate: 48000 });
        this.gainNode = this.audioContext.createGain();
        this.gainNode.gain.setValueAtTime(this.isMuted ? 0 : this.volume, this.audioContext.currentTime);
        this.gainNode.connect(this.audioContext.destination);

        // Apply configured Monitoring Output Speaker immediately and listen for live changes
        void this.applyConfiguredOutputDevice(settingsState.getValue('outputDeviceId'));
        this.outputChangeListener = (event: Event) => {
          const customEvent = event as CustomEvent<{ deviceId?: string }>;
          const targetId = customEvent.detail?.deviceId ?? settingsState.getValue('outputDeviceId');
          void this.applyConfiguredOutputDevice(targetId);
        };
        if (typeof window.addEventListener === 'function') {
          window.addEventListener(EVENT_AUDIO_OUTPUT_CHANGED, this.outputChangeListener);
        }
      }

      if (typeof AudioDecoder !== 'undefined') {
        this.decoder = new AudioDecoder({
          output: (audioData: AudioData) => {
            this.handleDecodedAudio(audioData);
          },
          error: (error: DOMException | Error) => {
            console.warn('[OpusAudioPlayer] Decoder error:', error);
          }
        });

        this.decoder.configure({
          codec: 'opus',
          sampleRate: 48000,
          numberOfChannels: 1
        });
        this.isRunning = true;
      } else {
        console.warn('[OpusAudioPlayer] WebCodecs AudioDecoder not available in this environment.');
      }
    } catch (e) {
      console.warn('[OpusAudioPlayer] Failed to initialize audio pipeline:', e);
    }
  }

  /**
   * Receives a raw Base64 Opus frame, decodes and enqueues it for playback.
   *
   * @param base64 Base64 string of the Opus packet
   */
  public feedBase64Packet(base64: string): void {
    if (!this.isRunning || !this.decoder || this.decoder.state !== 'configured') {
      return;
    }

    try {
      // Decode Base64 to Uint8Array binary
      const binaryString = atob(base64);
      const len = binaryString.length;
      const bytes = new Uint8Array(len);
      for (let i = 0; i < len; i++) {
        bytes[i] = binaryString.charCodeAt(i);
      }

      this.packetCount++;
      const timestampMicros = this.packetCount * 20000; // 20ms frames = 20,000 microseconds

      const chunk = new EncodedAudioChunk({
        type: 'key',
        timestamp: timestampMicros,
        data: bytes
      });

      this.decoder.decode(chunk);
    } catch (e) {
      console.warn('[OpusAudioPlayer] Failed to feed audio packet:', e);
    }
  }

  /**
   * Renders decoded PCM AudioData directly into the AudioContext timeline.
   */
  private handleDecodedAudio(audioData: AudioData): void {
    if (!this.audioContext || !this.gainNode) {
      audioData.close();
      return;
    }

    try {
      // Resume AudioContext if suspended by browser autoplay policy
      if (this.audioContext.state === 'suspended') {
        this.audioContext.resume().catch(() => {});
      }

      const numberOfChannels = audioData.numberOfChannels;
      const numberOfFrames = audioData.numberOfFrames;
      const sampleRate = audioData.sampleRate;

      const audioBuffer = this.audioContext.createBuffer(
        numberOfChannels,
        numberOfFrames,
        sampleRate
      );

      for (let ch = 0; ch < numberOfChannels; ch++) {
        const channelData = audioBuffer.getChannelData(ch);
        audioData.copyTo(channelData, { planeIndex: ch, format: 'f32' });
      }

      const sourceNode = this.audioContext.createBufferSource();
      sourceNode.buffer = audioBuffer;
      sourceNode.connect(this.gainNode);

      const currentTime = this.audioContext.currentTime;
      // Schedule immediately or seamlessly after previous packet with 30ms jitter buffer
      if (this.nextPlayTime < currentTime || this.nextPlayTime > currentTime + 0.5) {
        this.nextPlayTime = currentTime + 0.03;
      }

      sourceNode.start(this.nextPlayTime);
      this.nextPlayTime += audioBuffer.duration;

    } catch (e) {
      console.warn('[OpusAudioPlayer] Error scheduling decoded audio:', e);
    } finally {
      audioData.close();
    }
  }

  /**
   * Sets the output volume from 0.0 to 1.0.
   */
  public setVolume(volume: number): void {
    this.volume = Math.max(0, Math.min(1, volume));
    if (this.gainNode && this.audioContext && !this.isMuted) {
      this.gainNode.gain.setValueAtTime(this.volume, this.audioContext.currentTime);
    }
  }

  /**
   * Mutes or unmutes the audio output.
   */
  public setMuted(muted: boolean): void {
    this.isMuted = muted;
    if (this.gainNode && this.audioContext) {
      const targetGain = muted ? 0 : this.volume;
      this.gainNode.gain.setValueAtTime(targetGain, this.audioContext.currentTime);
    }
  }

  /**
   * Resets playback state and flush pending decoder frames.
   */
  public reset(): void {
    this.packetCount = 0;
    this.nextPlayTime = 0;
    if (this.decoder && this.decoder.state === 'configured') {
      try {
        this.decoder.reset();
        this.decoder.configure({
          codec: 'opus',
          sampleRate: 48000,
          numberOfChannels: 1
        });
      } catch (e) {
        console.warn('[OpusAudioPlayer] Reset error:', e);
      }
    }
  }

  /**
   * Routes the internal AudioContext destination to the chosen Monitoring Output Speaker.
   */
  public async applyConfiguredOutputDevice(deviceId: string): Promise<void> {
    if (!this.audioContext) return;
    await applyAudioOutputDevice(
      this.audioContext as unknown as { setSinkId?: (sinkId: string) => Promise<void> },
      deviceId
    );
  }

  /**
   * Tears down decoder and AudioContext.
   */
  public stop(): void {
    this.isRunning = false;
    this.reset();

    if (
      this.outputChangeListener &&
      typeof window !== 'undefined' &&
      typeof window.removeEventListener === 'function'
    ) {
      window.removeEventListener(EVENT_AUDIO_OUTPUT_CHANGED, this.outputChangeListener);
      this.outputChangeListener = null;
    }

    if (this.decoder && this.decoder.state !== 'closed') {
      try {
        this.decoder.close();
      } catch (e) {
        console.warn('[OpusAudioPlayer] Decoder close error:', e);
      }
      this.decoder = null;
    }

    if (this.audioContext && this.audioContext.state !== 'closed') {
      try {
        this.audioContext.close().catch(() => {});
      } catch (e) {
        console.warn('[OpusAudioPlayer] AudioContext close error:', e);
      }
      this.audioContext = null;
      this.gainNode = null;
    }
  }
}
