package com.gourav.finango.ui.login

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.gourav.finango.R
import com.gourav.finango.databinding.ActivityForgotPasswordBinding

class ForgotPassword : AppCompatActivity() {
    private lateinit var binding:ActivityForgotPasswordBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding=ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.btnBack.setOnClickListener{
            finish()
        }

        binding.btnReset.setOnClickListener{
            val email=binding.emailInput.text.trim().toString()
            if(!email.isEmpty()){

            }else{

            }
        }
    }
}