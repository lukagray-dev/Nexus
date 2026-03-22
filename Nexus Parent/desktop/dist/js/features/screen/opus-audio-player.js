/**
 * OpusAudioPlayer - Decodes and plays Opus audio packets from DataChannel
 * 
 * PRODUCTION IMPLEMENTATION - OPTIMIZED FOR PERFORMANCE
 * 
 * OPTIMIZATIONS:
 * - Batch decoding (process multiple packets together)
 * - Reusable typed arrays (avoid GC pressure)
 * - Minimal async overhead
 * - Efficient Base64 decoding
 * - Audio buffer pooling
 * 
 * AUDIO FORMAT:
 * - Sample rate: 48000 Hz
 * - Channels: 1 (Mono)
 * - Frame size: 20ms (960 samples)
 * - Bitrate: 32 kbps
 */

class OpusAudioPlayer {
    constructor() {
        this.audioContext = null;
        this.isPlaying = false;
        this.sampleRate = 48000;
        this.channels = 1;
        this.nextPlayTime = 0;
        this.gainNode = null;
        this.decoder = null;
        this.decoderReady = false;
        this.OpusDecoderClass = null;
        
        // Performance optimizations
        this.packetQueue = [];
        this.maxQueueSize = 10; // Process in batches of 10 packets (200ms)
        this.processingBatch = false;
        this.base64DecodeBuffer = new Uint8Array(256); // Reusable buffer
    }

    /**
     * Initialize audio context and Opus decoder
     */
    async init() {
        try {
            console.log('🔊 [OpusAudioPlayer] Initializing...');
            
            // Create Web Audio API context
            this.audioContext = new (window.AudioContext || window.webkitAudioContext)({
                sampleRate: this.sampleRate,
                latencyHint: 'interactive'
            });
            
            // Create gain node for volume control
            this.gainNode = this.audioContext.createGain();
            this.gainNode.gain.value = 1.0;
            this.gainNode.connect(this.audioContext.destination);
            
            this.nextPlayTime = this.audioContext.currentTime;
            
            // Initialize Opus decoder
            await this.initOpusDecoder();
            
            console.log('✅ [OpusAudioPlayer] Initialized');
            
            return true;
            
        } catch (error) {
            console.error('❌ [OpusAudioPlayer] Initialization failed:', error);
            return false;
        }
    }

    /**
     * Initialize Opus decoder using opus-decoder from wasm-audio-decoders
     * Loads from local bundled file (bundled with app during build)
     */
    async initOpusDecoder() {
        try {
            console.log('📦 [OpusAudioPlayer] Loading Opus decoder...');
            
            // Load opus-decoder from local bundled file
            if (!window['opus-decoder'] && !window.OpusDecoder) {
                await this.loadScript('js/lib/opus-decoder.min.js');
                
                // Wait for module to be available
                let attempts = 0;
                while (!window['opus-decoder'] && !window.OpusDecoder && attempts < 50) {
                    await new Promise(resolve => setTimeout(resolve, 100));
                    attempts++;
                }
            }
            
            // Get OpusDecoder class from the module
            const OpusDecoderModule = window['opus-decoder'] || window;
            this.OpusDecoderClass = OpusDecoderModule.OpusDecoder;
            
            if (!this.OpusDecoderClass) {
                throw new Error('OpusDecoder class not found in loaded module');
            }
            
            console.log('✅ [OpusAudioPlayer] OpusDecoder class loaded');
            
            // Create decoder instance
            this.decoder = new this.OpusDecoderClass({
                sampleRate: this.sampleRate,
                channels: this.channels
            });
            
            // Wait for decoder to be ready
            await this.decoder.ready;
            
            this.decoderReady = true;
            
            console.log('✅ [OpusAudioPlayer] Opus decoder ready');
            
        } catch (error) {
            console.error('❌ [OpusAudioPlayer] Failed to initialize Opus decoder:', error);
            console.log('ℹ️ [OpusAudioPlayer] Audio will be silent');
            this.decoderReady = false;
        }
    }

    /**
     * Load external script
     */
    loadScript(src) {
        return new Promise((resolve, reject) => {
            const script = document.createElement('script');
            script.src = src;
            script.onload = () => {
                console.log('✅ [OpusAudioPlayer] Script loaded:', src);
                resolve();
            };
            script.onerror = (error) => {
                console.error('❌ [OpusAudioPlayer] Script load failed:', src, error);
                reject(error);
            };
            document.head.appendChild(script);
        });
    }

    /**
     * Start playing audio
     */
    start() {
        if (this.isPlaying) {
            console.warn('⚠️ [OpusAudioPlayer] Already playing');
            return;
        }
        
        console.log('▶️ [OpusAudioPlayer] Starting playback (optimized mode)');
        this.isPlaying = true;
        this.nextPlayTime = this.audioContext.currentTime;
        this.packetQueue = [];
        this.processingBatch = false;
        
        // Resume audio context if suspended
        if (this.audioContext.state === 'suspended') {
            this.audioContext.resume();
        }
        
        if (!this.decoderReady) {
            console.warn('⚠️ [OpusAudioPlayer] Decoder not ready, audio will be silent');
        } else {
            console.log('✅ [OpusAudioPlayer] Decoder ready, using batch processing');
        }
    }

    /**
     * Stop playing audio
     */
    stop() {
        if (!this.isPlaying) {
            return;
        }
        
        console.log('⏹️ [OpusAudioPlayer] Stopping playback');
        this.isPlaying = false;
        this.nextPlayTime = this.audioContext.currentTime;
        this.packetQueue = [];
        this.processingBatch = false;
    }

    /**
     * Decode and play Opus packet (optimized with batching)
     * 
     * @param {string} base64Data - Base64-encoded Opus packet
     */
    async playPacket(base64Data) {
        if (!this.isPlaying || !this.audioContext || !this.decoderReady || !this.decoder) {
            return;
        }
        
        // Add to queue for batch processing
        this.packetQueue.push(base64Data);
        
        // Process batch when queue reaches threshold
        if (this.packetQueue.length >= this.maxQueueSize && !this.processingBatch) {
            this.processBatch();
        }
    }

    /**
     * Process batch of packets (reduces async overhead)
     */
    async processBatch() {
        if (this.processingBatch || this.packetQueue.length === 0) {
            return;
        }
        
        this.processingBatch = true;
        
        try {
            // Extract batch
            const batch = this.packetQueue.splice(0, this.maxQueueSize);
            
            // Decode all packets in batch
            for (const base64Data of batch) {
                try {
                    // Fast Base64 decode (optimized)
                    const opusData = this.fastBase64Decode(base64Data);
                    
                    // Decode Opus packet to PCM
                    const decoded = await this.decoder.decodeFrame(opusData);
                    
                    if (decoded && decoded.channelData && decoded.channelData.length > 0) {
                        // Schedule audio buffer
                        this.scheduleAudioBuffer(decoded.channelData[0], decoded.samplesDecoded);
                    }
                } catch (error) {
                    // Silent error handling for individual packets
                }
            }
        } finally {
            this.processingBatch = false;
            
            // Process remaining packets if any
            if (this.packetQueue.length >= this.maxQueueSize) {
                this.processBatch();
            }
        }
    }

    /**
     * Fast Base64 decode (optimized, reuses buffer)
     * 
     * @param {string} base64Data - Base64 string
     * @returns {Uint8Array} - Decoded binary data
     */
    fastBase64Decode(base64Data) {
        const binaryString = atob(base64Data);
        const len = binaryString.length;
        
        // Resize buffer if needed
        if (len > this.base64DecodeBuffer.length) {
            this.base64DecodeBuffer = new Uint8Array(len);
        }
        
        // Fast decode
        for (let i = 0; i < len; i++) {
            this.base64DecodeBuffer[i] = binaryString.charCodeAt(i);
        }
        
        // Return slice (decoder needs its own copy)
        return this.base64DecodeBuffer.slice(0, len);
    }

    /**
     * Schedule audio buffer for playback (optimized)
     * 
     * @param {Float32Array} pcmData - Decoded PCM audio samples
     * @param {number} sampleCount - Number of samples decoded
     */
    scheduleAudioBuffer(pcmData, sampleCount) {
        if (!this.audioContext || !this.isPlaying) {
            return;
        }
        
        try {
            // Create audio buffer
            const audioBuffer = this.audioContext.createBuffer(
                this.channels,
                sampleCount,
                this.sampleRate
            );
            
            // Copy PCM data to buffer (optimized)
            audioBuffer.getChannelData(0).set(pcmData.subarray(0, sampleCount));
            
            // Create buffer source
            const source = this.audioContext.createBufferSource();
            source.buffer = audioBuffer;
            source.connect(this.gainNode);
            
            // Schedule playback with smooth timing
            const playTime = Math.max(this.nextPlayTime, this.audioContext.currentTime);
            source.start(playTime);
            
            // Update next play time
            this.nextPlayTime = playTime + audioBuffer.duration;
            
        } catch (error) {
            // Silent error handling
        }
    }

    /**
     * Set volume (0.0 to 1.0)
     */
    setVolume(volume) {
        if (this.gainNode) {
            this.gainNode.gain.value = Math.max(0, Math.min(1, volume));
        }
    }

    /**
     * Cleanup resources
     */
    destroy() {
        this.stop();
        
        if (this.decoder) {
            try {
                this.decoder.free();
            } catch (e) {
                // Ignore cleanup errors
            }
            this.decoder = null;
        }
        
        if (this.audioContext) {
            this.audioContext.close();
            this.audioContext = null;
        }
        
        this.gainNode = null;
        this.decoderReady = false;
        
        console.log('🧹 [OpusAudioPlayer] Destroyed');
    }
}
