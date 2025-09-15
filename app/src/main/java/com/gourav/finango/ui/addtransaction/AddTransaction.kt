package com.gourav.finango.ui.addtransaction

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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AddTransaction : AppCompatActivity() {

    private lateinit var binding: ActivityAddTransactionBinding
    private var selectedType: String = "expense"
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityAddTransactionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.backbutton.setOnClickListener {
            finish()
        }
        binding.submitTransaction.setOnClickListener {
            saveTransaction()
        }


        // Set initial state
        updateUI()

        // Set up click listeners for the LinearLayouts
        binding.llExpense.setOnClickListener {
            selectedType = "expense"
            updateUI()
        }

        binding.llIncome.setOnClickListener {
            selectedType = "income"
            updateUI()
        }

        supportFragmentManager.setFragmentResultListener(SelectListFragment.REQ_KEY, this) { _, bundle ->
            val type = bundle.getString(SelectListFragment.BUNDLE_TYPE) ?: return@setFragmentResultListener
            val name = bundle.getString(SelectListFragment.BUNDLE_NAME).orEmpty()
            val code = bundle.getString(SelectListFragment.BUNDLE_CODE) // for currency
            val iconRes = bundle.getInt(SelectListFragment.BUNDLE_ICON, 0)

            if (type == SelectListFragment.TYPE_CATEGORY) {
                binding.tvCategory.text = name
            } else if (type == SelectListFragment.TYPE_CURRENCY) {
                // Update icon and keep currency code in a tag (use it when saving)
                if (iconRes != 0) binding.tvCurrency.setImageResource(iconRes)
                binding.tvCurrency.contentDescription = "$code $name"
                binding.tvCurrency.tag = code
            }
        }

        // Open category selector
        binding.tvCategory.setOnClickListener {
            SelectListFragment.newInstance(SelectListFragment.TYPE_CATEGORY)
                .show(supportFragmentManager, SelectListFragment.TAG)
        }

        // Open currency selector
        binding.tvCurrency.setOnClickListener {
            SelectListFragment.newInstance(SelectListFragment.TYPE_CURRENCY)
                .show(supportFragmentManager, SelectListFragment.TAG)
        }
    }


    private fun updateUI() {
        if (selectedType == "expense") {
            // Select Expense: Apply selected drawable and change text color
            binding.llExpense.setBackgroundResource(R.drawable.card_expense_selected)
            binding.tvExpense.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            binding.tvCategory.visibility= View.VISIBLE
            // Deselect Income: Apply unselected drawable and change text color
            binding.llIncome.setBackgroundResource(R.drawable.card_unselected)
            binding.tvIncome.setTextColor(ContextCompat.getColor(this, R.color.black))
        } else {
            // Select Income: Apply selected drawable and change text color
            binding.llIncome.setBackgroundResource(R.drawable.card_income_selected)
            binding.tvIncome.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            binding.tvCategory.visibility= View.GONE

            // Deselect Expense: Apply unselected drawable and change text color
            binding.llExpense.setBackgroundResource(R.drawable.card_unselected)
            binding.tvExpense.setTextColor(ContextCompat.getColor(this, R.color.black))
        }
    }

    private fun saveTransaction() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show()
            return
        }

        val type = selectedType // "expense" or "income"
        var category = binding.tvCategory.text?.toString()?.trim().orEmpty()
        val description = binding.etDescription.text?.toString()?.trim().orEmpty()
        val amountText = binding.etAmount.text?.toString()?.trim().orEmpty()
        val currencyCode = (binding.tvCurrency.tag as? String) ?: "INR"

        // If it's an expense and category wasn't changed (still "Category" or empty), store "General"
        if (type == "expense" && (category.isEmpty() || category.equals("Category", ignoreCase = true))) {
            category = "General"
        }

        if (amountText.isEmpty()) {
            Toast.makeText(this, "Please enter amount", Toast.LENGTH_SHORT).show()
            return
        }

        val amount = amountText.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show()
            return
        }

        // Create HashMap for Firestore
        val firestoreData = hashMapOf(
            "type" to type,
            "category" to category,
            "description" to description,
            "amount" to amount,
            "currency" to currencyCode,
            "date" to getCurrentDate(),
            "timestamp" to FieldValue.serverTimestamp()
        )

        setLoading(true)
        db.collection("users")
            .document(uid)
            .collection("transactions")
            .add(firestoreData)
            .addOnSuccessListener { documentReference ->
                // Fetch the document to get the server timestamp
                documentReference.get().addOnSuccessListener { document ->
                    val serverTimestamp = document.getTimestamp("timestamp")
                    val transaction = Transactionadd(
                        documentId = document.id,
                        amount = amount,
                        category = category,
                        currency = currencyCode,
                        date = getCurrentDate(),
                        description = description,
                        timestamp = serverTimestamp, // Now has actual server timestamp
                        type = type
                    )

                    updateTotalIncomeExpense(uid, type, amount)

                    // CHANGED: Use setResult instead of FragmentResult
                    val resultIntent = Intent().apply {
                        putExtra("newTransaction", transaction)
                    }
                    setResult(Activity.RESULT_OK, resultIntent)

                    setLoading(false)
                    Toast.makeText(this, "Transaction saved", Toast.LENGTH_SHORT).show()
                    finish()
                }.addOnFailureListener { e ->
                    setLoading(false)
                    Toast.makeText(this, "Failed to fetch timestamp: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->
                setLoading(false)
                Toast.makeText(this, "Failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
    }

    private fun updateTotalIncomeExpense(uid: String, type: String, amount: Double) {
        // Reference to the user's document
        val userRef = db.collection("users").document(uid)

        // Check the type of transaction and update the total
        if (type == "income") {
            userRef.update("total_income", FieldValue.increment(amount))
        } else if (type == "expense") {
            userRef.update("total_expense", FieldValue.increment(amount))
        }
    }




    private fun getCurrentDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }
    private fun setLoading(loading: Boolean) {
        binding.loadingOverlay.isVisible = loading
        binding.submitTransaction.isEnabled = !loading
        binding.backbutton.isEnabled = !loading
    }


}