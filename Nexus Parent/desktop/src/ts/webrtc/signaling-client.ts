/**
 * @file signaling-client.ts
 * @description Firebase Realtime Database signaling client for Nexus Parent Desktop.
 *
 * Hey friend! WebRTC is peer-to-peer, but the two devices need an initial handshake
 * to swap network routing parameters (SDP and ICE candidates).
 *
 * Here is how the Parent signaling client operates:
 * 1. Checks `/child-devices/{childId}` to ensure the phone exists and is currently online.
 * 2. Writes the Parent's Non-Trickle SDP Offer to `/signaling/{childId}/offer`.
 * 3. Listens for the child's response on `/signaling/{childId}/answer`.
 * 4. Cleans up signaling paths when disconnecting or starting fresh.
 */

import {
  ref,
  get,
  set,
  onValue,
  off,
  update,
  type DataSnapshot
} from 'firebase/database';
import { getFirebaseDatabase } from './firebase-config.js';

export interface ChildDeviceMetadata {
  status: string;
  lastSeen?: number;
  model?: string;
  manufacturer?: string;
  androidVersion?: string;
  batteryPercent?: number;
  [key: string]: unknown;
}

export class DesktopSignalingClient {
  private activeAnswerListenerRef: ReturnType<typeof ref> | null = null;
  private activeAnswerCallback: ((snapshot: DataSnapshot) => void) | null = null;

  /**
   * Cleans the 12-digit Child ID by stripping hyphens and whitespace.
   */
  public normalizeChildId(rawId: string): string {
    return rawId.replace(/[\s-]/g, '').trim();
  }

  /**
   * Queries Firebase to verify that a child device exists with the given ID
   * and is currently in an online or reachable state.
   *
   * @param childId The 12-digit Child ID
   * @returns Device metadata if online and valid, or throws a human-friendly error
   */
  public async validateChildDevice(childId: string): Promise<ChildDeviceMetadata> {
    const rawId = this.normalizeChildId(childId);
    const db = getFirebaseDatabase();
    const deviceRef = ref(db, `child-devices/${rawId}`);

    console.log('[SignalingClient] Validating Child Device ID:', rawId);
    const snapshot = await get(deviceRef);

    if (!snapshot.exists()) {
      throw new Error(`Child device "${childId}" was not found. Please verify the 12-digit ID.`);
    }

    const data = snapshot.val() as ChildDeviceMetadata;
    const status = data.status || 'unknown';

    console.log('[SignalingClient] Child device status:', status);

    const validStatuses = ['online', 'online-waiting', 'connected', 'connecting'];
    if (!validStatuses.includes(status)) {
      throw new Error(`Child device is currently "${status}". Please ensure the child app is open.`);
    }

    return data;
  }

  /**
   * Writes the Parent's Non-Trickle SDP Offer to /signaling/{childId}/offer.
   *
   * @param childId The 12-digit normalized Child ID
   * @param offer The RTCSessionDescriptionInit object containing the offer SDP
   */
  public async sendOffer(childId: string, offer: RTCSessionDescriptionInit): Promise<void> {
    const rawId = this.normalizeChildId(childId);
    const db = getFirebaseDatabase();
    const offerRef = ref(db, `signaling/${rawId}/offer`);

    console.log(`[SignalingClient] Publishing Non-Trickle SDP Offer to /signaling/${rawId}/offer...`);
    await set(offerRef, {
      type: offer.type,
      sdp: offer.sdp
    });
    console.log('[SignalingClient] SDP Offer successfully published to Firebase.');
  }

  /**
   * Listens for the child's Non-Trickle SDP Answer on /signaling/{childId}/answer.
   *
   * @param childId The 12-digit Child ID
   * @param onAnswer Callback triggered when a valid answer is received
   * @returns An unsubscribe cleanup function
   */
  public listenForAnswer(
    childId: string,
    onAnswer: (answer: RTCSessionDescriptionInit) => void
  ): () => void {
    const rawId = this.normalizeChildId(childId);
    const db = getFirebaseDatabase();
    const answerRef = ref(db, `signaling/${rawId}/answer`);

    this.cleanupAnswerListener();

    console.log(`[SignalingClient] Subscribing to /signaling/${rawId}/answer...`);

    let lastProcessedSdp: string | null = null;

    const listener = (snapshot: DataSnapshot) => {
      if (!snapshot.exists()) {
        return;
      }

      const val = snapshot.val() as { type?: string; sdp?: string };
      if (!val || !val.sdp || val.sdp.trim().length === 0) {
        return;
      }

      // Avoid reprocessing duplicate answer snapshots
      if (val.sdp === lastProcessedSdp) {
        return;
      }

      lastProcessedSdp = val.sdp;
      console.log('[SignalingClient] Valid SDP Answer received from child.');

      onAnswer({
        type: (val.type as RTCSdpType) || 'answer',
        sdp: val.sdp
      });
    };

    this.activeAnswerListenerRef = answerRef;
    this.activeAnswerCallback = listener;

    onValue(answerRef, listener);

    return () => {
      this.cleanupAnswerListener();
    };
  }

  /**
   * Publishes the Parent's SDP Answer back to /signaling/{childId}/offer during role-reversal renegotiation.
   *
   * @param childId The 12-digit normalized Child ID
   * @param answer The generated RTCSessionDescriptionInit answer
   */
  public async sendRenegotiationAnswer(childId: string, answer: RTCSessionDescriptionInit): Promise<void> {
    const rawId = this.normalizeChildId(childId);
    const db = getFirebaseDatabase();
    const offerRef = ref(db, `signaling/${rawId}/offer`);

    console.log(`[SignalingClient] Publishing renegotiation SDP Answer to /signaling/${rawId}/offer...`);
    await set(offerRef, {
      type: answer.type,
      sdp: answer.sdp
    });
    console.log('[SignalingClient] Renegotiation SDP Answer successfully published to Firebase.');
  }

  /**
   * Resets and clears the signaling session paths in Firebase.
   */
  public async clearSignalingSession(childId: string): Promise<void> {
    const rawId = this.normalizeChildId(childId);
    const db = getFirebaseDatabase();
    const signalingRef = ref(db, `signaling/${rawId}`);

    console.log(`[SignalingClient] Clearing signaling session for: ${rawId}`);
    try {
      await update(signalingRef, {
        offer: null,
        answer: null,
        'ice-candidates': null
      });
    } catch (e) {
      console.warn('[SignalingClient] Warning during session cleanup:', e);
    }
  }

  /**
   * Removes active answer listeners.
   */
  public cleanupAnswerListener(): void {
    if (this.activeAnswerListenerRef && this.activeAnswerCallback) {
      off(this.activeAnswerListenerRef, 'value', this.activeAnswerCallback);
      this.activeAnswerListenerRef = null;
      this.activeAnswerCallback = null;
    }
  }
}

export const signalingClient = new DesktopSignalingClient();
