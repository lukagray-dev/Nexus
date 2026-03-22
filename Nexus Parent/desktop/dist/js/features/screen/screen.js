/**
 * Screen Recording Feature Module
 * Uses native WebRTC screen streaming with Electron
 * Follows exact same pattern as CameraFeature
 */

class ScreenFeature {
    constructor(container) {
        this.container = container;
        this.isStreaming = false;
        this.videoElement = null;
        this.stream = null;
        this.confirmationTimeout = null;
        this.opusAudioPlayer = null; // Opus audio player for system audio
        this.init();
    }

    async init() {
        // Load HTML template
        const html = await this.loadTemplate();
        this.container.innerHTML = html;

        // Load CSS
        await this.loadStyles();

        // Initialize Opus audio player for system audio
        await this.initOpusAudioPlayer();

        // Initialize recording manager
        this.initRecordingManager();

        // Setup event listeners
        this.setupEventListeners();

        // Listen for screen stream
        this.listenForStream();

        // Listen for confirmation messages from child
        this.listenForConfirmations();
        
        // Listen for Opus audio packets
        this.listenForAudioPackets();
    }

    async loadTemplate() {
        try {
            const response = await fetch('js/features/screen/screen.html');
            return await response.text();
        } catch (error) {
            console.error('Failed to load screen template:', error);
            return '<div>Failed to load screen feature</div>';
        }
    }

    async loadStyles() {
        try {
            const link = document.createElement('link');
            link.rel = 'stylesheet';
            link.href = 'js/features/screen/screen.css';
            document.head.appendChild(link);
        } catch (error) {
            console.error('Failed to load screen styles:', error);
        }
    }

    async initOpusAudioPlayer() {
        try {
            // Load Opus audio player script
            if (!window.OpusAudioPlayer) {
                const script = document.createElement('script');
                script.src = 'js/features/screen/opus-audio-player.js';
                document.head.appendChild(script);
                
                // Wait for script to load
                await new Promise((resolve, reject) => {
                    script.onload = resolve;
                    script.onerror = reject;
                });
            }
            
            // Create Opus audio player instance
            this.opusAudioPlayer = new OpusAudioPlayer();
            await this.opusAudioPlayer.init();
            
            console.log('✅ [Screen] Opus audio player initialized');
        } catch (error) {
            console.error('❌ [Screen] Failed to initialize Opus audio player:', error);
        }
    }

    initRecordingManager() {
        // Get or create global recording manager
        if (!window.nexusRecordingManager) {
            window.nexusRecordingManager = new RecordingManager();
        }
        this.recordingManager = window.nexusRecordingManager;
    }

    setupEventListeners() {
        const recordBtn = this.container.querySelector('#screen-record-btn');
        const playPauseBtn = this.container.querySelector('#screen-play-pause-btn');
        const fullscreenBtn = this.container.querySelector('#screen-fullscreen-btn');

        // Recording button - direct toggle (no dropdown)
        if (recordBtn) {
            recordBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                
                if (this.recordingManager && this.recordingManager.isRecording()) {
                    this.stopRecording();
                } else {
                    // Always record both video and system audio
                    this.startRecording('both');
                }
            });
        }

        // Screen on/off control (stream overlay button)
        if (playPauseBtn) {
            playPauseBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.toggleScreen();
            });
        }

        // Fullscreen control (stream overlay button)
        if (fullscreenBtn) {
            fullscreenBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.toggleFullscreen();
            });
        }
    }

    listenForStream() {
        // Listen for screen stream from WebRTC
        console.log('🖥️ [Screen] Setting up stream listener...');
        
        // Check if stream already exists (panel reopened while streaming)
        const app = window.nexusApp;
        if (app && app.connectionManager && app.connectionManager.webrtcManager) {
            const existingStream = app.connectionManager.webrtcManager.getStream('screen');
            if (existingStream) {
                console.log('🖥️ [Screen] Found existing stream on panel open');
                
                // Check if stream is actually active (has active video tracks)
                const videoTracks = existingStream.getVideoTracks();
                const hasActiveTrack = videoTracks.some(track => track.readyState === 'live' && track.enabled);
                
                if (hasActiveTrack) {
                    console.log('🖥️ [Screen] Stream has active video track, attaching immediately');
                    this.isStreaming = true;
                    this.updateUI(true);
                    this.attachStream(existingStream);
                } else {
                    console.log('🖥️ [Screen] Stream exists but no active tracks, waiting for new stream');
                }
            }
        }
        
        // Listen for future stream events
        window.addEventListener('screen-stream-received', (event) => {
            console.log('🖥️ [Screen] Stream event received!', event);
            
            // Only attach if we're actually streaming (prevents attaching when panel is closed)
            if (this.isStreaming) {
                console.log('🖥️ [Screen] Attaching stream (isStreaming=true)');
                this.attachStream(event.detail.stream);
            } else {
                console.log('🖥️ [Screen] Ignoring stream (isStreaming=false, panel might be closed)');
            }
        });
        console.log('✅ [Screen] Stream listener ready');
    }

    attachStream(stream) {
        console.log('🖥️ [Screen] Attaching stream to video element');
        
        // Validate stream has active video tracks
        const videoTracks = stream.getVideoTracks();
        if (videoTracks.length === 0) {
            console.warn('⚠️ [Screen] Stream has no video tracks, skipping attach');
            return;
        }
        
        const activeTrack = videoTracks.find(track => track.readyState === 'live' && track.enabled);
        if (!activeTrack) {
            console.warn('⚠️ [Screen] No active video tracks in stream, skipping attach');
            return;
        }
        
        console.log('✅ [Screen] Stream has active video track:', activeTrack.label);
        
        this.stream = stream;
        const streamDiv = this.container.querySelector('#screen-stream');
        
        // Remove placeholder
        const placeholder = streamDiv.querySelector('.stream-placeholder');
        if (placeholder) {
            placeholder.remove();
        }
        
        // Always recreate video element for fresh attachment
        if (this.videoElement && this.videoElement.parentNode) {
            console.log('🖥️ [Screen] Removing old video element');
            this.videoElement.srcObject = null;
            this.videoElement.parentNode.removeChild(this.videoElement);
            this.videoElement = null;
        }
        
        console.log('🖥️ [Screen] Creating new video element');
        this.videoElement = document.createElement('video');
        this.videoElement.id = 'screen-video';
        this.videoElement.className = 'video-screen';
        this.videoElement.autoplay = true;
        this.videoElement.playsInline = true;
        this.videoElement.style.cssText = 'width: 100%; height: 100%; object-fit: contain; background: #000; position: absolute; top: 0; left: 0;';
        
        // Insert video before controls
        const controls = streamDiv.querySelector('.stream-controls');
        if (controls) {
            streamDiv.insertBefore(this.videoElement, controls);
        } else {
            streamDiv.appendChild(this.videoElement);
        }
        
        // Attach stream to video element
        console.log('🖥️ [Screen] Setting srcObject on video element');
        this.videoElement.srcObject = stream;
        
        this.videoElement.onloadedmetadata = () => {
            this.videoElement.play().catch(err => {
                console.error('❌ [Screen] Failed to play video:', err);
            });
            console.log('✅ [Screen] Video playing');
        };
    }

    listenForConfirmations() {
        // Listen for confirmation messages from child device via custom events
        window.addEventListener('screen-confirmation', (event) => {
            const message = event.detail.message;
            console.log('🖥️ [Screen] Received confirmation event:', message);
            
            if (message === 'SCREEN_RECORDING_STARTED') {
                console.log('🖥️ [Screen] Screen recording started confirmed');
                this.clearConfirmationTimeout();
                // isStreaming already set to true in startScreen()
                this.updateUI(true);
                this.hideLoading();
                
                // Start Opus audio player for system audio
                if (this.opusAudioPlayer) {
                    this.opusAudioPlayer.start();
                    console.log('� [Screen] System audio playback started');
                }
                
                // NOTE: Stream should already be attached via screen-stream-received event
                // which fires when track arrives (before this confirmation)
                console.log('🖥️ [Screen] Start confirmed, stream should be attached');
            } else if (message === 'SCREEN_RECORDING_STOPPED') {
                console.log('🖥️ [Screen] Screen recording stopped confirmed');
                this.clearConfirmationTimeout();
                this.isStreaming = false;
                this.updateUI(false);
                this.hideLoading();
                
                // Stop Opus audio player
                if (this.opusAudioPlayer) {
                    this.opusAudioPlayer.stop();
                    console.log('🔊 [Screen] System audio playback stopped');
                }
                
                // Restore placeholder
                this.restorePlaceholder();
            } else if (message === 'SCREEN_RECORDING_PERMISSION_DENIED') {
                console.warn('⚠️ [Screen] Permission denied');
                this.clearConfirmationTimeout();
                this.isStreaming = false; // Reset on error
                this.hideLoading();
                this.showError('Screen recording permission denied');
            } else if (message.startsWith('SCREEN_RECORDING_ERROR')) {
                console.error('❌ [Screen] Error:', message);
                this.clearConfirmationTimeout();
                this.isStreaming = false; // Reset on error
                this.hideLoading();
                this.showError('Screen recording error');
            }
        });
    }

    clearConfirmationTimeout() {
        if (this.confirmationTimeout) {
            clearTimeout(this.confirmationTimeout);
            this.confirmationTimeout = null;
            console.log('✅ [Screen] Confirmation timeout cleared');
        }
    }

    listenForAudioPackets() {
        // Listen for Opus audio packets from DataChannel
        window.addEventListener('screen-audio-packet', (event) => {
            const { base64Data } = event.detail;
            
            // Play Opus audio packet
            if (this.opusAudioPlayer && this.isStreaming) {
                this.opusAudioPlayer.playPacket(base64Data);
            }
        });
        
        console.log('✅ [Screen] Audio packet listener ready');
    }

    showLoading() {
        const playPauseBtn = this.container.querySelector('#screen-play-pause-btn');
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
        const playPauseBtn = this.container.querySelector('#screen-play-pause-btn');
        if (playPauseBtn) {
            playPauseBtn.disabled = false;
            
            // Restore the play/pause icons based on current state
            if (this.isStreaming) {
                // Show pause icon (screen is active)
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
                // Show play icon (screen is inactive)
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

    async toggleScreen() {
        if (this.isStreaming) {
            await this.stopScreen();
        } else {
            await this.startScreen();
        }
    }

    async startScreen() {
        console.log('🖥️ [Screen] Starting screen recording');

        try {
            // Check if already streaming (prevent duplicate commands)
            if (this.isStreaming) {
                console.log('⚠️ [Screen] Already streaming, ignoring start command');
                this.showNotification('⚠️ Screen recording already active', 'warning');
                return;
            }

            // Get connection manager from app
            const app = window.nexusApp;
            if (!app || !app.connectionManager) {
                throw new Error('Connection manager not available');
            }

            // Clear any existing timeout
            this.clearConfirmationTimeout();

            // Show loading spinner
            this.showLoading();
            
            // CRITICAL: Set isStreaming BEFORE sending command
            // Track arrives via ontrack BEFORE confirmation message
            // So we need isStreaming=true to accept the stream when it arrives
            this.isStreaming = true;

            // Send SCREEN_RECORDING_ON command to child device
            app.connectionManager.sendCommand('SCREEN_RECORDING_ON');

            console.log('✅ [Screen] Screen recording start command sent, waiting for confirmation...');

            // Set timeout in case confirmation never arrives
            this.confirmationTimeout = setTimeout(() => {
                if (this.container.querySelector('.loading-spinner')) {
                    console.warn('⚠️ [Screen] Confirmation timeout');
                    this.isStreaming = false; // Reset on timeout
                    this.hideLoading();
                    this.showError('Screen recording start timeout. Please try again.');
                }
            }, 30000); // 30 seconds

        } catch (error) {
            console.error('❌ [Screen] Failed to start screen recording:', error);
            this.isStreaming = false; // Reset on error
            this.hideLoading();
            this.showError('Failed to start screen recording: ' + error.message);
        }
    }

    async stopScreen() {
        console.log('🖥️ [Screen] Stopping screen recording');

        try {
            // Check if not streaming (prevent duplicate commands)
            if (!this.isStreaming) {
                console.log('⚠️ [Screen] Not streaming, ignoring stop command');
                this.showNotification('⚠️ Screen recording not active', 'warning');
                return;
            }

            // Get connection manager from app
            const app = window.nexusApp;
            if (!app || !app.connectionManager) {
                throw new Error('Connection manager not available');
            }

            // Clear any existing timeout
            this.clearConfirmationTimeout();

            // Show loading spinner
            this.showLoading();

            // Send SCREEN_RECORDING_OFF command to child device
            app.connectionManager.sendCommand('SCREEN_RECORDING_OFF');

            console.log('✅ [Screen] Screen recording stop command sent, waiting for confirmation...');

            // Set timeout in case confirmation never arrives
            this.confirmationTimeout = setTimeout(() => {
                if (this.container.querySelector('.loading-spinner')) {
                    console.warn('⚠️ [Screen] Confirmation timeout');
                    this.hideLoading();
                    this.showError('Screen recording stop timeout. Please try again.');
                }
            }, 30000); // 30 seconds

            // Clear video but keep the element for reuse
            if (this.videoElement) {
                console.log('🖥️ [Screen] Clearing video srcObject');
                this.videoElement.srcObject = null;
                this.videoElement.pause();
            }

            this.stream = null;

        } catch (error) {
            console.error('❌ [Screen] Failed to stop screen recording:', error);
            this.hideLoading();
            this.showError('Failed to stop screen recording: ' + error.message);
        }
    }

    updateUI(isActive) {
        const indicator = this.container.querySelector('.status-indicator');
        const playIcon = this.container.querySelector('.play-icon');
        const pauseIcon = this.container.querySelector('.pause-icon');

        if (indicator) {
            if (isActive) {
                indicator.classList.add('active');
            } else {
                indicator.classList.remove('active');
            }
        }

        if (playIcon && pauseIcon) {
            if (isActive) {
                playIcon.style.display = 'none';
                pauseIcon.style.display = 'block';
            } else {
                playIcon.style.display = 'block';
                pauseIcon.style.display = 'none';
            }
        }
    }

    showError(message) {
        const streamDiv = this.container.querySelector('#screen-stream');
        if (streamDiv) {
            streamDiv.innerHTML = `
                <div class="stream-placeholder">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="opacity: 0.3; width: 64px; height: 64px;">
                        <circle cx="12" cy="12" r="10"></circle>
                        <line x1="15" y1="9" x2="9" y2="15"></line>
                        <line x1="9" y1="9" x2="15" y2="15"></line>
                    </svg>
                    <p style="color: #ef4444;">${message}</p>
                </div>
            `;
        }
    }

    restorePlaceholder() {
        console.log('🖥️ [Screen] Restoring placeholder');
        const streamDiv = this.container.querySelector('#screen-stream');
        if (streamDiv) {
            // Remove video element if it exists
            if (this.videoElement && this.videoElement.parentNode) {
                this.videoElement.parentNode.removeChild(this.videoElement);
            }
            
            // Check if placeholder already exists
            const existingPlaceholder = streamDiv.querySelector('.stream-placeholder');
            if (!existingPlaceholder) {
                // Create placeholder
                const placeholder = document.createElement('div');
                placeholder.className = 'stream-placeholder';
                placeholder.innerHTML = `
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="opacity: 0.3;">
                        <rect x="2" y="3" width="20" height="14" rx="2" ry="2"></rect>
                        <line x1="8" y1="21" x2="16" y2="21"></line>
                        <line x1="12" y1="17" x2="12" y2="21"></line>
                    </svg>
                    <p>Click play button to start screen recording</p>
                `;
                
                // Insert before controls
                const controls = streamDiv.querySelector('.stream-controls');
                if (controls) {
                    streamDiv.insertBefore(placeholder, controls);
                } else {
                    streamDiv.appendChild(placeholder);
                }
            }
        }
    }

    async startRecording(type) {
        console.log('🖥️ [Screen] Start recording requested, type:', type);

        if (!this.isStreaming) {
            this.showNotification('⚠️ Start screen recording before recording', 'warning');
            return;
        }

        const app = window.nexusApp;
        if (!app || !app.connectionManager || !app.connectionManager.webrtcManager) {
            this.showNotification('❌ Connection not available', 'error');
            return;
        }

        try {
            let recordingInput;
            let recordingType;

            if (type === 'video') {
                // Video only (screen stream without system audio)
                const screenStream = app.connectionManager.webrtcManager.getStream('screen');
                if (!screenStream || screenStream.getVideoTracks().length === 0) {
                    this.showNotification('⚠️ Screen stream not available', 'warning');
                    return;
                }
                recordingInput = screenStream;
                recordingType = 'video';
            } else if (type === 'both') {
                // Video & System Audio (screen stream + Opus decoded audio)
                const screenStream = app.connectionManager.webrtcManager.getStream('screen');

                if (!screenStream || screenStream.getVideoTracks().length === 0) {
                    this.showNotification('⚠️ Screen stream not available', 'warning');
                    return;
                }

                // Get audio stream from Web Audio API (Opus decoded audio)
                const audioStream = await this.getAudioStreamFromWebAudio();
                
                if (!audioStream) {
                    this.showNotification('⚠️ System audio not available', 'warning');
                    recordingInput = screenStream;
                    recordingType = 'video';
                } else {
                    // Combine video and audio streams
                    recordingInput = {
                        videoStream: screenStream,
                        audioStream: audioStream
                    };
                    recordingType = 'both';
                }
            }

            // Start recording
            const success = await this.recordingManager.start(recordingInput, recordingType);

            if (success) {
                this.updateRecordingUI(true);
                this.showNotification(`⏺️ Recording screen ${type === 'both' ? '& system audio' : 'only'}`, 'success');

                // Setup callbacks
                this.recordingManager.onTick((ms) => {
                    this.updateTimer(ms);
                });

                this.recordingManager.onStop((filePath) => {
                    this.updateRecordingUI(false);
                    this.showNotification(`💾 Saved: ${filePath}`, 'success');
                });

                this.recordingManager.onError((error) => {
                    this.updateRecordingUI(false);
                    this.showNotification('❌ Recording error', 'error');
                    console.error('Recording error:', error);
                });
            }
        } catch (error) {
            console.error('❌ [Screen] Failed to start recording:', error);
            this.showNotification('❌ Failed to start recording', 'error');
        }
    }

    stopRecording() {
        console.log('🖥️ [Screen] Stop recording requested');
        this.recordingManager.stop();
    }

    /**
     * Get audio stream from Web Audio API (Opus decoded audio)
     * Creates MediaStream from the gain node output
     * 
     * @returns {MediaStream|null} - Audio stream or null if not available
     */
    async getAudioStreamFromWebAudio() {
        try {
            if (!this.opusAudioPlayer || !this.opusAudioPlayer.audioContext || !this.opusAudioPlayer.gainNode) {
                console.warn('⚠️ [Screen] Opus audio player not available');
                return null;
            }

            // Create MediaStreamDestination to capture Web Audio API output
            const destination = this.opusAudioPlayer.audioContext.createMediaStreamDestination();
            
            // Connect gain node to destination (this taps the audio without interrupting playback)
            this.opusAudioPlayer.gainNode.connect(destination);
            
            console.log('✅ [Screen] Created audio stream from Web Audio API');
            
            return destination.stream;
            
        } catch (error) {
            console.error('❌ [Screen] Failed to create audio stream from Web Audio API:', error);
            return null;
        }
    }

    updateRecordingUI(recording) {
        const recordBtn = this.container.querySelector('#screen-record-btn');
        const timer = this.container.querySelector('#screen-record-timer');

        if (recordBtn) {
            if (recording) {
                recordBtn.classList.add('recording');
                recordBtn.title = 'Stop Recording';
            } else {
                recordBtn.classList.remove('recording');
                recordBtn.title = 'Record';
            }
        }

        if (timer) {
            if (recording) {
                timer.classList.add('show');
                timer.textContent = '00:00';
            } else {
                timer.classList.remove('show');
            }
        }
    }

    updateTimer(ms) {
        const timer = this.container.querySelector('#screen-record-timer');
        if (timer) {
            const seconds = Math.floor(ms / 1000);
            const minutes = Math.floor(seconds / 60);
            const hours = Math.floor(minutes / 60);
            
            const ss = String(seconds % 60).padStart(2, '0');
            const mm = String(minutes % 60).padStart(2, '0');
            
            if (hours > 0) {
                timer.textContent = `${hours}:${mm}:${ss}`;
            } else {
                timer.textContent = `${mm}:${ss}`;
            }
        }
    }

    showNotification(message, type = 'info') {
        // Use app notification system if available
        const app = window.nexusApp;
        if (app && app.showNotification) {
            app.showNotification(message, type);
        } else {
            // Show toast notification in panel
            this.showToast(message, type);
            console.log(`[${type.toUpperCase()}] ${message}`);
        }
    }

    showToast(message, type = 'info') {
        // Remove existing toast if any
        const existingToast = this.container.querySelector('.toast-notification');
        if (existingToast) {
            existingToast.remove();
        }

        // Create toast element
        const toast = document.createElement('div');
        toast.className = `toast-notification toast-${type}`;
        toast.innerHTML = `
            <div class="toast-content">
                <svg class="toast-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    ${type === 'error' ? `
                        <circle cx="12" cy="12" r="10"></circle>
                        <line x1="15" y1="9" x2="9" y2="15"></line>
                        <line x1="9" y1="9" x2="15" y2="15"></line>
                    ` : type === 'warning' ? `
                        <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path>
                        <line x1="12" y1="9" x2="12" y2="13"></line>
                        <line x1="12" y1="17" x2="12.01" y2="17"></line>
                    ` : `
                        <circle cx="12" cy="12" r="10"></circle>
                        <line x1="12" y1="16" x2="12" y2="12"></line>
                        <line x1="12" y1="8" x2="12.01" y2="8"></line>
                    `}
                </svg>
                <span class="toast-message">${message}</span>
            </div>
        `;

        // Add to container
        this.container.appendChild(toast);

        // Trigger animation
        setTimeout(() => toast.classList.add('show'), 10);

        // Auto remove after 4 seconds
        setTimeout(() => {
            toast.classList.remove('show');
            setTimeout(() => toast.remove(), 300);
        }, 4000);
    }

    toggleFullscreen() {
        console.log('🖥️ [Screen] Fullscreen toggle requested');
        
        const streamDiv = this.container.querySelector('#screen-stream');
        
        if (!document.fullscreenElement) {
            // Enter fullscreen
            if (streamDiv.requestFullscreen) {
                streamDiv.requestFullscreen();
            } else if (streamDiv.webkitRequestFullscreen) {
                streamDiv.webkitRequestFullscreen();
            } else if (streamDiv.msRequestFullscreen) {
                streamDiv.msRequestFullscreen();
            }
            console.log('✅ [Screen] Entered fullscreen mode');
        } else {
            // Exit fullscreen
            if (document.exitFullscreen) {
                document.exitFullscreen();
            } else if (document.webkitExitFullscreen) {
                document.webkitExitFullscreen();
            } else if (document.msExitFullscreen) {
                document.msExitFullscreen();
            }
            console.log('✅ [Screen] Exited fullscreen mode');
        }
    }

    destroy() {
        if (this.isStreaming) {
            this.stopScreen();
        }

        if (this.videoElement) {
            this.videoElement.srcObject = null;
            this.videoElement = null;
        }

        // Cleanup Opus audio player
        if (this.opusAudioPlayer) {
            this.opusAudioPlayer.destroy();
            this.opusAudioPlayer = null;
        }

        this.stream = null;
    }
}

// Export to window for dynamic loading
window.ScreenFeature = ScreenFeature;
