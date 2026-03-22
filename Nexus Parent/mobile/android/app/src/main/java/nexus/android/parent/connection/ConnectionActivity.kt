package nexus.android.parent.connection

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import nexus.android.parent.MainActivity
import nexus.android.parent.R
import nexus.android.parent.webrtc.ConnectionManager

/**
 * ConnectionActivity - Connection screen for connecting to child device
 * Follows desktop UI and connection flow
 */
class ConnectionActivity : AppCompatActivity() {

    private lateinit var childIdInput: EditText
    private lateinit var connectBtn: MaterialButton
    private lateinit var feedbackText: TextView
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var loadingText: TextView

    private var isConnecting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connection)

        // Initialize ConnectionManager
        ConnectionManager.initialize(this)

        // Find views
        childIdInput = findViewById(R.id.child_id_input)
        connectBtn = findViewById(R.id.connect_btn)
        feedbackText = findViewById(R.id.connect_feedback)
        loadingOverlay = findViewById(R.id.loading_overlay)
        loadingText = findViewById(R.id.loading_text)

        // Setup listeners
        setupListeners()
    }

    /**
     * Setup event listeners
     */
    private fun setupListeners() {
        // Format input as user types (add spaces every 4 digits)
        childIdInput.addTextChangedListener(object : TextWatcher {
            private var isFormatting = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isFormatting) return

                val input = s.toString().replace(" ", "")
                if (input.length <= 12) {
                    isFormatting = true
                    val formatted = formatChildId(input)
                    childIdInput.setText(formatted)
                    childIdInput.setSelection(formatted.length)
                    isFormatting = false

                    // Clear feedback when user types
                    if (feedbackText.text.isNotEmpty()) {
                        feedbackText.text = ""
                    }
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // Connect button click
        connectBtn.setOnClickListener {
            handleConnect()
        }

        // Enter key on keyboard
        childIdInput.setOnEditorActionListener { _, _, _ ->
            handleConnect()
            true
        }
    }

    /**
     * Format child ID with spaces (e.g., "1234 5678 9012")
     */
    private fun formatChildId(input: String): String {
        val sb = StringBuilder()
        for (i in input.indices) {
            if (i > 0 && i % 4 == 0) {
                sb.append(" ")
            }
            sb.append(input[i])
        }
        return sb.toString()
    }

    /**
     * Handle connect button click
     */
    private fun handleConnect() {
        if (isConnecting) return

        val childId = childIdInput.text.toString().replace(" ", "").trim()

        // Validate
        if (childId.length != 12) {
            showFeedback("Child ID must be exactly 12 digits.", isError = true)
            return
        }

        if (!childId.all { it.isDigit() }) {
            showFeedback("Child ID must contain only digits.", isError = true)
            return
        }

        // Start connection
        isConnecting = true
        showLoading("Connecting...")
        clearFeedback()

        lifecycleScope.launch {
            try {
                // Connect to child device
                ConnectionManager.connect(childId)

                // Wait for connection to establish
                loadingText.text = "Establishing connection..."
                ConnectionManager.waitForConnection()

                // Connection successful
                loadingText.text = "Connected!"
                
                // Navigate to MainActivity
                val intent = Intent(this@ConnectionActivity, MainActivity::class.java)
                intent.putExtra("CHILD_ID", childId)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()

            } catch (e: Exception) {
                // Connection failed
                hideLoading()
                showFeedback(e.message ?: "Connection failed. Please try again.", isError = true)
                isConnecting = false
            }
        }
    }

    /**
     * Show feedback message
     */
    private fun showFeedback(message: String, isError: Boolean = false) {
        feedbackText.text = message
        feedbackText.setTextColor(
            if (isError) {
                getColor(android.R.color.holo_red_light)
            } else {
                getColor(android.R.color.holo_green_light)
            }
        )
    }

    /**
     * Clear feedback message
     */
    private fun clearFeedback() {
        feedbackText.text = ""
    }

    /**
     * Show loading overlay
     */
    private fun showLoading(message: String) {
        loadingText.text = message
        loadingOverlay.visibility = View.VISIBLE
        connectBtn.isEnabled = false
        childIdInput.isEnabled = false
    }

    /**
     * Hide loading overlay
     */
    private fun hideLoading() {
        loadingOverlay.visibility = View.GONE
        connectBtn.isEnabled = true
        childIdInput.isEnabled = true
    }

    override fun onBackPressed() {
        if (!isConnecting) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
