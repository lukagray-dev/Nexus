import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import '../dist/js/features/sms/sms';

describe('SmsFeature', () => {
  let container;
  let feature;

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);

    window.nexusApp = {
      connectionManager: {
        sendCommand: vi.fn(),
      }
    };

    global.fetch = vi.fn().mockResolvedValue({
      text: vi.fn().mockResolvedValue('<div class="sms-content"></div><div id="sms-list"></div><button id="sms-play-pause-btn"></button><input id="sms-search" />')
    });

    feature = new window.SmsFeature(container);
  });

  afterEach(() => {
    if(typeof feature.destroy === "function") feature.destroy();
    document.body.removeChild(container);
    vi.clearAllMocks();
    window.smsFeatureData = { messages: [], latestTimestamp: 0, isMonitoring: false };
  });

  it('can be instantiated', async () => {
    expect(feature).not.toBeNull();
    await new Promise(r => setTimeout(r, 60));
    expect(global.fetch).toHaveBeenCalled();
  });

  it('startMonitoring sends command with latest timestamp', async () => {
    await new Promise(r => setTimeout(r, 60));
    feature.data.latestTimestamp = 12345;
    feature.startMonitoring();
    expect(window.nexusApp.connectionManager.sendCommand).toHaveBeenCalledWith(JSON.stringify({
      cmd: 'SMS_ON',
      since: 12345
    }));
  });

  it('stops monitoring', async () => {
    await new Promise(r => setTimeout(r, 60));
    feature.stopMonitoring();
    expect(window.nexusApp.connectionManager.sendCommand).toHaveBeenCalledWith('SMS_OFF');
  });

  it('handles sms-message events and parses body correctly', async () => {
    await new Promise(r => setTimeout(r, 60));
    
    const msg = { address: '+1234567', body: 'Hello World', timestamp: 12346, sms_type: 'inbox' };
    const evt = new CustomEvent('sms-message', { detail: msg });
    window.dispatchEvent(evt);
    
    expect(feature.data.messages).toContain(msg);
    expect(feature.data.latestTimestamp).toBe(12346);
    
    // Check if UI reflects
    const list = container.querySelector('#sms-list');
    expect(list.innerHTML).toContain('+1234567');
    expect(list.innerHTML).toContain('Hello World');
  });

  it('handles search queries', async () => {
    await new Promise(r => setTimeout(r, 60));
    
    feature.addMessage({ address: '+111', body: 'Alpha', timestamp: 1 });
    feature.addMessage({ address: '+222', body: 'Beta', timestamp: 2 });
    
    feature.searchTerm = 'alpha';
    feature.renderMessages();
    
    const list = container.querySelector('#sms-list');
    expect(list.innerHTML).toContain('Alpha');
    expect(list.innerHTML).not.toContain('Beta');
  });
});
