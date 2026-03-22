/**
 * WebRTC Manager - Main Orchestrator
 * Handles all WebRTC connections, signaling, and media streams
 */

class WebRTCManager {
  constructor() {
    this.peerConnection = null;
    this.dataChannel = null;
    this.videoStreams = {
      camera: null,
      screen: null,
      mic: null
    };
    this.currentChildId = null;
    this.firebaseDB = null;
    this.offerRef = null;
    this.answerRef = null;
    this.lastAnswerHash = null; // Track last processed answer to avoid duplicates
    this.isRenegotiating = false; // Prevent concurrent renegotiations
    this.isProcessingAnswer = false; // Prevent concurrent answer processing

    // Callbacks
    this.onConnectionEstablished = null;
    this.onDataChannelMessage = null;
    this.onStreamReceived = null;
  }

  /**
   * Initialize Firebase
   */
  initializeFirebase(config) {
    if (!firebase.apps.length) {
      firebase.initializeApp(config);
    }
    this.firebaseDB = firebase.database();
    console.log('✅ Firebase initialized');
  }

  /**
   * Connect to child device
   */
  async connect(childId) {
    console.log('🔗 Connecting to Child ID:', childId);
    this.currentChildId = childId;

    // Check if child device exists and is online
    const childStatusRef = this.firebaseDB.ref(`child-devices/${childId}/status`);
    const statusSnap = await childStatusRef.once('value');
    const status = statusSnap.val();

    if (!status) {
      throw new Error('Child device not found. Please check the ID.');
    }

    // Accept online, online-waiting, or connected status
    const validStatuses = ['online', 'online-waiting', 'connected'];
    if (!validStatuses.includes(status)) {
      throw new Error(`Child device is ${status}. Please ensure the child app is running and in foreground.`);
    }

    console.log(`✅ Child device status: ${status}`);

    // Create peer connection
    this.createPeerConnection();

    // Setup Firebase signaling
    this.setupFirebaseSignaling(childId);

    return { peerConnection: this.peerConnection, dataChannel: this.dataChannel };
  }

  /**
   * Create RTCPeerConnection
   */
  createPeerConnection() {
    const config = {
      iceServers: [
        { urls: 'stun:stun.l.google.com:19302' },
        { urls: 'stun:stun1.l.google.com:19302' }
      ]
    };

    this.peerConnection = new RTCPeerConnection(config);
    console.log('✅ RTCPeerConnection created');

    // Create data channel (parent creates it, child receives it)
    this.dataChannel = this.peerConnection.createDataChannel('nexus-control', {
      ordered: true
    });
    this.dataChannel.binaryType = 'arraybuffer';
    console.log('✅ DataChannel created:', this.dataChannel.label);

    // Setup data channel handlers
    this.dataChannel.onopen = () => {
      console.log('🚀 DataChannel is open');
      if (this.onConnectionEstablished) {
        this.onConnectionEstablished(this.dataChannel);
      }
    };

    this.dataChannel.onmessage = (e) => {
      // Silent logging for audio packets to prevent console flooding
      if (typeof e.data === 'string' && !e.data.startsWith('OPUS_AUDIO:')) {
        console.log('📩 From Child:', e.data);
      }
      if (this.onDataChannelMessage) {
        this.onDataChannelMessage(e.data);
      }
    };

    this.dataChannel.onclose = () => console.warn('🛑 DataChannel closed');
    this.dataChannel.onerror = (e) => console.error('❌ DataChannel error:', e);

    // Setup event handlers
    this.setupEventHandlers();
  }

  /**
   * Setup all event handlers
   */
  setupEventHandlers() {
    const pc = this.peerConnection;

    // Connection state monitoring
    pc.oniceconnectionstatechange = () => {
      console.log('🌐 ICE State:', pc.iceConnectionState);
    };

    pc.onconnectionstatechange = () => {
      console.log('🔌 Connection State:', pc.connectionState);
      if (pc.connectionState === 'connected') {
        console.log('✅ WebRTC connection established!');
      }
    };

    pc.onicegatheringstatechange = () => {
      console.log('📶 ICE Gathering State:', pc.iceGatheringState);
    };

    // Track handler (video/audio streams from child)
    pc.ontrack = (event) => {
      console.log('🎥 Track received:', event.track.kind);
      this.handleTrack(event);
    };
  }

  /**
   * Handle incoming track
   */
  handleTrack(event) {
    const track = event.track;
    const stream = event.streams?.[0] || new MediaStream([track]);

    console.log('🎥 Track received!');
    console.log('Track details:', {
      kind: track.kind,
      id: track.id,
      label: track.label,
      enabled: track.enabled,
      readyState: track.readyState,
      muted: track.muted
    });
    console.log('Stream details:', {
      id: stream.id,
      active: stream.active,
      videoTracks: stream.getVideoTracks().length,
      audioTracks: stream.getAudioTracks().length
    });

    if (track.kind === 'video') {
      this.handleVideoTrack(track, stream);
    } else if (track.kind === 'audio') {
      this.handleAudioTrack(track, stream);
    }
  }

  /**
   * Handle video track
   */
  handleVideoTrack(track, stream) {
    // Determine if it's camera or screen based on track label AND stream ID
    const trackLabel = track.label.toLowerCase();
    const streamId = stream.id.toLowerCase();
    
    // Check both track label and stream ID for screen identification
    const isScreen = trackLabel.includes('screen') || 
                     trackLabel.includes('display') ||
                     streamId.includes('screen');
    
    const streamType = isScreen ? 'screen' : 'camera';

    console.log(`📹 Video track identified as: ${streamType}`);
    console.log(`📹 Track label: "${track.label}", ID: ${track.id}, Stream ID: ${stream.id}`);

    // Store the stream
    this.videoStreams[streamType] = stream;
    
    // Listen for track ended event to clear stale streams
    track.onended = () => {
      console.log(`🛑 ${streamType} track ended, clearing stream`);
      this.videoStreams[streamType] = null;
    };

    // Dispatch event for features to listen
    console.log(`📡 Dispatching ${streamType}-stream-received event`);
    const event = new CustomEvent(`${streamType}-stream-received`, {
      detail: { stream }
    });
    window.dispatchEvent(event);

    // Call callback if provided
    if (this.onStreamReceived) {
      this.onStreamReceived(streamType, stream);
    }
  }

  /**
   * Handle audio track
   */
  handleAudioTrack(track, stream) {
    console.log('🎤 Audio track received');
    this.videoStreams.mic = stream;
    
    // Listen for track ended event to clear stale streams
    track.onended = () => {
      console.log('🛑 Mic track ended, clearing stream');
      this.videoStreams.mic = null;
    };

    // Dispatch event for mic feature to listen
    console.log('📡 Dispatching mic-stream-received event');
    const event = new CustomEvent('mic-stream-received', {
      detail: { stream }
    });
    window.dispatchEvent(event);

    if (this.onStreamReceived) {
      this.onStreamReceived('mic', stream);
    }
  }

  /**
   * Setup Firebase signaling
   */
  setupFirebaseSignaling(childId) {
    const rawChildId = childId.replace(/-/g, '');
    const basePath = `signaling/${rawChildId}`;

    this.offerRef = this.firebaseDB.ref(`${basePath}/offer`);
    this.answerRef = this.firebaseDB.ref(`${basePath}/answer`);
    this.iceCandidatesRef = this.firebaseDB.ref(`${basePath}/ice-candidates`);

    console.log('🔗 Firebase signaling path:', basePath);

    // Setup ICE candidate handler NOW (after Firebase refs are ready)
    this.peerConnection.onicecandidate = (event) => {
      if (event.candidate) {
        console.log('🧊 ICE candidate generated:', event.candidate.candidate.substring(0, 50) + '...');
        this.iceCandidatesRef.child('parent').push({
          candidate: event.candidate.candidate,
          sdpMid: event.candidate.sdpMid,
          sdpMLineIndex: event.candidate.sdpMLineIndex
        }).then(() => {
          console.log('✅ ICE candidate sent to Firebase');
        }).catch(err => {
          console.error('❌ Failed to send ICE candidate:', err);
        });
      } else {
        console.log('🧊 ICE gathering complete (null candidate)');
      }
    };

    // Listen for answer from child
    this.answerRef.on('value', async (snap) => {
      const answer = snap.val();
      if (!answer) {
        console.log('⏳ Waiting for answer from child...');
        return;
      }

      // Check if this is a duplicate answer (same SDP hash)
      const answerHash = answer.sdp ? answer.sdp.hashCode() : null;
      if (answerHash && answerHash === this.lastAnswerHash) {
        console.log('ℹ️ Ignoring duplicate answer (same SDP hash)');
        return;
      }

      // Prevent concurrent answer processing (race condition protection)
      if (this.isProcessingAnswer) {
        console.log('⚠️ Already processing an answer, skipping...');
        return;
      }

      this.isProcessingAnswer = true;

      console.log('📥 Answer received from child');
      console.log('Current signaling state:', this.peerConnection.signalingState);

      try {
        // Check if this is a renegotiation offer from child (type will be 'offer')
        if (answer.type === 'offer' && this.peerConnection.signalingState === 'stable') {
          console.log('🔄 Received renegotiation OFFER from child (child is temporary offerer)');
          
          // Set remote description (the offer from child)
          await this.peerConnection.setRemoteDescription(new RTCSessionDescription(answer));
          console.log('✅ Remote description set (child\'s offer)');
          
          // Create answer
          const ourAnswer = await this.peerConnection.createAnswer();
          await this.peerConnection.setLocalDescription(ourAnswer);
          console.log('✅ Created and set our answer');
          
          // Send answer back to child in the 'offer' path
          await this.offerRef.set({
            type: this.peerConnection.localDescription.type,
            sdp: this.peerConnection.localDescription.sdp
          });
          console.log('📤 Answer sent to Firebase (in offer path)');
          
          // Renegotiation complete
          this.isRenegotiating = false;
          
          // Send completion message to child
          if (this.dataChannel && this.dataChannel.readyState === 'open') {
            this.dataChannel.send('RENEGOTIATION_COMPLETE');
            console.log('📤 Sent RENEGOTIATION_COMPLETE to child');
          }
          
          this.lastAnswerHash = answerHash;
        } else {
          // Normal answer handling
          await this.handleAnswer(answer);
          this.lastAnswerHash = answerHash;
        }
      } catch (err) {
        console.error('❌ Error handling answer:', err);
      } finally {
        this.isProcessingAnswer = false;
      }
    });

    // Listen for ICE candidates from child
    this.iceCandidatesRef.child('child').on('child_added', async (snap) => {
      const candidate = snap.val();
      if (candidate && this.peerConnection) {
        try {
          await this.peerConnection.addIceCandidate(new RTCIceCandidate(candidate));
          console.log('✅ Added ICE candidate from child');
        } catch (err) {
          console.error('❌ Error adding ICE candidate:', err);
        }
      }
    });

    // Create and send offer
    this.createAndSendOffer();
  }

  /**
   * Create and send offer to child
   */
  async createAndSendOffer() {
    try {
      console.log('📝 Creating offer...');

      // Add transceiver for receiving video (tells child we want to receive video)
      this.peerConnection.addTransceiver('video', {
        direction: 'recvonly'
      });
      console.log('✅ Added video transceiver (recvonly)');

      // Add transceiver for receiving audio (tells child we want to receive audio)
      this.peerConnection.addTransceiver('audio', {
        direction: 'recvonly'
      });
      console.log('✅ Added audio transceiver (recvonly)');

      const offer = await this.peerConnection.createOffer();
      await this.peerConnection.setLocalDescription(offer);
      console.log('✅ Local description set');

      // Send offer to Firebase
      await this.offerRef.set({
        type: this.peerConnection.localDescription.type,
        sdp: this.peerConnection.localDescription.sdp
      });

      console.log('📤 Offer sent to Firebase');
    } catch (err) {
      console.error('❌ Error creating/sending offer:', err);
      throw err;
    }
  }

  /**
   * Handle incoming answer from child
   */
  async handleAnswer(answer) {
    console.log('📥 Received answer from child');
    console.log('Signaling state:', this.peerConnection.signalingState);
    console.log('Is renegotiating:', this.isRenegotiating);

    // Accept answer if we're waiting for one (have-local-offer)
    // This includes both initial connection and renegotiation
    if (this.peerConnection.signalingState === 'have-local-offer') {
      try {
        await this.peerConnection.setRemoteDescription(new RTCSessionDescription(answer));
        console.log('✅ Remote description set (answer)');

        // Clear renegotiating flag on success
        if (this.isRenegotiating) {
          this.isRenegotiating = false;
          console.log('✅ Renegotiation complete');
          
          // Notify child that renegotiation is complete
          if (this.dataChannel && this.dataChannel.readyState === 'open') {
            this.dataChannel.send('RENEGOTIATION_COMPLETE');
            console.log('📤 Sent RENEGOTIATION_COMPLETE to child');
          }
        }
      } catch (err) {
        console.error('❌ Error setting remote description:', err);
        this.isRenegotiating = false; // Clear flag on error too
      }
    } else if (this.peerConnection.signalingState === 'stable') {
      // Already stable - this might be a duplicate answer, ignore it
      console.log('ℹ️ Ignoring answer - already in stable state (likely duplicate)');
      this.isRenegotiating = false; // Clear flag just in case
    } else {
      console.warn('⚠️ Unexpected signaling state for answer:', this.peerConnection.signalingState);
      this.isRenegotiating = false; // Clear flag
    }
  }

  /**
   * Send command to child
   */
  sendCommand(command) {
    if (this.dataChannel && this.dataChannel.readyState === 'open') {
      this.dataChannel.send(command);
      console.log('📤 Sent command:', command);
      return true;
    } else {
      console.error('❌ DataChannel not ready');
      return false;
    }
  }

  /**
   * Get stream by type
   */
  getStream(type) {
    return this.videoStreams[type];
  }

  /**
   * Renegotiate connection (create new offer)
   */
  async renegotiate() {
    console.log('🔄 Starting renegotiation...');
    console.log('Current signaling state:', this.peerConnection.signalingState);
    console.log('Current connection state:', this.peerConnection.connectionState);

    // Prevent concurrent renegotiations
    if (this.isRenegotiating) {
      console.log('⚠️ Renegotiation already in progress, skipping...');
      return;
    }

    // Wait for stable state before renegotiating
    if (this.peerConnection.signalingState !== 'stable') {
      console.log('⚠️ Signaling state not stable, waiting...');
      // Wait a bit and try again
      await new Promise(resolve => setTimeout(resolve, 500));
      if (this.peerConnection.signalingState !== 'stable') {
        console.log('⚠️ Still not stable, aborting renegotiation');
        return;
      }
    }

    this.isRenegotiating = true;

    // Reset lastAnswerHash so we accept the new answer
    this.lastAnswerHash = null;
    console.log('🗑️ Reset lastAnswerHash to accept new answer');

    try {
      // Get existing transceivers and log their state
      const transceivers = this.peerConnection.getTransceivers();
      console.log(`📊 Found ${transceivers.length} transceivers`);

      for (const transceiver of transceivers) {
        const kind = transceiver.receiver.track.kind;
        const hasSender = transceiver.sender && transceiver.sender.track;
        const senderTrackEnabled = hasSender ? transceiver.sender.track.enabled : false;
        console.log(`🔍 Transceiver: kind=${kind}, direction=${transceiver.direction}, mid=${transceiver.mid}, hasSenderTrack=${hasSender}, senderEnabled=${senderTrackEnabled}`);
      }

      // Clear old answer to ensure Firebase triggers value change
      await this.answerRef.remove();
      console.log('🗑️ Cleared old answer from Firebase');

      // Small delay to ensure Firebase processes the removal
      await new Promise(resolve => setTimeout(resolve, 100));

      // Create new offer
      const offer = await this.peerConnection.createOffer({ iceRestart: false });
      await this.peerConnection.setLocalDescription(offer);
      console.log('✅ New local description set');
      console.log('New signaling state:', this.peerConnection.signalingState);

      // Send new offer to Firebase (overwrite existing)
      await this.offerRef.set({
        type: this.peerConnection.localDescription.type,
        sdp: this.peerConnection.localDescription.sdp
      });

      console.log('📤 New offer sent to Firebase for renegotiation');
      console.log('Waiting for answer from child...');

      // Answer listener is already set up, will handle the new answer
      // The flag will be cleared in handleAnswer

    } catch (err) {
      console.error('❌ Error during renegotiation:', err);
      this.isRenegotiating = false; // Clear flag on error
      throw err;
    }
  }

  /**
   * Disconnect and cleanup
   */
  disconnect() {
    console.log('🧹 Disconnecting...');

    // Close data channel
    if (this.dataChannel) {
      this.dataChannel.close();
      this.dataChannel = null;
    }

    // Close peer connection
    if (this.peerConnection) {
      this.peerConnection.close();
      this.peerConnection = null;
    }

    // Remove Firebase listeners
    if (this.offerRef) {
      this.offerRef.off();
      this.offerRef = null;
    }

    if (this.answerRef) {
      this.answerRef.off();
      this.answerRef = null;
    }

    if (this.iceCandidatesRef) {
      this.iceCandidatesRef.off();
      this.iceCandidatesRef = null;
    }

    // Clear streams
    this.videoStreams = { camera: null, screen: null, mic: null };
    this.currentChildId = null;

    console.log('✅ Disconnected');
  }

  /**
   * Cleanup method (alias for disconnect)
   */
  cleanup() {
    this.disconnect();
  }

  /**
   * Set callbacks
   */
  setCallbacks(callbacks) {
    this.onConnectionEstablished = callbacks.onConnectionEstablished;
    this.onDataChannelMessage = callbacks.onDataChannelMessage;
    this.onStreamReceived = callbacks.onStreamReceived;
  }
}

// Export to window
window.WebRTCManager = WebRTCManager;

// Helper function to hash strings
String.prototype.hashCode = function () {
  let hash = 0;
  if (this.length === 0) return hash;
  for (let i = 0; i < this.length; i++) {
    const char = this.charCodeAt(i);
    hash = ((hash << 5) - hash) + char;
    hash = hash & hash; // Convert to 32bit integer
  }
  return hash;
};
