package nexus.android.parent

import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import nexus.android.parent.managers.DrawerManager
import nexus.android.parent.managers.PanelManager
import nexus.android.parent.managers.UIManager
import nexus.android.parent.webrtc.ConnectionManager
import kotlin.math.abs

/**
 * MainActivity - Main entry point of the Nexus Parent Android app
 * Manages the drawer layout, toolbar, and feature panels
 * Architecture follows the desktop app design with sliding sidebar and dynamic panels
 * Implements push-style drawer (like ChatGPT) where sidebar pushes content instead of overlaying
 */
class MainActivity : AppCompatActivity() {

    // UI Components
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var contentContainer: LinearLayout
    private lateinit var mainContentLayout: FrameLayout
    private lateinit var welcomeSection: LinearLayout
    private lateinit var arrowGesture: TextView
    private lateinit var menuIcon: ImageView
    private lateinit var aiIcon: ImageView
    private lateinit var profileIcon: ImageView
    private lateinit var toolbarTitle: TextView

    // Managers
    private lateinit var drawerManager: DrawerManager
    private lateinit var panelManager: PanelManager
    private lateinit var uiManager: UIManager
    
    // Connection
    private var childId: String? = null
    
    // Gesture detector for swipe to open
    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize ConnectionManager
        ConnectionManager.initialize(this)

        // Get child ID from intent
        childId = intent.getStringExtra("CHILD_ID")

        // Initialize UI components
        initializeViews()

        // Initialize managers
        initializeManagers()

        // Setup listeners
        setupListeners()
        
        // Enable swipe to open drawer
        setupSwipeGesture()
        
        // Setup modern back press handling
        setupBackPressHandler()
    }

    /**
     * Initialize all view references
     */
    private fun initializeViews() {
        // Drawer and navigation
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)
        contentContainer = findViewById(R.id.content_container)
        mainContentLayout = findViewById(R.id.main_content_layout)
        welcomeSection = findViewById(R.id.welcome_section)
        arrowGesture = findViewById(R.id.arrow_gesture)

        // Toolbar components
        menuIcon = findViewById(R.id.menu_icon)
        aiIcon = findViewById(R.id.ai_icon)
        profileIcon = findViewById(R.id.profile_icon)
        toolbarTitle = findViewById(R.id.toolbar_title)
        
        // Apply golden gradient to title (matching desktop)
        applyGradientToTitle()
        
        // Start arrow animation
        startArrowAnimation()
        
        // Load logo from assets into sidebar header
        loadLogoFromAssets()
    }
    
    /**
     * Load Nexus logo from assets folder
     */
    private fun loadLogoFromAssets() {
        try {
            // Find logo in the fixed header (now in parent FrameLayout, not NavigationView header)
            val drawerFrameLayout = navigationView.parent as? android.view.ViewGroup
            val logoImageView = drawerFrameLayout?.findViewById<ImageView>(R.id.app_logo)
            
            // Load image from assets
            val inputStream = assets.open("nexus_parent_icon.png")
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            logoImageView?.setImageBitmap(bitmap)
            inputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to placeholder if loading fails
        }
    }

    /**
     * Initialize all manager classes
     */
    private fun initializeManagers() {
        // UI Manager for animations
        uiManager = UIManager()

        // Drawer Manager for sidebar
        drawerManager = DrawerManager(drawerLayout, navigationView)

        // Panel Manager for feature panels
        panelManager = PanelManager(this, contentContainer, welcomeSection, uiManager)
    }

    /**
     * Setup modern back press handling using OnBackPressedDispatcher
     */
    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    drawerManager.isDrawerOpen() -> {
                        drawerManager.closeDrawer()
                    }
                    panelManager.getActivePanelCount() > 0 -> {
                        // Remove all panels before exiting
                        panelManager.removeAllPanels()
                    }
                    else -> {
                        // Let the system handle back press (exit app)
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    /**
     * Setup swipe gesture to open drawer
     * Professional implementation that works anywhere in the main content area
     */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun setupSwipeGesture() {
        // Enable swipe gesture for drawer
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        
        // Set scrim color (overlay when drawer is open) - transparent for push effect
        drawerLayout.setScrimColor(android.graphics.Color.TRANSPARENT)
        
        // Create gesture detector for swipe detection with smoother thresholds
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 30 // Lower threshold for more responsive opening
            private val SWIPE_VELOCITY_THRESHOLD = 30 // Lower velocity for smoother feel

            override fun onDown(e: MotionEvent): Boolean {
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                
                // Check if it's a horizontal swipe (more horizontal than vertical)
                if (abs(diffX) > abs(diffY) * 1.5f) { // More lenient horizontal detection
                    // Swipe right to left (close drawer)
                    if (diffX < 0 && abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (drawerManager.isDrawerOpen()) {
                            drawerManager.closeDrawer()
                            return true
                        }
                    }
                    // Swipe left to right (open drawer)
                    else if (diffX > 0 && abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (!drawerManager.isDrawerOpen()) {
                            drawerManager.openDrawer()
                            return true
                        }
                    }
                }
                return false
            }
        })
    }

    /**
     * Dispatch touch events to gesture detector to allow swipe anywhere
     */
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        // Check if gesture detector is initialized (might be null during early init)
        if (::gestureDetector.isInitialized && ev != null) {
            gestureDetector.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    /**
     * Setup all click listeners and callbacks
     */
    private fun setupListeners() {
        // Menu icon - toggle drawer
        menuIcon.setOnClickListener {
            drawerManager.toggleDrawer()
        }

        // AI icon - open AI assistant (placeholder)
        aiIcon.setOnClickListener {
            // Open AI Chat Activity
            val intent = android.content.Intent(this, nexus.android.parent.ai.AIChatActivity::class.java)
            startActivity(intent)
        }

        // Profile icon - show dropdown menu
        profileIcon.setOnClickListener {
            showProfileMenu()
        }

        // Feature selection listener
        drawerManager.setOnFeatureSelectedListener { featureType ->
            // Add feature panel to content area
            panelManager.addPanel(featureType)
            
            // Title stays as "Nexus Dashboard" - no change
        }

        // Drawer state listener for push animation
        drawerManager.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: android.view.View, slideOffset: Float) {
                // Push main content as drawer slides (ChatGPT style) with smooth interpolation
                val drawerWidth = drawerView.width
                // Use decelerate interpolator for smoother feel
                val interpolatedOffset = android.view.animation.DecelerateInterpolator(1.5f).getInterpolation(slideOffset)
                mainContentLayout.translationX = drawerWidth * interpolatedOffset
            }

            override fun onDrawerOpened(drawerView: android.view.View) {
                // Drawer fully opened
            }

            override fun onDrawerClosed(drawerView: android.view.View) {
                // Reset main content position
                mainContentLayout.translationX = 0f
            }

            override fun onDrawerStateChanged(newState: Int) {
                // Drawer state changed
            }
        })
    }

    /**
     * Show a toast message
     */
    private fun showToast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    /**
     * Apply golden gradient to title text (matching desktop theme)
     */
    private fun applyGradientToTitle() {
        toolbarTitle.post {
            val width = toolbarTitle.width
            val height = toolbarTitle.height
            
            if (width > 0 && height > 0) {
                val shader = android.graphics.LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    intArrayOf(
                        android.graphics.Color.parseColor("#ffffff"),  // White
                        android.graphics.Color.parseColor("#fbbf24")   // Gold
                    ),
                    null,
                    android.graphics.Shader.TileMode.CLAMP
                )
                toolbarTitle.paint.shader = shader
                toolbarTitle.invalidate()
            }
        }
    }

    /**
     * Start arrow gesture animation (sliding right animation)
     */
    private fun startArrowAnimation() {
        arrowGesture.animate()
            .translationX(20f)
            .alpha(1f)
            .setDuration(800)
            .withEndAction {
                arrowGesture.animate()
                    .translationX(0f)
                    .alpha(0.5f)
                    .setDuration(800)
                    .withEndAction {
                        // Repeat animation
                        startArrowAnimation()
                    }
                    .start()
            }
            .start()
    }

    /**
     * Show profile dropdown menu
     */
    private fun showProfileMenu() {
        val popupView = layoutInflater.inflate(R.layout.popup_profile_menu, null)
        val popupWindow = android.widget.PopupWindow(
            popupView,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        )
        
        // Set background drawable for proper dismissal
        popupWindow.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        popupWindow.elevation = 8f
        
        // Setup menu item click listeners
        popupView.findViewById<TextView>(R.id.menu_settings).setOnClickListener {
            popupWindow.dismiss()
            showToast("Settings - Coming Soon")
        }
        
        popupView.findViewById<TextView>(R.id.menu_disconnect).setOnClickListener {
            popupWindow.dismiss()
            showDisconnectConfirmation()
        }
        
        popupView.findViewById<TextView>(R.id.menu_delete_account).setOnClickListener {
            popupWindow.dismiss()
            showToast("Delete Account - Coming Soon")
        }
        
        // Show popup below profile icon
        popupWindow.showAsDropDown(profileIcon, -150, 8)
    }
    
    /**
     * Show disconnect confirmation dialog
     */
    private fun showDisconnectConfirmation() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_disconnect_confirmation, null)
        val cancelButton = dialogView.findViewById<android.widget.Button>(R.id.dialog_cancel_btn)
        val confirmButton = dialogView.findViewById<android.widget.Button>(R.id.dialog_confirm_btn)
        
        val dialog = android.app.AlertDialog.Builder(this, R.style.DarkDialogTheme)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        confirmButton.setOnClickListener {
            dialog.dismiss()
            handleDisconnect()
        }
        
        dialog.show()
    }
    
    /**
     * Handle disconnect from device
     */
    private fun handleDisconnect() {
        // Disconnect from child device
        if (ConnectionManager.isConnected()) {
            ConnectionManager.disconnect()
        }
        
        // Close all panels
        panelManager.removeAllPanels()
        
        // Navigate back to connection activity
        val intent = Intent(this, nexus.android.parent.connection.ConnectionActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save state if needed
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        // Restore state if needed
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cleanup drawer manager
        drawerManager?.cleanup()
        // Disconnect when activity is destroyed
        if (ConnectionManager.isConnected()) {
            ConnectionManager.disconnect()
        }
    }

    /**
     * Handle activity results (e.g., image picker for wallpaper feature)
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        // Forward to WallpaperFeature if it's active
        val wallpaperFeature = nexus.android.parent.features.FeatureFactory.getFeature(
            nexus.android.parent.managers.FeatureType.WALLPAPER
        ) as? nexus.android.parent.features.wallpaper.WallpaperFeature
        
        wallpaperFeature?.onActivityResult(requestCode, resultCode, data)
    }

    /**
     * Handle permission results
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        // Forward to MicFeature if it's active
        val micFeature = nexus.android.parent.features.FeatureFactory.getFeature(
            nexus.android.parent.managers.FeatureType.MIC
        ) as? nexus.android.parent.features.mic.MicFeature
        
        val granted = grantResults.isNotEmpty() && 
                     grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
        
        micFeature?.onPermissionResult(requestCode, granted)
    }

    /**
     * Get connected child ID
     */
    fun getChildId(): String? = childId
}
