/**
 * Microphone Feature Module
 * Uses native WebRTC audio streaming with beautiful visualizer
 */

class MicFeature {
    constructor(container) {
        this.container = container;
        this.isStreaming = false;
        this.audioElement = null;
        this.stream = null;
        this.confirmationTimeout = null;
        this.audioContext = null;
        this.analyser = null;
        this.animationId = null;
        this.parentAudioController = null;
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

        // Listen for mic stream
        this.listenForStream();

        // Listen for confirmation messages from child
        this.listenForConfirmations();
    }

    async loadTemplate() {
        try {
            const response = await fetch('js/features/mic/mic.html');
            return await response.text();
        } catch (error) {
            console.error('Failed to load mic template:', error);
            return '<div>Failed to load mic feature</div>';
        }
    }

    async loadStyles() {
        try {
            const link = document.createElement('link');
            link.rel = 'stylesheet';
            link.href = 'js/features/mic/mic.css';
            document.head.appendChild(link);
        } catch (error) {
            console.error('Failed to load mic styles:', error);
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
        const recordBtn = this.container.querySelector('#mic-record-btn');
        const recordMenu = this.container.querySelector('#mic-record-menu');
        const audioOnlyBtn = this.container.querySelector('#mic-audio-only-btn');
        const bothBtn = this.container.querySelector('#mic-both-btn');
        const playPauseBtn = this.container.querySelector('#mic-play-pause-btn');
        const parentAudioBtn = this.container.querySelector('#parent-audio-mute-btn');

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

        // Audio only recording
        if (audioOnlyBtn) {
            audioOnlyBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                if (recordMenu) recordMenu.classList.remove('show');
                this.startRecording('audio');
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

        // Mic on/off control (visualizer overlay button)
        if (playPauseBtn) {
            playPauseBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.toggleMic();
            });
        }

        // Parent audio mute/unmute control (bottom left button)
        if (parentAudioBtn) {
            parentAudioBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.toggleParentAudio();
            });
        }

        // Initialize parent audio controller
        this.initializeParentAudioController();
    }

    listenForStream() {
        // Listen for mic stream from WebRTC
        console.log('🎤 [Mic] Setting up stream listener...');
        
        // Check if stream already exists (arrived before window opened)
        const app = window.nexusApp;
        if (app && app.connectionManager && app.connectionManager.webrtcManager) {
            const existingStream = app.connectionManager.webrtcManager.getStream('mic');
            if (existingStream) {
                console.log('🎤 [Mic] Found existing stream, attaching immediately');
                this.attachStream(existingStream);
                return;
            }
        }
        
        // Listen for future stream events
        window.addEventListener('mic-stream-received', (event) => {
            console.log('🎤 [Mic] Stream event received!', event);
            this.attachStream(event.detail.stream);
        });
        console.log('✅ [Mic] Stream listener ready');
    }

    listenForConfirmations() {
        // Listen for confirmation messages from child device via custom events
        window.addEventListener('mic-confirmation', (event) => {
            const message = event.detail.message;
            console.log('🎤 [Mic] Received confirmation event:', message);
            
            if (message === 'MIC_STARTED') {
                console.log('🎤 [Mic] Mic started confirmed');
                this.clearConfirmationTimeout();
                this.isStreaming = true;
                this.updateUI(true);
                this.hideLoading();
            } else if (message === 'MIC_STOPPED') {
                console.log('🎤 [Mic] Mic stopped confirmed');
                this.clearConfirmationTimeout();
                this.isStreaming = false;
                this.updateUI(false);
                this.hideLoading();
                this.stopVisualizer();
            }
        });
    }

    clearConfirmationTimeout() {
        if (this.confirmationTimeout) {
            clearTimeout(this.confirmationTimeout);
            this.confirmationTimeout = null;
            console.log('✅ [Mic] Confirmation timeout cleared');
        }
    }

    showLoading() {
        const playPauseBtn = this.container.querySelector('#mic-play-pause-btn');
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
        const playPauseBtn = this.container.querySelector('#mic-play-pause-btn');
        if (playPauseBtn) {
            playPauseBtn.disabled = false;
            
            // Restore the play/pause icons based on current state
            if (this.isStreaming) {
                // Show pause icon (mic is active)
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
                // Show play icon (mic is inactive)
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

    attachStream(stream) {
        console.log('🎤 [Mic] Attaching stream to audio element');
        
        this.stream = stream;
        const visualizerDiv = this.container.querySelector('#mic-visualizer');
        
        // Remove placeholder
        const placeholder = visualizerDiv.querySelector('.visualizer-placeholder');
        if (placeholder) {
            placeholder.remove();
        }
        
        // Create audio element if not exists
        if (!this.audioElement) {
            this.audioElement = document.createElement('audio');
            this.audioElement.id = 'mic-audio';
            this.audioElement.autoplay = true;
            this.audioElement.style.display = 'none'; // Hidden audio element
            visualizerDiv.appendChild(this.audioElement);
        }
        
        // Attach stream to audio element
        this.audioElement.srcObject = stream;
        
        this.audioElement.onloadedmetadata = () => {
            this.audioElement.play().catch(err => {
                console.warn('🎤 [Mic] Audio autoplay blocked:', err);
                // User gesture required - will play when user interacts
            });
            console.log('✅ [Mic] Audio playing');
        };

        // Start visualizer
        this.startVisualizer(stream);
    }

    startVisualizer(stream) {
        console.log('🎨 [Mic] Starting audio visualizer');
        
        const visualizerDiv = this.container.querySelector('#mic-visualizer');
        
        // Create canvas for visualizer
        let canvas = visualizerDiv.querySelector('#visualizer-canvas');
        if (!canvas) {
            canvas = document.createElement('canvas');
            canvas.id = 'visualizer-canvas';
            canvas.style.cssText = 'width: 100%; height: 100%; position: absolute; top: 0; left: 0;';
            visualizerDiv.insertBefore(canvas, visualizerDiv.firstChild);
        }

        // Setup audio context and analyser
        try {
            this.audioContext = new (window.AudioContext || window.webkitAudioContext)();
            this.analyser = this.audioContext.createAnalyser();
            this.analyser.fftSize = 256;
            
            const source = this.audioContext.createMediaStreamSource(stream);
            source.connect(this.analyser);
            
            // Start animation
            this.animateVisualizer(canvas);
            
            console.log('✅ [Mic] Visualizer started');
        } catch (error) {
            console.error('❌ [Mic] Failed to start visualizer:', error);
        }
    }

    animateVisualizer(canvas) {
            const ctx = canvas.getContext('2d');
            const bufferLength = this.analyser.frequencyBinCount;
            const dataArray = new Uint8Array(bufferLength);

            // Set canvas size
            canvas.width = canvas.offsetWidth;
            canvas.height = canvas.offsetHeight;

            const centerX = canvas.width / 2;
            const centerY = canvas.height / 2;
            const radius = Math.min(centerX, centerY) * 0.6;

            const draw = () => {
                this.animationId = requestAnimationFrame(draw);

                this.analyser.getByteFrequencyData(dataArray);

                // Clear canvas with dark background
                ctx.fillStyle = 'rgba(0, 0, 0, 0.15)';
                ctx.fillRect(0, 0, canvas.width, canvas.height);

                // Calculate average volume for center circle
                let sum = 0;
                for (let i = 0; i < bufferLength; i++) {
                    sum += dataArray[i];
                }
                const average = sum / bufferLength;
                const normalizedAverage = average / 255;

                // Draw outer glow ring
                const glowRadius = radius + (normalizedAverage * 40);
                const glowGradient = ctx.createRadialGradient(centerX, centerY, radius * 0.8, centerX, centerY, glowRadius);
                glowGradient.addColorStop(0, 'rgba(251, 191, 36, 0)');
                glowGradient.addColorStop(0.5, 'rgba(251, 191, 36, 0.1)');
                glowGradient.addColorStop(1, 'rgba(251, 191, 36, 0)');
                ctx.fillStyle = glowGradient;
                ctx.beginPath();
                ctx.arc(centerX, centerY, glowRadius, 0, Math.PI * 2);
                ctx.fill();

                // Draw circular frequency bars
                const barCount = 64; // Number of bars around the circle
                const angleStep = (Math.PI * 2) / barCount;

                for (let i = 0; i < barCount; i++) {
                    const dataIndex = Math.floor((i / barCount) * bufferLength);
                    const value = dataArray[dataIndex] / 255;
                    const barHeight = value * radius * 0.8;

                    const angle = i * angleStep - Math.PI / 2;
                    const x1 = centerX + Math.cos(angle) * radius;
                    const y1 = centerY + Math.sin(angle) * radius;
                    const x2 = centerX + Math.cos(angle) * (radius + barHeight);
                    const y2 = centerY + Math.sin(angle) * (radius + barHeight);

                    // Create gradient for each bar
                    const gradient = ctx.createLinearGradient(x1, y1, x2, y2);
                    gradient.addColorStop(0, `rgba(251, 191, 36, ${0.3 + value * 0.7})`);
                    gradient.addColorStop(1, `rgba(239, 68, 68, ${0.5 + value * 0.5})`);

                    ctx.strokeStyle = gradient;
                    ctx.lineWidth = 3;
                    ctx.lineCap = 'round';
                    ctx.shadowBlur = 8;
                    ctx.shadowColor = '#fbbf24';

                    ctx.beginPath();
                    ctx.moveTo(x1, y1);
                    ctx.lineTo(x2, y2);
                    ctx.stroke();
                }

                // Draw center circle with pulsing effect
                const pulseRadius = radius * 0.4 + (normalizedAverage * 20);
                const centerGradient = ctx.createRadialGradient(centerX, centerY, 0, centerX, centerY, pulseRadius);
                centerGradient.addColorStop(0, 'rgba(251, 191, 36, 0.8)');
                centerGradient.addColorStop(0.5, 'rgba(251, 191, 36, 0.4)');
                centerGradient.addColorStop(1, 'rgba(251, 191, 36, 0.1)');

                ctx.fillStyle = centerGradient;
                ctx.shadowBlur = 20;
                ctx.shadowColor = '#fbbf24';
                ctx.beginPath();
                ctx.arc(centerX, centerY, pulseRadius, 0, Math.PI * 2);
                ctx.fill();

                // Draw inner circle border
                ctx.strokeStyle = 'rgba(251, 191, 36, 0.6)';
                ctx.lineWidth = 2;
                ctx.shadowBlur = 10;
                ctx.beginPath();
                ctx.arc(centerX, centerY, radius, 0, Math.PI * 2);
                ctx.stroke();

                // Draw waveform as smooth curve
                this.analyser.getByteTimeDomainData(dataArray);

                ctx.strokeStyle = 'rgba(34, 211, 238, 0.6)';
                ctx.lineWidth = 2;
                ctx.shadowBlur = 15;
                ctx.shadowColor = '#22d3ee';
                ctx.beginPath();

                const waveRadius = radius * 0.7;
                for (let i = 0; i < bufferLength; i++) {
                    const angle = (i / bufferLength) * Math.PI * 2 - Math.PI / 2;
                    const value = (dataArray[i] / 128.0 - 1) * 0.3;
                    const r = waveRadius + (value * radius * 0.2);
                    const x = centerX + Math.cos(angle) * r;
                    const y = centerY + Math.sin(angle) * r;

                    if (i === 0) {
                        ctx.moveTo(x, y);
                    } else {
                        ctx.lineTo(x, y);
                    }
                }

                ctx.closePath();
                ctx.stroke();

                // Draw volume level text in center
                ctx.shadowBlur = 0;
                ctx.fillStyle = 'rgba(255, 255, 255, 0.9)';
                ctx.font = 'bold 14px Arial';
                ctx.textAlign = 'center';
                ctx.textBaseline = 'middle';
                const volumePercent = Math.round(normalizedAverage * 100);
                ctx.fillText(`${volumePercent}%`, centerX, centerY);
            };

            draw();
        }


    stopVisualizer() {
        if (this.animationId) {
            cancelAnimationFrame(this.animationId);
            this.animationId = null;
        }
        
        if (this.audioContext) {
            this.audioContext.close();
            this.audioContext = null;
        }
        
        // Clear canvas
        const canvas = this.container.querySelector('#visualizer-canvas');
        if (canvas) {
            const ctx = canvas.getContext('2d');
            ctx.clearRect(0, 0, canvas.width, canvas.height);
        }
        
        console.log('🛑 [Mic] Visualizer stopped');
    }

    async toggleMic() {
        if (this.isStreaming) {
            await this.stopMic();
        } else {
            await this.startMic();
        }
    }

    async startMic() {
        console.log('🎤 [Mic] Starting microphone');

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

            // Send MIC_ON command to child device
            app.connectionManager.sendCommand('MIC_ON');

            console.log('✅ [Mic] Mic start command sent, waiting for confirmation...');

            // Set timeout in case confirmation never arrives
            this.confirmationTimeout = setTimeout(() => {
                if (this.container.querySelector('.loading-spinner')) {
                    console.warn('⚠️ [Mic] Confirmation timeout');
                    this.hideLoading();
                    this.showError('Mic start timeout. Please try again.');
                }
            }, 30000); // 30 seconds

        } catch (error) {
            console.error('❌ [Mic] Failed to start mic:', error);
            this.hideLoading();
            this.showError('Failed to start mic: ' + error.message);
        }
    }

    async stopMic() {
        console.log('🎤 [Mic] Stopping microphone');

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

            // Send MIC_OFF command to child device
            app.connectionManager.sendCommand('MIC_OFF');

            console.log('✅ [Mic] Mic stop command sent, waiting for confirmation...');

            // Set timeout in case confirmation never arrives
            this.confirmationTimeout = setTimeout(() => {
                if (this.container.querySelector('.loading-spinner')) {
                    console.warn('⚠️ [Mic] Confirmation timeout');
                    this.hideLoading();
                    this.showError('Mic stop timeout. Please try again.');
                }
            }, 30000); // 30 seconds

            // Clear audio
            if (this.audioElement) {
                this.audioElement.srcObject = null;
            }

            this.stream = null;

        } catch (error) {
            console.error('❌ [Mic] Failed to stop mic:', error);
            this.hideLoading();
            this.showError('Failed to stop mic: ' + error.message);
        }
    }

    updateUI(isActive) {
        const indicator = this.container.querySelector('.status-indicator');

        if (indicator) {
            if (isActive) {
                indicator.classList.add('active');
            } else {
                indicator.classList.remove('active');
            }
        }
    }

    showError(message) {
        const visualizerDiv = this.container.querySelector('#mic-visualizer');
        if (visualizerDiv) {
            visualizerDiv.innerHTML = `
                <div class="visualizer-placeholder">
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

    async startRecording(type) {
        console.log('🎤 [Mic] Start recording requested, type:', type);

        if (!this.isStreaming) {
            this.showNotification('⚠️ Start microphone before recording', 'warning');
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

            if (type === 'audio') {
                // Audio only
                const micStream = app.connectionManager.webrtcManager.getStream('mic');
                if (!micStream || micStream.getAudioTracks().length === 0) {
                    this.showNotification('⚠️ Microphone stream not available', 'warning');
                    return;
                }
                recordingInput = micStream;
                recordingType = 'audio';
            } else if (type === 'both') {
                // Video & Audio
                const micStream = app.connectionManager.webrtcManager.getStream('mic');
                const cameraStream = app.connectionManager.webrtcManager.getStream('camera');

                if (!micStream || micStream.getAudioTracks().length === 0) {
                    this.showNotification('⚠️ Microphone stream not available', 'warning');
                    return;
                }

                // Check if camera stream exists
                if (!cameraStream || cameraStream.getVideoTracks().length === 0) {
                    // Check if camera panel is open
                    const cameraWindow = document.querySelector('.feature-window[data-feature="camera"]');
                    
                    if (!cameraWindow) {
                        // Camera panel is not open - open it
                        this.showNotification('⚠️ Opening Camera panel... Please start video and try again', 'warning');
                        const cameraBtn = document.querySelector('.sidebar-btn[data-feature="camera"]');
                        if (cameraBtn) {
                            cameraBtn.click();
                        }
                    } else {
                        // Camera panel is open but video not started
                        this.showNotification('⚠️ Please start camera video first (click play button in Camera panel)', 'warning');
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
                this.showNotification(`⏺️ Recording ${type === 'audio' ? 'audio' : 'video & audio'}`, 'success');

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
            console.error('❌ [Mic] Failed to start recording:', error);
            this.showNotification('❌ Failed to start recording', 'error');
        }
    }

    stopRecording() {
        console.log('🎤 [Mic] Stop recording requested');
        this.recordingManager.stop();
    }

    updateRecordingUI(recording) {
        const recordBtn = this.container.querySelector('#mic-record-btn');
        const timer = this.container.querySelector('#mic-record-timer');

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
        const timer = this.container.querySelector('#mic-record-timer');
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

    initializeParentAudioController() {
        // Get WebRTC manager from app
        const app = window.nexusApp;
        if (app && app.connectionManager && app.connectionManager.webrtcManager) {
            this.parentAudioController = new ParentAudioController(app.connectionManager.webrtcManager);
            console.log('✅ [Mic] Parent audio controller initialized');
        } else {
            console.warn('⚠️ [Mic] WebRTC manager not available, parent audio disabled');
        }
    }

    async toggleParentAudio() {
        if (!this.parentAudioController) {
            console.error('❌ [Mic] Parent audio controller not available');
            this.showError('Parent audio not available. Please connect to a device first.');
            return;
        }

        try {
            const parentAudioBtn = this.container.querySelector('#parent-audio-mute-btn');
            const parentAudioIcon = this.container.querySelector('#parent-audio-icon');
            const pulseRing = this.container.querySelector('#parent-audio-pulse');

            // Show loading state
            if (parentAudioBtn) {
                parentAudioBtn.disabled = true;
            }

            // Toggle mute state
            await this.parentAudioController.toggleMute();

            // Update UI based on new state
            const isMuted = this.parentAudioController.getIsMuted();
            
            if (parentAudioBtn) {
                parentAudioBtn.disabled = false;
                
                if (isMuted) {
                    // Muted state (red button)
                    parentAudioBtn.classList.remove('active');
                    parentAudioBtn.title = 'Speak to child (Muted)';
                    
                    // Muted icon (microphone)
                    if (parentAudioIcon) {
                        parentAudioIcon.innerHTML = `
                            <path d="M12 2C10.34 2 9 3.34 9 5V11C9 12.66 10.34 14 12 14C13.66 14 15 12.66 15 11V5C15 3.34 13.66 2 12 2Z"/>
                            <path d="M19 11C19 14.53 16.39 17.44 13 17.93V21H11V17.93C7.61 17.44 5 14.53 5 11H7C7 13.76 9.24 16 12 16C14.76 16 17 13.76 17 11H19Z"/>
                        `;
                    }
                    
                    // Stop pulse animation
                    if (pulseRing) {
                        pulseRing.classList.remove('active');
                    }
                } else {
                    // Unmuted state (green button)
                    parentAudioBtn.classList.add('active');
                    parentAudioBtn.title = 'Stop speaking (Unmuted)';
                    
                    // Unmuted icon (microphone with slash)
                    if (parentAudioIcon) {
                        parentAudioIcon.innerHTML = `
                            <path d="M12 2C10.34 2 9 3.34 9 5V11C9 12.66 10.34 14 12 14C13.66 14 15 12.66 15 11V5C15 3.34 13.66 2 12 2Z"/>
                            <path d="M19 11C19 14.53 16.39 17.44 13 17.93V21H11V17.93C7.61 17.44 5 14.53 5 11H7C7 13.76 9.24 16 12 16C14.76 16 17 13.76 17 11H19Z"/>
                            <line x1="4" y1="4" x2="20" y2="20" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                        `;
                    }
                    
                    // Start pulse animation
                    if (pulseRing) {
                        pulseRing.classList.add('active');
                    }
                }
            }

            console.log(`✅ [Mic] Parent audio ${isMuted ? 'muted' : 'unmuted'}`);

        } catch (error) {
            console.error('❌ [Mic] Failed to toggle parent audio:', error);
            
            // Re-enable button
            const parentAudioBtn = this.container.querySelector('#parent-audio-mute-btn');
            if (parentAudioBtn) {
                parentAudioBtn.disabled = false;
            }
            
            // Show error message
            if (error.message.includes('Permission denied') || error.message.includes('NotAllowedError')) {
                this.showError('Microphone permission denied. Please allow microphone access in your browser settings.');
            } else {
                this.showError('Failed to access microphone: ' + error.message);
            }
        }
    }

    destroy() {
        if (this.isStreaming) {
            this.stopMic();
        }

        this.stopVisualizer();

        if (this.audioElement) {
            this.audioElement.srcObject = null;
            this.audioElement = null;
        }

        // Cleanup parent audio controller
        if (this.parentAudioController) {
            this.parentAudioController.cleanup();
            this.parentAudioController = null;
        }

        this.stream = null;
    }
}

// Export to window for dynamic loading
window.MicFeature = MicFeature;
