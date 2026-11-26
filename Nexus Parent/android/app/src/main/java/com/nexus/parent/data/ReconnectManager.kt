package com.nexus.parent.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ReconnectStatus {
    IDLE,
    WAITING,
    CONNECTING
}

data class ReconnectTargetDevice(
    val rawId: String,
    val formattedId: String,
    val name: String,
    val model: String
)

data class ReconnectState(
    val status: ReconnectStatus = ReconnectStatus.IDLE,
    val isActive: Boolean = false,
    val currentAttempt: Int = 0,
    val maxAttempts: Int = ReconnectManager.MAX_RECONNECT_ATTEMPTS,
    val secondsRemaining: Int = 0,
    val totalDelaySeconds: Int = 0,
    val targetDevice: ReconnectTargetDevice? = null,
    val lastError: String? = null
)

/**
 * ReconnectManager.kt - 9-attempt exponential-backoff scheduler matching Desktop `reconnect-manager.ts`.
 *
 * Hey friend! When an active WebRTC link drops unexpectedly (Wi-Fi blip, cellular handoff, etc.),
 * we automatically retry up to 9 times with increasing delays:
 * 10s -> 30s -> 60s -> 90s -> 120s -> 150s -> 180s -> 210s -> 240s.
 * Every second, `secondsRemaining` updates so the drawer pill and DeviceStatusPopover display
 * a live countdown, and the parent can tap "Retry Now" or "Cancel Reconnection" at any time.
 */
class ReconnectManager(
    private val scope: CoroutineScope,
    private val delaysSeconds: List<Int> = DEFAULT_RECONNECT_DELAYS_SECONDS,
    private val maxAttempts: Int = MAX_RECONNECT_ATTEMPTS,
    private val tickIntervalMs: Long = 1000L
) {

    companion object {
        const val MAX_RECONNECT_ATTEMPTS = 9
        val DEFAULT_RECONNECT_DELAYS_SECONDS: List<Int> = listOf(10, 30, 60, 90, 120, 150, 180, 210, 240)
    }

    private val _state = MutableStateFlow(ReconnectState(maxAttempts = maxAttempts))
    val state: StateFlow<ReconnectState> = _state.asStateFlow()

    private var countdownJob: Job? = null
    private var attemptJob: Job? = null
    private var isCancelled = false

    private var connectHandler: (suspend (String) -> Boolean)? = null
    private var onCancelledCallback: (() -> Unit)? = null
    private var onExhaustedCallback: ((ReconnectTargetDevice?) -> Unit)? = null
    private var onSucceededCallback: ((ReconnectTargetDevice?) -> Unit)? = null

    fun setConnectHandler(handler: suspend (String) -> Boolean) {
        connectHandler = handler
    }

    fun setCallbacks(
        onCancelled: () -> Unit,
        onExhausted: (ReconnectTargetDevice?) -> Unit,
        onSucceeded: (ReconnectTargetDevice?) -> Unit
    ) {
        onCancelledCallback = onCancelled
        onExhaustedCallback = onExhausted
        onSucceededCallback = onSucceeded
    }

    fun isReconnecting(): Boolean {
        val s = _state.value
        return s.isActive && (s.status == ReconnectStatus.WAITING || s.status == ReconnectStatus.CONNECTING)
    }

    fun start(target: ReconnectTargetDevice) {
        val current = _state.value
        if (current.isActive && current.targetDevice?.rawId == target.rawId) {
            return
        }

        clearTimers()
        isCancelled = false
        val firstDelay = delaysSeconds.firstOrNull() ?: 10

        _state.value = ReconnectState(
            status = ReconnectStatus.WAITING,
            isActive = true,
            currentAttempt = 1,
            maxAttempts = maxAttempts,
            secondsRemaining = firstDelay,
            totalDelaySeconds = firstDelay,
            targetDevice = target,
            lastError = null
        )
        startCountdown()
    }

    fun retryNow() {
        val s = _state.value
        if (!s.isActive || s.status == ReconnectStatus.CONNECTING || s.targetDevice == null) return
        clearTimers()
        _state.value = s.copy(secondsRemaining = 0)
        launchAttempt()
    }

    fun cancel(silent: Boolean = false) {
        val s = _state.value
        if (!s.isActive && s.status == ReconnectStatus.IDLE) return

        isCancelled = true
        clearTimers()
        _state.value = ReconnectState(maxAttempts = maxAttempts)
        if (!silent) {
            onCancelledCallback?.invoke()
        }
    }

    fun handleSuccess() {
        val s = _state.value
        if (!s.isActive && s.status == ReconnectStatus.IDLE) return

        val device = s.targetDevice
        clearTimers()
        _state.value = ReconnectState(maxAttempts = maxAttempts)
        onSucceededCallback?.invoke(device)
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            while (true) {
                delay(tickIntervalMs)
                val s = _state.value
                if (!s.isActive || s.status != ReconnectStatus.WAITING || isCancelled) break

                if (s.secondsRemaining > 1) {
                    _state.value = s.copy(secondsRemaining = s.secondsRemaining - 1)
                } else {
                    _state.value = s.copy(secondsRemaining = 0)
                    launchAttempt()
                    break
                }
            }
        }
    }

    private fun launchAttempt() {
        val target = _state.value.targetDevice ?: return
        val handler = connectHandler
        if (handler == null) {
            handleAttemptFailure("No connect handler available")
            return
        }

        _state.value = _state.value.copy(status = ReconnectStatus.CONNECTING)
        attemptJob?.cancel()
        attemptJob = scope.launch {
            val result = runCatching { handler(target.rawId) }
            if (isCancelled) return@launch

            val succeeded = result.getOrDefault(false)
            if (succeeded) {
                handleSuccess()
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Connection failed"
                handleAttemptFailure(msg)
            }
        }
    }

    private fun handleAttemptFailure(errorMessage: String) {
        val s = _state.value
        if (isCancelled || !s.isActive) return

        if (s.currentAttempt < maxAttempts) {
            val nextAttempt = s.currentAttempt + 1
            val delayIdx = (nextAttempt - 1).coerceIn(0, (delaysSeconds.size - 1).coerceAtLeast(0))
            val nextDelay = delaysSeconds.getOrElse(delayIdx) { 10 }
            _state.value = s.copy(
                status = ReconnectStatus.WAITING,
                currentAttempt = nextAttempt,
                totalDelaySeconds = nextDelay,
                secondsRemaining = nextDelay,
                lastError = errorMessage
            )
            startCountdown()
        } else {
            val failedTarget = s.targetDevice
            clearTimers()
            _state.value = ReconnectState(maxAttempts = maxAttempts, lastError = errorMessage)
            onExhaustedCallback?.invoke(failedTarget)
        }
    }

    private fun clearTimers() {
        countdownJob?.cancel()
        countdownJob = null
        attemptJob?.cancel()
        attemptJob = null
    }
}
