import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import '../dist/js/features/mic/mic';

describe('MicFeature', () => {
  let container;
  let feature;

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);

    window.nexusRecordingManager = {
      isRecording: vi.fn(),
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

    // Mock AudioContext
    window.AudioContext = vi.fn().mockImplementation(() => ({
      createAnalyser: vi.fn().mockReturnValue({
        fftSize: 256,
        frequencyBinCount: 128,
        getByteFrequencyData: vi.fn(),
        getByteTimeDomainData: vi.fn()
      }),
      createMediaStreamSource: vi.fn().mockReturnValue({
        connect: vi.fn()
      }),
      close: vi.fn()
    }));

    global.fetch = vi.fn().mockResolvedValue({
      text: vi.fn().mockResolvedValue('<div id="mic-visualizer"></div><canvas id="visualizer-canvas"></canvas><button id="mic-play-pause-btn"></button>')
    });
    
    // Mock requestAnimationFrame
    window.requestAnimationFrame = vi.fn().mockReturnValue(1234);
    window.cancelAnimationFrame = vi.fn();

    feature = new window.MicFeature(container);
  });

  afterEach(() => {
    if(typeof feature.destroy === "function") feature.destroy();
    document.body.removeChild(container);
    vi.clearAllMocks();
  });

  it('can be instantiated', async () => {
    expect(feature).not.toBeNull();
    await new Promise(r => setTimeout(r, 60));
    expect(global.fetch).toHaveBeenCalled();
  });

  it('startMic sends mic start command', async () => {
    await new Promise(r => setTimeout(r, 60));
    feature.startMic();
    expect(window.nexusApp.connectionManager.sendCommand).toHaveBeenCalledWith('MIC_ON');
  });

  it('stopMic sends mic stop command', async () => {
    await new Promise(r => setTimeout(r, 60));
    feature.stopMic();
    expect(window.nexusApp.connectionManager.sendCommand).toHaveBeenCalledWith('MIC_OFF');
  });

  it('handles stream attachment and sets up visualizer', async () => {
    await new Promise(r => setTimeout(r, 60));
    const mockTrack = { readyState: 'live' };
    const mockStream = { getAudioTracks: () => [mockTrack], id: 'audio-stream' };
    
    // Stub canvas drawing
    const visualizerDiv = container.querySelector('#mic-visualizer');
    const canvas = document.createElement('canvas');
    canvas.id = 'visualizer-canvas';
    canvas.getContext = vi.fn().mockReturnValue({
      createRadialGradient: vi.fn().mockReturnValue({ addColorStop: vi.fn() }),
      createLinearGradient: vi.fn().mockReturnValue({ addColorStop: vi.fn() }),
      beginPath: vi.fn(),
      arc: vi.fn(),
      fill: vi.fn(),
      fillRect: vi.fn(),
      moveTo: vi.fn(),
      lineTo: vi.fn(),
      stroke: vi.fn(),
      closePath: vi.fn(),
      fillText: vi.fn()
    });
    visualizerDiv.appendChild(canvas);

    feature.attachStream(mockStream);
    
    expect(feature.audioElement).toBeDefined();
    expect(feature.audioElement.srcObject).toBe(mockStream);
    expect(window.AudioContext).toHaveBeenCalled();
    expect(window.requestAnimationFrame).toHaveBeenCalled();
  });
});
