import { describe, it, expect, vi, beforeEach } from 'vitest';
import '../dist/js/splash';

describe('Splash', () => {
  beforeEach(() => {
    document.body.innerHTML = `
      <div id="splash-screen">
        <div class="splash-particles"></div>
      </div>
    `;
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.clearAllMocks();
    vi.useRealTimers();
  });

  it('creates particles and removes splash screen eventually', () => {
    // Re-trigger the DOMContentLoaded logic
    const evt = new Event('DOMContentLoaded');
    document.dispatchEvent(evt);

    const particles = document.querySelectorAll('.splash-particle');
    expect(particles.length).toBe(20);

    const splashScreen = document.getElementById('splash-screen');
    expect(splashScreen).not.toBeNull();

    // Fast forward 5.5 seconds (5000ms delay + 500ms fade)
    vi.advanceTimersByTime(5500);

    // Should be removed
    const removedSplashScreen = document.getElementById('splash-screen');
    expect(removedSplashScreen).toBeNull();
  });
});
