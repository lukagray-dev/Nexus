package nexus.android.parent.managers

import android.app.AlertDialog
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import nexus.android.parent.R
import nexus.android.parent.webrtc.ConnectionManager

/**
 * DrawerManager - Manages navigation drawer state and interactions
 * Handles sidebar opening/closing and menu item selection
 */
class DrawerManager(
    private val drawerLayout: DrawerLayout,
    private val navigationView: NavigationView
) {

    private var onFeatureSelectedListener: ((FeatureType) -> Unit)? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var stealthModeButton: MaterialButton? = null
    private var isStealthActive = false
    private var footerContainer: FrameLayout? = null

    init {
        setupDrawer()
        setupStealthMode()
    }

    /**
     * Setup drawer behavior and listeners
     */
    private fun setupDrawer() {
        // Set drawer width to 70% of screen width
        val displayMetrics = navigationView.context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val drawerWidth = (screenWidth * 0.7).toInt()
        
        // Set width on parent FrameLayout
        val frameLayout = navigationView.parent as? android.view.ViewGroup
        val params = frameLayout?.layoutParams
        params?.width = drawerWidth
        frameLayout?.layoutParams = params

        // Setup navigation item selection listener
        navigationView.setNavigationItemSelectedListener { menuItem ->
            handleMenuItemClick(menuItem)
            true
        }
    }

    /**
     * Setup stealth mode button
     */
    private fun setupStealthMode() {
        // Find the parent FrameLayout
        val frameLayout = navigationView.parent as? android.view.ViewGroup
        
        // Create a container for the footer that overlays at the bottom
        footerContainer = FrameLayout(navigationView.context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
            }
        }
        
        // Inflate footer into container
        val footerView = View.inflate(navigationView.context, R.layout.sidebar_footer, footerContainer)
        
        // Add container to parent FrameLayout
        frameLayout?.addView(footerContainer)
        
        // Find button
        stealthModeButton = footerContainer?.findViewById(R.id.stealth_mode_btn)
        
        stealthModeButton?.setOnClickListener {
            handleStealthModeClick()
        }
    }

    /**
     * Handle stealth mode button click
     */
    private fun handleStealthModeClick() {
        val context = navigationView.context
        
        // Show confirmation dialog
        val dialogView = View.inflate(context, R.layout.dialog_stealth_confirmation, null)
        val titleText = dialogView.findViewById<TextView>(R.id.dialog_title)
        val messageText = dialogView.findViewById<TextView>(R.id.dialog_message)
        val cancelButton = dialogView.findViewById<Button>(R.id.dialog_cancel_btn)
        val confirmButton = dialogView.findViewById<Button>(R.id.dialog_confirm_btn)
        
        // Set content based on current state
        if (isStealthActive) {
            titleText.text = context.getString(R.string.stealth_deactivate_title)
            messageText.text = context.getString(R.string.stealth_deactivate_message)
            confirmButton.text = context.getString(R.string.stealth_deactivate_button)
        } else {
            titleText.text = context.getString(R.string.stealth_activate_title)
            messageText.text = context.getString(R.string.stealth_activate_message)
            confirmButton.text = context.getString(R.string.stealth_activate_button)
        }
        
        val dialog = AlertDialog.Builder(context, R.style.DarkDialogTheme)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        confirmButton.setOnClickListener {
            dialog.dismiss()
            toggleStealthMode()
        }
        
        dialog.show()
    }

    /**
     * Toggle stealth mode
     */
    private fun toggleStealthMode() {
        val context = navigationView.context
        val button = stealthModeButton ?: return
        
        // Disable button and show loading
        button.isEnabled = false
        val originalText = button.text
        button.text = context.getString(R.string.stealth_processing)
        
        scope.launch {
            try {
                if (isStealthActive) {
                    // Deactivate stealth mode
                    ConnectionManager.sendCommand("STEALTH_OFF")
                    
                    // Wait for confirmation
                    withTimeout(30000) {
                        ConnectionManager.dataChannelEvents
                            .filter { it == "STEALTH_OFF_ACK" }
                            .first()
                    }
                    
                    // Update UI
                    isStealthActive = false
                    button.text = context.getString(R.string.stealth_mode)
                    button.icon = context.getDrawable(R.drawable.ic_eye)
                    
                } else {
                    // Activate stealth mode
                    ConnectionManager.sendCommand("STEALTH_ON")
                    
                    // Wait for confirmation
                    withTimeout(30000) {
                        ConnectionManager.dataChannelEvents
                            .filter { it == "STEALTH_ON_ACK" }
                            .first()
                    }
                    
                    // Update UI
                    isStealthActive = true
                    button.text = context.getString(R.string.stealth_active)
                    button.icon = context.getDrawable(R.drawable.ic_stealth)
                }
                
                button.isEnabled = true
                
            } catch (e: TimeoutCancellationException) {
                // Timeout - restore button
                button.text = originalText
                button.isEnabled = true
                
                // Show error
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(context, R.style.DarkDialogTheme)
                        .setTitle(context.getString(R.string.error))
                        .setMessage(context.getString(R.string.stealth_timeout))
                        .setPositiveButton(context.getString(R.string.close)) { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                }
                
            } catch (e: Exception) {
                // Error - restore button
                button.text = originalText
                button.isEnabled = true
                
                // Show error
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(context, R.style.DarkDialogTheme)
                        .setTitle(context.getString(R.string.error))
                        .setMessage("${context.getString(R.string.stealth_error)}: ${e.message}")
                        .setPositiveButton(context.getString(R.string.close)) { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                }
            }
        }
    }

    /**
     * Handle menu item click
     */
    private fun handleMenuItemClick(menuItem: MenuItem) {
        val featureType = when (menuItem.itemId) {
            R.id.nav_camera -> FeatureType.CAMERA
            R.id.nav_screen -> FeatureType.SCREEN
            R.id.nav_location -> FeatureType.LOCATION
            R.id.nav_sms -> FeatureType.SMS
            R.id.nav_calllog -> FeatureType.CALLLOG
            R.id.nav_mic -> FeatureType.MIC
            R.id.nav_notifications -> FeatureType.NOTIFICATIONS
            R.id.nav_chats -> FeatureType.CHATS
            R.id.nav_gmail -> FeatureType.GMAIL
            R.id.nav_files -> FeatureType.FILES
            R.id.nav_wellbeing -> FeatureType.WELLBEING
            R.id.nav_applock -> FeatureType.APPLOCK
            R.id.nav_wallpaper -> FeatureType.WALLPAPER
            R.id.nav_vibrateflash -> FeatureType.VIBRATEFLASH
            // Social Media
            R.id.nav_whatsapp -> FeatureType.WHATSAPP
            R.id.nav_facebook -> FeatureType.FACEBOOK
            R.id.nav_instagram -> FeatureType.INSTAGRAM
            R.id.nav_messenger -> FeatureType.MESSENGER
            R.id.nav_snapchat -> FeatureType.SNAPCHAT
            R.id.nav_telegram -> FeatureType.TELEGRAM
            // AI Assistants
            R.id.nav_chatgpt -> FeatureType.CHATGPT
            R.id.nav_claude -> FeatureType.CLAUDE
            R.id.nav_grok -> FeatureType.GROK
            else -> return
        }

        onFeatureSelectedListener?.invoke(featureType)
        closeDrawer()
    }

    /**
     * Open the drawer
     */
    fun openDrawer() {
        val drawerView = navigationView.parent as? android.view.View
        if (drawerView != null) {
            drawerLayout.openDrawer(drawerView)
        }
    }

    /**
     * Close the drawer
     */
    fun closeDrawer() {
        val drawerView = navigationView.parent as? android.view.View
        if (drawerView != null) {
            drawerLayout.closeDrawer(drawerView)
        }
    }

    /**
     * Toggle drawer open/close
     */
    fun toggleDrawer() {
        if (isDrawerOpen()) {
            closeDrawer()
        } else {
            openDrawer()
        }
    }

    /**
     * Check if drawer is open
     */
    fun isDrawerOpen(): Boolean {
        // Check the parent FrameLayout instead of NavigationView
        val drawerView = navigationView.parent as? android.view.View
        return if (drawerView != null) {
            drawerLayout.isDrawerOpen(drawerView)
        } else {
            false
        }
    }

    /**
     * Set listener for feature selection
     */
    fun setOnFeatureSelectedListener(listener: (FeatureType) -> Unit) {
        onFeatureSelectedListener = listener
    }

    /**
     * Add drawer state listener
     */
    fun addDrawerListener(listener: DrawerLayout.DrawerListener) {
        drawerLayout.addDrawerListener(listener)
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        scope.cancel()
    }
}

/**
 * Enum representing all available features (23 total - matching desktop)
 */
enum class FeatureType {
    // Core Features
    CAMERA,
    SCREEN,
    LOCATION,
    SMS,
    CALLLOG,
    MIC,
    NOTIFICATIONS,
    CHATS,
    GMAIL,
    FILES,
    WELLBEING,
    APPLOCK,
    WALLPAPER,
    VIBRATEFLASH,
    
    // Social Media
    WHATSAPP,
    FACEBOOK,
    INSTAGRAM,
    MESSENGER,
    SNAPCHAT,
    TELEGRAM,
    
    // AI Assistants
    CHATGPT,
    CLAUDE,
    GROK
}
