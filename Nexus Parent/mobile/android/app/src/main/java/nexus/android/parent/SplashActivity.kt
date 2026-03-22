package nexus.android.parent

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import nexus.android.parent.auth.AuthManager

/**
 * Splash Activity
 * Checks authentication state and routes to appropriate screen
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        
        // Initialize AuthManager
        authManager = AuthManager(this)
        authManager.init()
        
        // Check auth state and navigate
        Handler(Looper.getMainLooper()).postDelayed({
            navigateToAppropriateScreen()
        }, 500) // Small delay for smooth transition
    }

    private fun navigateToAppropriateScreen() {
        lifecycleScope.launch {
            val startActivity = authManager.getStartActivity()
            val intent = Intent(this@SplashActivity, startActivity)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
