package com.gourav.finango.ui.login

import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.gourav.finango.R
import com.gourav.finango.databinding.ActivityForgotPasswordBinding

class ForgotPassword : AppCompatActivity() {

    private lateinit var binding: ActivityForgotPasswordBinding
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Back button
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Reset password button
        binding.btnReset.setOnClickListener {
            val email = binding.emailInput.text?.toString()?.trim().orEmpty()

            // Basic validation
            if (email.isEmpty()) {
                toast("Please enter your email")
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                toast("Please enter a valid email")
                return@setOnClickListener
            }

            // Optionally disable button to avoid double-taps
            binding.btnReset.isEnabled = false

            // Call Firebase to send reset email
            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    binding.btnReset.isEnabled = true

                    if (task.isSuccessful) {
                        toast("Password reset link sent to $email")
                        // Optional: close this screen and go back to Login
                        finish()
                    } else {
                        val msg = task.exception?.localizedMessage
                            ?: "Failed to send reset email. Please try again."
                        toast(msg)
                    }
                }
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
