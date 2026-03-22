import { describe, it, expect, vi, beforeEach } from 'vitest';
import '../dist/js/loaders/feature-loader';

describe('FeatureLoader', () => {
  let loader;

  beforeEach(() => {
    loader = window.featureLoader;
    document.body.innerHTML = ``;
  });

  afterEach(() => {
    loader.unloadAll();
    vi.clearAllMocks();
  });

  it('getFeatureClassName formats correctly', () => {
    expect(loader.getFeatureClassName('camera')).toBe('CameraFeature');
    expect(loader.getFeatureClassName('app-lock')).toBe('AppLockFeature');
  });

  it('loadFeatureStyles appends link only once', async () => {
    const appendSpy = vi.spyOn(document.head, 'appendChild');
    
    // Mock successful style load
    vi.spyOn(document, 'createElement').mockImplementation((tag) => {
      const el = { tag, Id: '' };
      // auto-fire onload securely
      setTimeout(() => { if (el.onload) el.onload() }, 10);
      return el;
    });

    await loader.loadFeatureStyles('camera');
    
    expect(appendSpy).toHaveBeenCalled();
    expect(loader.loadedStyles.has('camera')).toBe(true);

    // Call again, should not append
    appendSpy.mockClear();
    await loader.loadFeatureStyles('camera');
    expect(appendSpy).not.toHaveBeenCalled();
  });

  it('loadFeature handles failed template load gracefully', async () => {
    global.fetch = vi.fn(() =>
      Promise.resolve({
        ok: false,
        status: 404
      })
    );

    const container = document.createElement('div');
    const instance = await loader.loadFeature('unknown', container);

    expect(instance).toBeNull();
    expect(container.innerHTML).toContain('Failed to load unknown feature');
  });

});
