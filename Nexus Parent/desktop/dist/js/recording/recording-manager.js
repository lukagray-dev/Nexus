/**
 * Recording Manager
 * Handles video and audio recording from WebRTC streams
 */

class RecordingManager {
    constructor() {
        this.mediaRecorder = null;
        this.recordedChunks = [];
        this.recording = false;
        this.startTime = null;
        this.timerInterval = null;
        this.onStopCallbacks = [];
        this.onErrorCallbacks = [];
        this.onTickCallbacks = [];
        this.filePath = null;
    }

    /**
     * Get supported MIME type for recording
     */
    getSupportedMimeType() {
        const candidates = [
            'video/webm;codecs=vp8,opus',
            'video/webm;codecs=vp9,opus',
            'video/webm;codecs=vp9',
            'video/webm;codecs=vp8',
            'video/webm',
        ];
        
        for (const type of candidates) {
            if (MediaRecorder.isTypeSupported(type)) {
                console.log('📹 [Recording] Using MIME type:', type);
                return type;
            }
        }
        
        console.warn('⚠️ [Recording] No preferred MIME type supported, using default');
        return '';
    }

    /**
     * Pick save location for recording
     */
    async pickSaveLocation(defaultFileName) {
        try {
            const result = await window.IPCBridge.showSaveDialog({
                title: 'Save Recording',
                defaultPath: defaultFileName,
                filters: [
                    { name: 'WebM Video', extensions: ['webm'] }
                ]
            });

            if (!result || result.canceled) {
                return null;
            }

            return result.filePath;
        } catch (error) {
            console.error('❌ [Recording] Failed to show save dialog:', error);
            return null;
        }
    }

    /**
     * Get default save location from settings
     */
    getDefaultSaveLocation(type) {
        const savedPath = localStorage.getItem(`nexus_download_${type}`);
        if (savedPath) {
            const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
            const fileName = `${type}-${timestamp}.webm`;
            return `${savedPath}\\${fileName}`;
        }
        return null;
    }

    /**
     * Start recording
     * @param {MediaStream|{videoStream?: MediaStream, audioStream?: MediaStream}} input
     * @param {string} type - 'video', 'audio', or 'both'
     */
    async start(input, type = 'video') {
        if (this.recording) {
            console.warn('⚠️ [Recording] Already recording');
            return false;
        }

        if (!input) {
            throw new Error('No stream to record');
        }

        // Reset state
        this.recordedChunks = [];
        this.onStopCallbacks = [];
        this.onErrorCallbacks = [];
        this.onTickCallbacks = [];

        // Get or pick save location
        const defaultPath = this.getDefaultSaveLocation(type);
        const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
        const defaultFileName = `${type}-${timestamp}.webm`;

        this.filePath = defaultPath || await this.pickSaveLocation(defaultFileName);
        
        if (!this.filePath) {
            console.log('📹 [Recording] User canceled save dialog');
            return false;
        }

        // Save directory for future use if not already saved
        if (!defaultPath) {
            const pathParts = this.filePath.split('\\');
            if (pathParts.length > 1) {
                pathParts.pop(); // Remove filename
                const directory = pathParts.join('\\');
                localStorage.setItem(`nexus_download_${type}`, directory);
                console.log('✅ [Recording] Saved default directory:', directory);
            }
        }

        // Build final stream
        let finalStream = null;
        
        if (input instanceof MediaStream) {
            // Single stream (video or audio only)
            finalStream = new MediaStream();
            input.getTracks().forEach(track => {
                if (track.readyState === 'live') {
                    finalStream.addTrack(track);
                }
            });
        } else if (input.videoStream || input.audioStream) {
            // Combined streams (video + audio)
            finalStream = new MediaStream();
            
            if (input.videoStream) {
                input.videoStream.getVideoTracks().forEach(track => {
                    if (track.readyState === 'live') {
                        finalStream.addTrack(track);
                    }
                });
            }
            
            if (input.audioStream) {
                input.audioStream.getAudioTracks().forEach(track => {
                    if (track.readyState === 'live') {
                        finalStream.addTrack(track);
                    }
                });
            }
        }

        if (!finalStream || finalStream.getTracks().length === 0) {
            this.emitError(new Error('No valid tracks to record'));
            return false;
        }

        // Create MediaRecorder
        const mimeType = this.getSupportedMimeType();
        
        try {
            this.mediaRecorder = new MediaRecorder(
                finalStream,
                mimeType ? { mimeType } : undefined
            );
        } catch (error) {
            this.emitError(error);
            return false;
        }

        // Handle data available
        this.mediaRecorder.ondataavailable = (event) => {
            if (event.data && event.data.size > 0) {
                this.recordedChunks.push(event.data);
            }
        };

        // Handle stop
        this.mediaRecorder.onstop = async () => {
            console.log('📹 [Recording] Stopped, saving file...');
            
            if (this.timerInterval) {
                clearInterval(this.timerInterval);
                this.timerInterval = null;
            }

            this.recording = false;

            // Save file
            try {
                await this.saveRecording();
                this.onStopCallbacks.forEach(cb => {
                    try {
                        cb(this.filePath);
                    } catch (e) {
                        console.error('Error in stop callback:', e);
                    }
                });
            } catch (error) {
                this.emitError(error);
            }
        };

        // Handle errors
        this.mediaRecorder.onerror = (event) => {
            console.error('❌ [Recording] MediaRecorder error:', event);
            this.emitError(event.error || new Error('Recording error'));
        };

        // Start recording
        try {
            this.mediaRecorder.start(1000); // Collect data every second
            this.recording = true;
            this.startTime = Date.now();

            // Start timer
            this.timerInterval = setInterval(() => {
                const elapsedMs = Date.now() - this.startTime;
                this.onTickCallbacks.forEach(cb => {
                    try {
                        cb(elapsedMs);
                    } catch (e) {
                        console.error('Error in tick callback:', e);
                    }
                });
            }, 1000);

            console.log('✅ [Recording] Started successfully');
            return true;
        } catch (error) {
            this.emitError(error);
            return false;
        }
    }

    /**
     * Save recording to file
     */
    async saveRecording() {
        if (this.recordedChunks.length === 0) {
            throw new Error('No data to save');
        }

        const blob = new Blob(this.recordedChunks, { type: 'video/webm' });
        const buffer = await blob.arrayBuffer();

        // Use Node.js fs to write file
        const fs = require('fs');
        
        return new Promise((resolve, reject) => {
            fs.writeFile(this.filePath, Buffer.from(buffer), (error) => {
                if (error) {
                    console.error('❌ [Recording] Failed to save file:', error);
                    reject(error);
                } else {
                    console.log('✅ [Recording] File saved:', this.filePath);
                    resolve();
                }
            });
        });
    }

    /**
     * Stop recording
     */
    stop() {
        if (this.mediaRecorder && this.recording) {
            try {
                this.mediaRecorder.stop();
            } catch (error) {
                console.error('❌ [Recording] Error stopping recorder:', error);
            }
        }
    }

    /**
     * Check if currently recording
     */
    isRecording() {
        return this.recording;
    }

    /**
     * Register callback for when recording stops
     */
    onStop(callback) {
        if (typeof callback === 'function') {
            this.onStopCallbacks.push(callback);
        }
    }

    /**
     * Register callback for recording errors
     */
    onError(callback) {
        if (typeof callback === 'function') {
            this.onErrorCallbacks.push(callback);
        }
    }

    /**
     * Register callback for timer ticks
     */
    onTick(callback) {
        if (typeof callback === 'function') {
            this.onTickCallbacks.push(callback);
        }
    }

    /**
     * Emit error to all error callbacks
     */
    emitError(error) {
        console.error('❌ [Recording] Error:', error);
        this.onErrorCallbacks.forEach(cb => {
            try {
                cb(error);
            } catch (e) {
                console.error('Error in error callback:', e);
            }
        });
    }
}

// Export to window
window.RecordingManager = RecordingManager;
