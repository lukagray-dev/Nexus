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
import kotlinx.coroutines.launch
import nexus.android.parent.R
import nexus.android.parent.configuration.SupabaseConfig

class SignInActivity : AppCompatActivity() {

    private val supabase = SupabaseConfig.client
    
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var submitBtn: Button
    // private lateinit var forgotPasswordBtn: TextView // TODO: Add to layout
    private lateinit var showSignupBtn: TextView
    private lateinit var errorText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signin)
        
        // Cache elements
        cacheElements()
        
        // Setup listeners
        setupEventListeners()
        
        // Check if already signed in
        checkExistingSession()
    }

    private fun cacheElements() {
        emailInput = findViewById(R.id.signin_email)
        passwordInput = findViewById(R.id.signin_password)
        submitBtn = findViewById(R.id.signin_submit_btn)
        // forgotPasswordBtn = findViewById(R.id.forgot_password_btn) // TODO: Add to layout
        showSignupBtn = findViewById(R.id.show_signup_btn)
        errorText = findViewById(R.id.signin_error)
    }

    private fun setupEventListeners() {
        submitBtn.setOnClickListener {
            handleEmailSignIn()
        }
        
        // forgotPasswordBtn.setOnClickListener {
        //     handleForgotPassword()
        // }
        
        showSignupBtn.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }
    }

    private fun checkExistingSession() {
        lifecycleScope.launch {
            try {
                val session = supabase.auth.currentSessionOrNull()
                if (session != null) {
                    // User already signed in
                    navigateToUserInfo()
                }
            } catch (e: Exception) {
                // No session, stay on sign-in
            }
        }
    }

    private fun handleEmailSignIn() {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()

        if (email.isEmpty() || password.isEmpty()) {
            showError("Please enter email and password")
            return
        }

        setLoading(true)
        hideError()

        lifecycleScope.launch {
            try {
                supabase.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
                
                // Check if email is verified
                val user = supabase.auth.currentUserOrNull()
                if (user?.emailConfirmedAt == null) {
                    supabase.auth.signOut()
                    showError("Please verify your email before signing in. Check your inbox for the verification link.")
                    setLoading(false)
                    return@launch
                }
                
                // Sign in successful
                navigateToUserInfo()
            } catch (e: Exception) {
                setLoading(false)
                handleSignInError(e)
            }
        }
    }

    private fun handleForgotPassword() {
        val email = emailInput.text.toString().trim()

        if (email.isEmpty()) {
            showError("Please enter your email address first")
            return
        }

        hideError()

        lifecycleScope.launch {
            try {
                supabase.auth.resetPasswordForEmail(email)
                showSuccess("If an account exists with this email, you will receive a password reset link.")
            } catch (e: Exception) {
                showError("Failed to send reset email. Please try again.")
            }
        }
    }

    private fun handleSignInError(exception: Exception) {
        val message = when {
            exception.message?.contains("Invalid login credentials") == true -> "Invalid email or password."
            exception.message?.contains("Email not confirmed") == true -> "Please verify your email before signing in."
            exception.message?.contains("User not found") == true -> "No account found with this email."
            exception.message?.contains("Too many requests") == true -> "Too many failed attempts. Please try again later."
            else -> "Sign-in failed. Please try again."
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
        // forgotPasswordBtn.isEnabled = !loading // TODO: Add to layout
        submitBtn.text = if (loading) "Signing in..." else "Sign In"
    }

    private fun navigateToUserInfo() {
        val intent = Intent(this, UserInfoActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
