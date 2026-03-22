package nexus.android.parent.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import nexus.android.parent.R
import nexus.android.parent.configuration.SupabaseConfig

class SignUpActivity : AppCompatActivity() {

    private val supabase = SupabaseConfig.client
    
    private lateinit var nameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var confirmInput: EditText
    private lateinit var submitBtn: Button
    private lateinit var showSigninBtn: TextView
    private lateinit var errorText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)
        
        // Cache elements
        cacheElements()
        
        // Setup listeners
        setupEventListeners()
    }

    private fun cacheElements() {
        nameInput = findViewById(R.id.signup_name)
        emailInput = findViewById(R.id.signup_email)
        passwordInput = findViewById(R.id.signup_password)
        confirmInput = findViewById(R.id.signup_confirm)
        submitBtn = findViewById(R.id.signup_submit_btn)
        showSigninBtn = findViewById(R.id.show_signin_btn)
        errorText = findViewById(R.id.signup_error)
    }

    private fun setupEventListeners() {
        submitBtn.setOnClickListener {
            handleSignUp()
        }
        
        showSigninBtn.setOnClickListener {
            startActivity(Intent(this, SignInActivity::class.java))
            finish()
        }
    }

    private fun handleSignUp() {
        val name = nameInput.text.toString().trim()
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()
        val confirm = confirmInput.text.toString()

        // Validation
        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
            showError("Please fill in all fields")
            return
        }

        if (!isValidEmail(email)) {
            showError("Please enter a valid email address")
            return
        }

        if (password.length < 6) {
            showError("Password must be at least 6 characters")
            return
        }

        if (password != confirm) {
            showError("Passwords do not match")
            return
        }

        setLoading(true)
        hideError()

        lifecycleScope.launch {
            try {
                // Sign up with Supabase Auth
                val currentUser = supabase.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }
                
                // Check if user already exists (identities will be empty if user exists)
                if (currentUser?.identities?.isEmpty() == true) {
                    setLoading(false)
                    showError("An account with this email already exists. Please sign in instead.")
                    // Navigate to sign-in after 2 seconds
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        startActivity(Intent(this@SignUpActivity, SignInActivity::class.java))
                        finish()
                    }, 2000)
                    return@launch
                }
                
                // Save user profile to Supabase database
                val userId = currentUser?.id
                if (userId != null) {
                    try {
                        supabase.from("users").insert(
                            mapOf(
                                "id" to userId,
                                "name" to name,
                                "email" to email,
                                "subscription_plan" to "free",
                                "subscription_status" to "active"
                            )
                        )
                    } catch (e: Exception) {
                        // Profile creation failed, but auth succeeded
                        android.util.Log.w("SignUpActivity", "Failed to save user profile", e)
                    }
                }
                
                setLoading(false)
                
                // Show success message
                showSuccess("Account created! Please check your email to verify your account.")
                
                // Navigate to sign-in after 3 seconds
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    startActivity(Intent(this@SignUpActivity, SignInActivity::class.java))
                    finish()
                }, 3000)
                
            } catch (e: Exception) {
                setLoading(false)
                handleSignUpError(e)
            }
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun handleSignUpError(exception: Exception) {
        val message = when {
            exception.message?.contains("already registered") == true || 
            exception.message?.contains("User already registered") == true -> 
                "An account with this email already exists. Please sign in instead."
            exception.message?.contains("invalid email") == true -> 
                "Invalid email address."
            exception.message?.contains("weak password") == true || 
            exception.message?.contains("Password") == true -> 
                "Password is too weak. Use at least 6 characters."
            else -> exception.message ?: "Sign-up failed. Please try again."
        }
        showError(message)
    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.setTextColor(getColor(android.R.color.holo_red_light))
        errorText.visibility = View.VISIBLE
    }

    private fun showSuccess(message: String) {
        errorText.text = message
        errorText.setTextColor(getColor(android.R.color.holo_green_light))
        errorText.visibility = View.VISIBLE
    }

    private fun hideError() {
        errorText.visibility = View.GONE
    }

    private fun setLoading(loading: Boolean) {
        submitBtn.isEnabled = !loading
        submitBtn.text = if (loading) "Creating account..." else "Sign Up"
    }
}
