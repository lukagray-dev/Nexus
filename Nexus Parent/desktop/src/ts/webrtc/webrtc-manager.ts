/**
 * @file webrtc-manager.ts
 * @description Native WebRTC manager coordinating RTCPeerConnection and DataChannel in Nexus Parent Desktop.
 *
 * Hey friend! Here is how the desktop WebRTC connection pipeline works step-by-step:
 * 1. Checks that the child device is valid and currently marked as online in Firebase.
 * 2. Creates a clean RTCPeerConnection instance with Google STUN servers.
 * 3. Adds transceivers for receiving child camera video and bidirectional mic audio.
 * 4. Creates the 'nexus-control' DataChannel for commands and telemetry.
 * 5. Generates the SDP Offer and gathers all local/STUN ICE candidates into the SDP (Fast Non-Trickle ICE).
 * 6. Publishes the self-contained SDP offer to Firebase at `/signaling/{childId}/offer`.
 * 7. Listens for the child's self-contained SDP answer at `/signaling/{childId}/answer`.
 * 8. Applies remote description, and direct peer-to-peer connectivity is active within 1-2 seconds!
 */

import { STUN_SERVERS, DATA_CHANNEL_LABEL } from './firebase-config.js';
import { signalingClient, type ChildDeviceMetadata } from './signaling-client.js';
import { authManager } from './auth-manager.js';
import { settingsState } from '../settings/sidebar/state.js';

/**
 * Sorts WebRTC video codec capabilities to prioritize GPU hardware-decoded codecs
 * (H.264 / VP9) when `hardwareAcceleration` is enabled, or software-friendly VP8 first when disabled.
 */
export function sortVideoCodecsForAcceleration<T extends { mimeType: string }>(
  codecs: readonly T[],
  hardwareAcceleration: boolean
): T[] {
  const priority = hardwareAcceleration
    ? ['video/h264', 'video/vp9', 'video/vp8']
    : ['video/vp8', 'video/vp9', 'video/h264'];

  const rankOf = (mime: string): number => {
    const normalized = mime.toLowerCase();
    const idx = priority.indexOf(normalized);
    return idx === -1 ? priority.length : idx;
  };

  return [...codecs].sort((a, b) => rankOf(a.mimeType) - rankOf(b.mimeType));
}

/**
 * Applies preferred video codec ordering to the receiving video transceiver when supported.
 */
export function applyVideoCodecPreference(
  transceiver: RTCRtpTransceiver | null | undefined,
  hardwareAcceleration: boolean
): boolean {
  if (!transceiver || typeof transceiver.setCodecPreferences !== 'function') {
    return false;
  }

  try {
    const caps =
      typeof RTCRtpReceiver !== 'undefined' && typeof RTCRtpReceiver.getCapabilities === 'function'
        ? RTCRtpReceiver.getCapabilities('video')
        : null;
    if (!caps || !Array.isArray(caps.codecs) || caps.codecs.length === 0) {
      return false;
    }

    const sorted = sortVideoCodecsForAcceleration(caps.codecs, hardwareAcceleration);
    transceiver.setCodecPreferences(sorted);
    return true;
  } catch (err) {
    console.warn('[WebRTCManager] Could not apply video codec preferences:', err);
    return false;
  }
}

export type ConnectionStateListener = (state: RTCPeerConnectionState) => void;
export type MessageListener = (message: string) => void;
export type TrackListener = (track: MediaStreamTrack, stream: MediaStream) => void;

/**
 * Supported hardware video feed categories in Nexus Parent.
 * Hey friend! Because the child device shares a single WebRTC video transceiver
 * across both feeds (zero SDP renegotiation architecture), these feeds are strictly
 * mutually exclusive. Only one can be actively routing frames at any time!
 */
export type VideoFeedType = 'camera' | 'screen';

export class DesktopWebRtcManager {
  private peerConnection: RTCPeerConnection | null = null;
  private dataChannel: RTCDataChannel | null = null;
  private currentChildId: string | null = null;
  private unsubscribeAnswer: (() => void) | null = null;
  private micStream: MediaStream | null = null;

  // Cached hardware media streams for camera and screen
  private cameraStream: MediaStream | null = null;
  private screenStream: MediaStream | null = null;
  private rawVideoStream: MediaStream | null = null;

  // Tracks which video feed is currently authorized to receive frames ('camera' | 'screen' | null)
  private activeVideoType: VideoFeedType | null = null;

  // Registry of deactivation callbacks allowing camera and screen to cleanly yield to each other
  private feedDeactivators: Map<VideoFeedType, (sendOffCommand?: boolean) => void> = new Map();

  // Event callback listeners
  private connectionStateListeners: Set<ConnectionStateListener> = new Set();
  private messageListeners: Set<MessageListener> = new Set();
  private trackListeners: Set<TrackListener> = new Set();

  /**
   * Registers a deactivation handler for a specific video feed ('camera' | 'screen').
   * Hey friend! When the user hits Play on Screen while Camera is playing,
   * the screen module calls deactivateFeed('camera') so camera cleanly shuts down first!
   *
   * @param feed The video feed type to register
   * @param deactivator The callback that sets the feed to IDLE and clears its surfaces
   * @returns Cleanup unsubscribe function
   */
  public registerFeedDeactivator(feed: VideoFeedType, deactivator: (sendOffCommand?: boolean) => void): () => void {
    this.feedDeactivators.set(feed, deactivator);
    return () => {
      this.feedDeactivators.delete(feed);
    };
  }

  /**
   * Triggers the registered deactivation handler for a given video feed.
   *
   * @param feed The feed to deactivate ('camera' | 'screen')
   * @param sendOffCommand Whether to dispatch the off command over DataChannel (default: true)
   */
  public deactivateFeed(feed: VideoFeedType, sendOffCommand = true): void {
    const deactivator = this.feedDeactivators.get(feed);
    if (deactivator) {
      console.log(`[WebRTCManager] Deactivating video feed via coordinator: ${feed} (sendOffCommand=${sendOffCommand})`);
      deactivator(sendOffCommand);
    }
  }

  /**
   * Updates the active video feed type ('camera' | 'screen' | null).
   *
   * Hey friend! Here is why this router method is essential:
   * The Android child attaches both Camera2 and ScreenCapturer tracks to the same
   * video RtpSender on the connection. By gating getCameraStream() and getScreenStream()
   * behind this activeVideoType check:
   * - Camera page only ever gets frames when 'camera' is active.
   * - Screen page only ever gets frames when 'screen' is active.
   * - When switching, neither page can EVER show the other's video frames!
   */
  public setActiveVideoType(type: VideoFeedType | null): void {
    if (this.activeVideoType === type) return;
    console.log(`[WebRTCManager] Switching active video feed type: ${this.activeVideoType} -> ${type}`);
    this.activeVideoType = type;

    if (type === 'camera') {
      this.cameraStream = this.rawVideoStream;
      this.screenStream = null;
      if (this.cameraStream && this.cameraStream.getVideoTracks().length > 0) {
        window.dispatchEvent(new CustomEvent('nexus-camera-stream', {
          detail: { track: this.cameraStream.getVideoTracks()[0], stream: this.cameraStream }
        }));
      }
    } else if (type === 'screen') {
      this.screenStream = this.rawVideoStream;
      this.cameraStream = null;
      if (this.screenStream && this.screenStream.getVideoTracks().length > 0) {
        window.dispatchEvent(new CustomEvent('nexus-screen-stream', {
          detail: { track: this.screenStream.getVideoTracks()[0], stream: this.screenStream }
        }));
      }
    } else {
      this.cameraStream = null;
      this.screenStream = null;
    }
  }

  /**
   * Returns the currently active video feed type ('camera' | 'screen' | null).
   */
  public getActiveVideoType(): VideoFeedType | null {
    return this.activeVideoType;
  }

  /**
   * Returns the active RTCPeerConnection instance, or null if disconnected.
   */
  public getPeerConnection(): RTCPeerConnection | null {
    return this.peerConnection;
  }

  /**
   * Returns the active DataChannel instance, or null if closed.
   */
  public getDataChannel(): RTCDataChannel | null {
    return this.dataChannel;
  }

  /**
   * Returns the incoming microphone MediaStream if available, or null.
   */
  public getMicStream(): MediaStream | null {
    return this.micStream;
  }

  /**
   * Returns the incoming camera MediaStream IF camera monitoring is actively authorized, or null.
   */
  public getCameraStream(): MediaStream | null {
    if (this.activeVideoType !== 'camera') {
      return null;
    }
    return this.cameraStream;
  }

  /**
   * Returns the incoming screen MediaStream IF screen monitoring is actively authorized, or null.
   */
  public getScreenStream(): MediaStream | null {
    if (this.activeVideoType !== 'screen') {
      return null;
    }
    return this.screenStream;
  }

  /**
   * Initiates the Fast Non-Trickle WebRTC connection handshake with the target child device.
   *
   * @param childId The 12-digit Child ID entered by the parent
   * @returns Device metadata upon successful connection
   */
  public async connect(childId: string): Promise<ChildDeviceMetadata> {
    console.log('[WebRTCManager] Starting connection sequence for Child ID:', childId);

    // Step 1: Ensure active Firebase anonymous authentication
    await authManager.ensureAuthenticated();

    // Step 2: Validate child device status in Firebase Realtime Database
    const deviceMetadata = await signalingClient.validateChildDevice(childId);
    this.currentChildId = childId;

    // Step 3: Clear any stale signaling handshake sessions
    await signalingClient.clearSignalingSession(childId);

    // Step 4: Create fresh RTCPeerConnection
    this.createPeerConnection();
    const pc = this.peerConnection;
    if (!pc) throw new Error('Failed to create RTCPeerConnection');

    // Step 5: Add receiving transceivers for camera/screen video and bidirectional transceiver for mic audio
    const videoTransceiver = pc.addTransceiver('video', { direction: 'recvonly' });
    applyVideoCodecPreference(videoTransceiver, settingsState.getValue('hardwareAcceleration'));
    pc.addTransceiver('audio', { direction: 'sendrecv' });
    console.log('[WebRTCManager] Video transceiver (recvonly) and Audio transceiver (sendrecv) added.');

    // Step 6: Create the primary control DataChannel
    this.setupDataChannel(pc);

    // Step 7: Create offer and gather ICE candidates locally (Fast Non-Trickle ICE)
    const offer = await pc.createOffer();
    await pc.setLocalDescription(offer);
    console.log('[WebRTCManager] Local description set. Gathering ICE candidates locally...');

    // Wait for fast ICE gathering to complete (typically 200-400ms with 2 Google STUN mirrors)
    await this.waitForIceGatheringComplete(pc, 1000);

    const completedSdp = pc.localDescription;
    if (!completedSdp) {
      throw new Error('Local description was null after ICE gathering completed.');
    }

    console.log('[WebRTCManager] Non-Trickle ICE gathering complete. Transmitting offer...');
    await signalingClient.sendOffer(childId, completedSdp);

    // Step 8: Listen for child's answer on Firebase and maintain listener for renegotiation
    await new Promise<void>((resolve, reject) => {
      let initialHandshakeDone = false;
      const timeoutTimer = setTimeout(() => {
        if (!initialHandshakeDone) {
          this.unsubscribeAnswer?.();
          reject(new Error('Connection timed out waiting for child device response.'));
        }
      }, 30000);

      this.unsubscribeAnswer = signalingClient.listenForAnswer(childId, async (answer) => {
        try {
          // Case 1: Initial Answer Handshake
          if (pc.signalingState === 'have-local-offer') {
            console.log('[WebRTCManager] Received initial Non-Trickle SDP Answer from child. Applying remote description...');
            clearTimeout(timeoutTimer);
            initialHandshakeDone = true;
            await pc.setRemoteDescription(new RTCSessionDescription(answer));
            console.log('[WebRTCManager] Remote description set successfully. WebRTC link active!');
            resolve();
            return;
          }

          // Case 2: Role-Reversal Renegotiation Offer from Child (e.g. MIC_ON / MIC_OFF)
          if (answer.type === 'offer' && pc.signalingState === 'stable') {
            console.log('[WebRTCManager] Received role-reversal renegotiation OFFER from child.');
            await pc.setRemoteDescription(new RTCSessionDescription(answer));
            console.log('[WebRTCManager] Remote description set (child offer). Creating parent answer...');

            const parentAnswer = await pc.createAnswer();
            await pc.setLocalDescription(parentAnswer);
            console.log('[WebRTCManager] Created and set parent local answer. Transmitting immediately...');

            // On an active connection, candidate routes are already established. Transmit immediately!
            await signalingClient.sendRenegotiationAnswer(childId, parentAnswer);
            console.log('[WebRTCManager] Sent parent renegotiation answer to Firebase (/offer)');

            // Acknowledge renegotiation completion over DataChannel so Child can confirm MIC_STARTED
            if (this.dataChannel && this.dataChannel.readyState === 'open') {
              this.dataChannel.send('RENEGOTIATION_COMPLETE');
              console.log('[WebRTCManager] Dispatched RENEGOTIATION_COMPLETE to child via DataChannel.');
            }
            return;
          }

          console.log('[WebRTCManager] Received SDP in state:', pc.signalingState, 'type:', answer.type);
        } catch (err) {
          console.error('[WebRTCManager] Error during signaling message processing:', err);
          if (!initialHandshakeDone) {
            clearTimeout(timeoutTimer);
            reject(err);
          }
        }
      });
    });

    return deviceMetadata;
  }

  /**
   * Waits for local ICE candidate gathering to transition to 'complete'.
   * Resolves immediately when 'complete' fires (typically 200-400ms).
   * Includes a 1000ms safety window fallback to prevent hanging if an obscure interface stalls.
   */
  private waitForIceGatheringComplete(pc: RTCPeerConnection, maxWaitMs = 1000): Promise<void> {
    return new Promise((resolve) => {
      if (pc.iceGatheringState === 'complete') {
        resolve();
        return;
      }

      let timer: ReturnType<typeof setTimeout>;

      const checkState = () => {
        if (pc.iceGatheringState === 'complete') {
          pc.removeEventListener('icegatheringstatechange', checkState);
          clearTimeout(timer);
          resolve();
        }
      };

      pc.addEventListener('icegatheringstatechange', checkState);

      timer = setTimeout(() => {
        pc.removeEventListener('icegatheringstatechange', checkState);
        console.log('[WebRTCManager] Fast ICE gathering window finished. Proceeding with gathered SDP.');
        resolve();
      }, maxWaitMs);
    });
  }

  /**
   * Creates a fresh RTCPeerConnection instance with Google STUN servers.
   */
  private createPeerConnection(): void {
    this.disconnect();

    const config: RTCConfiguration = {
      iceServers: STUN_SERVERS,
      bundlePolicy: 'max-bundle',
      rtcpMuxPolicy: 'require'
    };

    console.log('[WebRTCManager] Creating RTCPeerConnection with STUN servers...');
    const pc = new RTCPeerConnection(config);
    this.peerConnection = pc;

    pc.onconnectionstatechange = () => {
      console.log('[WebRTCManager] Connection State:', pc.connectionState);
      this.notifyConnectionStateChange(pc.connectionState);
    };

    pc.oniceconnectionstatechange = () => {
      console.log('[WebRTCManager] ICE Connection State:', pc.iceConnectionState);
    };

    pc.ontrack = (event) => {
      console.log(`[WebRTCManager] Remote media track received: ${event.track.kind} (id=${event.track.id})`);
      const stream = event.streams[0] || new MediaStream([event.track]);

      if (event.track.kind === 'audio') {
        this.micStream = stream;
        window.dispatchEvent(new CustomEvent('nexus-mic-stream', {
          detail: { track: event.track, stream }
        }));
      } else if (event.track.kind === 'video') {
        // Hey friend! Always cache the incoming raw hardware stream from the single video transceiver
        this.rawVideoStream = stream;

        // Route strictly to the authorized active video feed to prevent cross-view frame leakage
        if (this.activeVideoType === 'camera') {
          this.cameraStream = stream;
          this.screenStream = null;
          window.dispatchEvent(new CustomEvent('nexus-camera-stream', {
            detail: { track: event.track, stream: this.cameraStream }
          }));
        } else if (this.activeVideoType === 'screen') {
          this.screenStream = stream;
          this.cameraStream = null;
          window.dispatchEvent(new CustomEvent('nexus-screen-stream', {
            detail: { track: event.track, stream: this.screenStream }
          }));
        } else {
          // No video feed actively authorized yet: clear specific pointers so neither view plays unexpectedly
          this.cameraStream = null;
          this.screenStream = null;
        }
      }

      this.notifyTrackReceived(event.track, stream);

      // Dispatch global window event for modular views to bind directly
      window.dispatchEvent(new CustomEvent('nexus-media-track', {
        detail: { track: event.track, stream }
      }));
    };
  }

  /**
   * Sets up the 'nexus-control' DataChannel on the PeerConnection.
   */
  private setupDataChannel(pc: RTCPeerConnection): void {
    console.log('[WebRTCManager] Creating DataChannel:', DATA_CHANNEL_LABEL);
    const dc = pc.createDataChannel(DATA_CHANNEL_LABEL, { ordered: true });
    this.dataChannel = dc;

    dc.onopen = () => {
      console.log('[WebRTCManager] DataChannel is now OPEN and ready for telemetry.');
    };

    dc.onclose = () => {
      console.log('[WebRTCManager] DataChannel closed.');
    };

    dc.onerror = (error) => {
      console.error('[WebRTCManager] DataChannel error:', error);
    };

    dc.onmessage = (event) => {
      if (typeof event.data === 'string' && event.data.startsWith('OPUS_AUDIO:')) {
        window.dispatchEvent(new CustomEvent('nexus-screen-audio', {
          detail: { packet: event.data.slice('OPUS_AUDIO:'.length) }
        }));
      } else {
        console.log('[WebRTCManager] Incoming message from Child:', event.data);
      }
      this.notifyMessageReceived(event.data);
    };
  }

  /**
   * Sends a text command across the active WebRTC DataChannel.
   *
   * @param command Text string or serialized JSON payload
   * @returns true if dispatched, false if channel is closed
   */
  public sendCommand(command: string): boolean {
    if (this.dataChannel && this.dataChannel.readyState === 'open') {
      this.dataChannel.send(command);
      console.log('[WebRTCManager] Command dispatched to Child:', command);
      return true;
    }
    console.warn('[WebRTCManager] Cannot send command: DataChannel is not open.');
    return false;
  }

  /**
   * Closes the active peer connection and tears down signaling listeners.
   */
  public disconnect(): void {
    console.log('[WebRTCManager] Disconnecting active session...');
    this.unsubscribeAnswer?.();
    this.unsubscribeAnswer = null;
    this.micStream = null;
    this.cameraStream = null;
    this.screenStream = null;
    this.rawVideoStream = null;
    this.activeVideoType = null;

    // Dispatches proactive DISCONNECT command over DataChannel so Child executes immediate hardware teardown
    if (this.dataChannel && this.dataChannel.readyState === 'open') {
      try {
        this.dataChannel.send('DISCONNECT');
        console.log('[WebRTCManager] Dispatched proactive DISCONNECT command to Child');
      } catch (e) {
        console.warn('[WebRTCManager] Error sending DISCONNECT command:', e);
      }
    }

    if (this.dataChannel) {
      try {
        this.dataChannel.close();
      } catch (e) {
        console.warn('[WebRTCManager] Error closing DataChannel:', e);
      }
      this.dataChannel = null;
    }

    if (this.peerConnection) {
      try {
        this.peerConnection.close();
      } catch (e) {
        console.warn('[WebRTCManager] Error closing PeerConnection:', e);
      }
      this.peerConnection = null;
    }

    if (this.currentChildId) {
      signalingClient.clearSignalingSession(this.currentChildId).catch((err) => {
        console.warn('[WebRTCManager] Error resetting signaling on disconnect:', err);
      });
      this.currentChildId = null;
    }
  }

  // Listener registration helpers

  public onConnectionStateChange(listener: ConnectionStateListener): () => void {
    this.connectionStateListeners.add(listener);
    return () => this.connectionStateListeners.delete(listener);
  }

  public onMessage(listener: MessageListener): () => void {
    this.messageListeners.add(listener);
    return () => this.messageListeners.delete(listener);
  }

  public onTrack(listener: TrackListener): () => void {
    this.trackListeners.add(listener);
    return () => this.trackListeners.delete(listener);
  }

  private notifyConnectionStateChange(state: RTCPeerConnectionState): void {
    this.connectionStateListeners.forEach((listener) => {
      try {
        listener(state);
      } catch (err) {
        console.error('[WebRTCManager] Error in connection state listener:', err);
      }
    });
  }

  private notifyMessageReceived(message: string): void {
    this.messageListeners.forEach((listener) => {
      try {
        listener(message);
      } catch (err) {
        console.error('[WebRTCManager] Error in message listener:', err);
      }
    });
  }

  private notifyTrackReceived(track: MediaStreamTrack, stream: MediaStream): void {
    this.trackListeners.forEach((listener) => {
      try {
        listener(track, stream);
      } catch (err) {
        console.error('[WebRTCManager] Error in track listener:', err);
      }
    });
  }
}

export const webrtcManager = new DesktopWebRtcManager();
