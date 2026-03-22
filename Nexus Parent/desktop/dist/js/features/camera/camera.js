/**
 * Camera Feature Module
 * Uses native WebRTC video streaming with Electron
 */

class CameraFeature {
    constructor(container) {
        this.container = container;
        this.isStreaming = false;
        this.videoElement = null;
        this.stream = null;
        this.confirmationTimeout = null; // Track timeout
        this.init();
    }

    async init() {
        // Load HTML template
        const html = await this.loadTemplate();
        this.container.innerHTML = html;

        // Load CSS
        await this.loadStyles();

        // Initialize recording manager
        this.initRecordingManager();

        // Setup event listeners
        this.setupEventListeners();

        // Listen for camera stream
        this.listenForStream();

        // Listen for confirmation messages from child
        this.listenForConfirmations();
    }

    async loadTemplate() {
        try {
            const response = await fetch('js/features/camera/camera.html');
            return await response.text();
        } catch (error) {
            console.error('Failed to load camera template:', error);
            return '<div>Failed to load camera feature</div>';
        }
    }

    async loadStyles() {
        try {
            const link = document.createElement('link');
            link.rel = 'stylesheet';
            link.href = 'js/features/camera/camera.css';
            document.head.appendChild(link);
        } catch (error) {
            console.error('Failed to load camera styles:', error);
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
        const recordBtn = this.container.querySelector('#camera-record-btn');
        const recordMenu = this.container.querySelector('#camera-record-menu');
        const videoOnlyBtn = this.container.querySelector('#camera-video-only-btn');
        const bothBtn = this.container.querySelector('#camera-both-btn');
        const playPauseBtn = this.container.querySelector('#camera-play-pause-btn');
        const switchBtn = this.container.querySelector('#camera-switch-btn');
        const fullscreenBtn = this.container.querySelector('#camera-fullscreen-btn');

        // Recording button - toggle menu or stop recording
        if (recordBtn) {
            recordBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                
                if (this.recordingManager && this.recordingManager.isRecording()) {
                    this.stopRecording();
                } else if (recordMenu) {
                    recordMenu.classList.toggle('show');
                }
            });
        }

        // Close menu when clicking outside
        document.addEventListener('click', (e) => {
            const recordWrap = this.container.querySelector('.record-wrap');
            if (recordMenu && recordWrap && !recordWrap.contains(e.target)) {
                recordMenu.classList.remove('show');
            }
        });

        // Video only recording
        if (videoOnlyBtn) {
            videoOnlyBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                if (recordMenu) recordMenu.classList.remove('show');
                this.startRecording('video');
            });
        }

        // Video & Audio recording
        if (bothBtn) {
            bothBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                if (recordMenu) recordMenu.classList.remove('show');
                this.startRecording('both');
            });
        }

        // Camera on/off control (stream overlay button)
        if (playPauseBtn) {
            playPauseBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.toggleCamera();
            });
        }

        // Switch camera control (stream overlay button)
        if (switchBtn) {
            switchBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.switchCamera();
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
        // Listen for camera stream from WebRTC
        console.log('📹 [Camera] Setting up stream listener...');
        
        // Check if stream already exists AND camera is streaming (arrived before window opened)
        const app = window.nexusApp;
        if (app && app.connectionManager && app.connectionManager.webrtcManager) {
            const existingStream = app.connectionManager.webrtcManager.getStream('camera');
            if (existingStream && this.isStreaming) {
                console.log('📹 [Camera] Found existing stream and camera is streaming, attaching immediately');
                this.attachStream(existingStream);
                return;
            }
        }
        
        // Listen for future stream events
        window.addEventListener('camera-stream-received', (event) => {
            console.log('📹 [Camera] Stream event received!', event);
            // Only attach if we're actually streaming
            if (this.isStreaming) {
                this.attachStream(event.detail.stream);
            }
        });
        console.log('✅ [Camera] Stream listener ready');
    }

    attachStream(stream) {
        console.log('📹 [Camera] Attaching stream to video element');
        
        this.stream = stream;
        const streamDiv = this.container.querySelector('#camera-stream');
        
        // Remove placeholder
        const placeholder = streamDiv.querySelector('.stream-placeholder');
        if (placeholder) {
            placeholder.remove();
        }
        
        // Create video element if not exists OR if it was removed
        if (!this.videoElement || !this.videoElement.parentNode) {
            console.log('📹 [Camera] Creating new video element');
            this.videoElement = document.createElement('video');
            this.videoElement.id = 'camera-video';
            this.videoElement.className = 'video-camera'; // Add class for WebRTC manager
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
        }
        
        // Always attach stream to video element (even if video element already exists)
        console.log('📹 [Camera] Setting srcObject on video element');
        this.videoElement.srcObject = stream;
        
        this.videoElement.onloadedmetadata = () => {
            this.videoElement.play().catch(err => {
                console.error('❌ [Camera] Failed to play video:', err);
            });
            console.log('✅ [Camera] Video playing');
        };
    }

    listenForConfirmations() {
        // Listen for confirmation messages from child device via custom events
        window.addEventListener('camera-confirmation', (event) => {
            const message = event.detail.message;
            console.log('📹 [Camera] Received confirmation event:', message);
            
            if (message === 'CAMERA_STARTED') {
                console.log('📹 [Camera] Camera started confirmed');
                this.clearConfirmationTimeout();
                this.isStreaming = true;
                this.updateUI(true);
                this.hideLoading();
                
                // Check if we have a stream and reattach if needed
                const app = window.nexusApp;
                if (app && app.connectionManager && app.connectionManager.webrtcManager) {
                    const existingStream = app.connectionManager.webrtcManager.getStream('camera');
                    if (existingStream) {
                        console.log('📹 [Camera] Reattaching existing stream after start confirmation');
                        this.attachStream(existingStream);
                    }
                }
            } else if (message === 'CAMERA_STOPPED') {
                console.log('📹 [Camera] Camera stopped confirmed');
                this.clearConfirmationTimeout();
                this.isStreaming = false;
                this.updateUI(false);
                this.hideLoading();
                
                // Restore placeholder
                this.restorePlaceholder();
            } else if (message === 'CAMERA_SWITCHED') {
                console.log('📹 [Camera] Camera switched confirmed');
                this.clearConfirmationTimeout();
                this.hideLoading();
            }
        });
    }

    clearConfirmationTimeout() {
        if (this.confirmationTimeout) {
            clearTimeout(this.confirmationTimeout);
            this.confirmationTimeout = null;
            console.log('✅ [Camera] Confirmation timeout cleared');
        }
    }

    showLoading() {
        const playPauseBtn = this.container.querySelector('#camera-play-pause-btn');
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
        const playPauseBtn = this.container.querySelector('#camera-play-pause-btn');
        if (playPauseBtn) {
            playPauseBtn.disabled = false;
            
            // Restore the play/pause icons based on current state
            if (this.isStreaming) {
                // Show pause icon (camera is active)
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
                // Show play icon (camera is inactive)
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

    async toggleCamera() {
        if (this.isStreaming) {
            await this.stopCamera();
        } else {
            await this.startCamera();
        }
    }

    async startCamera() {
        console.log('📹 [Camera] Starting camera');

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

            // Send CAMERA_ON command to child device
            app.connectionManager.sendCommand('CAMERA_ON');

            console.log('✅ [Camera] Camera start command sent, waiting for confirmation...');

            // Set timeout in case confirmation never arrives
            this.confirmationTimeout = setTimeout(() => {
                if (this.container.querySelector('.loading-spinner')) {
                    console.warn('⚠️ [Camera] Confirmation timeout');
                    this.hideLoading();
                    this.showError('Camera start timeout. Please try again.');
                }
            }, 30000); // 30 seconds

        } catch (error) {
            console.error('❌ [Camera] Failed to start camera:', error);
            this.hideLoading();
            this.showError('Failed to start camera: ' + error.message);
        }
    }

    async stopCamera() {
        console.log('📹 [Camera] Stopping camera');

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

            // Send CAMERA_OFF command to child device
            app.connectionManager.sendCommand('CAMERA_OFF');

            console.log('✅ [Camera] Camera stop command sent, waiting for confirmation...');

            // Set timeout in case confirmation never arrives
            this.confirmationTimeout = setTimeout(() => {
                if (this.container.querySelector('.loading-spinner')) {
                    console.warn('⚠️ [Camera] Confirmation timeout');
                    this.hideLoading();
                    this.showError('Camera stop timeout. Please try again.');
                }
            }, 30000); // 30 seconds

            // Clear video but keep the element for reuse
            if (this.videoElement) {
                console.log('📹 [Camera] Clearing video srcObject');
                this.videoElement.srcObject = null;
                this.videoElement.pause();
            }

            this.stream = null;

        } catch (error) {
            console.error('❌ [Camera] Failed to stop camera:', error);
            this.hideLoading();
            this.showError('Failed to stop camera: ' + error.message);
        }
    }

    async switchCamera() {
        console.log('📹 [Camera] Switching camera');

        try {
            // Get connection manager from app
            const app = window.nexusApp;
            if (!app || !app.connectionManager) {
                throw new Error('Connection manager not available');
            }

            // Show loading spinner on switch button
            const switchBtn = this.container.querySelector('#camera-switch-btn');
            if (switchBtn) {
                const originalHTML = switchBtn.innerHTML;
                switchBtn.innerHTML = `
                    <svg viewBox="0 0 24 24" fill="currentColor">
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
                switchBtn.disabled = true;

                // Restore after timeout
                setTimeout(() => {
                    switchBtn.innerHTML = originalHTML;
                    switchBtn.disabled = false;
                }, 2000);
            }

            // Send CAMERA_SWITCH command to child device
            app.connectionManager.sendCommand('CAMERA_SWITCH');
            console.log('✅ [Camera] Camera switch command sent');

        } catch (error) {
            console.error('❌ [Camera] Failed to switch camera:', error);
            this.showError('Failed to switch camera: ' + error.message);
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
        const streamDiv = this.container.querySelector('#camera-stream');
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
        console.log('📹 [Camera] Restoring placeholder');
        const streamDiv = this.container.querySelector('#camera-stream');
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
                        <polygon points="23 7 16 12 23 17 23 7"></polygon>
                        <rect x="1" y="5" width="15" height="14" rx="2" ry="2"></rect>
                    </svg>
                    <p>Click play button to start camera</p>
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
        console.log('📹 [Camera] Start recording requested, type:', type);

        if (!this.isStreaming) {
            this.showNotification('⚠️ Start camera before recording', 'warning');
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
                // Video only
                const cameraStream = app.connectionManager.webrtcManager.getStream('camera');
                if (!cameraStream || cameraStream.getVideoTracks().length === 0) {
                    this.showNotification('⚠️ Camera stream not available', 'warning');
                    return;
                }
                recordingInput = cameraStream;
                recordingType = 'video';
            } else if (type === 'both') {
                // Video & Audio
                const cameraStream = app.connectionManager.webrtcManager.getStream('camera');
                const micStream = app.connectionManager.webrtcManager.getStream('mic');

                if (!cameraStream || cameraStream.getVideoTracks().length === 0) {
                    this.showNotification('⚠️ Camera stream not available', 'warning');
                    return;
                }

                // Check if mic stream exists
                if (!micStream || micStream.getAudioTracks().length === 0) {
                    // Check if mic panel is open
                    const micWindow = document.querySelector('.feature-window[data-feature="mic"]');
                    
                    if (!micWindow) {
                        // Mic panel is not open - open it
                        this.showNotification('⚠️ Opening Mic panel... Please start audio and try again', 'warning');
                        const micBtn = document.querySelector('.sidebar-btn[data-feature="mic"]');
                        if (micBtn) {
                            micBtn.click();
                        }
                    } else {
                        // Mic panel is open but audio not started
                        this.showNotification('⚠️ Please start microphone audio first (click play button in Mic panel)', 'warning');
                    }
                    return;
                }

                recordingInput = {
                    videoStream: cameraStream,
                    audioStream: micStream
                };
                recordingType = 'both';
            }

            // Start recording
            const success = await this.recordingManager.start(recordingInput, recordingType);

            if (success) {
                this.updateRecordingUI(true);
                this.showNotification(`⏺️ Recording ${type === 'video' ? 'video' : 'video & audio'}`, 'success');

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
            console.error('❌ [Camera] Failed to start recording:', error);
            this.showNotification('❌ Failed to start recording', 'error');
        }
    }

    stopRecording() {
        console.log('📹 [Camera] Stop recording requested');
        this.recordingManager.stop();
    }

    updateRecordingUI(recording) {
        const recordBtn = this.container.querySelector('#camera-record-btn');
        const timer = this.container.querySelector('#camera-record-timer');

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
        const timer = this.container.querySelector('#camera-record-timer');
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
        console.log('📹 [Camera] Fullscreen toggle requested');
        
        const streamDiv = this.container.querySelector('#camera-stream');
        
        if (!document.fullscreenElement) {
            // Enter fullscreen
            if (streamDiv.requestFullscreen) {
                streamDiv.requestFullscreen();
            } else if (streamDiv.webkitRequestFullscreen) {
                streamDiv.webkitRequestFullscreen();
            } else if (streamDiv.msRequestFullscreen) {
                streamDiv.msRequestFullscreen();
            }
            console.log('✅ [Camera] Entered fullscreen mode');
        } else {
            // Exit fullscreen
            if (document.exitFullscreen) {
                document.exitFullscreen();
            } else if (document.webkitExitFullscreen) {
                document.webkitExitFullscreen();
            } else if (document.msExitFullscreen) {
                document.msExitFullscreen();
            }
            console.log('✅ [Camera] Exited fullscreen mode');
        }
    }

    destroy() {
        if (this.isStreaming) {
            this.stopCamera();
        }

        if (this.videoElement) {
            this.videoElement.srcObject = null;
            this.videoElement = null;
        }

        this.stream = null;
    }
}

// Export to window for dynamic loading
window.CameraFeature = CameraFeature;
