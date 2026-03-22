import { describe, it, expect, vi, beforeEach } from 'vitest';
import '../dist/js/components/ai-sidebar/ai-sidebar';

describe('AISidebar', () => {
  let sidebar;

  beforeEach(() => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: vi.fn().mockResolvedValue({ data: [] })
    });
    
    document.body.innerHTML = `
      <div class="ai-sidebar">
        <div class="ai-messages"></div>
        <textarea class="ai-input"></textarea>
        <button id="ai-send"></button>
        <button id="ai-close"></button>
        <div class="ai-session-name"></div>
        <div class="ai-context-percent"></div>
        <div class="ai-model-name"></div>
        <div class="ai-model-dropdown">
            <div class="ai-model-list"></div>
        </div>
        <div class="ai-history-dropdown">
             <div class="ai-history-list"></div>
        </div>
        <div class="ai-resize-handle"></div>
      </div>
      <div class="ai-config-modal">
        <input id="ai-api-key-input"/>
        <button id="ai-config-close"></button>
        <button id="ai-config-cancel"></button>
        <button id="ai-config-save"></button>
      </div>
    `;

    sidebar = new window.AISidebar({});
    sidebar.init();
  });

  afterEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it('opens and closes via toggle', () => {
    sidebar.open();
    expect(sidebar.isOpen).toBe(true);
    expect(sidebar.container.classList.contains('open')).toBe(true);

    sidebar.close();
    expect(sidebar.isOpen).toBe(false);
    expect(sidebar.container.classList.contains('open')).toBe(false);
  });

  it('loads sessions and creates default on boot', () => {
    expect(sidebar.currentSessionId).not.toBeNull();
    expect(sidebar.sessions[sidebar.currentSessionId].name).toBe('New Chat');
  });

  it('updates font size correctly', () => {
    sidebar.changeFontSize(2); // default 13 + 2
    expect(sidebar.fontSize).toBe(15);
    expect(document.documentElement.style.getPropertyValue('--ai-chat-font-size')).toBe('15px');
  });

  it('API keys map directly to config properties gracefully failing if missing', async () => {
    sidebar.input.value = "Hello AI";
    
    // Simulate empty API config fetch
    await sidebar.sendMessage();
    expect(sidebar.configModal.classList.contains('hidden')).toBe(false); // Modal popped implicitly
  });
});
