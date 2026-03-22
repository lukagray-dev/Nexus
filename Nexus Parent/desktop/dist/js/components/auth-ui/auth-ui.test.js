import { describe, it, expect, vi, beforeEach } from 'vitest';
import '../dist/js/components/auth-ui/auth-ui';

describe('AuthUI', () => {
  let authUI;

  beforeEach(() => {
    document.body.innerHTML = `
      <div id="connect-screen"></div>
    `;

    global.fetch = vi.fn().mockResolvedValue({
      text: vi.fn().mockResolvedValue('<div id="auth-ui-payload"></div>')
    });

    authUI = new window.AuthUI();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('loads payload into DOM', async () => {
    await authUI.load();
    expect(global.fetch).toHaveBeenCalled();
    const payload = document.getElementById('auth-ui-payload');
    expect(payload).not.toBeNull();
    expect(authUI.loaded).toBe(true);
  });
});
