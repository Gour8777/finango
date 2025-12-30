package com.gourav.finango.ui.faq

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gourav.finango.R
import com.gourav.finango.databinding.ActivityFaqscreenBinding
import com.gourav.finango.databinding.ActivitySettingsBinding

class faqscreen : AppCompatActivity() {
    private lateinit var binding: ActivityFaqscreenBinding
    private lateinit var rvFaq: RecyclerView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityFaqscreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Edge-to-edge insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        binding.btnClose.setOnClickListener { finish() }
        rvFaq = findViewById(R.id.rvFaq)
        rvFaq.layoutManager = LinearLayoutManager(this)
        rvFaq.adapter = FaqAdapter(getStaticFaqs().toMutableList())
    }
    private fun getStaticFaqs(): List<FaqItem> {
        return listOf(
            FaqItem(
                "What is the Personalized AI Finance Manager?",
                "It’s your smart assistant for tracking expenses, budgets, investments, taxes, and savings goals with insights generated from your data."
            ),
            FaqItem(
                "How do I add an expense?",
                "Go to Add Transaction → enter amount, category, description and date → Save. You can also paste text like “Paid ₹250 for groceries” in Quick Add."
            ),
            FaqItem(
                "Can I sync bank SMS or statements?",
                "Yes. Enable SMS permissions for auto-parsing messages. For statements, upload PDF/CSV in Imports. Your data stays encrypted at rest."
            ),
            FaqItem(
                "How are categories assigned?",
                "We use NLP to classify descriptions (e.g., ‘milk’ → Groceries). You can override a category; future similar entries will auto-correct."
            ),
            FaqItem(
                "Is my data private?",
                "Absolutely. Data is locally encrypted and synced via secure channels. We don’t sell your data. You can export or delete it anytime."
            ),
            FaqItem(
                "How do budgets and alerts work?",
                "Set monthly budgets per category. We monitor your spend and send alerts when you’re nearing or crossing limits."
            ),
            FaqItem(
                "How do I export my data?",
                "Settings → Export → choose CSV or PDF. You can select a date range and include categories, notes, and tags."
            )
        )
    }
}
