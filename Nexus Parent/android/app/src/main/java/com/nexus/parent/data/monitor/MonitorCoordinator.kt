package com.nexus.parent.data.monitor

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.nexus.parent.state.AppLockAttemptItem
import com.nexus.parent.state.FeatureId
import com.nexus.parent.state.KeyboardUiItem
import com.nexus.parent.state.MonitorPanelsUiState
import com.nexus.parent.state.MonitoringState
import com.nexus.parent.state.NotificationUiItem
import com.nexus.parent.state.ParentStateHolder
import com.nexus.parent.state.SmsUiItem
import com.nexus.parent.state.WallpaperTarget
import com.nexus.parent.state.WellbeingAppItem
import com.nexus.parent.state.WellbeingBucket
import com.nexus.parent.state.WellbeingRange
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.ceil

/**
 * MonitorCoordinator.kt - Live WebRTC DataChannel coordinator for all 8 monitor panels.
 *
 * Hey friend! Think of this class as the Android counterpart to Desktop's `right-sidebar.ts` +
 * `right-sidebar/state.ts`. Every button press (Start/Pause spinner, Wellbeing range toggle,
 * App Lock PIN/Toggle/Clear, Vibrate/Flash actuator, Wallpaper fetch & chunked 16KB upload)
 * goes through here, awaits Child confirmation (`*_STARTED`, `*_STOPPED`, `*_SUCCESS`) with
 * exact Desktop timeout fallbacks, and merges incoming DataChannel telemetry into `ParentStateHolder`.
 */
class MonitorCoordinator(
    private val context: Context?,
    private val scope: CoroutineScope,
    private val sendCommand: (String) -> Boolean,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val base64Decoder: (String) -> ByteArray = { Base64.decode(it, Base64.DEFAULT) },
    private val base64Encoder: (ByteArray) -> String = { Base64.encodeToString(it, Base64.NO_WRAP) }
) {

    private var stateHolder: ParentStateHolder? = null

    private val timeoutJobs = mutableMapOf<String, Job>()

    private data class IncomingWellbeingBuffer(
        val range: WellbeingRange,
        val totalChunks: Int,
        val totalScreenTimeMs: Long,
        val unlockCount: Int,
        val totalLaunches: Int,
        val activeAppName: String?,
        val generatedAtMs: Long,
        val buckets: List<RawWellbeingBucket>,
        val chunks: MutableMap<Int, List<WellbeingAppItem>> = mutableMapOf()
    )

    private data class IncomingWallpaperBuffer(
        val totalChunks: Int,
        var isSame: Boolean,
        val chunks: MutableMap<Int, String> = mutableMapOf()
    )

    private var incomingWellbeing: IncomingWellbeingBuffer? = null
    private val incomingWallpaper = mutableMapOf<String, IncomingWallpaperBuffer>()

    fun attachStateHolder(holder: ParentStateHolder) {
        stateHolder = holder
    }

    // -------------------------------------------------------------------------
    // PLAY / PAUSE MONITORING LIFECYCLE (WITH LOADING SPINNER + 20s TIMEOUT)
    // -------------------------------------------------------------------------

    fun toggleFeatureMonitoring(featureId: FeatureId) {
        val holder = stateHolder ?: return
        val monitor = holder.uiState.value.monitor
        val currentState = when (featureId) {
            FeatureId.SMS -> monitor.smsState
            FeatureId.CALLLOG -> monitor.callLogState
            FeatureId.NOTIFICATION -> monitor.notificationState
            FeatureId.KEYBOARD -> monitor.keyboardState
            FeatureId.WELLBEING -> monitor.wellbeing.monitoringState
            FeatureId.APPLOCK -> monitor.appLock.monitoringState
            FeatureId.WALLPAPER -> monitor.wallpaper.monitoringState
            else -> return
        }

        if (currentState == MonitoringState.STARTING || currentState == MonitoringState.STOPPING) {
            return
        }

        if (currentState == MonitoringState.IDLE) {
            val startCmd = when (featureId) {
                FeatureId.SMS -> MonitorProtocol.CMD_SMS_ON
                FeatureId.CALLLOG -> MonitorProtocol.CMD_CALLLOG_ON
                FeatureId.NOTIFICATION -> MonitorProtocol.CMD_NOTIFICATION_ON
                FeatureId.KEYBOARD -> MonitorProtocol.CMD_KEYBOARD_ON
                FeatureId.APPLOCK -> MonitorProtocol.CMD_APPLOCK_ON
                FeatureId.WALLPAPER -> MonitorProtocol.CMD_WALLPAPER_ON
                FeatureId.WELLBEING -> JSONObject().apply {
                    put("type", MonitorProtocol.CMD_WELLBEING_ON)
                    put("range", MonitorProtocol.rangeToWire(monitor.wellbeing.range))
                }.toString()
                else -> return
            }

            if (featureId == FeatureId.WELLBEING) {
                holder.updateMonitor { m -> m.copy(wellbeing = m.wellbeing.copy(errorMessage = null)) }
            }
            setFeatureMonitoringState(featureId, MonitoringState.STARTING, isLiveBadge = false)

            if (!sendCommand(startCmd)) {
                setFeatureMonitoringState(featureId, MonitoringState.IDLE, isLiveBadge = false)
                holder.showToast("Cannot start: DataChannel not connected")
                return
            }

            scheduleTimeout(
                key = featureId.name,
                timeoutMs = MonitorProtocol.MONITOR_CONFIRM_TIMEOUT_MS
            ) {
                if (getFeatureMonitoringState(featureId) == MonitoringState.STARTING) {
                    setFeatureMonitoringState(featureId, MonitoringState.IDLE, isLiveBadge = false)
                    holder.showToast("${featureId.title} request timed out")
                }
            }
        } else if (currentState == MonitoringState.LIVE) {
            val stopCmd = when (featureId) {
                FeatureId.SMS -> MonitorProtocol.CMD_SMS_OFF
                FeatureId.CALLLOG -> MonitorProtocol.CMD_CALLLOG_OFF
                FeatureId.NOTIFICATION -> MonitorProtocol.CMD_NOTIFICATION_OFF
                FeatureId.KEYBOARD -> MonitorProtocol.CMD_KEYBOARD_OFF
                FeatureId.APPLOCK -> MonitorProtocol.CMD_APPLOCK_OFF
                FeatureId.WALLPAPER -> MonitorProtocol.CMD_WALLPAPER_OFF
                FeatureId.WELLBEING -> MonitorProtocol.CMD_WELLBEING_OFF
                else -> return
            }

            setFeatureMonitoringState(featureId, MonitoringState.STOPPING, isLiveBadge = false)

            if (!sendCommand(stopCmd)) {
                setFeatureMonitoringState(featureId, MonitoringState.IDLE, isLiveBadge = false)
                return
            }

            val stopTimeout = if (featureId == FeatureId.APPLOCK) {
                MonitorProtocol.APPLOCK_STOP_TIMEOUT_MS
            } else {
                MonitorProtocol.MONITOR_CONFIRM_TIMEOUT_MS
            }
            scheduleTimeout(key = featureId.name, timeoutMs = stopTimeout) {
                if (getFeatureMonitoringState(featureId) == MonitoringState.STOPPING) {
                    setFeatureMonitoringState(featureId, MonitoringState.IDLE, isLiveBadge = false)
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // FEATURE-SPECIFIC ACTIONS (WELLBEING, APPLOCK, VIBRATE/FLASH, WALLPAPER)
    // -------------------------------------------------------------------------

    fun selectWellbeingRange(range: WellbeingRange) {
        val holder = stateHolder ?: return
        val current = holder.uiState.value.monitor.wellbeing
        if (current.range == range) return

        holder.updateMonitor { m -> m.copy(wellbeing = m.wellbeing.copy(range = range)) }
        if (current.monitoringState == MonitoringState.LIVE) {
            val sent = sendCommand("${MonitorProtocol.CMD_WELLBEING_RANGE_PREFIX}${MonitorProtocol.rangeToWire(range)}")
            if (!sent) {
                holder.showToast("Cannot switch range: DataChannel not connected")
            }
        }
    }

    fun refreshWellbeing() {
        val holder = stateHolder ?: return
        val range = holder.uiState.value.monitor.wellbeing.range
        val payload = JSONObject().apply {
            put("type", MonitorProtocol.CMD_WELLBEING_REFRESH)
            put("range", MonitorProtocol.rangeToWire(range))
        }.toString()
        if (sendCommand(payload)) {
            holder.showToast("Refreshing Digital Wellbeing telemetry...")
        } else {
            holder.showToast("Cannot refresh: DataChannel not connected")
        }
    }

    fun toggleRemoteAppLock() {
        val holder = stateHolder ?: return
        val nextEnabled = !holder.uiState.value.monitor.appLock.isLockEnabled
        val payload = JSONObject().apply {
            put("type", MonitorProtocol.CMD_APPLOCK_TOGGLE)
            put("enabled", nextEnabled)
        }.toString()

        if (sendCommand(payload)) {
            holder.updateMonitor { m -> m.copy(appLock = m.appLock.copy(isLockEnabled = nextEnabled)) }
            holder.showToast("App Lock remote command sent: ${if (nextEnabled) "Enabled" else "Disabled"}")
        } else {
            holder.showToast("Cannot send: DataChannel not connected")
        }
    }

    fun setRemoteAppLockPin(pin: String) {
        val holder = stateHolder ?: return
        if (pin.length != 6 || !pin.all { it.isDigit() }) {
            holder.showToast("PIN must be exactly 6 numeric digits")
            return
        }
        val payload = JSONObject().apply {
            put("type", MonitorProtocol.CMD_APPLOCK_SET_PIN)
            put("pin", pin)
        }.toString()

        if (sendCommand(payload)) {
            holder.updateMonitor { m -> m.copy(appLock = m.appLock.copy(showChangePinDialog = false)) }
            holder.showToast("PIN change command sent to device")
        } else {
            holder.showToast("Failed to send: DataChannel not connected")
        }
    }

    fun clearAppLockRecords() {
        val holder = stateHolder ?: return
        holder.updateMonitor { m -> m.copy(appLock = m.appLock.copy(attempts = emptyList())) }
        if (sendCommand(MonitorProtocol.CMD_APPLOCK_CLEAR)) {
            holder.showToast("App Lock attempt logs cleared")
        } else {
            holder.showToast("Cleared local logs; child not connected")
        }
    }

    fun triggerVibrate(pattern: VibratePattern) {
        dispatchVibrateFlash(
            cmd = "${MonitorProtocol.CMD_VIBRATE_PREFIX}${pattern.wireValue}",
            pendingMessage = "Sending vibration '${pattern.displayLabel}' to child device..."
        )
    }

    fun triggerFlash(action: FlashAction) {
        dispatchVibrateFlash(
            cmd = "${MonitorProtocol.CMD_FLASH_PREFIX}${action.wireValue}",
            pendingMessage = "Sending flashlight '${action.displayLabel}' to child device..."
        )
    }

    fun triggerCombined(pattern: CombinedPattern) {
        dispatchVibrateFlash(
            cmd = "${MonitorProtocol.CMD_VIBRATE_FLASH_PREFIX}${pattern.wireValue}",
            pendingMessage = "Sending combined '${pattern.displayLabel}' to child device..."
        )
    }

    private fun dispatchVibrateFlash(cmd: String, pendingMessage: String) {
        val holder = stateHolder ?: return
        holder.updateMonitor { m ->
            m.copy(vibrateFlash = m.vibrateFlash.copy(lastSuccess = null, statusMessage = pendingMessage))
        }
        if (!sendCommand(cmd)) {
            holder.updateMonitor { m ->
                m.copy(
                    vibrateFlash = m.vibrateFlash.copy(
                        lastSuccess = false,
                        statusMessage = "Failed: DataChannel not connected"
                    )
                )
            }
            holder.showToast("Cannot send: DataChannel not connected")
        }
    }

    fun fetchActiveWallpaper() {
        val holder = stateHolder ?: return
        if (holder.uiState.value.monitor.wallpaper.isFetching) return

        holder.updateMonitor { m -> m.copy(wallpaper = m.wallpaper.copy(isFetching = true)) }
        if (!sendCommand(MonitorProtocol.CMD_WALLPAPER_GET)) {
            holder.updateMonitor { m -> m.copy(wallpaper = m.wallpaper.copy(isFetching = false)) }
            holder.showToast("Cannot fetch: DataChannel not connected")
            return
        }

        scheduleTimeout("wallpaperFetch", MonitorProtocol.WALLPAPER_FETCH_TIMEOUT_MS) {
            if (stateHolder?.uiState?.value?.monitor?.wallpaper?.isFetching == true) {
                stateHolder?.updateMonitor { m -> m.copy(wallpaper = m.wallpaper.copy(isFetching = false)) }
                stateHolder?.showToast("Wallpaper fetch request timed out")
            }
        }
    }

    fun pushWallpaperFromUri(uri: Uri) {
        val appContext = context ?: return
        val holder = stateHolder ?: return
        if (holder.uiState.value.monitor.wallpaper.isPushing) return

        scope.launch {
            val bytes = withContext(ioDispatcher) {
                runCatching {
                    appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
            }
            if (bytes == null || bytes.isEmpty()) {
                holder.showToast("Failed reading selected image")
                return@launch
            }
            pushWallpaperBytes(bytes)
        }
    }

    suspend fun pushWallpaperBytes(rawBytes: ByteArray) {
        val holder = stateHolder ?: return
        if (rawBytes.size.toLong() > MonitorProtocol.WALLPAPER_MAX_FILE_BYTES) {
            holder.showToast("Image too large: maximum file size is 10MB")
            return
        }

        val target = holder.uiState.value.monitor.wallpaper.target
        holder.updateMonitor { m ->
            m.copy(wallpaper = m.wallpaper.copy(isPushing = true, pushProgressPercent = 0))
        }

        val base64 = withContext(ioDispatcher) { base64Encoder(rawBytes) }
        if (base64.isEmpty()) {
            holder.updateMonitor { m -> m.copy(wallpaper = m.wallpaper.copy(isPushing = false, pushProgressPercent = 0)) }
            holder.showToast("Failed encoding wallpaper image")
            return
        }

        val chunkSize = MonitorProtocol.WALLPAPER_PUSH_CHUNK_SIZE
        val totalChunks = ceil(base64.length.toDouble() / chunkSize.toDouble()).toInt().coerceAtLeast(1)

        val startJson = JSONObject().apply {
            put("type", MonitorProtocol.CMD_PUSH_WALLPAPER_START)
            put("target", target.wireValue)
            put("totalChunks", totalChunks)
            put("fileSize", rawBytes.size)
        }.toString()

        if (!sendCommand(startJson)) {
            holder.updateMonitor { m -> m.copy(wallpaper = m.wallpaper.copy(isPushing = false, pushProgressPercent = 0)) }
            holder.showToast("Push failed: DataChannel not connected")
            return
        }

        withContext(ioDispatcher) {
            for (i in 0 until totalChunks) {
                val startIdx = i * chunkSize
                val endIdx = minOf(startIdx + chunkSize, base64.length)
                val slice = base64.substring(startIdx, endIdx)

                val chunkJson = JSONObject().apply {
                    put("type", MonitorProtocol.CMD_PUSH_WALLPAPER_CHUNK)
                    put("chunkIndex", i)
                    put("totalChunks", totalChunks)
                    put("data", slice)
                }.toString()

                if (!sendCommand(chunkJson)) {
                    holder.updateMonitor { m ->
                        m.copy(wallpaper = m.wallpaper.copy(isPushing = false, pushProgressPercent = 0))
                    }
                    holder.showToast("Wallpaper upload interrupted: connection lost")
                    return@withContext
                }
                val pct = (((i + 1).toFloat() / totalChunks.toFloat()) * 100f).toInt().coerceIn(0, 100)
                holder.updateMonitor { m ->
                    m.copy(wallpaper = m.wallpaper.copy(isPushing = true, pushProgressPercent = pct))
                }
            }
        }

        // Optimistically update local HUD preview while awaiting Child's WALLPAPER_SET_SUCCESS confirmation
        if (holder.uiState.value.monitor.wallpaper.isPushing) {
            holder.updateMonitor { m ->
                val wp = m.wallpaper
                m.copy(
                    wallpaper = when (target) {
                        WallpaperTarget.HOME -> wp.copy(homeImageBytes = rawBytes, hasPreviewLoaded = true)
                        WallpaperTarget.LOCK -> wp.copy(lockImageBytes = rawBytes, isSameWallpaper = false, hasPreviewLoaded = true)
                        WallpaperTarget.BOTH -> wp.copy(homeImageBytes = rawBytes, lockImageBytes = rawBytes, isSameWallpaper = true, hasPreviewLoaded = true)
                    }
                )
            }
            scheduleTimeout("wallpaperPush", MonitorProtocol.WALLPAPER_PUSH_TIMEOUT_MS) {
                if (stateHolder?.uiState?.value?.monitor?.wallpaper?.isPushing == true) {
                    stateHolder?.updateMonitor { m ->
                        m.copy(wallpaper = m.wallpaper.copy(isPushing = false, pushProgressPercent = 0))
                    }
                    stateHolder?.showToast("Push wallpaper confirmation timed out")
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // INCOMING DATACHANNEL EVENT ROUTER
    // -------------------------------------------------------------------------

    fun handleIncomingMessage(rawMessage: String): Boolean {
        val event = MonitorPayloadParser.parse(rawMessage) ?: return false
        val holder = stateHolder ?: return true

        when (event) {
            // SMS
            MonitorDataEvent.SmsStarted -> {
                cancelTimeout(FeatureId.SMS.name)
                setFeatureMonitoringState(FeatureId.SMS, MonitoringState.LIVE, isLiveBadge = true)
            }
            MonitorDataEvent.SmsStopped -> {
                cancelTimeout(FeatureId.SMS.name)
                setFeatureMonitoringState(FeatureId.SMS, MonitoringState.IDLE, isLiveBadge = false)
            }
            is MonitorDataEvent.SmsError -> {
                cancelTimeout(FeatureId.SMS.name)
                setFeatureMonitoringState(FeatureId.SMS, MonitoringState.IDLE, isLiveBadge = false)
                holder.showToast(event.message)
            }
            is MonitorDataEvent.SmsHistoryChunk -> applySmsChunk(event.chunkIndex, event.messages)
            is MonitorDataEvent.SmsNew -> {
                prependSms(event.item)
                holder.showToast("New SMS from ${event.item.senderName.ifBlank { event.item.address }}")
            }

            // Call Log
            MonitorDataEvent.CallLogStarted -> {
                cancelTimeout(FeatureId.CALLLOG.name)
                setFeatureMonitoringState(FeatureId.CALLLOG, MonitoringState.LIVE, isLiveBadge = true)
            }
            MonitorDataEvent.CallLogStopped -> {
                cancelTimeout(FeatureId.CALLLOG.name)
                setFeatureMonitoringState(FeatureId.CALLLOG, MonitoringState.IDLE, isLiveBadge = false)
            }
            is MonitorDataEvent.CallLogError -> {
                cancelTimeout(FeatureId.CALLLOG.name)
                setFeatureMonitoringState(FeatureId.CALLLOG, MonitoringState.IDLE, isLiveBadge = false)
                holder.showToast(event.message)
            }
            is MonitorDataEvent.CallLogHistory -> {
                holder.updateMonitor { m -> m.copy(callItems = event.records) }
            }
            is MonitorDataEvent.CallLogNew -> {
                prependCallLog(event.item)
                holder.showToast("New Call: ${event.item.displayName} (${event.item.filterType.label})")
            }

            // Notifications
            MonitorDataEvent.NotificationStarted -> {
                cancelTimeout(FeatureId.NOTIFICATION.name)
                setFeatureMonitoringState(FeatureId.NOTIFICATION, MonitoringState.LIVE, isLiveBadge = true)
            }
            MonitorDataEvent.NotificationStopped -> {
                cancelTimeout(FeatureId.NOTIFICATION.name)
                setFeatureMonitoringState(FeatureId.NOTIFICATION, MonitoringState.IDLE, isLiveBadge = false)
            }
            is MonitorDataEvent.NotificationError -> {
                cancelTimeout(FeatureId.NOTIFICATION.name)
                setFeatureMonitoringState(FeatureId.NOTIFICATION, MonitoringState.IDLE, isLiveBadge = false)
                holder.showToast(event.message)
            }
            is MonitorDataEvent.NotificationSnapshot -> {
                holder.updateMonitor { m -> m.copy(notificationItems = event.items) }
            }
            is MonitorDataEvent.NotificationNew -> {
                upsertNotification(event.item)
                holder.showToast("${event.item.appName}: ${event.item.title}")
            }
            is MonitorDataEvent.NotificationRemoved -> {
                holder.updateMonitor { m ->
                    m.copy(
                        notificationItems = m.notificationItems.filterNot { it.key == event.key },
                        selectedNotification = m.selectedNotification?.takeUnless { it.key == event.key }
                    )
                }
            }

            // Keyboard Audit
            MonitorDataEvent.KeyboardStarted -> {
                cancelTimeout(FeatureId.KEYBOARD.name)
                setFeatureMonitoringState(FeatureId.KEYBOARD, MonitoringState.LIVE, isLiveBadge = true)
            }
            MonitorDataEvent.KeyboardStopped -> {
                cancelTimeout(FeatureId.KEYBOARD.name)
                setFeatureMonitoringState(FeatureId.KEYBOARD, MonitoringState.IDLE, isLiveBadge = false)
            }
            is MonitorDataEvent.KeyboardError -> {
                cancelTimeout(FeatureId.KEYBOARD.name)
                setFeatureMonitoringState(FeatureId.KEYBOARD, MonitoringState.IDLE, isLiveBadge = false)
                holder.showToast(event.message)
            }
            is MonitorDataEvent.KeyboardSnapshot -> {
                holder.updateMonitor { m ->
                    m.copy(keyboardItems = event.records.take(MonitorProtocol.KEYBOARD_MAX_RECORDS))
                }
            }
            is MonitorDataEvent.KeyboardNew -> {
                prependKeyboardRecord(event.record)
                val snippet = if (event.record.isPassword) "[PASSWORD]" else event.record.text.take(30)
                holder.showToast("Keystroke: ${event.record.appName} - $snippet")
            }

            // Digital Wellbeing
            MonitorDataEvent.WellbeingStarted -> {
                cancelTimeout(FeatureId.WELLBEING.name)
                holder.updateMonitor { m -> m.copy(wellbeing = m.wellbeing.copy(errorMessage = null)) }
                setFeatureMonitoringState(FeatureId.WELLBEING, MonitoringState.LIVE, isLiveBadge = true)
            }
            MonitorDataEvent.WellbeingStopped -> {
                cancelTimeout(FeatureId.WELLBEING.name)
                setFeatureMonitoringState(FeatureId.WELLBEING, MonitoringState.IDLE, isLiveBadge = false)
            }
            is MonitorDataEvent.WellbeingError -> {
                cancelTimeout(FeatureId.WELLBEING.name)
                holder.updateMonitor { m -> m.copy(wellbeing = m.wellbeing.copy(errorMessage = event.message)) }
                setFeatureMonitoringState(FeatureId.WELLBEING, MonitoringState.IDLE, isLiveBadge = false)
                holder.showToast(event.message)
            }
            is MonitorDataEvent.WellbeingSnapshotChunk -> handleWellbeingChunk(event)

            // App Lock
            MonitorDataEvent.AppLockStarted -> {
                cancelTimeout(FeatureId.APPLOCK.name)
                setFeatureMonitoringState(FeatureId.APPLOCK, MonitoringState.LIVE, isLiveBadge = true)
            }
            MonitorDataEvent.AppLockStopped -> {
                cancelTimeout(FeatureId.APPLOCK.name)
                setFeatureMonitoringState(FeatureId.APPLOCK, MonitoringState.IDLE, isLiveBadge = false)
            }
            MonitorDataEvent.AppLockClearedAck -> Unit
            is MonitorDataEvent.AppLockError -> {
                cancelTimeout(FeatureId.APPLOCK.name)
                setFeatureMonitoringState(FeatureId.APPLOCK, MonitoringState.IDLE, isLiveBadge = false)
                holder.showToast(event.message)
            }
            is MonitorDataEvent.AppLockStatus -> {
                holder.updateMonitor { m ->
                    m.copy(
                        appLock = m.appLock.copy(
                            isLockEnabled = event.enabled,
                            isLockedOut = event.isLockedOut
                        )
                    )
                }
            }
            is MonitorDataEvent.AppLockPinChanged -> holder.showToast(event.message)
            is MonitorDataEvent.AppLockSnapshotChunk -> applyAppLockSnapshotChunk(event.chunkIndex, event.records)
            is MonitorDataEvent.AppLockNew -> {
                prependAppLockAttempt(event.record)
                val outcome = when {
                    event.record.pinCorrect -> "Successful unlock"
                    event.record.isLockedOut -> "Device locked out (60s)"
                    else -> "Wrong PIN attempt"
                }
                holder.showToast("App Lock: $outcome")
            }

            // Vibrate & Flash
            is MonitorDataEvent.VibrateResult -> {
                val msg = if (event.success) {
                    "Vibration '${event.detail}' executed on child"
                } else {
                    event.detail
                }
                updateVibrateFlashResult(event.success, msg)
                holder.showToast(if (event.success) "Vibration '${event.detail}' confirmed" else event.detail)
            }
            is MonitorDataEvent.FlashResult -> {
                val msg = if (event.success) {
                    "Flashlight '${event.detail}' executed on child"
                } else {
                    event.detail
                }
                updateVibrateFlashResult(event.success, msg)
                holder.showToast(if (event.success) "Flashlight '${event.detail}' confirmed" else event.detail)
            }
            is MonitorDataEvent.VibrateFlashResult -> {
                val msg = if (event.success) {
                    "Combined pattern '${event.detail}' executed"
                } else {
                    event.detail
                }
                updateVibrateFlashResult(event.success, msg)
                holder.showToast(if (event.success) "Combined '${event.detail}' confirmed" else event.detail)
            }

            // Wallpaper
            MonitorDataEvent.WallpaperStarted -> {
                cancelTimeout(FeatureId.WALLPAPER.name)
                setFeatureMonitoringState(FeatureId.WALLPAPER, MonitoringState.LIVE, isLiveBadge = true)
            }
            MonitorDataEvent.WallpaperStopped -> {
                cancelTimeout(FeatureId.WALLPAPER.name)
                setFeatureMonitoringState(FeatureId.WALLPAPER, MonitoringState.IDLE, isLiveBadge = false)
            }
            is MonitorDataEvent.WallpaperError -> {
                cancelTimeout(FeatureId.WALLPAPER.name)
                cancelTimeout("wallpaperFetch")
                holder.updateMonitor { m -> m.copy(wallpaper = m.wallpaper.copy(isFetching = false)) }
                setFeatureMonitoringState(FeatureId.WALLPAPER, MonitoringState.IDLE, isLiveBadge = false)
                holder.showToast(event.message)
            }
            is MonitorDataEvent.WallpaperChunk -> handleWallpaperChunk(event)
            MonitorDataEvent.WallpaperSetSuccess -> {
                cancelTimeout("wallpaperPush")
                holder.updateMonitor { m ->
                    m.copy(wallpaper = m.wallpaper.copy(isPushing = false, pushProgressPercent = 100))
                }
                holder.showToast("Wallpaper successfully applied to child device")
            }
            is MonitorDataEvent.WallpaperSetError -> {
                cancelTimeout("wallpaperPush")
                holder.updateMonitor { m ->
                    m.copy(wallpaper = m.wallpaper.copy(isPushing = false, pushProgressPercent = 0))
                }
                holder.showToast(event.message)
            }
        }
        return true
    }

    fun onPeerDisconnected() {
        timeoutJobs.values.forEach { it.cancel() }
        timeoutJobs.clear()
        incomingWellbeing = null
        incomingWallpaper.clear()

        stateHolder?.resetMonitorConnections()
    }

    // -------------------------------------------------------------------------
    // INTERNAL STATE MERGERS & CHUNK ASSEMBLERS
    // -------------------------------------------------------------------------

    private fun applySmsChunk(chunkIndex: Int, incoming: List<SmsUiItem>) {
        stateHolder?.updateMonitor { m ->
            val base = if (chunkIndex == 0) emptyList() else m.smsItems
            val byId = LinkedHashMap<String, SmsUiItem>()
            (base + incoming).sortedByDescending { it.timestampMs }.forEach { item ->
                byId[item.id] = item
            }
            m.copy(smsItems = byId.values.toList())
        }
    }

    private fun prependSms(item: SmsUiItem) {
        stateHolder?.updateMonitor { m ->
            val updated = listOf(item) + m.smsItems.filterNot { it.id == item.id }
            m.copy(smsItems = updated)
        }
    }

    private fun prependCallLog(item: CallLogUiItemAlias) {
        stateHolder?.updateMonitor { m ->
            val updated = listOf(item) + m.callItems.filterNot { it.id == item.id }
            m.copy(callItems = updated)
        }
    }

    private fun upsertNotification(item: NotificationUiItem) {
        stateHolder?.updateMonitor { m ->
            val existingIdx = m.notificationItems.indexOfFirst { it.key == item.key }
            val updated = if (existingIdx >= 0) {
                m.notificationItems.toMutableList().apply { set(existingIdx, item) }
            } else {
                listOf(item) + m.notificationItems
            }
            m.copy(notificationItems = updated)
        }
    }

    private fun prependKeyboardRecord(record: KeyboardUiItem) {
        stateHolder?.updateMonitor { m ->
            val withoutDup = m.keyboardItems.filterNot { it.id == record.id }
            val updated = (listOf(record) + withoutDup).take(MonitorProtocol.KEYBOARD_MAX_RECORDS)
            m.copy(keyboardItems = updated)
        }
    }

    private fun applyAppLockSnapshotChunk(chunkIndex: Int, incoming: List<AppLockAttemptItem>) {
        stateHolder?.updateMonitor { m ->
            val base = if (chunkIndex == 0) emptyList() else m.appLock.attempts
            val byId = LinkedHashMap<String, AppLockAttemptItem>()
            (base + incoming).sortedByDescending { it.timestampMs }.forEach { item ->
                byId[item.id] = item
            }
            m.copy(appLock = m.appLock.copy(attempts = byId.values.toList()))
        }
    }

    private fun prependAppLockAttempt(record: AppLockAttemptItem) {
        stateHolder?.updateMonitor { m ->
            if (m.appLock.attempts.any { it.id == record.id }) {
                m
            } else {
                m.copy(appLock = m.appLock.copy(attempts = listOf(record) + m.appLock.attempts))
            }
        }
    }

    private fun handleWellbeingChunk(event: MonitorDataEvent.WellbeingSnapshotChunk) {
        val currentBuf = incomingWellbeing
        val buffer = if (event.chunkIndex == 0 || currentBuf == null || currentBuf.range != event.range) {
            IncomingWellbeingBuffer(
                range = event.range,
                totalChunks = event.totalChunks,
                totalScreenTimeMs = event.totalScreenTimeMs,
                unlockCount = event.unlockCount,
                totalLaunches = event.totalLaunches,
                activeAppName = event.activeAppName,
                generatedAtMs = event.generatedAtMs,
                buckets = event.buckets
            ).also { incomingWellbeing = it }
        } else {
            currentBuf
        }

        buffer.chunks[event.chunkIndex] = event.apps
        if (buffer.chunks.size < buffer.totalChunks) return

        val assembledApps = buildList {
            for (i in 0 until buffer.totalChunks) {
                addAll(buffer.chunks[i].orEmpty())
            }
        }.sortedByDescending { it.totalTimeMs }

        val maxBucketMs = buffer.buckets.maxOfOrNull { it.durationMs }?.coerceAtLeast(1L) ?: 1L
        val uiBuckets = buffer.buckets.map { raw ->
            val normalized = if (raw.durationMs > 0L) {
                (raw.durationMs.toFloat() / maxBucketMs.toFloat()).coerceIn(0.08f, 1f)
            } else {
                0f
            }
            WellbeingBucket(
                label = raw.label,
                normalizedValue = normalized,
                durationLabel = MonitorProtocol.formatScreenTimeDuration(raw.durationMs)
            )
        }

        incomingWellbeing = null
        stateHolder?.updateMonitor { m ->
            m.copy(
                wellbeing = m.wellbeing.copy(
                    range = buffer.range,
                    totalScreenTime = MonitorProtocol.formatScreenTimeDuration(buffer.totalScreenTimeMs),
                    unlockCount = buffer.unlockCount,
                    totalLaunches = buffer.totalLaunches,
                    activeAppName = buffer.activeAppName ?: if (buffer.range == WellbeingRange.SEVEN_DAYS) "7-Day View" else "Idle",
                    syncedLabel = MonitorProtocol.formatRelativeTime(buffer.generatedAtMs),
                    errorMessage = null,
                    buckets = uiBuckets,
                    apps = assembledApps
                )
            )
        }
    }

    private fun handleWallpaperChunk(event: MonitorDataEvent.WallpaperChunk) {
        val screen = event.screen
        val buffer = incomingWallpaper.getOrPut(screen) {
            IncomingWallpaperBuffer(totalChunks = event.totalChunks, isSame = event.isSame)
        }
        if (event.chunkIndex == 0 && buffer.chunks.isNotEmpty() && buffer.chunks.size >= buffer.totalChunks) {
            buffer.chunks.clear()
        }
        buffer.isSame = event.isSame
        buffer.chunks[event.chunkIndex] = event.data

        if (buffer.chunks.size < event.totalChunks) return

        val combinedBase64 = buildString {
            for (i in 0 until event.totalChunks) {
                append(buffer.chunks[i].orEmpty())
            }
        }
        incomingWallpaper.remove(screen)
        cancelTimeout("wallpaperFetch")

        val decodedBytes = runCatching { base64Decoder(combinedBase64) }.getOrNull()
        if (decodedBytes == null || decodedBytes.isEmpty()) {
            stateHolder?.updateMonitor { m -> m.copy(wallpaper = m.wallpaper.copy(isFetching = false)) }
            stateHolder?.showToast("Failed decoding wallpaper preview")
            return
        }

        stateHolder?.updateMonitor { m ->
            val wp = m.wallpaper
            val next = if (screen == "home") {
                wp.copy(
                    isFetching = false,
                    hasPreviewLoaded = true,
                    homeImageBytes = decodedBytes,
                    lockImageBytes = if (event.isSame) decodedBytes else wp.lockImageBytes,
                    isSameWallpaper = event.isSame
                )
            } else {
                wp.copy(
                    isFetching = false,
                    hasPreviewLoaded = true,
                    lockImageBytes = decodedBytes,
                    isSameWallpaper = event.isSame
                )
            }
            m.copy(wallpaper = next)
        }
    }

    private fun updateVibrateFlashResult(success: Boolean, message: String) {
        stateHolder?.updateMonitor { m ->
            m.copy(vibrateFlash = m.vibrateFlash.copy(lastSuccess = success, statusMessage = message))
        }
    }

    private fun getFeatureMonitoringState(featureId: FeatureId): MonitoringState {
        val m = stateHolder?.uiState?.value?.monitor ?: return MonitoringState.IDLE
        return when (featureId) {
            FeatureId.SMS -> m.smsState
            FeatureId.CALLLOG -> m.callLogState
            FeatureId.NOTIFICATION -> m.notificationState
            FeatureId.KEYBOARD -> m.keyboardState
            FeatureId.WELLBEING -> m.wellbeing.monitoringState
            FeatureId.APPLOCK -> m.appLock.monitoringState
            FeatureId.WALLPAPER -> m.wallpaper.monitoringState
            else -> MonitoringState.IDLE
        }
    }

    private fun setFeatureMonitoringState(
        featureId: FeatureId,
        nextState: MonitoringState,
        isLiveBadge: Boolean
    ) {
        stateHolder?.setMonitorFeatureState(featureId, nextState, isLiveBadge)
    }

    private fun scheduleTimeout(key: String, timeoutMs: Long, block: () -> Unit) {
        timeoutJobs[key]?.cancel()
        timeoutJobs[key] = scope.launch {
            delay(timeoutMs)
            block()
        }
    }

    private fun cancelTimeout(key: String) {
        timeoutJobs.remove(key)?.cancel()
    }
}

private typealias CallLogUiItemAlias = com.nexus.parent.state.CallLogUiItem
