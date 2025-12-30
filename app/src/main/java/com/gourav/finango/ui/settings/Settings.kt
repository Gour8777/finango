package com.gourav.finango.ui.settings

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.gourav.finango.databinding.ActivitySettingsBinding

class Settings : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Edge-to-edge insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.btnClose.setOnClickListener { finish() }

        binding.managerecurringtv.setOnClickListener {
            supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .replace(android.R.id.content, ManageRecurringTransaction())
                .addToBackStack("settings") // Back goes to Settings page again
                .commit()
        }
    }
}
