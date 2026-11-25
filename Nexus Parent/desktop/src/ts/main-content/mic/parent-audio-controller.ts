/**
 * @file parent-audio-controller.ts
 * @description Manages parent microphone capture, real-time voice changer DSP,
 * and live WebRTC audio transmission for the Parent-to-Child Voice Intercom feature.
 *
 * Hey friend! Here is how speaking to the child phone works from Desktop:
 * 1. Parent clicks the Speak button in the Mic panel (or selects a voice).
 * 2. ParentAudioController acquires the chosen PC microphone via `navigator.mediaDevices.getUserMedia`
 *    applying `inputDeviceId` and `noiseSuppression` (AEC / noise reduction / AGC) from Settings.
 * 3. The raw microphone stream is routed through VoiceChangerEngine, which applies the active
 *    DSP preset (e.g. Robot, Monster, Human Female, Alien, Echo, or clean Original).
 * 4. We find the WebRTC audio transceiver and attach our processed audio track using sender.replaceTrack(track).
 * 5. We set track.enabled = true, and dispatch "PARENT_AUDIO_UNMUTE" via the DataChannel.
 * 6. Changing `inputDeviceId` or `noiseSuppression` in Settings hot-swaps the capture stream
 *    seamlessly if speaking, or resets idle capture so the next Speak uses the new settings.
 */

import { webrtcManager } from '../../webrtc/webrtc-manager.js';
import { settingsState } from '../../settings/sidebar/state.js';
import { showToast } from '../../shared/toast.js';
import { VoicePreset, VoicePresetId, VOICE_PRESETS } from './voice-presets.js';
import { voiceChangerEngine } from './voice-changer-engine.js';

/**
 * Builds WebRTC `getUserMedia` audio constraints from Settings values.
 */
export function buildAudioCaptureConstraints(
  inputDeviceId: string,
  noiseSuppression: boolean
): MediaTrackConstraints {
  const constraints: MediaTrackConstraints = {
    echoCancellation: noiseSuppression,
    noiseSuppression,
    autoGainControl: noiseSuppression,
    sampleRate: 48000,
  };

  const trimmedDeviceId = (inputDeviceId || '').trim();
  if (trimmedDeviceId && trimmedDeviceId !== 'default') {
    constraints.deviceId = { exact: trimmedDeviceId };
  }

  return constraints;
}

export interface MicStreamAcquireResult {
  stream: MediaStream;
  usedFallback: boolean;
}

/**
 * Acquires the requested microphone stream and automatically falls back to System Default
 * if a previously saved external microphone is disconnected (`OverconstrainedError` / `NotFoundError`).
 */
export async function acquireMicStreamWithFallback(
  inputDeviceId: string,
  noiseSuppression: boolean,
  onFallback?: (message: string) => void
): Promise<MicStreamAcquireResult> {
  if (
    typeof navigator === 'undefined' ||
    !navigator.mediaDevices ||
    typeof navigator.mediaDevices.getUserMedia !== 'function'
  ) {
    throw new Error('MediaDevices audio capture is unavailable in this environment.');
  }

  const trimmedId = (inputDeviceId || '').trim();
  const hasExplicitDevice = Boolean(trimmedId && trimmedId !== 'default');

  try {
    const stream = await navigator.mediaDevices.getUserMedia({
      audio: buildAudioCaptureConstraints(trimmedId, noiseSuppression),
    });
    return { stream, usedFallback: false };
  } catch (err) {
    const errName = err instanceof Error ? err.name : '';
    const shouldFallback =
      hasExplicitDevice &&
      (errName === 'OverconstrainedError' ||
        errName === 'NotFoundError' ||
        errName === 'ConstraintNotSatisfiedError');

    if (!shouldFallback) {
      throw err;
    }

    console.warn(
      `[ParentAudio] Saved microphone "${trimmedId}" unavailable (${errName}); falling back to System Default.`,
      err
    );
    onFallback?.('Selected microphone unavailable — using System Default');

    const fallbackStream = await navigator.mediaDevices.getUserMedia({
      audio: buildAudioCaptureConstraints('default', noiseSuppression),
    });
    return { stream: fallbackStream, usedFallback: true };
  }
}

export class ParentAudioController {
  private localAudioStream: MediaStream | null = null;
  private audioTrack: MediaStreamTrack | null = null;
  private isMutedState = true;
  private isInitialized = false;
  private activeInputDeviceId = 'default';
  private activeNoiseSuppression = true;

  /**
   * Initializes local microphone capture, mounts the VoiceChangerEngine DSP graph,
   * and attaches the processed track to the WebRTC peer connection audio transceiver sender.
   * Audio track starts muted (disabled) until parent explicitly clicks to speak.
   *
   * @returns true if microphone was initialized and attached successfully; false otherwise.
   */
  public async initialize(): Promise<boolean> {
    if (this.isInitialized && this.audioTrack && this.audioTrack.readyState === 'live') {
      console.log('[ParentAudio] Microphone is already initialized and live.');
      return true;
    }

    try {
      const configuredDeviceId = settingsState.getValue('inputDeviceId');
      const configuredNoiseSuppression = settingsState.getValue('noiseSuppression');

      console.log(
        `[ParentAudio] Requesting microphone access (device=${configuredDeviceId}, noiseSuppression=${configuredNoiseSuppression})...`
      );
      const { stream } = await acquireMicStreamWithFallback(
        configuredDeviceId,
        configuredNoiseSuppression,
        (msg) => showToast(msg)
      );
      this.localAudioStream = stream;
      this.activeInputDeviceId = configuredDeviceId;
      this.activeNoiseSuppression = configuredNoiseSuppression;

      const rawTracks = this.localAudioStream.getAudioTracks();
      if (!rawTracks || rawTracks.length === 0) {
        console.error('[ParentAudio] No audio tracks found in local stream!');
        return false;
      }

      // Initialize VoiceChangerEngine with raw microphone stream
      console.log('[ParentAudio] Initializing VoiceChangerEngine DSP pipeline...');
      const dspSuccess = voiceChangerEngine.initialize(this.localAudioStream);
      if (!dspSuccess) {
        console.warn('[ParentAudio] VoiceChangerEngine failed to initialize. Falling back to raw track.');
        this.audioTrack = rawTracks[0];
      } else {
        const processedTrack = voiceChangerEngine.getProcessedTrack();
        this.audioTrack = processedTrack || rawTracks[0];
      }

      // Keep microphone track muted initially
      this.audioTrack.enabled = false;
      this.isMutedState = true;

      await this.attachTrackToWebRtcSender(this.audioTrack, this.localAudioStream);

      this.isInitialized = true;
      console.log('[ParentAudio] Local microphone and voice changer initialized (muted baseline).');
      return true;
    } catch (error) {
      console.error('[ParentAudio] Failed to initialize local microphone:', error);
      return false;
    }
  }

  /**
   * Attaches or replaces the outbound audio track on the WebRTC audio transceiver sender.
   */
  private async attachTrackToWebRtcSender(
    track: MediaStreamTrack,
    stream: MediaStream
  ): Promise<void> {
    const pc = webrtcManager.getPeerConnection();
    if (!pc) {
      console.warn('[ParentAudio] PeerConnection not available yet. Track will be attached when session connects.');
      return;
    }

    const transceivers = pc.getTransceivers ? pc.getTransceivers() : [];
    const audioTransceiver = transceivers.find(
      (t) => t.receiver?.track?.kind === 'audio' || t.sender?.track?.kind === 'audio'
    );

    if (audioTransceiver && audioTransceiver.sender) {
      console.log('[ParentAudio] Replacing track on existing audio transceiver sender...');
      await audioTransceiver.sender.replaceTrack(track);
    } else {
      console.log('[ParentAudio] No audio transceiver sender found. Adding track to PeerConnection...');
      pc.addTrack(track, stream);
    }
  }

  /**
   * Applies updated `inputDeviceId` or `noiseSuppression` Settings values.
   * - When idle/muted: releases the old stream so the next `unmute()` captures fresh settings.
   * - When actively speaking: hot-swaps the capture stream without dropping live intercom.
   */
  public async reconfigureAudioInput(): Promise<boolean> {
    const nextDeviceId = settingsState.getValue('inputDeviceId');
    const nextNoiseSuppression = settingsState.getValue('noiseSuppression');

    if (!this.isInitialized) {
      return true;
    }

    if (
      this.activeInputDeviceId === nextDeviceId &&
      this.activeNoiseSuppression === nextNoiseSuppression
    ) {
      return true;
    }

    // Idle state: tear down cached capture cleanly so next Speak opens with new hardware/AEC config
    if (this.isMutedState) {
      console.log('[ParentAudio] Audio settings changed while idle; resetting capture for next Speak.');
      this.cleanup();
      return true;
    }

    // Active speaking state: acquire replacement stream first before stopping the old one
    const previousStream = this.localAudioStream;
    try {
      const { stream: nextStream } = await acquireMicStreamWithFallback(
        nextDeviceId,
        nextNoiseSuppression,
        (msg) => showToast(msg)
      );

      const nextRawTracks = nextStream.getAudioTracks();
      if (!nextRawTracks || nextRawTracks.length === 0) {
        nextStream.getTracks().forEach((t) => t.stop());
        return false;
      }

      const swappedIntoDsp = voiceChangerEngine.updateInputStream(nextStream);
      if (swappedIntoDsp) {
        const processedTrack = voiceChangerEngine.getProcessedTrack();
        if (processedTrack) {
          processedTrack.enabled = true;
          this.audioTrack = processedTrack;
        }
      } else {
        const rawTrack = nextRawTracks[0];
        rawTrack.enabled = true;
        this.audioTrack = rawTrack;
        await this.attachTrackToWebRtcSender(rawTrack, nextStream);
      }

      this.localAudioStream = nextStream;
      this.activeInputDeviceId = nextDeviceId;
      this.activeNoiseSuppression = nextNoiseSuppression;

      if (previousStream) {
        previousStream.getTracks().forEach((track) => {
          try {
            track.stop();
          } catch {
            // Ignore cleanup error on replaced stream
          }
        });
      }

      console.log('[ParentAudio] Live microphone input reconfigured seamlessly.');
      return true;
    } catch (error) {
      console.error('[ParentAudio] Failed to hot-swap microphone settings:', error);
      showToast('Failed to switch microphone settings; keeping active input');
      return false;
    }
  }

  /**
   * Unmutes the microphone track and transmits PARENT_AUDIO_UNMUTE over the DataChannel.
   *
   * @returns true if unmuted successfully; false otherwise.
   */
  public async unmute(): Promise<boolean> {
    if (!this.isInitialized || !this.audioTrack || this.audioTrack.readyState !== 'live') {
      const initialized = await this.initialize();
      if (!initialized) {
        throw new Error('Microphone permission denied or audio device unavailable.');
      }
    }

    if (!this.audioTrack) {
      throw new Error('Audio track is not available.');
    }

    // Resume any suspended browser audio contexts
    await voiceChangerEngine.resume();

    // Enable track so live speech begins streaming over WebRTC RTP sender
    this.audioTrack.enabled = true;
    this.isMutedState = false;

    console.log('[ParentAudio] Audio track enabled. Sending PARENT_AUDIO_UNMUTE command...');
    const sent = webrtcManager.sendCommand('PARENT_AUDIO_UNMUTE');
    if (!sent) {
      console.warn('[ParentAudio] Could not send PARENT_AUDIO_UNMUTE: DataChannel is not ready.');
    }

    return true;
  }

  /**
   * Mutes the microphone track and transmits PARENT_AUDIO_MUTE over the DataChannel.
   *
   * @returns true if muted successfully; false otherwise.
   */
  public mute(): boolean {
    if (this.audioTrack) {
      // Disable track so zero audio data is transmitted
      this.audioTrack.enabled = false;
    }
    this.isMutedState = true;

    console.log('[ParentAudio] Audio track disabled (muted). Sending PARENT_AUDIO_MUTE command...');
    const sent = webrtcManager.sendCommand('PARENT_AUDIO_MUTE');
    if (!sent) {
      console.warn('[ParentAudio] Could not send PARENT_AUDIO_MUTE: DataChannel is not ready.');
    }

    return true;
  }

  /**
   * Changes the active voice preset. Can be called while idle or during active live speech.
   *
   * @param voiceId The identifier of the voice preset to activate.
   */
  public setVoice(voiceId: VoicePresetId): void {
    console.log(`[ParentAudio] Setting active voice preset to: ${voiceId}`);
    voiceChangerEngine.setVoice(voiceId);
  }

  /**
   * Returns the currently active voice preset identifier.
   */
  public getActiveVoice(): VoicePresetId {
    return voiceChangerEngine.getActiveVoice();
  }

  /**
   * Returns the full list of all available voice presets.
   */
  public getAvailableVoices(): VoicePreset[] {
    return VOICE_PRESETS;
  }

  /**
   * Returns whether the parent microphone is currently muted.
   */
  public isMuted(): boolean {
    return this.isMutedState;
  }

  /**
   * Returns whether live parent audio is currently transmitting.
   */
  public isActive(): boolean {
    return !this.isMutedState && this.isInitialized;
  }

  /**
   * Stops all active microphone tracks, tears down VoiceChangerEngine, and releases hardware inputs.
   */
  public cleanup(): void {
    console.log('[ParentAudio] Cleaning up microphone resources...');
    voiceChangerEngine.cleanup();

    if (this.audioTrack) {
      this.audioTrack.enabled = false;
      this.audioTrack.stop();
      this.audioTrack = null;
    }

    if (this.localAudioStream) {
      this.localAudioStream.getTracks().forEach((track) => track.stop());
      this.localAudioStream = null;
    }

    this.isInitialized = false;
    this.isMutedState = true;
    this.activeInputDeviceId = 'default';
    this.activeNoiseSuppression = true;
  }
}

export const parentAudioController = new ParentAudioController();
