package com.gourav.finango

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.gourav.finango.databinding.ActivityMainBinding
import com.gourav.finango.ui.login.LoginScreen
import com.gourav.finango.ui.profile.ProfileSetup
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : AppCompatActivity() {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private lateinit var binding: ActivityMainBinding
    private var navReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1) Show loader, hide content at start
        binding.progressOverlay.isVisible = true
        binding.contentRoot.isVisible = false

        // 2) Route
        lifecycleScope.launch {
            routeUser()
        }
    }

    private suspend fun routeUser() {
        val user = auth.currentUser
        if (user == null) {
            startClear(LoginScreen::class.java); return
        }

        val profileCompleted = try {
            val snap = db.collection("users").document(user.uid).get().await()
            snap.getBoolean("profileCompleted") == true
        } catch (e: Exception) {
            // On failure, safest is to send to Login (or show a retry)
            startClear(LoginScreen::class.java); return
        }

        if (!profileCompleted) {
            startClear(ProfileSetup::class.java); return
        }

        // 3) Good to go → init dashboard once
        if (!navReady) {
            initBottomNav()
            navReady = true
        }
        binding.progressOverlay.isVisible = false
        binding.contentRoot.isVisible = true
    }

    private fun initBottomNav() {
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        val navView: BottomNavigationView = binding.navView

        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home,
                R.id.navigation_wallet,
                R.id.navigation_account
            )
        )
        // If you use a Toolbar, re-enable:
        // setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
    }

    private fun <T : Activity> startClear(cls: Class<T>) {
        startActivity(Intent(this, cls).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        finish()
    }
}
