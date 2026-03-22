import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import '../dist/js/features/location/location';

describe('LocationFeature', () => {
  let container;
  let feature;

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);

    // Mock global map tools
    window.L = {
      map: vi.fn().mockReturnValue({
        remove: vi.fn(),
        invalidateSize: vi.fn(),
        panTo: vi.fn()
      }),
      tileLayer: vi.fn().mockReturnValue({ addTo: vi.fn() }),
      divIcon: vi.fn().mockReturnValue({}),
      marker: vi.fn().mockReturnValue({
        addTo: vi.fn().mockReturnValue({
          bindPopup: vi.fn().mockReturnValue({
            setLatLng: vi.fn(),
            getPopup: vi.fn().mockReturnValue({ setContent: vi.fn() }),
            openPopup: vi.fn()
          })
        })
      }),
      LatLng: vi.fn()
    };

    window.nexusApp = {
      connectionManager: {
        sendCommand: vi.fn()
      }
    };

    global.fetch = vi.fn().mockResolvedValue({
      text: vi.fn().mockResolvedValue('<div id="location-map"></div><button id="location-play-pause-btn"></button><div id="location-name"></div><div id="location-coords"></div>')
    });

    feature = new window.LocationFeature(container);
  });

  afterEach(() => {
    if(typeof feature.destroy === "function") feature.destroy();
    document.body.removeChild(container);
    vi.clearAllMocks();
    window.locationFeatureData = { isTracking: false, currentLocation: null, map: null, marker: null };
  });

  it('can be instantiated', async () => {
    expect(feature).not.toBeNull();
    await new Promise(r => setTimeout(r, 60));
    expect(global.fetch).toHaveBeenCalled();
  });

  it('startTracking sends command', async () => {
    await new Promise(r => setTimeout(r, 60));
    feature.startTracking();
    expect(window.nexusApp.connectionManager.sendCommand).toHaveBeenCalledWith('LOCATE_CHILD');
  });

  it('stopTracking sends stop command', async () => {
    await new Promise(r => setTimeout(r, 60));
    feature.stopTracking();
    expect(window.nexusApp.connectionManager.sendCommand).toHaveBeenCalledWith('LOCATE_CHILD_STOP');
  });

  it('handles location updates', async () => {
    await new Promise(r => setTimeout(r, 60));
    
    // Create actual mock instances for Leaflet
    const evt = new CustomEvent('location-update', { detail: { lat: 21.1, lng: 11.2, name: 'Home' } });
    
    // Spy reverseGeocode since name is provided
    const reverseGeocodeSpy = vi.spyOn(feature, 'reverseGeocode');
    feature.updateLocation(21.1, 11.2, 'Test Place');
    
    expect(reverseGeocodeSpy).not.toHaveBeenCalled();
    expect(feature.data.currentLocation.lat).toBe(21.1);
    
    // Test reverseGeocode path
    global.fetch.mockResolvedValueOnce({
      json: vi.fn().mockResolvedValue({ address: { city: 'Demo City' } })
    });
    await feature.reverseGeocode(21.1, 11.2);
    expect(global.fetch).toHaveBeenCalledWith(expect.stringContaining('nominatim.openstreetmap.org'), expect.any(Object));
  });

});
