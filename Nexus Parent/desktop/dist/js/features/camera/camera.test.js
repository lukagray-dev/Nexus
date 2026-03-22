import { describe, it, expect, vi, beforeEach } from 'vitest';
import '../dist/js/features/camera/camera';

describe('CameraFeature', () => {
  let container;
  let feature;

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
    
    // Mock global app connection manager
    window.nexusApp = {
      connectionManager: {
        sendCommand: vi.fn(),
        webrtcManager: {
          getStream: vi.fn()
        }
      }
    };

    // Override fetch to skip real network load in init
    global.fetch = vi.fn().mockResolvedValue({
      text: vi.fn().mockResolvedValue(`
        <div id="camera-stream">
           <div class="stream-controls"></div>
        </div>
        <button id="camera-play-pause-btn"></button>
        <button id="camera-switch-btn"></button>
        <button id="camera-record-btn"></button>
      `)
    });

    feature = new window.CameraFeature(container);
  });

  afterEach(() => {
    document.body.removeChild(container);
    vi.clearAllMocks();
  });

  it('initializes layout and buttons', async () => {
    // init is async because of templates
    await new Promise(r => setTimeout(r, 60));
    const stream = container.querySelector('#camera-stream');
    expect(stream).not.toBeNull();
  });

  it('startCamera sends command and adds loading state', async () => {
    await new Promise(r => setTimeout(r, 60));
    
    feature.startCamera();
    
    expect(window.nexusApp.connectionManager.sendCommand).toHaveBeenCalledWith('CAMERA_ON');
    expect(feature.confirmationTimeout).not.toBeNull();
    
    const playBtn = container.querySelector('#camera-play-pause-btn');
    expect(playBtn.disabled).toBe(true);
  });

  it('listens to camera-confirmation and updates UI', async () => {
    await new Promise(r => setTimeout(r, 60));
    
    // Simulate start
    feature.startCamera();
    
    // Dispatch confirmation
    const event = new CustomEvent('camera-confirmation', { detail: { message: 'CAMERA_STARTED' } });
    window.dispatchEvent(event);
    
    expect(feature.isStreaming).toBe(true);
    expect(feature.confirmationTimeout).toBeNull();
  });

  it('stops camera safely', async () => {
    await new Promise(r => setTimeout(r, 60));
    
    feature.isStreaming = true;
    feature.videoElement = document.createElement('video');
    const pauseSpy = vi.spyOn(feature.videoElement, 'pause').mockImplementation(() => {});
    
    feature.stopCamera();
    
    expect(window.nexusApp.connectionManager.sendCommand).toHaveBeenCalledWith('CAMERA_OFF');
    expect(feature.videoElement.srcObject).toBeNull();
    expect(pauseSpy).toHaveBeenCalled();
  });

  it('attaches stream upon UI action', async () => {
    await new Promise(r => setTimeout(r, 60));
    
    const mockStream = { id: 1 };
    feature.isStreaming = true; // Needs to be true to attach stream from event
    
    const event = new CustomEvent('camera-stream-received', { detail: { stream: mockStream } });
    window.dispatchEvent(event);
    
    expect(feature.videoElement).not.toBeNull();
    expect(feature.videoElement.srcObject).toBe(mockStream);
    expect(feature.videoElement.autoplay).toBe(true);
  });

});
