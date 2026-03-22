/**
 * Location Feature Module
 * Displays real-time location tracking from child device using Leaflet maps
 */

// Global storage for Location data (persists across feature instances)
if (!window.locationFeatureData) {
    window.locationFeatureData = {
        isTracking: false,
        currentLocation: null,
        map: null,
        marker: null
    };
}

class LocationFeature {
    constructor(container) {
        this.container = container;
        this.confirmationTimeout = null;
        
        // Use global storage for persistence
        this.data = window.locationFeatureData;
        
        this.init();
    }

    async init() {
        // Load HTML template
        const html = await this.loadTemplate();
        this.container.innerHTML = html;

        // Load CSS
        await this.loadStyles();

        // Load Leaflet library
        await this.loadLeaflet();

        // Initialize map
        this.initializeMap();

        // Setup event listeners
        this.setupEventListeners();

        // Listen for location updates
        this.listenForLocation();

        // Restore tracking state if needed
        if (this.data.isTracking) {
            this.updateButton(true);
        }
    }

    async loadTemplate() {
        try {
            const response = await fetch('js/features/location/location.html');
            return await response.text();
        } catch (error) {
            console.error('Failed to load Location template:', error);
            return '<div>Failed to load Location feature</div>';
        }
    }

    async loadStyles() {
        try {
            const link = document.createElement('link');
            link.rel = 'stylesheet';
            link.href = 'js/features/location/location.css';
            document.head.appendChild(link);
        } catch (error) {
            console.error('Failed to load Location styles:', error);
        }
    }

    async loadLeaflet() {
        // Check if Leaflet is already loaded
        if (window.L) {
            return;
        }

        // Load Leaflet CSS
        const cssLink = document.createElement('link');
        cssLink.rel = 'stylesheet';
        cssLink.href = 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.css';
        document.head.appendChild(cssLink);

        // Load Leaflet JS
        return new Promise((resolve, reject) => {
            const script = document.createElement('script');
            script.src = 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.js';
            script.onload = resolve;
            script.onerror = reject;
            document.head.appendChild(script);
        });
    }

    initializeMap() {
        // Remove existing map if any
        if (this.data.map) {
            this.data.map.remove();
            this.data.map = null;
        }

        const mapElement = this.container.querySelector('#location-map');
        if (!mapElement) return;

        // Create map
        this.data.map = L.map(mapElement, {
            center: [23.5, 87.3],
            zoom: 13,
            zoomControl: true,
            attributionControl: false
        });

        // Add tile layers
        L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {
            maxZoom: 19
        }).addTo(this.data.map);

        L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}', {
            maxZoom: 19
        }).addTo(this.data.map);

        // Create custom marker icon
        const customIcon = L.divIcon({
            className: 'custom-marker',
            html: `
                <div style="
                    width: 20px;
                    height: 20px;
                    background: rgba(251, 191, 36, 0.9);
                    border: 3px solid #fff;
                    border-radius: 50%;
                    box-shadow: 0 0 0 3px rgba(251, 191, 36, 0.3);
                    animation: pulse 2s infinite;
                    position: relative;
                ">
                    <div style="
                        position: absolute;
                        top: -8px;
                        left: 50%;
                        transform: translateX(-50%);
                        width: 0;
                        height: 0;
                        border-left: 8px solid transparent;
                        border-right: 8px solid transparent;
                        border-bottom: 12px solid rgba(251, 191, 36, 0.9);
                    "></div>
                </div>
            `,
            iconSize: [20, 20],
            iconAnchor: [10, 10]
        });

        // Create marker
        this.data.marker = L.marker([23.5, 87.3], { icon: customIcon })
            .addTo(this.data.map)
            .bindPopup('Waiting for location...', {
                closeButton: false,
                className: 'location-popup'
            });

        // Restore location if exists
        if (this.data.currentLocation) {
            this.updateLocation(
                this.data.currentLocation.lat,
                this.data.currentLocation.lng,
                this.data.currentLocation.name
            );
        }

        // Invalidate size after a short delay
        setTimeout(() => {
            if (this.data.map) {
                this.data.map.invalidateSize();
            }
        }, 100);
    }

    setupEventListeners() {
        const playPauseBtn = this.container.querySelector('#location-play-pause-btn');

        if (playPauseBtn) {
            playPauseBtn.addEventListener('click', () => this.toggleTracking());
        }
    }

    listenForLocation() {
        // Listen for location updates from connection manager
        window.addEventListener('location-update', (event) => {
            console.log('📍 Location update received:', event.detail);
            const { lat, lng, name } = event.detail;
            this.updateLocation(lat, lng, name);
        });

        // Listen for confirmation messages
        window.addEventListener('location-confirmation', (event) => {
            const message = event.detail.message;
            console.log('📍 [Location] Received confirmation event:', message);
            
            if (message === 'LOCATION_STARTED') {
                console.log('📍 [Location] Tracking started confirmed');
                this.clearConfirmationTimeout();
                this.data.isTracking = true;
                this.updateButton(true);
                this.hideLoading();
            } else if (message === 'LOCATION_STOPPED') {
                console.log('📍 [Location] Tracking stopped confirmed');
                this.clearConfirmationTimeout();
                this.data.isTracking = false;
                this.updateButton(false);
                this.hideLoading();
            }
        });
    }

    toggleTracking() {
        if (this.data.isTracking) {
            this.stopTracking();
        } else {
            this.startTracking();
        }
    }

    startTracking() {
        console.log('📍 Starting location tracking');

        try {
            // Get connection manager from app
            const app = window.nexusApp;
            if (!app || !app.connectionManager) {
                throw new Error('Connection manager not available');
            }

            // Clear any existing timeout
            this.clearConfirmationTimeout();

            // Show loading spinner
            this.showLoading();

            // Send LOCATE_CHILD command
            app.connectionManager.sendCommand('LOCATE_CHILD');

            console.log('✅ Location tracking command sent, waiting for confirmation...');

            // Set timeout in case confirmation never arrives
            this.confirmationTimeout = setTimeout(() => {
                if (this.container.querySelector('.loading-spinner')) {
                    console.warn('⚠️ [Location] Confirmation timeout');
                    this.hideLoading();
                    this.showError('Location tracking start timeout. Please try again.');
                }
            }, 30000); // 30 seconds

        } catch (error) {
            console.error('❌ Failed to start location tracking:', error);
            this.hideLoading();
            this.showError('Failed to start location tracking: ' + error.message);
        }
    }

    stopTracking() {
        console.log('📍 Stopping location tracking');

        try {
            // Get connection manager from app
            const app = window.nexusApp;
            if (!app || !app.connectionManager) {
                throw new Error('Connection manager not available');
            }

            // Clear any existing timeout
            this.clearConfirmationTimeout();

            // Show loading spinner
            this.showLoading();

            // Send LOCATE_CHILD_STOP command
            app.connectionManager.sendCommand('LOCATE_CHILD_STOP');

            console.log('✅ Location tracking stop command sent, waiting for confirmation...');

            // Set timeout in case confirmation never arrives
            this.confirmationTimeout = setTimeout(() => {
                if (this.container.querySelector('.loading-spinner')) {
                    console.warn('⚠️ [Location] Confirmation timeout');
                    this.hideLoading();
                    this.showError('Location tracking stop timeout. Please try again.');
                }
            }, 30000); // 30 seconds

        } catch (error) {
            console.error('❌ Failed to stop location tracking:', error);
            this.hideLoading();
        }
    }

    updateLocation(lat, lng, locationName = null) {
        if (!this.data.map || !this.data.marker) return;

        const newLatLng = new L.LatLng(lat, lng);
        this.data.currentLocation = { lat, lng, name: locationName };

        // Update marker position
        this.data.marker.setLatLng(newLatLng);

        // Pan to new location with animation
        this.data.map.panTo(newLatLng, {
            animate: true,
            duration: 1.5,
            easeLinearity: 0.1
        });

        // Update popup content
        let popupContent = `<div style="color: rgba(255, 255, 255, 0.9); font-size: 11px; text-align: center;">`;
        if (locationName) {
            popupContent += `<strong>${locationName}</strong><br>`;
        }
        popupContent += `${lat.toFixed(6)}, ${lng.toFixed(6)}</div>`;
        
        this.data.marker.getPopup().setContent(popupContent);
        this.data.marker.openPopup();

        // Update location info in header
        this.updateLocationInfo(lat, lng, locationName);

        // If no location name provided, try reverse geocoding
        if (!locationName) {
            this.reverseGeocode(lat, lng);
        }

        // Flash effect
        const mapContainer = this.container.querySelector('.location-map-container');
        if (mapContainer) {
            mapContainer.classList.add('location-flash');
            setTimeout(() => {
                mapContainer.classList.remove('location-flash');
            }, 1000);
        }
    }

    async reverseGeocode(lat, lng) {
        try {
            const response = await fetch(
                `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}&zoom=18&addressdetails=1`,
                {
                    headers: {
                        'User-Agent': 'NexusParentApp/1.0'
                    }
                }
            );
            
            const data = await response.json();
            
            if (data && data.address) {
                const address = data.address;
                let locationName = '';
                
                // Build location name from address components
                if (address.railway) {
                    locationName = `${address.railway} Railway Station`;
                } else if (address.amenity) {
                    locationName = address.amenity;
                } else if (address.shop) {
                    locationName = address.shop;
                } else if (address.building) {
                    locationName = address.building;
                } else if (address.road) {
                    locationName = address.road;
                    if (address.suburb) locationName += `, ${address.suburb}`;
                } else if (address.suburb) {
                    locationName = address.suburb;
                } else if (address.city || address.town || address.village) {
                    locationName = address.city || address.town || address.village;
                }
                
                if (!locationName && data.display_name) {
                    locationName = data.display_name.split(',')[0];
                }
                
                if (locationName) {
                    // Update the stored location name
                    if (this.data.currentLocation) {
                        this.data.currentLocation.name = locationName;
                    }
                    
                    // Update UI
                    this.updateLocationInfo(lat, lng, locationName);
                    
                    // Update popup
                    let popupContent = `<div style="color: rgba(255, 255, 255, 0.9); font-size: 11px; text-align: center;">`;
                    popupContent += `<strong>${locationName}</strong><br>`;
                    popupContent += `${lat.toFixed(6)}, ${lng.toFixed(6)}</div>`;
                    this.data.marker.getPopup().setContent(popupContent);
                }
            }
        } catch (error) {
            console.error('Reverse geocoding failed:', error);
            // Keep "Unknown Location" if geocoding fails
        }
    }

    updateLocationInfo(lat, lng, locationName) {
        const locationNameEl = this.container.querySelector('#location-name');
        const locationCoordsEl = this.container.querySelector('#location-coords');

        if (locationNameEl) {
            locationNameEl.textContent = locationName || 'Unknown Location';
        }

        if (locationCoordsEl) {
            locationCoordsEl.textContent = `${lat.toFixed(6)}, ${lng.toFixed(6)}`;
        }
    }

    clearConfirmationTimeout() {
        if (this.confirmationTimeout) {
            clearTimeout(this.confirmationTimeout);
            this.confirmationTimeout = null;
            console.log('✅ [Location] Confirmation timeout cleared');
        }
    }

    showLoading() {
        const playPauseBtn = this.container.querySelector('#location-play-pause-btn');
        if (playPauseBtn) {
            playPauseBtn.innerHTML = `
                <svg class="loading-spinner" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="10" opacity="0.25"></circle>
                    <path d="M12 2 A10 10 0 0 1 22 12" opacity="0.75">
                        <animateTransform
                            attributeName="transform"
                            type="rotate"
                            from="0 12 12"
                            to="360 12 12"
                            dur="1s"
                            repeatCount="indefinite"/>
                    </path>
                </svg>
            `;
            playPauseBtn.disabled = true;
        }
    }

    hideLoading() {
        const playPauseBtn = this.container.querySelector('#location-play-pause-btn');
        if (playPauseBtn) {
            playPauseBtn.disabled = false;
            
            // Restore the play/pause icons based on current state
            if (this.data.isTracking) {
                playPauseBtn.innerHTML = `
                    <svg class="play-icon" viewBox="0 0 24 24" fill="currentColor" style="display:none;">
                        <polygon points="5 3 19 12 5 21 5 3"></polygon>
                    </svg>
                    <svg class="pause-icon" viewBox="0 0 24 24" fill="currentColor" style="display:block;">
                        <rect x="6" y="4" width="4" height="16"></rect>
                        <rect x="14" y="4" width="4" height="16"></rect>
                    </svg>
                `;
            } else {
                playPauseBtn.innerHTML = `
                    <svg class="play-icon" viewBox="0 0 24 24" fill="currentColor" style="display:block;">
                        <polygon points="5 3 19 12 5 21 5 3"></polygon>
                    </svg>
                    <svg class="pause-icon" viewBox="0 0 24 24" fill="currentColor" style="display:none;">
                        <rect x="6" y="4" width="4" height="16"></rect>
                        <rect x="14" y="4" width="4" height="16"></rect>
                    </svg>
                `;
            }
        }
    }

    updateButton(isActive) {
        const playPauseBtn = this.container.querySelector('#location-play-pause-btn');
        if (!playPauseBtn) return;

        const playIcon = playPauseBtn.querySelector('.play-icon');
        const pauseIcon = playPauseBtn.querySelector('.pause-icon');

        if (!playIcon || !pauseIcon) return;

        if (isActive) {
            playIcon.style.display = 'none';
            pauseIcon.style.display = 'block';
        } else {
            playIcon.style.display = 'block';
            pauseIcon.style.display = 'none';
        }
    }

    showError(message) {
        const locationNameEl = this.container.querySelector('#location-name');
        const locationCoordsEl = this.container.querySelector('#location-coords');

        if (locationNameEl) {
            locationNameEl.textContent = 'Error';
            locationNameEl.style.color = '#ef4444';
        }

        if (locationCoordsEl) {
            locationCoordsEl.textContent = message;
            locationCoordsEl.style.color = '#ef4444';
        }

        // Reset colors after 3 seconds
        setTimeout(() => {
            if (locationNameEl) {
                locationNameEl.style.color = '';
            }
            if (locationCoordsEl) {
                locationCoordsEl.style.color = '';
            }
        }, 3000);
    }

    destroy() {
        if (this.data.isTracking) {
            this.stopTracking();
        }
        // Note: We don't destroy the map, so it persists across panel open/close
    }
}

// Export to window for dynamic loading
window.LocationFeature = LocationFeature;
