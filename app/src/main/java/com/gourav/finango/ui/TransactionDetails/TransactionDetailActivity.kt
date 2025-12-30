package com.gourav.finango.ui.TransactionDetails

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.gourav.finango.R
import com.gourav.finango.databinding.ActivityAddTransactionBinding
import com.gourav.finango.databinding.ActivityTransactionDetailBinding
import com.gourav.finango.ui.addtransaction.SelectListFragment
import com.gourav.finango.ui.addtransaction.Transactionadd
import java.text.SimpleDateFormat
import java.util.*

class TransactionDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTransactionDetailBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    // current (possibly edited) state
    private var selectedType: String = "expense"

    // original values (to adjust totals correctly)
    private var originalType: String = "expense"
    private var originalAmount: Double = 0.0

    private var documentId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityTransactionDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // ----- Read incoming data -----
        documentId = intent.getStringExtra("documentId")
        val date        = intent.getStringExtra("date") ?: ""  // you can show it if needed
        val amount      = intent.getDoubleExtra("amount", 0.0)
        val type        = intent.getStringExtra("type") ?: "expense"
        val category    = intent.getStringExtra("category") ?: ""
        val description = intent.getStringExtra("description") ?: ""
        val currency    = intent.getStringExtra("currency") ?: "INR"

        // Keep originals for total adjustments
        originalType = type
        originalAmount = amount
        selectedType = type

        binding.submitTransaction.text = "Update"

        // Back
        binding.backbutton.setOnClickListener { finish() }

        // ----- Prefill fields -----
        binding.etDescription.setText(description)
        binding.etAmount.setText(if (amount == 0.0) "" else trimTrailingZeros(amount))
        binding.tvCategory.text = category.ifEmpty { if (type == "expense") "General" else "" }

        // currency code in tag (icon if you want; fallback to your rupee drawable for INR)
        binding.tvCurrency.tag = currency
        if (currency.equals("INR", ignoreCase = true)) {
            binding.tvCurrency.setImageResource(R.drawable.rupee)
        }
        binding.tvCurrency.contentDescription = "$currency"

        // Set the type visual state
        updateTypeUI()

        // ----- Toggle listeners (same behavior as Add) -----
        binding.llExpense.setOnClickListener {
            selectedType = "expense"
            updateTypeUI()
        }
        binding.llIncome.setOnClickListener {
            selectedType = "income"
            updateTypeUI()
        }

        // ----- Selectors (reuse your fragment) -----
        supportFragmentManager.setFragmentResultListener(SelectListFragment.REQ_KEY, this) { _, bundle ->
            val selType = bundle.getString(SelectListFragment.BUNDLE_TYPE) ?: return@setFragmentResultListener
            val name = bundle.getString(SelectListFragment.BUNDLE_NAME).orEmpty()
            val code = bundle.getString(SelectListFragment.BUNDLE_CODE)
            val iconRes = bundle.getInt(SelectListFragment.BUNDLE_ICON, 0)

            if (selType == SelectListFragment.TYPE_CATEGORY) {
                binding.tvCategory.text = name
            } else if (selType == SelectListFragment.TYPE_CURRENCY) {
                if (iconRes != 0) binding.tvCurrency.setImageResource(iconRes)
                binding.tvCurrency.contentDescription = "$code $name"
                binding.tvCurrency.tag = code
            }
        }

        binding.tvCategory.setOnClickListener {
            SelectListFragment.newInstance(SelectListFragment.TYPE_CATEGORY)
                .show(supportFragmentManager, SelectListFragment.TAG)
        }
        binding.tvCurrency.setOnClickListener {
            SelectListFragment.newInstance(SelectListFragment.TYPE_CURRENCY)
                .show(supportFragmentManager, SelectListFragment.TAG)
        }

        // ----- Update/Save button -----
        binding.submitTransaction.setOnClickListener { updateTransaction(date) }
    }

    // Mirrors your Add screen type UI logic
    private fun updateTypeUI() {
        if (selectedType == "expense") {
            binding.llExpense.setBackgroundResource(R.drawable.card_expense_selected)
            binding.tvExpense.setTextColor(ContextCompat.getColor(this, android.R.color.white))

            binding.llIncome.setBackgroundResource(R.drawable.card_unselected)
            binding.tvIncome.setTextColor(ContextCompat.getColor(this, R.color.black))

            binding.tvCategory.visibility = View.VISIBLE
            if (binding.tvCategory.text.isNullOrBlank() || binding.tvCategory.text.toString().equals("Category", true)) {
                binding.tvCategory.text = "General"
            }
        } else {
            binding.llIncome.setBackgroundResource(R.drawable.card_income_selected)
            binding.tvIncome.setTextColor(ContextCompat.getColor(this, android.R.color.white))

            binding.llExpense.setBackgroundResource(R.drawable.card_unselected)
            binding.tvExpense.setTextColor(ContextCompat.getColor(this, R.color.black))

            binding.tvCategory.visibility = View.GONE
        }
    }

    private fun updateTransaction(existingDate: String) {
        val uid = auth.currentUser?.uid
        val docId = documentId
        if (uid == null || docId.isNullOrEmpty()) {
            Toast.makeText(this, "Invalid session or document", Toast.LENGTH_SHORT).show()
            return
        }

        val description = binding.etDescription.text?.toString()?.trim().orEmpty()
        val amountText  = binding.etAmount.text?.toString()?.trim().orEmpty()
        var category    = binding.tvCategory.text?.toString()?.trim().orEmpty()
        val currency    = (binding.tvCurrency.tag as? String) ?: "INR"
        val newType     = selectedType

        if (amountText.isEmpty()) {
            Toast.makeText(this, "Please enter amount", Toast.LENGTH_SHORT).show()
            return
        }
        val newAmount = amountText.toDoubleOrNull()
        if (newAmount == null || newAmount <= 0.0) {
            Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show()
            return
        }

        if (newType == "expense" && (category.isEmpty() || category.equals("Category", true))) {
            category = "General"
        }
        if (newType == "income") {
            category = "" // we hide category for income; store empty to keep model clean
        }

        // Build update map – keep original 'timestamp' untouched, add 'updatedAt'
        val updateMap = mapOf(
            "type" to newType,
            "category" to category,
            "description" to description,
            "amount" to newAmount,
            "currency" to currency,
            "date" to (existingDate.ifEmpty { currentDate() }),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        setLoading(true)

        db.collection("users")
            .document(uid)
            .collection("transactions")
            .document(docId)
            .update(updateMap)
            .addOnSuccessListener {
                val updated = Transactionadd(
                    documentId = documentId!!,
                    amount = newAmount,
                    category = category,
                    currency = currency,
                    date = existingDate,
                    description = description,
                    timestamp = null,  // or keep old
                    type = newType
                )
                val data = Intent()
                data.putExtra("action", "updated")
                data.putExtra("updatedTransaction", updated)
                setResult(Activity.RESULT_OK, data)

                // Adjust totals if needed
                adjustTotals(uid, originalType, originalAmount, newType, newAmount)
                setLoading(false)
                Toast.makeText(this, "Transaction updated", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                setLoading(false)
                Toast.makeText(this, "Update failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
    }

    /**
     * Adjusts total_income / total_expense based on change in type/amount.
     */
    private fun adjustTotals(
        uid: String,
        oldType: String,
        oldAmount: Double,
        newType: String,
        newAmount: Double
    ) {
        val userRef = db.collection("users").document(uid)

        when {
            // Type unchanged → adjust delta on the same bucket
            oldType == newType && newType == "income" -> {
                val delta = newAmount - oldAmount
                if (delta != 0.0) userRef.update("total_income", FieldValue.increment(delta))
            }
            oldType == newType && newType == "expense" -> {
                val delta = newAmount - oldAmount
                if (delta != 0.0) userRef.update("total_expense", FieldValue.increment(delta))
            }

            // Type changed: move amounts across buckets
            oldType == "income" && newType == "expense" -> {
                userRef.update(
                    mapOf(
                        "total_income" to FieldValue.increment(-oldAmount),
                        "total_expense" to FieldValue.increment(newAmount)
                    )
                )
            }
            oldType == "expense" && newType == "income" -> {
                userRef.update(
                    mapOf(
                        "total_expense" to FieldValue.increment(-oldAmount),
                        "total_income" to FieldValue.increment(newAmount)
                    )
                )
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.loadingOverlay.isVisible = loading
        binding.submitTransaction.isEnabled = !loading
        binding.backbutton.isEnabled = !loading
    }

    private fun currentDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun trimTrailingZeros(value: Double): String {
        val s = value.toString()
        return if (s.contains('.')) s.trimEnd('0').trimEnd('.') else s
    }
}
