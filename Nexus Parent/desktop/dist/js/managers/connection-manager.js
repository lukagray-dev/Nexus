/**
 * Connection Manager
 * Handles all device connection logic including validation, connection, and disconnection
 */

class ConnectionManager {
    constructor(app) {
        this.app = app;
        this.webrtcManager = null;
        this.dataChannel = null;
    }

    /**
     * Validate child device ID format
     */
    validateChildId(childId) {
        if (childId.length !== 12 || !/^\d{12}$/.test(childId)) {
            return {
                valid: false,
                error: 'Child ID must be exactly 12 digits.'
            };
        }
        return { valid: true };
    }

    /**
     * Handle connection to child device
     */
    async connect(childId) {
        // Validate
        const validation = this.validateChildId(childId);
        if (!validation.valid) {
            throw new Error(validation.error);
        }

        console.log('🔗 Connecting to Child ID:', childId);

        try {
            // Initialize WebRTC Manager
            if (!this.webrtcManager) {
                this.webrtcManager = new WebRTCManager();
                
                // Initialize Firebase with config
                const firebaseConfig = window.firebaseConfig;
                if (firebaseConfig) {
                    this.webrtcManager.initializeFirebase(firebaseConfig);
                } else {
                    throw new Error('Firebase config not available');
                }
            }

            // Setup callbacks
            this.webrtcManager.setCallbacks({
                onConnectionEstablished: (dataChannel) => {
                    console.log('✅ WebRTC connection established');
                    this.dataChannel = dataChannel;
                },
                onStreamReceived: (streamType, stream) => {
                    console.log(`📹 Stream received: ${streamType}`);
                    this.handleStreamReceived(stream, streamType);
                },
                onDataChannelMessage: (message) => {
                    this.handleDataChannelMessage(message);
                }
            });

            // Connect to child device
            await this.webrtcManager.connect(childId);
            console.log('✓ WebRTC connection initiated');

            return childId;
        } catch (error) {
            console.error('❌ Connection error:', error);
            throw error;
        }
    }

    /**
     * Wait for connection to establish
     */
    async waitForConnection() {
        const maxAttempts = 20;
        let attempts = 0;

        return new Promise((resolve, reject) => {
            const checkConnection = () => {
                if (this.dataChannel && this.dataChannel.readyState === 'open') {
                    console.log('✓ Connected successfully!');
                    resolve(true);
                    return;
                }

                attempts++;
                if (attempts >= maxAttempts) {
                    reject(new Error('Connection timeout. Please try again.'));
                    return;
                }

                // Check again in 500ms
                setTimeout(checkConnection, 500);
            };

            checkConnection();
        });
    }

    /**
     * Handle received media stream
     */
    handleStreamReceived(stream, type) {
        // Notify features about new stream
        if (type === 'camera') {
            const event = new CustomEvent('camera-stream-received', { detail: { stream } });
            window.dispatchEvent(event);
        } else if (type === 'screen') {
            const event = new CustomEvent('screen-stream-received', { detail: { stream } });
            window.dispatchEvent(event);
        } else if (type === 'mic') {
            const event = new CustomEvent('mic-stream-received', { detail: { stream } });
            window.dispatchEvent(event);
        }
    }

    /**
     * Handle data channel message
     */
    handleDataChannelMessage(message) {
        // Dispatch confirmation events
        if (typeof message === 'string') {
            // Handle Opus audio packets for screen recording (silent - no logging)
            if (message.startsWith('OPUS_AUDIO:')) {
                const base64Data = message.substring(11); // Remove "OPUS_AUDIO:" prefix
                const event = new CustomEvent('screen-audio-packet', {
                    detail: { base64Data }
                });
                window.dispatchEvent(event);
                return;
            }
            
            // Log non-audio messages only
            console.log('📩 Data channel message:', message);
            console.log('📩 Message type: string, value:', message);
            
            // Try to parse as JSON for SMS and Call Log data
            try {
                const data = JSON.parse(message);
                
                // SMS messages
                if (data.type === 'sms') {
                    console.log('📨 SMS message received');
                    const event = new CustomEvent('sms-message', {
                        detail: data
                    });
                    window.dispatchEvent(event);
                    return;
                }
                
                // Call log messages
                if (data.type === 'calllog') {
                    console.log('📞 Call log received');
                    const event = new CustomEvent('calllog-message', {
                        detail: data
                    });
                    window.dispatchEvent(event);
                    return;
                }
                
                // Location updates
                if (data.type === 'location' || data.type === 'LOCATION_UPDATE') {
                    console.log('📍 Location update received');
                    
                    let lat, lng;
                    
                    // Handle both formats
                    if (data.coords && Array.isArray(data.coords) && data.coords.length >= 2) {
                        // Format: {type: "LOCATION_UPDATE", coords: [lat, lng]}
                        lat = data.coords[0];
                        lng = data.coords[1];
                    } else if (data.latitude !== undefined && data.longitude !== undefined) {
                        // Format: {type: "location", latitude: lat, longitude: lng}
                        lat = data.latitude;
                        lng = data.longitude;
                    }
                    
                    if (lat !== undefined && lng !== undefined) {
                        const event = new CustomEvent('location-update', {
                            detail: {
                                lat: lat,
                                lng: lng,
                                name: data.locationName || null,
                                accuracy: data.accuracy || null,
                                timestamp: data.timestamp || null
                            }
                        });
                        window.dispatchEvent(event);
                    }
                    return;
                }
                
                // Notification messages
                if (data.type === 'NOTIFICATION' || data.type === 'NOTIFICATION_BATCH') {
                    console.log('🔔 Notification received');
                    
                    if (data.type === 'NOTIFICATION_BATCH') {
                        // Handle batch of notifications
                        if (data.notifications && Array.isArray(data.notifications)) {
                            data.notifications.forEach(notif => {
                                const event = new CustomEvent('notification-update', {
                                    detail: notif
                                });
                                window.dispatchEvent(event);
                            });
                        }
                    } else {
                        // Single notification
                        const event = new CustomEvent('notification-update', {
                            detail: data
                        });
                        window.dispatchEvent(event);
                    }
                    return;
                }
                
                // Chat messages (v2 payload structure)
                if (data.type === 'CONVERSATION_UPDATE' || data.type === 'TYPING_INDICATOR' || data.type === 'APP_OPENED') {
                    console.log('💬 Chat message received:', data.type);
                    const event = new CustomEvent('chat-update', {
                        detail: data
                    });
                    window.dispatchEvent(event);
                    return;
                }
                
                // Legacy chat messages (v1 compatibility)
                if (data.type === 'MESSAGE' || data.type === 'TYPING' || data.type === 'APP_FOREGROUND') {
                    console.log('💬 Legacy chat message received');
                    const event = new CustomEvent('chat-update', {
                        detail: data
                    });
                    window.dispatchEvent(event);
                    return;
                }
                
                // Keyboard monitoring messages
                if (data.type === 'KEYBOARD_INPUT' || data.type === 'CLIPBOARD_COPY') {
                    console.log('⌨️ Keyboard event received:', data.type);
                    const event = new CustomEvent('keyboard-update', {
                        detail: data
                    });
                    window.dispatchEvent(event);
                    return;
                }
                
                // Wellbeing data
                if (data.type === 'WELLBEING_DATA') {
                    console.log('📊 Wellbeing data received');
                    const event = new CustomEvent('wellbeing-data', {
                        detail: data
                    });
                    window.dispatchEvent(event);
                    return;
                }
                
                // App lock alerts
                if (data.type === 'APPLOCK_ALERT') {
                    console.log('🔐 App lock alert received');
                    const event = new CustomEvent('applock-alert', {
                        detail: data
                    });
                    window.dispatchEvent(event);
                    return;
                }
                
                // App lock status
                if (data.type === 'APP_LOCK_STATUS' || data.type === 'APPLOCK_STATUS' || data.type === 'APP_LOCK_ENABLED_SET') {
                    console.log('🔐 App lock status received');
                    const event = new CustomEvent('applock-status', {
                        detail: data
                    });
                    window.dispatchEvent(event);
                    return;
                }
                
                // File list messages
                if (data.type === 'LIST_FILES_BATCH') {
                    console.log('📁 File list batch received');
                    const event = new CustomEvent('file-list-batch', {
                        detail: data
                    });
                    window.dispatchEvent(event);
                    return;
                }
                
                if (data.type === 'LIST_FILES_COMPLETE') {
                    console.log('📁 File list complete');
                    const event = new CustomEvent('file-list-complete', {
                        detail: data
                    });
                    window.dispatchEvent(event);
                    return;
                }
                
                if (data.type === 'LIST_FILES_ERROR') {
                    console.log('📁 File list error');
                    const event = new CustomEvent('file-list-error', {
                        detail: data
                    });
                    window.dispatchEvent(event);
                    return;
                }
                
                // File transfer messages
                if (data.type && data.type.startsWith('FILE_TRANSFER')) {
                    console.log('📁 File transfer update');
                    const event = new CustomEvent('file-transfer', {
                        detail: data
                    });
                    window.dispatchEvent(event);
                    return;
                }
            } catch (e) {
                // Not JSON, continue with string handling
            }
            
            // Camera confirmations
            if (message === 'CAMERA_STARTED' || message === 'CAMERA_STOPPED' || message === 'CAMERA_SWITCHED') {
                console.log('📡 Dispatching camera-confirmation event for:', message);
                const event = new CustomEvent('camera-confirmation', {
                    detail: { message }
                });
                window.dispatchEvent(event);
                console.log('✅ Event dispatched');
            }
            
            // Screen recording confirmations
            if (message === 'SCREEN_RECORDING_STARTED' || message === 'SCREEN_RECORDING_STOPPED' || 
                message === 'SCREEN_RECORDING_PERMISSION_DENIED' || message.startsWith('SCREEN_RECORDING_ERROR')) {
                console.log('📡 Dispatching screen-confirmation event for:', message);
                const event = new CustomEvent('screen-confirmation', {
                    detail: { message }
                });
                window.dispatchEvent(event);
                console.log('✅ Event dispatched');
            }
            
            // Mic confirmations
            if (message === 'MIC_STARTED' || message === 'MIC_STOPPED') {
                console.log('📡 Dispatching mic-confirmation event for:', message);
                const event = new CustomEvent('mic-confirmation', {
                    detail: { message }
                });
                window.dispatchEvent(event);
                console.log('✅ Event dispatched');
            }
            
            // SMS confirmations
            if (message === 'SMS_STARTED' || message === 'SMS_STOPPED') {
                console.log('📡 Dispatching sms-confirmation event for:', message);
                const event = new CustomEvent('sms-confirmation', {
                    detail: { message }
                });
                window.dispatchEvent(event);
                console.log('✅ Event dispatched');
            }
            
            // Call Log confirmations
            if (message === 'CALLLOG_STARTED' || message === 'CALLLOG_STOPPED') {
                console.log('📡 Dispatching calllog-confirmation event for:', message);
                const event = new CustomEvent('calllog-confirmation', {
                    detail: { message }
                });
                window.dispatchEvent(event);
                console.log('✅ Event dispatched');
            }
            
            // Location confirmations
            if (message === 'LOCATION_STARTED' || message === 'LOCATION_STOPPED') {
                console.log('📡 Dispatching location-confirmation event for:', message);
                const event = new CustomEvent('location-confirmation', {
                    detail: { message }
                });
                window.dispatchEvent(event);
                console.log('✅ Event dispatched');
            }
            
            // Notification confirmations
            if (message === 'NOTIFICATION_STARTED' || message === 'NOTIFICATION_STOPPED') {
                console.log('📡 Dispatching notification-confirmation event for:', message);
                const event = new CustomEvent('notification-confirmation', {
                    detail: { message }
                });
                window.dispatchEvent(event);
                console.log('✅ Event dispatched');
            }
            
            // Chat confirmations
            if (message === 'CHAT_STARTED' || message === 'CHAT_STOPPED') {
                console.log('📡 Dispatching chat-confirmation event for:', message);
                const event = new CustomEvent('chat-confirmation', {
                    detail: { message }
                });
                window.dispatchEvent(event);
                console.log('✅ Event dispatched');
            }
            
            // Keyboard confirmations
            if (message === 'KEYBOARD_STARTED' || message === 'KEYBOARD_STOPPED') {
                console.log('📡 Dispatching keyboard-confirmation event for:', message);
                const event = new CustomEvent('keyboard-confirmation', {
                    detail: { message }
                });
                window.dispatchEvent(event);
                console.log('✅ Event dispatched');
            }
            
            // Stealth mode confirmations
            if (message === 'STEALTH_ON_ACK' || message === 'STEALTH_OFF_ACK') {
                console.log('📡 Dispatching stealth-confirmation event for:', message);
                const event = new CustomEvent('stealth-confirmation', {
                    detail: { message }
                });
                window.dispatchEvent(event);
                console.log('✅ Event dispatched');
            }
            
            // Wallpaper status messages
            if (message.startsWith('WALLPAPER_')) {
                console.log('📡 Dispatching wallpaper-status event for:', message);
                const event = new CustomEvent('wallpaper-status', {
                    detail: { message }
                });
                window.dispatchEvent(event);
                console.log('✅ Event dispatched');
            }
        }
        
        // Handle other message types here
    }

    /**
     * Handle renegotiation request from child
     */
    async handleRenegotiation() {
        if (this.webrtcManager) {
            try {
                await this.webrtcManager.renegotiate();
                console.log('✅ Renegotiation completed');
            } catch (error) {
                console.error('❌ Renegotiation failed:', error);
            }
        }
    }

    /**
     * Send command to child device
     */
    sendCommand(command) {
        if (this.webrtcManager) {
            return this.webrtcManager.sendCommand(command);
        }
        console.error('❌ WebRTC manager not initialized');
        return false;
    }

    /**
     * Send binary data to child device
     */
    sendBinaryData(data) {
        if (this.dataChannel && this.dataChannel.readyState === 'open') {
            this.dataChannel.send(data);
            return true;
        }
        console.error('❌ DataChannel not available or not open');
        return false;
    }

    /**
     * Disconnect from device
     */
    async disconnect() {
        try {
            console.log('Disconnecting...');
            
            if (this.webrtcManager) {
                this.webrtcManager.cleanup();
                this.webrtcManager = null;
            }
            
            this.dataChannel = null;
            console.log('✓ Disconnected');
            return true;
        } catch (error) {
            console.error('❌ Disconnect failed:', error);
            throw error;
        }
    }
}

// Export for use in app
window.ConnectionManager = ConnectionManager;

// Export for use in app
window.ConnectionManager = ConnectionManager;
