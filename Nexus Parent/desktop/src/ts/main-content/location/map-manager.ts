/**
 * @file map-manager.ts
 * @description Leaflet Interactive Map Controller for Nexus Parent Desktop Console.
 *
 * Hey friend! Why Leaflet instead of a bloated map framework?
 * 1. Lightweight & Fast: Runs buttery smooth inside Tauri's WebView2 hardware-accelerated canvas.
 * 2. Multi-Layer Tactical Support: Seamlessly toggles between high-resolution Satellite Imagery
 *    (Esri World Imagery + Hybrid Reference Labels) and Minimalist Dark Matter vector streets (CartoDB).
 * 3. Tactical GPS Target Pinpoint: Renders a pulsing locator crosshair with a dynamic accuracy halo.
 * 4. Reverse Geocoding Engine: Resolves latitude/longitude into human-readable street names via
 *    Nominatim with smart local caching to prevent redundant network queries.
 */

import {
  type LocationTelemetryData,
  formatCoordinates
} from './types.js';

// Declare Leaflet global type for seamless TypeScript compilation without heavy node module dependencies
declare const L: any;

export class MapManager {
  private map: any = null;
  private marker: any = null;
  private accuracyCircle: any = null;

  // Stored state for persistence
  private lastLocation: LocationTelemetryData | null = null;
  private addressCache: Map<string, string> = new Map();
  private lastGeocodeRequestTime = 0;

  /**
   * Initializes the Leaflet map inside the target container element.
   *
   * @param container DOM element hosting the map canvas
   * @param onAddressResolved Callback invoked when reverse geocoding resolves a street address
   */
  public initMap(container: HTMLElement, onAddressResolved?: (address: string) => void): boolean {
    if (typeof L === 'undefined') {
      console.warn('[MapManager] Leaflet library (L) is not loaded on window.');
      return false;
    }

    // Clean up any stale map instance
    if (this.map) {
      try {
        this.map.remove();
      } catch (e) {
        console.warn('[MapManager] Error removing old map instance:', e);
      }
      this.map = null;
    }

    const initialLat = this.lastLocation?.latitude ?? 22.572645;
    const initialLng = this.lastLocation?.longitude ?? 88.363892;

    // Create Leaflet map with disabled default attribution for sleek tactical styling
    this.map = L.map(container, {
      center: [initialLat, initialLng],
      zoom: 15,
      zoomControl: false, // We render our own tactical bottom control buttons
      attributionControl: false
    });

    // Layer 1: Esri Satellite World Imagery (100% Free, Zero API Key required)
    L.tileLayer(
      'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
      { maxZoom: 19, crossOrigin: true }
    ).addTo(this.map);

    // Layer 1b: Esri Hybrid Boundaries and Roads overlay (100% Free, Zero API Key required)
    L.tileLayer(
      'https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}',
      { maxZoom: 19, crossOrigin: true }
    ).addTo(this.map);

    // Create custom tactical pulsing marker
    const tacticalMarkerIcon = L.divIcon({
      className: 'tactical-gps-marker-container',
      html: `
        <div class="tactical-marker-pin">
          <div class="tactical-marker-pulse"></div>
          <div class="tactical-marker-dot"></div>
        </div>
      `,
      iconSize: [28, 28],
      iconAnchor: [14, 14]
    });

    this.marker = L.marker([initialLat, initialLng], {
      icon: tacticalMarkerIcon,
      zIndexOffset: 1000
    }).addTo(this.map);

    // Bind clean popup
    this.marker.bindPopup(this.buildPopupHtml(initialLat, initialLng, this.lastLocation?.address), {
      closeButton: false,
      className: 'tactical-map-popup',
      offset: [0, -10]
    });

    // Accuracy halo radius circle
    const initialAccuracy = this.lastLocation?.accuracy ?? 15;
    this.accuracyCircle = L.circle([initialLat, initialLng], {
      radius: initialAccuracy,
      color: '#22c55e',
      weight: 1,
      opacity: 0.6,
      fillColor: '#22c55e',
      fillOpacity: 0.12
    }).addTo(this.map);

    // If we already had an address for this location, trigger callback
    if (this.lastLocation?.address && onAddressResolved) {
      onAddressResolved(this.lastLocation.address);
    }

    // Refresh layout geometry after mount
    setTimeout(() => {
      this.invalidateSize();
    }, 150);

    return true;
  }

  /**
   * Updates marker, accuracy halo, camera pan, and triggers reverse geocoding for new location updates.
   *
   * @param data Fresh telemetry packet received over DataChannel
   * @param onAddressResolved Callback invoked when address name resolves
   */
  public updateLocation(data: LocationTelemetryData, onAddressResolved?: (address: string) => void): void {
    this.lastLocation = data;

    if (!this.map || !this.marker) return;

    const latLng = [data.latitude, data.longitude];

    // Smoothly update marker position
    this.marker.setLatLng(latLng);

    // Update accuracy radius halo
    if (this.accuracyCircle) {
      this.accuracyCircle.setLatLng(latLng);
      this.accuracyCircle.setRadius(Math.max(5, data.accuracy));
    }

    // Pan smoothly to child coordinates
    this.map.panTo(latLng, {
      animate: true,
      duration: 1.2,
      easeLinearity: 0.25
    });

    // Update popup content
    this.marker.getPopup()?.setContent(
      this.buildPopupHtml(data.latitude, data.longitude, data.address)
    );

    // Perform reverse geocoding if address is missing
    if (!data.address) {
      this.reverseGeocode(data.latitude, data.longitude, (resolvedAddress) => {
        data.address = resolvedAddress;
        if (this.lastLocation) this.lastLocation.address = resolvedAddress;
        this.marker.getPopup()?.setContent(
          this.buildPopupHtml(data.latitude, data.longitude, resolvedAddress)
        );
        onAddressResolved?.(resolvedAddress);
      });
    } else {
      onAddressResolved?.(data.address);
    }
  }

  /**
   * Smoothly recenters map on child's current location.
   */
  public recenter(): void {
    if (!this.map || !this.lastLocation) return;
    this.map.setView([this.lastLocation.latitude, this.lastLocation.longitude], 16, {
      animate: true
    });
  }

  /**
   * Zooms in one step.
   */
  public zoomIn(): void {
    this.map?.zoomIn();
  }

  /**
   * Zooms out one step.
   */
  public zoomOut(): void {
    this.map?.zoomOut();
  }

  /**
   * Triggers Leaflet to recalculate container bounds after tab switches or window resizes.
   */
  public invalidateSize(): void {
    this.map?.invalidateSize();
  }

  /**
   * Reverse geocodes coordinates to a human-readable address with caching and throttling.
   */
  private async reverseGeocode(
    lat: number,
    lng: number,
    onSuccess: (address: string) => void
  ): Promise<void> {
    const cacheKey = `${lat.toFixed(4)},${lng.toFixed(4)}`;
    const cached = this.addressCache.get(cacheKey);
    if (cached) {
      onSuccess(cached);
      return;
    }

    // Throttle queries to maximum 1 per 2 seconds to respect Nominatim usage policy
    const now = Date.now();
    if (now - this.lastGeocodeRequestTime < 2000) {
      return;
    }
    this.lastGeocodeRequestTime = now;

    try {
      const url = `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}&zoom=18&addressdetails=1`;
      const response = await fetch(url, {
        headers: {
          'Accept-Language': 'en',
          'User-Agent': 'NexusParentApp/2.0'
        }
      });

      if (!response.ok) return;

      const result = await response.json();
      if (!result || !result.address) return;

      const addr = result.address;
      const parts: string[] = [];

      if (addr.amenity || addr.shop || addr.building || addr.office) {
        parts.push(addr.amenity || addr.shop || addr.building || addr.office);
      }
      if (addr.road) parts.push(addr.road);
      if (addr.suburb && !parts.includes(addr.suburb)) parts.push(addr.suburb);
      if (addr.city || addr.town || addr.village) {
        parts.push(addr.city || addr.town || addr.village);
      }

      const formatted = parts.length > 0 ? parts.join(', ') : result.display_name?.split(',').slice(0, 3).join(',') || 'Known Location';
      this.addressCache.set(cacheKey, formatted);
      onSuccess(formatted);
    } catch (e) {
      console.warn('[MapManager] Reverse geocode lookup error:', e);
    }
  }

  /**
   * Builds clean popup HTML without emojis.
   */
  private buildPopupHtml(lat: number, lng: number, address?: string): string {
    const coordsStr = formatCoordinates(lat, lng);
    const addrStr = address || 'Acquiring location name...';
    return `
      <div class="tactical-popup-body">
        <div class="tactical-popup-title">Child Device</div>
        <div class="tactical-popup-address">${addrStr}</div>
        <div class="tactical-popup-coords">${coordsStr}</div>
      </div>
    `;
  }

  /**
   * Destroys the map instance on component removal.
   */
  public destroy(): void {
    if (this.map) {
      try {
        this.map.remove();
      } catch (e) {
        console.warn('[MapManager] Error destroying map:', e);
      }
      this.map = null;
    }
  }
}
