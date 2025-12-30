package com.gourav.finango.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.gourav.finango.MainActivity
import com.gourav.finango.R
import com.gourav.finango.databinding.ActivityLoginScreenBinding
import com.gourav.finango.ui.profile.ProfileSetup
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.log

class LoginScreen : AppCompatActivity() {
    private lateinit var binding: ActivityLoginScreenBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val prefs = getSharedPreferences("login_prefs", MODE_PRIVATE)
        val savedEmail = prefs.getString("email", "")
        val savedPassword = prefs.getString("password", "")
        binding.emailInput.setText(savedEmail)
        binding.passwordInput.setText(savedPassword)
        binding.checkboxRemember.isChecked = !savedEmail.isNullOrEmpty() && !savedPassword.isNullOrEmpty()


        binding.loginBtn.setOnClickListener {toggleToLogin()}
        binding.signupBtn.setOnClickListener {toggleToSingup()}
        binding.forgotPassword.setOnClickListener {navigateToForgotPassword()}
        binding.btnLogin.setOnClickListener { loginHandling() }
        binding.btnSignup.setOnClickListener { signupHandling() }





    }
    fun toggleToLogin(){

            // Apply selected background to login
            binding.loginBtn.setBackgroundResource(R.drawable.bg_toggle_selected)
            binding.signupBtn.setBackgroundResource(android.R.color.transparent) // or bg_toggle if you want default

            // Optional: change text color
            binding.loginBtn.setTextColor(ContextCompat.getColor(this, android.R.color.black))
            binding.signupBtn.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))

            binding.confirmPasswordInput.visibility=View.GONE
            binding.btnLoginContainer.visibility=View.VISIBLE
            binding.btnSignupContainer.visibility=View.GONE
    }
    fun toggleToSingup(){
        // Apply selected background to signup
        binding.signupBtn.setBackgroundResource(R.drawable.bg_toggle_selected)
        binding.loginBtn.setBackgroundResource(android.R.color.transparent) // or bg_toggle

        // Optional: change text color
        binding.signupBtn.setTextColor(ContextCompat.getColor(this, android.R.color.black))
        binding.loginBtn.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))

        //confirm password visibility
        binding.confirmPasswordInput.visibility=View.VISIBLE
        binding.btnSignupContainer.visibility=View.VISIBLE
        binding.btnLoginContainer.visibility=View.GONE
    }
    fun navigateToForgotPassword(){
        val intent=Intent(this,ForgotPassword::class.java)
        startActivity(intent)
    }


    fun loginHandling() {
        val email = binding.emailInput.text?.toString()?.trim().orEmpty()
        val password = binding.passwordInput.text?.toString()?.trim().orEmpty()

        // --- Validation ---
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            toast("Enter a valid email"); return
        }
        if (password.isEmpty()) {
            toast("Enter your password"); return
        }

        // --- UI: spinner in button ---
        binding.loginProgress.visibility=View.VISIBLE
        binding.btnLogin.isEnabled=false

        lifecycleScope.launch {
            try {
                val auth = FirebaseAuth.getInstance()
                val result = auth.signInWithEmailAndPassword(email, password).await()
                val uid = result.user?.uid ?: error("No UID")

                // Ensure user doc exists (in case it failed at signup earlier)
                val db = FirebaseFirestore.getInstance()
                val userRef = db.collection("users").document(uid)
                val snap = userRef.get().await()
                if (!snap.exists()) {
                    userRef.set(
                        mapOf(
                            "email" to (result.user?.email ?: email),
                            "profileCompleted" to false,
                            "createdAt" to com.google.firebase.Timestamp.now()
                        )
                    ).await()
                }
                // ✅ Save email & password if Remember Me is checked
                val prefs = getSharedPreferences("login_prefs", MODE_PRIVATE)
                if (binding.checkboxRemember.isChecked) {
                    prefs.edit()
                        .putString("email", email)
                        .putString("password", password)
                        .apply()
                } else {
                    prefs.edit().clear().apply()
                }

                // Go to Main; MainActivity will route to Profile/Dashboard
                startActivity(Intent(this@LoginScreen, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                })
                finish()

            }catch (e: Exception) {

                binding.loginProgress.visibility = View.GONE
                binding.btnLogin.isEnabled = true

                val errorMessage = when {
                    e.message?.contains("password is invalid") == true ->
                        "Incorrect password. Please try again."

                    e.message?.contains("no user record") == true ->
                        "No account found with this email."

                    e.message?.contains("disabled") == true ->
                        "Your account has been disabled. Contact support."

                    e.message?.contains("network") == true ->
                        "Network error. Please check your internet connection."

                    e.message?.contains("too many") == true ->
                        "Too many failed attempts. Try again later."

                    else ->
                        "Login failed. Please check your email & password."
                }

                toast(errorMessage)
                android.util.Log.e("Auth", "Login failed", e)
            }

        }
    }



    fun signupHandling(){
        val email = binding.emailInput.text?.toString()?.trim().orEmpty()
        val password = binding.passwordInput.text?.toString()?.trim().orEmpty()
        val confirm = binding.confirmPasswordInput.text?.toString()?.trim().orEmpty()
        // --- Validation ---
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            toast("Enter a valid email")
            return
        }

        if (password != confirm) {
            toast("Passwords do not match")
            return
        }
        binding.signupProgress.visibility=View.VISIBLE
        binding.btnSignup.isEnabled=false
        lifecycleScope.launch {
            try {
                val result = FirebaseAuth.getInstance()
                    .createUserWithEmailAndPassword(email, password)
                    .await()

                val uid = result.user?.uid ?: throw IllegalStateException("No UID")

                // minimal user document
                val userDoc = mapOf(
                    "email" to email,
                    "profileCompleted" to false,
                    "createdAt" to com.google.firebase.Timestamp.now()
                )
                FirebaseFirestore.getInstance()
                    .collection("users").document(uid)
                    .set(userDoc)
                    .await()

                // Go to Main; MainActivity routes to Profile if needed
                startActivity(Intent(this@LoginScreen, ProfileSetup::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                })
                finish()

            } catch (e: Exception) {
                binding.signupProgress.visibility=View.GONE
                binding.btnSignup.isEnabled=true
                toast("something went wrong")
            }
        }

    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()


}