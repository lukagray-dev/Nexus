import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import '../dist/js/features/screen/screen';

describe('ScreenFeature', () => {
  let container;
  let feature;

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);

    window.nexusRecordingManager = {
      isRecording: vi.fn().mockReturnValue(false),
      start: vi.fn(),
      stop: vi.fn(),
      onTick: vi.fn(),
      onStop: vi.fn(),
      onError: vi.fn()
    };

    window.nexusApp = {
      connectionManager: {
        sendCommand: vi.fn(),
        webrtcManager: {
          getStream: vi.fn()
        }
      }
    };

    global.fetch = vi.fn().mockResolvedValue({
      text: vi.fn().mockResolvedValue('<div id="screen-stream"></div><button id="screen-record-btn"></button><button id="screen-play-pause-btn"></button>')
    });

    feature = new window.ScreenFeature(container);
  });

  afterEach(() => {
    if(typeof feature.destroy === "function") feature.destroy();
    document.body.removeChild(container);
    vi.clearAllMocks();
  });

  it('can be instantiated and initializes properly', async () => {
    expect(feature).not.toBeNull();
    await new Promise(r => setTimeout(r, 60));
    expect(global.fetch).toHaveBeenCalled();
  });

  it('startScreen sends command and adds loading state', async () => {
    await new Promise(r => setTimeout(r, 60));
    feature.startScreen();
    expect(window.nexusApp.connectionManager.sendCommand).toHaveBeenCalledWith('SCREEN_RECORDING_ON');
    expect(feature.isStreaming).toBe(true);
  });

  it('handles SCREEN_RECORDING_STARTED confirmation', async () => {
    await new Promise(r => setTimeout(r, 60));
    const evt = new CustomEvent('screen-confirmation', { detail: { message: 'SCREEN_RECORDING_STARTED'} });
    window.dispatchEvent(evt);
    expect(feature.confirmationTimeout).toBeNull();
  });

  it('stopScreen sends stop command safely', async () => {
    await new Promise(r => setTimeout(r, 60));
    feature.isStreaming = true; // active mock
    feature.stopScreen();
    expect(window.nexusApp.connectionManager.sendCommand).toHaveBeenCalledWith('SCREEN_RECORDING_OFF');
  });

  it('attaches incoming valid stream with video tracks', async () => {
    await new Promise(r => setTimeout(r, 60));
    feature.isStreaming = true;
    const mockTrack = { readyState: 'live', enabled: true };
    const mockStream = { getVideoTracks: () => [mockTrack], id: 1 };
    
    // Dispatch stream
    const evt = new CustomEvent('screen-stream-received', { detail: { stream: mockStream } });
    window.dispatchEvent(evt);
    
    expect(feature.videoElement).not.toBeNull();
    expect(feature.videoElement.srcObject).toBe(mockStream);
  });
});
