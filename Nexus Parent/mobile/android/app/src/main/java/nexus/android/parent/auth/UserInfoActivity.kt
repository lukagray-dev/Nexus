package nexus.android.parent.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import nexus.android.parent.R
import nexus.android.parent.configuration.SupabaseConfig
import nexus.android.parent.connection.ConnectionActivity

@Serializable
data class UserProfile(
    val id: String,
    val name: String,
    val email: String,
    val subscription_plan: String? = "free",
    val subscription_status: String? = "active"
)

class UserInfoActivity : AppCompatActivity() {

    private val supabase = SupabaseConfig.client
    
    private lateinit var nameDisplay: TextView
    private lateinit var emailDisplay: TextView
    private lateinit var continueBtn: Button
    private lateinit var logoutBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_userinfo)
        
        // Cache elements
        cacheElements()
        
        // Setup listeners
        setupEventListeners()
        
        // Load user data
        loadUserData()
    }

    private fun cacheElements() {
        nameDisplay = findViewById(R.id.user_name_display)
        emailDisplay = findViewById(R.id.user_email_display)
        continueBtn = findViewById(R.id.continue_btn)
        logoutBtn = findViewById(R.id.logout_btn)
    }

    private fun setupEventListeners() {
        continueBtn.setOnClickListener {
            proceedToConnect()
        }
        
        logoutBtn.setOnClickListener {
            handleLogout()
        }
    }

    private fun loadUserData() {
        lifecycleScope.launch {
            try {
                val user = supabase.auth.currentUserOrNull()
                if (user == null) {
                    navigateToSignIn()
                    return@launch
                }

                // Display email immediately
                emailDisplay.text = user.email ?: "—"

                // Load user profile from Supabase database
                try {
                    val profiles = supabase.from("users")
                        .select {
                            filter {
                                eq("id", user.id)
                            }
                        }
                        .decodeList<UserProfile>()
                    
                    if (profiles.isNotEmpty()) {
                        val profile = profiles.first()
                        nameDisplay.text = profile.name
                    } else {
                        // Create default user profile if doesn't exist
                        val defaultName = user.userMetadata?.get("name")?.toString() 
                            ?: user.email?.split("@")?.first() 
                            ?: "User"
                        
                        val newProfile = UserProfile(
                            id = user.id,
                            name = defaultName,
                            email = user.email ?: "",
                            subscription_plan = "free",
                            subscription_status = "active"
                        )
                        
                        supabase.from("users").insert(newProfile)
                        nameDisplay.text = defaultName
                    }
                } catch (e: Exception) {
                    android.util.Log.e("UserInfoActivity", "Failed to load user profile", e)
                    // Fallback to user metadata
                    val fallbackName = user.userMetadata?.get("name")?.toString() 
                        ?: user.email?.split("@")?.first() 
                        ?: "User"
                    nameDisplay.text = fallbackName
                }
                
            } catch (e: Exception) {
                android.util.Log.e("UserInfoActivity", "Failed to get current user", e)
                navigateToSignIn()
            }
        }
    }

    private fun handleLogout() {
        lifecycleScope.launch {
            try {
                supabase.auth.signOut()
                navigateToSignIn()
            } catch (e: Exception) {
                android.util.Log.e("UserInfoActivity", "Logout failed", e)
                navigateToSignIn()
            }
        }
    }

    private fun proceedToConnect() {
        val intent = Intent(this, ConnectionActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun navigateToSignIn() {
        val intent = Intent(this, SignInActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
