/**
 * @file parent-audio-controller.ts
 * @description Manages parent microphone capture and live WebRTC audio transmission
 * for the Parent-to-Child Voice Intercom feature.
 *
 * Hey friend! Here is how speaking to the child phone works from Desktop:
 * 1. Parent clicks the Speak button in the Mic panel.
 * 2. ParentAudioController acquires the user's PC microphone via navigator.mediaDevices.getUserMedia
 *    with professional noise suppression, echo cancellation, auto-gain, and 48kHz audio.
 * 3. We find the WebRTC audio transceiver and attach our microphone track using sender.replaceTrack(track).
 * 4. We set track.enabled = true, and dispatch "PARENT_AUDIO_UNMUTE" via the DataChannel.
 * 5. Child device receives the command, cranks loudspeaker volume to MAX, and replies "PARENT_AUDIO_UNMUTED".
 * 6. When the parent clicks again to stop speaking, we set track.enabled = false and dispatch "PARENT_AUDIO_MUTE".
 * 7. Child restores normal volume levels and replies "PARENT_AUDIO_MUTED".
 */

import { webrtcManager } from '../../webrtc/webrtc-manager.js';

export class ParentAudioController {
  private localAudioStream: MediaStream | null = null;
  private audioTrack: MediaStreamTrack | null = null;
  private isMutedState = true;
  private isInitialized = false;

  /**
   * Initializes local microphone capture and attaches track to the peer connection sender.
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
      console.log('[ParentAudio] Requesting local microphone access with noise cancellation...');
      this.localAudioStream = await navigator.mediaDevices.getUserMedia({
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
          sampleRate: 48000,
        },
      });

      const tracks = this.localAudioStream.getAudioTracks();
      if (!tracks || tracks.length === 0) {
        console.error('[ParentAudio] No audio tracks found in local stream!');
        return false;
      }

      this.audioTrack = tracks[0];
      // Keep microphone track muted initially
      this.audioTrack.enabled = false;
      this.isMutedState = true;

      const pc = webrtcManager.getPeerConnection();
      if (pc) {
        // Attach track to the audio transceiver sender
        const transceivers = pc.getTransceivers ? pc.getTransceivers() : [];
        const audioTransceiver = transceivers.find(
          (t) => t.receiver?.track?.kind === 'audio' || t.sender?.track?.kind === 'audio'
        );

        if (audioTransceiver && audioTransceiver.sender) {
          console.log('[ParentAudio] Replacing track on existing audio transceiver sender...');
          await audioTransceiver.sender.replaceTrack(this.audioTrack);
        } else {
          console.log('[ParentAudio] No audio transceiver sender found. Adding track to PeerConnection...');
          pc.addTrack(this.audioTrack, this.localAudioStream);
        }
      } else {
        console.warn('[ParentAudio] PeerConnection not available yet. Track will be attached when session connects.');
      }

      this.isInitialized = true;
      console.log('[ParentAudio] Local microphone initialized (muted baseline).');
      return true;
    } catch (error) {
      console.error('[ParentAudio] Failed to initialize local microphone:', error);
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
   * Stops all active microphone tracks and releases hardware audio inputs.
   */
  public cleanup(): void {
    console.log('[ParentAudio] Cleaning up microphone resources...');
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
  }
}

export const parentAudioController = new ParentAudioController();
