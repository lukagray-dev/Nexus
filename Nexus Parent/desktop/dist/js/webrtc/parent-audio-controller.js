/**
 * Parent Audio Controller - Handles bidirectional audio via WebRTC
 * Allows parent to speak to child device with full volume playback
 */

class ParentAudioController {
  constructor(webrtcManager) {
    this.webrtcManager = webrtcManager;
    this.isMuted = true; // Parent starts muted
    this.localAudioStream = null;
    this.audioTrack = null;
    this.rtpSender = null;
    this.isInitialized = false;
  }

  /**
   * Initialize parent audio - get microphone access but keep muted
   */
  async initialize() {
    if (this.isInitialized) {
      console.log('⚠️ Parent audio already initialized');
      return true;
    }

    try {
      console.log('🎤 Initializing parent audio...');
      
      // Get microphone access
      this.localAudioStream = await navigator.mediaDevices.getUserMedia({
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
          sampleRate: 48000
        }
      });

      this.audioTrack = this.localAudioStream.getAudioTracks()[0];
      
      // Start muted
      this.audioTrack.enabled = false;
      
      // Add track to peer connection
      const pc = this.webrtcManager.peerConnection;
      if (pc) {
        // Find the audio transceiver and change its direction to sendrecv
        const transceivers = pc.getTransceivers();
        let audioTransceiver = null;
        
        for (const transceiver of transceivers) {
          if (transceiver.receiver.track.kind === 'audio') {
            audioTransceiver = transceiver;
            break;
          }
        }
        
        if (audioTransceiver) {
          console.log('🔄 Found existing audio transceiver, changing direction to sendrecv');
          audioTransceiver.direction = 'sendrecv';
          
          // Replace the sender's track with our microphone track
          if (audioTransceiver.sender) {
            await audioTransceiver.sender.replaceTrack(this.audioTrack);
            this.rtpSender = audioTransceiver.sender;
            console.log('✅ Replaced audio track in existing transceiver');
          }
        } else {
          // No audio transceiver exists, add a new one
          console.log('➕ No audio transceiver found, adding new one');
          this.rtpSender = pc.addTrack(this.audioTrack, this.localAudioStream);
        }
        
        console.log('✅ Parent audio track configured (muted)');
        
        // Trigger renegotiation to update the connection
        console.log('🔄 Triggering renegotiation...');
        await this.webrtcManager.renegotiate();
        console.log('✅ Renegotiation complete');
        
        this.isInitialized = true;
      }

      return true;
    } catch (error) {
      console.error('❌ Failed to initialize parent audio:', error);
      return false;
    }
  }

  /**
   * Unmute parent audio - child will hear parent
   */
  async unmute() {
    if (!this.isInitialized) {
      console.warn('⚠️ Parent audio not initialized, initializing now...');
      const success = await this.initialize();
      if (!success) {
        throw new Error('Failed to initialize parent audio');
      }
    }

    if (!this.isMuted) {
      console.warn('⚠️ Parent audio already unmuted');
      return;
    }

    try {
      // Enable audio track
      if (this.audioTrack) {
        this.audioTrack.enabled = true;
        this.isMuted = false;
        
        console.log('🔊 Audio track enabled:', {
          id: this.audioTrack.id,
          label: this.audioTrack.label,
          enabled: this.audioTrack.enabled,
          readyState: this.audioTrack.readyState,
          muted: this.audioTrack.muted
        });
        
        // Verify the sender is sending
        if (this.rtpSender) {
          const params = this.rtpSender.getParameters();
          console.log('📡 RTP Sender parameters:', params);
          
          // Check transceiver direction
          const pc = this.webrtcManager.peerConnection;
          const transceivers = pc.getTransceivers();
          for (const transceiver of transceivers) {
            if (transceiver.sender === this.rtpSender) {
              console.log('📡 Audio transceiver direction:', transceiver.direction);
              console.log('📡 Audio transceiver mid:', transceiver.mid);
            }
          }
        }
        
        // Send command to child to play audio at full volume
        if (this.webrtcManager.dataChannel && this.webrtcManager.dataChannel.readyState === 'open') {
          this.webrtcManager.sendCommand('PARENT_AUDIO_UNMUTE');
          console.log('📤 Sent PARENT_AUDIO_UNMUTE command to child');
        } else {
          console.error('❌ Data channel not ready');
        }
        
        console.log('🔊 Parent audio unmuted');
      }
    } catch (error) {
      console.error('❌ Failed to unmute parent audio:', error);
      throw error;
    }
  }

  /**
   * Mute parent audio - child won't hear parent
   */
  async mute() {
    if (!this.isInitialized) {
      console.warn('⚠️ Parent audio not initialized');
      return;
    }

    if (this.isMuted) {
      console.warn('⚠️ Parent audio already muted');
      return;
    }

    try {
      // Disable audio track
      if (this.audioTrack) {
        this.audioTrack.enabled = false;
        this.isMuted = true;
        
        // Send command to child to restore normal volume
        if (this.webrtcManager.dataChannel && this.webrtcManager.dataChannel.readyState === 'open') {
          this.webrtcManager.sendCommand('PARENT_AUDIO_MUTE');
        }
        
        console.log('🔇 Parent audio muted');
      }
    } catch (error) {
      console.error('❌ Failed to mute parent audio:', error);
      throw error;
    }
  }

  /**
   * Toggle mute/unmute
   */
  async toggleMute() {
    if (this.isMuted) {
      await this.unmute();
    } else {
      await this.mute();
    }
  }

  /**
   * Get current mute state
   */
  getIsMuted() {
    return this.isMuted;
  }

  /**
   * Get initialization state
   */
  getIsInitialized() {
    return this.isInitialized;
  }

  /**
   * Cleanup resources
   */
  cleanup() {
    try {
      // Remove track from peer connection
      if (this.rtpSender && this.webrtcManager.peerConnection) {
        this.webrtcManager.peerConnection.removeTrack(this.rtpSender);
        console.log('✅ Parent audio track removed from peer connection');
      }

      // Stop local stream
      if (this.localAudioStream) {
        this.localAudioStream.getTracks().forEach(track => track.stop());
        this.localAudioStream = null;
      }

      this.audioTrack = null;
      this.rtpSender = null;
      this.isMuted = true;
      this.isInitialized = false;

      console.log('🛑 Parent audio controller cleaned up');
    } catch (error) {
      console.error('❌ Error cleaning up parent audio:', error);
    }
  }
}

// Export to window
window.ParentAudioController = ParentAudioController;
