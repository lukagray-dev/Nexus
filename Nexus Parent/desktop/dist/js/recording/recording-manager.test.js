import { describe, it, expect, vi, beforeEach } from 'vitest';
import '../dist/js/recording/recording-manager';

describe('RecordingManager', () => {
  let recordingManager;

  beforeEach(() => {
    window.IPCBridge = {
      showSaveDialog: vi.fn().mockResolvedValue({ filePath: 'C:\\test\\video.webm', canceled: false })
    };

    window.MediaRecorder = vi.fn().mockImplementation((stream, options) => {
      return {
        start: vi.fn(),
        stop: vi.fn(),
        ondataavailable: null,
        onstop: null,
        onerror: null,
      };
    });
    window.MediaRecorder.isTypeSupported = vi.fn().mockReturnValue(true);

    recordingManager = new window.RecordingManager();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('pickSaveLocation uses IPCBridge', async () => {
    const result = await recordingManager.pickSaveLocation('video.webm');
    expect(result).toBe('C:\\test\\video.webm');
    expect(window.IPCBridge.showSaveDialog).toHaveBeenCalled();
  });

  it('start method throws error if no stream provided', async () => {
    await expect(recordingManager.start(null)).rejects.toThrow('No stream to record');
  });

  it('start method uses MediaRecorder with passed MediaStream', async () => {
    const mockTrack = { readyState: 'live' };
    
    // Mock MediaStream
    const MockMediaStream = vi.fn().mockImplementation(() => ({
      getTracks: () => [mockTrack],
      addTrack: vi.fn(),
      getVideoTracks: () => [mockTrack],
      getAudioTracks: () => []
    }));
    window.MediaStream = MockMediaStream;

    const stream = new MockMediaStream();
    
    const res = await recordingManager.start(stream, 'video');
    expect(res).toBe(true);
    expect(recordingManager.recording).toBe(true);
    expect(window.MediaRecorder).toHaveBeenCalled();
    expect(recordingManager.mediaRecorder.start).toHaveBeenCalledWith(1000);
  });

  it('supports stopping recording safely', async () => {
    recordingManager.recording = true;
    recordingManager.mediaRecorder = {
      stop: vi.fn()
    };
    
    recordingManager.stop();
    expect(recordingManager.mediaRecorder.stop).toHaveBeenCalled();
  });

});
