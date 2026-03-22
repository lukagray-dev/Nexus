import { beforeEach, describe, expect, it, vi } from 'vitest';

async function loadConnectionManager() {
  vi.resetModules();
  await import('./connection-manager.js');
  return window.ConnectionManager;
}

describe('ConnectionManager', () => {
  let ConnectionManager;

  beforeEach(async () => {
    document.body.innerHTML = '';
    ConnectionManager = await loadConnectionManager();
  });

  it('validates child IDs as exactly 12 digits', () => {
    const manager = new ConnectionManager({});

    expect(manager.validateChildId('123')).toEqual({
      valid: false,
      error: 'Child ID must be exactly 12 digits.'
    });
    expect(manager.validateChildId('12345678901a').valid).toBe(false);
    expect(manager.validateChildId('123456789012')).toEqual({ valid: true });
  });

  it('initializes WebRTC manager and callbacks on successful connect', async () => {
    window.firebaseConfig = { apiKey: 'demo' };
    const webrtcMock = {
      initializeFirebase: vi.fn(),
      setCallbacks: vi.fn(),
      connect: vi.fn(async () => {}),
      cleanup: vi.fn(),
      sendCommand: vi.fn(() => true)
    };

    window.WebRTCManager = vi.fn(function WebRTCManagerCtor() {
      return webrtcMock;
    });

    const manager = new ConnectionManager({});
    await expect(manager.connect('123456789012')).resolves.toBe('123456789012');

    expect(window.WebRTCManager).toHaveBeenCalledTimes(1);
    expect(webrtcMock.initializeFirebase).toHaveBeenCalledWith(window.firebaseConfig);
    expect(webrtcMock.setCallbacks).toHaveBeenCalledTimes(1);
    expect(webrtcMock.connect).toHaveBeenCalledWith('123456789012');
  });

  it('fails fast when firebase config is missing', async () => {
    delete window.firebaseConfig;
    window.WebRTCManager = vi.fn(function WebRTCManagerCtor() {
      return {
        initializeFirebase: vi.fn(),
        setCallbacks: vi.fn(),
        connect: vi.fn(),
        cleanup: vi.fn(),
        sendCommand: vi.fn()
      };
    });

    const manager = new ConnectionManager({});
    await expect(manager.connect('123456789012')).rejects.toThrow('Firebase config not available');
  });

  it('waitForConnection resolves when data channel opens and times out otherwise', async () => {
    const manager = new ConnectionManager({});

    manager.dataChannel = { readyState: 'open' };
    await expect(manager.waitForConnection()).resolves.toBe(true);

    vi.useFakeTimers();
    manager.dataChannel = { readyState: 'connecting' };

    const pending = manager.waitForConnection();
    vi.advanceTimersByTime(10_500);

    await expect(pending).rejects.toThrow('Connection timeout. Please try again.');
    vi.useRealTimers();
  });

  it('dispatches typed browser events from data channel payloads', () => {
    const manager = new ConnectionManager({});
    const dispatchSpy = vi.spyOn(window, 'dispatchEvent');

    manager.handleDataChannelMessage(JSON.stringify({ type: 'sms', body: 'hello' }));
    expect(dispatchSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        type: 'sms-message',
        detail: { type: 'sms', body: 'hello' }
      })
    );

    manager.handleDataChannelMessage('OPUS_AUDIO:YWJj');
    expect(dispatchSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        type: 'screen-audio-packet',
        detail: { base64Data: 'YWJj' }
      })
    );

    manager.handleDataChannelMessage('CAMERA_STARTED');
    expect(dispatchSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        type: 'camera-confirmation',
        detail: { message: 'CAMERA_STARTED' }
      })
    );
  });

  it('sends commands/binary payloads only when channels are available', () => {
    const manager = new ConnectionManager({});

    expect(manager.sendCommand('START')).toBe(false);

    manager.webrtcManager = { sendCommand: vi.fn(() => true) };
    expect(manager.sendCommand('START')).toBe(true);

    manager.dataChannel = { readyState: 'open', send: vi.fn() };
    expect(manager.sendBinaryData(new Uint8Array([1, 2, 3]))).toBe(true);
    expect(manager.dataChannel.send).toHaveBeenCalled();

    manager.dataChannel.readyState = 'closed';
    expect(manager.sendBinaryData(new Uint8Array([4]))).toBe(false);
  });

  it('disconnect releases local connection state', async () => {
    const cleanup = vi.fn();
    const manager = new ConnectionManager({});
    manager.webrtcManager = { cleanup };
    manager.dataChannel = { readyState: 'open' };

    await expect(manager.disconnect()).resolves.toBe(true);

    expect(cleanup).toHaveBeenCalledTimes(1);
    expect(manager.webrtcManager).toBeNull();
    expect(manager.dataChannel).toBeNull();
  });
});
