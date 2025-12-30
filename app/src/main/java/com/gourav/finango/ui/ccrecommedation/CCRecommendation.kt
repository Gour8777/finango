package com.gourav.finango.ui.ccrecommedation

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.gourav.finango.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CCRecommendation : AppCompatActivity() {
    private lateinit var rvRecommendations: RecyclerView
    private lateinit var adapter: CcRecommendationAdapter
    private lateinit var progressBar: View
    private lateinit var btnFetch: MaterialButton
    private lateinit var actTopK: AutoCompleteTextView
    private val uiScope = CoroutineScope(Dispatchers.Main + Job())
    private val firebaseAuth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    // ✅ Get userId from Firebase Auth
    private val userId: String?
        get() = firebaseAuth.currentUser?.uid
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_ccrecommendation)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        initViews()
        setupTopKDropdown()
        setupRecyclerView()
        setupClickListeners()
        val btnclose=findViewById<ImageView>(R.id.btnClose)
        btnclose.setOnClickListener { finish() }
        // If user not logged in → show error and finish
        if (userId == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Initial load with default topK = 5
        actTopK.setText("5", false)
        fetchRecommendations(5)
    }

    private fun initViews() {
        rvRecommendations = findViewById(R.id.rvRecommendations)
        progressBar = findViewById(R.id.progressBar)
        btnFetch = findViewById(R.id.btnFetch)
        actTopK = findViewById(R.id.actTopK)
    }

    // ✅ Correct dropdown: use ArrayAdapter (no weird Adapter reference)
    private fun setupTopKDropdown() {
        val options = listOf("3", "4", "5", "6")

        val arrayAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            options
        )
        actTopK.setAdapter(arrayAdapter)

        // open dropdown on click
        actTopK.setOnClickListener {
            actTopK.showDropDown()
        }

        // ✅ default value: 5 recommendations
        actTopK.setText("5", false)
    }



    private fun setupRecyclerView() {
        adapter = CcRecommendationAdapter()
        rvRecommendations.layoutManager = LinearLayoutManager(this)
        rvRecommendations.adapter = adapter
    }

    private fun setupClickListeners() {
        btnFetch.setOnClickListener {
            val topKText = actTopK.text?.toString()?.trim()
            val topK = topKText?.toIntOrNull()

            if (topK == null || topK !in 3..6) {
                Toast.makeText(this, "Please select between 3 to 6 cards", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }

            if (userId == null) {
                Toast.makeText(this, "User not logged in", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            fetchRecommendations(topK)
        }
    }

    private fun setLoading(isLoading: Boolean) {
        if (isLoading) {
            progressBar.visibility = View.VISIBLE
            btnFetch.isEnabled = false
            btnFetch.text = "Loading..."
        } else {
            progressBar.visibility = View.GONE
            btnFetch.isEnabled = true
            btnFetch.text = "Load"
        }
    }

    private fun fetchRecommendations(topK: Int) {
        val uid = userId ?: return

        setLoading(true)

        uiScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    ApiClient.ccApi.getRecommendations(uid, topK)
                }

                adapter.submitList(result)

                if (result.isEmpty()) {
                    Toast.makeText(
                        this@CCRecommendation,
                        "No recommendations found",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@CCRecommendation,
                    "Failed to load recommendations: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                setLoading(false)
            }
        }
    }
}