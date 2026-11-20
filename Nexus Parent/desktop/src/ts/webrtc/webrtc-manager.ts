/**
 * @file webrtc-manager.ts
 * @description WebRTC Connection and Media Stream Manager for Nexus Parent Desktop.
 *
 * Hey friend! Why is WebRTC so fast and low-latency in Tauri?
 * Because we run inside WebView2 (Chromium on Windows), which already has Google's
 * native libwebrtc compiled directly into the browser runtime!
 *
 * This manager:
 * 1. Creates a standard RTCPeerConnection configured with Google STUN servers.
 * 2. Creates the 'nexus-control' DataChannel for commands and telemetry.
 * 3. Registers 'recvonly' transceivers for receiving live video and audio from the phone.
 * 4. Executes Non-Trickle ICE gathering: waits until iceGatheringState === 'complete'
 *    before writing the self-contained SDP offer to Firebase.
 * 5. Receives the child's Non-Trickle SDP answer and applies it as remote description.
 * 6. Dispatches incoming media tracks (camera, screen, microphone) directly to the DOM!
 */

import { STUN_SERVERS, DATA_CHANNEL_LABEL } from './firebase-config.js';
import { signalingClient, type ChildDeviceMetadata } from './signaling-client.js';
import { authManager } from './auth-manager.js';

export type ConnectionStateListener = (state: RTCPeerConnectionState) => void;
export type MessageListener = (message: string) => void;
export type TrackListener = (track: MediaStreamTrack, stream: MediaStream) => void;

export class DesktopWebRtcManager {
  private peerConnection: RTCPeerConnection | null = null;
  private dataChannel: RTCDataChannel | null = null;
  private currentChildId: string | null = null;
  private unsubscribeAnswer: (() => void) | null = null;
  private micStream: MediaStream | null = null;
  private cameraStream: MediaStream | null = null;

  // Event callback listeners
  private connectionStateListeners: Set<ConnectionStateListener> = new Set();
  private messageListeners: Set<MessageListener> = new Set();
  private trackListeners: Set<TrackListener> = new Set();

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
   * Returns the incoming camera MediaStream if available, or null.
   */
  public getCameraStream(): MediaStream | null {
    return this.cameraStream;
  }

  /**
   * Returns the incoming screen MediaStream if available, or null.
   */
  public getScreenStream(): MediaStream | null {
    return this.cameraStream;
  }

  /**
   * Initiates the full end-to-end connection pipeline to a target child device:
   * 1. Authenticates with Firebase anonymously.
   * 2. Validates device existence and online status.
   * 3. Creates PeerConnection and adds transceivers.
   * 4. Gathers all ICE candidates locally (Non-Trickle ICE).
   * 5. Publishes the complete SDP offer to Firebase.
   * 6. Awaits the child's answer and applies it as remote description.
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
    pc.addTransceiver('video', { direction: 'recvonly' });
    pc.addTransceiver('audio', { direction: 'sendrecv' });
    console.log('[WebRTCManager] Video transceiver (recvonly) and Audio transceiver (sendrecv) added.');

    // Step 6: Create the primary control DataChannel
    this.setupDataChannel(pc);

    // Step 7: Create offer and gather ICE candidates (Non-Trickle ICE)
    const offer = await pc.createOffer();
    await pc.setLocalDescription(offer);
    console.log('[WebRTCManager] Local description set. Gathering ICE candidates locally...');

    // Wait for ICE gathering to complete before sending the SDP
    await this.waitForIceGatheringComplete(pc);

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
            console.log('[WebRTCManager] Received initial SDP Answer from child. Applying remote description...');
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
            console.log('[WebRTCManager] Created and set parent local answer. Gathering ICE...');

            await this.waitForIceGatheringComplete(pc);

            if (pc.localDescription) {
              await signalingClient.sendRenegotiationAnswer(childId, pc.localDescription);
              console.log('[WebRTCManager] Sent parent renegotiation answer to Firebase (/offer)');
            }

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
        if (!this.cameraStream || this.cameraStream.getVideoTracks()[0]?.id !== event.track.id) {
          this.cameraStream = stream;
        }
        window.dispatchEvent(new CustomEvent('nexus-camera-stream', {
          detail: { track: event.track, stream: this.cameraStream }
        }));
        window.dispatchEvent(new CustomEvent('nexus-screen-stream', {
          detail: { track: event.track, stream: this.cameraStream }
        }));
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
   * Waits for ICE gathering to transition to 'complete'.
   * Includes a safety timeout fallback to prevent indefinitely hanging if an interface stalls.
   */
  private waitForIceGatheringComplete(pc: RTCPeerConnection): Promise<void> {
    return new Promise((resolve) => {
      if (pc.iceGatheringState === 'complete') {
        resolve();
        return;
      }

      const checkState = () => {
        if (pc.iceGatheringState === 'complete') {
          pc.removeEventListener('icegatheringstatechange', checkState);
          clearTimeout(fallbackTimer);
          resolve();
        }
      };

      pc.addEventListener('icegatheringstatechange', checkState);

      // Fallback timer: if gathering doesn't report complete within 2.5s, proceed with gathered candidates
      const fallbackTimer = setTimeout(() => {
        pc.removeEventListener('icegatheringstatechange', checkState);
        console.log('[WebRTCManager] ICE gathering fallback timer expired. Proceeding with collected candidates.');
        resolve();
      }, 2500);
    });
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
    for (const listener of this.connectionStateListeners) {
      listener(state);
    }
  }

  private notifyMessageReceived(message: string): void {
    for (const listener of this.messageListeners) {
      listener(message);
    }
  }

  private notifyTrackReceived(track: MediaStreamTrack, stream: MediaStream): void {
    for (const listener of this.trackListeners) {
      listener(track, stream);
    }
  }
}

export const webrtcManager = new DesktopWebRtcManager();
