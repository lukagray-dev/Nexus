package nexus.android.parent.features.vibrateflash

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.*
import nexus.android.parent.R
import nexus.android.parent.features.BaseFeature
import nexus.android.parent.webrtc.ConnectionManager
import org.json.JSONObject

/**
 * VibrateFlashFeature - Remote vibration and flashlight control
 * Allows parents to trigger various vibration and flash patterns on child device
 */
class VibrateFlashFeature(context: Context) : BaseFeature(context) {

    private var vibrateGrid: GridLayout? = null
    private var flashGrid: GridLayout? = null
    private var combinedGrid: GridLayout? = null
    private var statusText: TextView? = null
    private var scrollView: ScrollView? = null
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var statusJob: Job? = null

    // Pattern definitions matching desktop app
    private val vibratePatterns = listOf(
        PatternButton("Short Buzz", "short", "#60a5fa"),
        PatternButton("Double Tap", "double", "#60a5fa"),
        PatternButton("Triple Tap", "triple", "#60a5fa"),
        PatternButton("SOS Pattern", "sos", "#ef4444"),
        PatternButton("Heartbeat", "heartbeat", "#ec4899"),
        PatternButton("Wave", "wave", "#a855f7"),
        PatternButton("Earthquake", "earthquake", "#f97316")
    )
    
    private val flashPatterns = listOf(
        PatternButton("Single Blink", "blink", "#fbbf24"),
        PatternButton("Double Blink", "double_blink", "#fbbf24"),
        PatternButton("Strobe", "strobe", "#fbbf24"),
        PatternButton("SOS Flash", "sos_flash", "#ef4444"),
        PatternButton("Pulse", "pulse", "#ec4899"),
        PatternButton("Beacon", "beacon", "#a855f7"),
        PatternButton("Disco", "disco", "#f97316")
    )
    
    private val combinedPatterns = listOf(
        PatternButton("Alert", "alert", "#4ade80"),
        PatternButton("Attention", "attention", "#f97316"),
        PatternButton("Emergency", "emergency", "#ef4444")
    )

    override fun createView(container: ViewGroup): View {
        // Container is the panel root, find views from it
        vibrateGrid = container.findViewById(R.id.vibrate_grid)
        flashGrid = container.findViewById(R.id.flash_grid)
        combinedGrid = container.findViewById(R.id.combined_grid)
        statusText = container.findViewById(R.id.vibrateflash_status)
        
        // Find ScrollView and enable nested scrolling
        val panelContent = container.findViewById<ViewGroup>(R.id.panel_content)
        scrollView = panelContent?.getChildAt(0) as? ScrollView
        scrollView?.let { sv ->
            sv.isNestedScrollingEnabled = true
            // Request parent to not intercept touch events when scrolling
            sv.setOnTouchListener { v, event ->
                v.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
        }
        
        setupButtons()
        
        return container
    }

    private fun setupButtons() {
        // Setup vibration buttons
        vibratePatterns.forEach { pattern ->
            val button = createPatternButton(pattern)
            button.setOnClickListener {
                sendVibrateCommand(pattern.pattern)
            }
            vibrateGrid?.addView(button)
        }
        
        // Setup flash buttons
        flashPatterns.forEach { pattern ->
            val button = createPatternButton(pattern)
            button.setOnClickListener {
                sendFlashCommand(pattern.pattern)
            }
            flashGrid?.addView(button)
        }
        
        // Setup combined buttons
        combinedPatterns.forEach { pattern ->
            val button = createPatternButton(pattern)
            button.setOnClickListener {
                sendCombinedCommand(pattern.pattern)
            }
            combinedGrid?.addView(button)
        }
    }

    private fun createPatternButton(pattern: PatternButton): Button {
        return Button(context).apply {
            text = pattern.label
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor(pattern.color))
            
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(8, 8, 8, 8)
            }
            layoutParams = params
            
            setPadding(24, 32, 24, 32)
            gravity = Gravity.CENTER
            isAllCaps = false
            
            // Rounded corners
            background = context.getDrawable(android.R.drawable.btn_default)?.apply {
                setTint(Color.parseColor(pattern.color))
            }
        }
    }

    private fun sendVibrateCommand(pattern: String) {
        try {
            val command = JSONObject().apply {
                put("cmd", "VIBRATE")
                put("pattern", pattern)
            }
            ConnectionManager.sendCommand(command.toString())
            
            showStatus("Sending vibration: $pattern...", "#60a5fa")
        } catch (e: Exception) {
            showStatus("Failed to send command", "#ef4444")
        }
    }

    private fun sendFlashCommand(pattern: String) {
        try {
            val command = JSONObject().apply {
                put("cmd", "FLASH")
                put("pattern", pattern)
            }
            ConnectionManager.sendCommand(command.toString())
            
            showStatus("Sending flash: $pattern...", "#fbbf24")
        } catch (e: Exception) {
            showStatus("Failed to send command", "#ef4444")
        }
    }

    private fun sendCombinedCommand(pattern: String) {
        try {
            val command = JSONObject().apply {
                put("cmd", "VIBRATE_FLASH")
                put("pattern", pattern)
            }
            ConnectionManager.sendCommand(command.toString())
            
            showStatus("Sending combined: $pattern...", "#4ade80")
        } catch (e: Exception) {
            showStatus("Failed to send command", "#ef4444")
        }
    }

    private fun showStatus(message: String, color: String) {
        statusJob?.cancel()
        
        statusText?.apply {
            text = message
            setTextColor(Color.parseColor(color))
        }
        
        // Clear status after 3 seconds
        statusJob = scope.launch {
            delay(3000)
            statusText?.text = ""
        }
    }

    override fun onStop() {
        super.onStop()
        statusJob?.cancel()
        scope.cancel()
    }

    override fun getTitle(): String = context.getString(R.string.feature_vibrateflash)
    override fun getDescription(): String = context.getString(R.string.feature_vibrateflash_desc)

    data class PatternButton(
        val label: String,
        val pattern: String,
        val color: String
    )
}
