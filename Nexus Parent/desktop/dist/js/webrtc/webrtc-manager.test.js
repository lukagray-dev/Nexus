import { beforeEach, describe, expect, it, vi } from 'vitest';

function createFirebaseRef(initialValue = null) {
  let value = initialValue;
  return {
    once: vi.fn(async () => ({ val: () => value })),
    set: vi.fn(async (next) => {
      value = next;
    }),
    on: vi.fn(),
    off: vi.fn(),
    remove: vi.fn(async () => {
      value = null;
    }),
    child: vi.fn(() => ({
      push: vi.fn(async () => {}),
      on: vi.fn(),
      off: vi.fn()
    }))
  };
}

function installWebRtcMocks() {
  class MockRTCSessionDescription {
    constructor(desc) {
      Object.assign(this, desc);
    }
  }

  class MockRTCIceCandidate {
    constructor(candidate) {
      Object.assign(this, candidate);
    }
  }

  class MockPeerConnection {
    constructor() {
      this.signalingState = 'stable';
      this.connectionState = 'new';
      this.localDescription = { type: 'offer', sdp: 'offer-sdp' };
      this.addTransceiver = vi.fn();
      this.setLocalDescription = vi.fn(async (desc) => {
        this.localDescription = desc;
        this.signalingState = 'have-local-offer';
      });
      this.createOffer = vi.fn(async () => ({ type: 'offer', sdp: 'offer-sdp' }));
      this.createAnswer = vi.fn(async () => ({ type: 'answer', sdp: 'answer-sdp' }));
      this.setRemoteDescription = vi.fn(async () => {
        this.signalingState = 'stable';
      });
      this.addIceCandidate = vi.fn(async () => {});
      this.getTransceivers = vi.fn(() => []);
      this.close = vi.fn();
      this.createDataChannel = vi.fn(() => ({
        label: 'nexus-control',
        readyState: 'open',
        send: vi.fn(),
        close: vi.fn()
      }));
    }
  }

  globalThis.RTCPeerConnection = MockPeerConnection;
  globalThis.RTCSessionDescription = MockRTCSessionDescription;
  globalThis.RTCIceCandidate = MockRTCIceCandidate;
}

async function loadWebRtcManager() {
  vi.resetModules();
  await import('./webrtc-manager.js');
  return window.WebRTCManager;
}

describe('WebRTCManager', () => {
  let WebRTCManager;

  beforeEach(async () => {
    installWebRtcMocks();
    WebRTCManager = await loadWebRtcManager();
  });

  it('initializes firebase once and stores firebaseDB handle', () => {
    const db = { ref: vi.fn(() => createFirebaseRef()) };
    globalThis.firebase = {
      apps: [],
      initializeApp: vi.fn(),
      database: vi.fn(() => db)
    };

    const manager = new WebRTCManager();
    manager.initializeFirebase({ apiKey: 'demo' });

    expect(firebase.initializeApp).toHaveBeenCalledWith({ apiKey: 'demo' });
    expect(manager.firebaseDB).toBe(db);

    firebase.apps = [{}];
    manager.initializeFirebase({ apiKey: 'demo-2' });
    expect(firebase.initializeApp).toHaveBeenCalledTimes(1);
  });

  it('rejects connect when child is missing or offline', async () => {
    const manager = new WebRTCManager();

    manager.firebaseDB = { ref: vi.fn(() => createFirebaseRef(null)) };
    await expect(manager.connect('123456789012')).rejects.toThrow('Child device not found');

    manager.firebaseDB = { ref: vi.fn(() => createFirebaseRef('offline')) };
    await expect(manager.connect('123456789012')).rejects.toThrow('Child device is offline');
  });

  it('connects online children and wires signaling', async () => {
    const statusRef = createFirebaseRef('online');
    const setupFirebaseSignaling = vi.fn();

    const manager = new WebRTCManager();
    manager.firebaseDB = { ref: vi.fn(() => statusRef) };
    manager.setupFirebaseSignaling = setupFirebaseSignaling;

    const result = await manager.connect('123456789012');

    expect(result.peerConnection).toBeTruthy();
    expect(result.dataChannel).toBeTruthy();
    expect(manager.currentChildId).toBe('123456789012');
    expect(setupFirebaseSignaling).toHaveBeenCalledWith('123456789012');
  });

  it('createAndSendOffer adds transceivers and persists offer', async () => {
    const manager = new WebRTCManager();
    manager.peerConnection = new RTCPeerConnection();
    manager.offerRef = createFirebaseRef();

    await manager.createAndSendOffer();

    expect(manager.peerConnection.addTransceiver).toHaveBeenCalledWith('video', { direction: 'recvonly' });
    expect(manager.peerConnection.addTransceiver).toHaveBeenCalledWith('audio', { direction: 'recvonly' });
    expect(manager.offerRef.set).toHaveBeenCalledWith({
      type: 'offer',
      sdp: 'offer-sdp'
    });
  });

  it('sendCommand returns readiness status', () => {
    const manager = new WebRTCManager();
    manager.dataChannel = { readyState: 'open', send: vi.fn() };

    expect(manager.sendCommand('START_CAMERA')).toBe(true);
    expect(manager.dataChannel.send).toHaveBeenCalledWith('START_CAMERA');

    manager.dataChannel.readyState = 'closing';
    expect(manager.sendCommand('STOP_CAMERA')).toBe(false);
  });

  it('handles answers and clears renegotiation flag on success', async () => {
    const manager = new WebRTCManager();
    manager.peerConnection = new RTCPeerConnection();
    manager.peerConnection.signalingState = 'have-local-offer';
    manager.isRenegotiating = true;
    manager.dataChannel = { readyState: 'open', send: vi.fn() };

    await manager.handleAnswer({ type: 'answer', sdp: 'remote-sdp' });

    expect(manager.peerConnection.setRemoteDescription).toHaveBeenCalled();
    expect(manager.isRenegotiating).toBe(false);
    expect(manager.dataChannel.send).toHaveBeenCalledWith('RENEGOTIATION_COMPLETE');
  });

  it('disconnect closes channels/listeners and resets state', () => {
    const manager = new WebRTCManager();

    manager.dataChannel = { close: vi.fn() };
    manager.peerConnection = new RTCPeerConnection();
    manager.offerRef = createFirebaseRef();
    manager.answerRef = createFirebaseRef();
    manager.iceCandidatesRef = createFirebaseRef();
    manager.videoStreams = { camera: {}, screen: {}, mic: {} };
    manager.currentChildId = '123456789012';

    manager.disconnect();

    expect(manager.dataChannel).toBeNull();
    expect(manager.peerConnection).toBeNull();
    expect(manager.offerRef).toBeNull();
    expect(manager.answerRef).toBeNull();
    expect(manager.iceCandidatesRef).toBeNull();
    expect(manager.videoStreams).toEqual({ camera: null, screen: null, mic: null });
    expect(manager.currentChildId).toBeNull();
  });
});
