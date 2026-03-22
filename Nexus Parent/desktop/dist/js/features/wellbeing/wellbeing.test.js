import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import '../dist/js/features/wellbeing/wellbeing';

describe('WellbeingFeature', () => {
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
      text: vi.fn().mockResolvedValue('<div id="wellbeing-stream"></div><button id="wellbeing-btn"></button>')
    });

    feature = new window.WellbeingFeature(container);
  });

  afterEach(() => {
    if(typeof feature.destroy === "function") feature.destroy();
    document.body.removeChild(container);
    vi.clearAllMocks();
  });

  it('can be instantiated', () => {
    expect(feature).not.toBeNull();
    expect(feature.container).toBe(container);
  });

  it('initializes layout via fetch', async () => {
    await new Promise(r => setTimeout(r, 60)); // Wait for async init
    expect(global.fetch).toHaveBeenCalled();
    expect(container.innerHTML).not.toBe('');
  });
  
  
});
