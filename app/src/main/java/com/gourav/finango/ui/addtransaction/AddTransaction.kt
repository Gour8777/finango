package com.gourav.finango.ui.addtransaction

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.gourav.finango.R
import com.gourav.finango.databinding.ActivityAddTransactionBinding
import com.gourav.finango.workers.AnomalyEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AddTransaction : AppCompatActivity() {

    private lateinit var binding: ActivityAddTransactionBinding
    private var selectedType: String = "expense"
    private var isRecurring: Boolean = false

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private var updatingCategoryInternally = false
    private var userManuallyPickedCategory = false
    private var lastAutoCategory: String? = null
    private var lastAutoConfidence: Double = 0.0

    // debounce with coroutines
    private var classifyJob: kotlinx.coroutines.Job? = null


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
        binding.etDescription.doAfterTextChanged { text ->
            if (selectedType != "expense") return@doAfterTextChanged // only for expense
            if (userManuallyPickedCategory) return@doAfterTextChanged // respect user override

            classifyJob?.cancel()
            classifyJob = lifecycleScope.launch {
                // debounce ~250ms so we don't classify every keystroke
                delay(250)
                val desc = text?.toString().orEmpty().trim()
                applyAutoCategory(desc)
            }
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
                userManuallyPickedCategory = true
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
            userManuallyPickedCategory = true
            SelectListFragment.newInstance(SelectListFragment.TYPE_CATEGORY)
                .show(supportFragmentManager, SelectListFragment.TAG)
        }

        // Open currency selector
        binding.tvCurrency.setOnClickListener {
            SelectListFragment.newInstance(SelectListFragment.TYPE_CURRENCY)
                .show(supportFragmentManager, SelectListFragment.TAG)
        }
        binding.addrecurringbutton.setOnClickListener {
            isRecurring = !isRecurring
            binding.layoutRecurringForm.visibility = if (isRecurring) View.VISIBLE else View.GONE
        }
        binding.enddateedittext.setOnClickListener {
            val cal = java.util.Calendar.getInstance()
            val dp = android.app.DatePickerDialog(
                this,
                { _, y, m, d ->
                    binding.enddateedittext.setText(String.format("%04d-%02d-%02d", y, m + 1, d))
                },
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH),
                cal.get(java.util.Calendar.DAY_OF_MONTH)
            )
            dp.show()
        }
    }
    private fun applyAutoCategory(description: String) {
        if (description.isBlank()) return

        val result =CategoryClassifier.classify(description)
        val predicted = result.category
        val conf = result.confidence

        // Only auto-set if confidence good OR the current is still placeholder/empty
        val current = binding.tvCategory.text?.toString()?.trim().orEmpty()
        val isPlaceholder = current.isEmpty() || current.equals("Category", ignoreCase = true) || current.equals("General", ignoreCase = true)

        val threshold = 0.50 // tweak
        if (isPlaceholder || conf >= threshold) {
            updatingCategoryInternally = true
            binding.tvCategory.text = predicted
            updatingCategoryInternally = false

            lastAutoCategory = predicted
            lastAutoConfidence = conf

            // Optional: visually hint low confidence
            binding.tvCategory.alpha = if (conf < 0.60) 0.8f else 1f
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
            if (!userManuallyPickedCategory) {
                val desc = binding.etDescription.text?.toString().orEmpty().trim()
                if (desc.isNotEmpty()) applyAutoCategory(desc)
            }
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

        if (type == "expense") {
            val isPlaceholder = category.isEmpty() || category.equals("Category", ignoreCase = true)
            if (isPlaceholder) {
                // try auto category at save time as a final fallback
                val result =CategoryClassifier.classify(description)
                if (result.confidence >= 0.40) { // gentle threshold
                    category = result.category
                    // reflect in UI so the calling activity sees it in resultIntent
                    binding.tvCategory.text = category
                } else {
                    category = "General"
                }
            }
        } else {
            // income path: we don't use category
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

        // ---------- Helpers (Calendar-based; API 24-safe) ----------
        fun sdf(): SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        fun todayCal(): Calendar = Calendar.getInstance().apply {
            // set a stable time (09:00) so serverTimestamp/order won't be weird later
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        fun fmt(cal: Calendar): String = sdf().format(cal.time)

        // Add exactly 1 month, clamp to last day if needed (handles Feb, 30/31)
        fun nextMonthlyAnchored(from: Calendar, anchorDay: Int): Calendar {
            val cal = (from.clone() as Calendar)
            cal.add(Calendar.MONTH, 1)
            val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            cal.set(Calendar.DAY_OF_MONTH, anchorDay.coerceIn(1, maxDay))
            return cal
        }

        // Add exactly 1 year, clamp Feb 29 -> Feb 28 on non-leap years
        fun nextYearlyAnchored(from: Calendar, anchorMonth0: Int, anchorDay: Int): Calendar {
            val cal = (from.clone() as Calendar)
            cal.add(Calendar.YEAR, 1)
            cal.set(Calendar.MONTH, anchorMonth0) // 0-11
            val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            cal.set(Calendar.DAY_OF_MONTH, anchorDay.coerceIn(1, maxDay))
            return cal
        }

        // Weekly: same weekday next week
        fun nextWeeklySameWeekday(from: Calendar): Calendar {
            val cal = (from.clone() as Calendar)
            cal.add(Calendar.WEEK_OF_YEAR, 1)
            return cal
        }

        // Read RadioGroup → frequency string
        fun readFrequency(): String {
            return when (binding.recurringFrequencyGroup.checkedRadioButtonId) {
                R.id.rbDaily -> "Daily"
                R.id.rbWeekly -> "Weekly"
                R.id.rbMonthly -> "Monthly"
                R.id.rbYearly -> "Yearly"
                else -> "Monthly"
            }
        }

        // ---------- Build normal transaction payload ----------
        val firestoreData = hashMapOf(
            "type" to type,
            "category" to category,
            "description" to description,
            "amount" to amount,
            "currency" to currencyCode,
            "date" to getCurrentDate(),                 // "yyyy-MM-dd"
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
                        timestamp = serverTimestamp,
                        type = type
                    )

                    // Update totals
                    updateTotalIncomeExpense(uid, type, amount)

                    //anomaly check for each transaction
                    AnomalyEngine.runForNewTransaction(
                        db = db,
                        uid = uid,
                        txId = document.id,
                        amount = amount.toDouble(),
                        category = category,
                        type = type,
                        currency = currencyCode,
                        timestamp = serverTimestamp
                    )





                    // If user didn't open the recurring form, finish as usual
                    val wantsRecurring = binding.layoutRecurringForm.visibility == View.VISIBLE
                    if (!wantsRecurring) {
                        val resultIntent = Intent().apply {
                            putExtra("action", "added")
                            putExtra("newTransaction", transaction)
                        }
                        setResult(Activity.RESULT_OK, resultIntent)
                        setLoading(false)
                        Toast.makeText(this, "Transaction saved", Toast.LENGTH_SHORT).show()
                        finish()
                        return@addOnSuccessListener
                    }

                    // ---------- Create recurring template (anchor = creation date) ----------
                    val creation = todayCal() // today; serves as "startDate" & anchor reference
                    val anchorDay = creation.get(Calendar.DAY_OF_MONTH)       // 1..31 (monthly/yearly)
                    val anchorMonth0 = creation.get(Calendar.MONTH)           // 0..11 (yearly)
                    val calDow = creation.get(Calendar.DAY_OF_WEEK)           // 1=Sun..7=Sat
                    val isoDow = if (calDow == Calendar.SUNDAY) 7 else calDow - 1 // 1=Mon..7=Sun (ISO)
                    val checkedId = binding.recurringFrequencyGroup.checkedRadioButtonId
                    Log.d("AddTransaction", "checkedId=$checkedId")
                    Log.d("AddTransaction", "rbDaily=${R.id.rbDaily}, rbWeekly=${R.id.rbWeekly}, rbMonthly=${R.id.rbMonthly}, rbYearly=${R.id.rbYearly}")
                    val frequency = readFrequency()
                    Log.d("AddTransaction", "Chosen frequency = $frequency")
                    val endDateStr = binding.enddateedittext.text?.toString()?.trim().orEmpty().ifBlank { null }

                    // Compute nextDueDate string
                    val nextDueCal: Calendar = when (frequency) {
                        "Daily"   -> (creation.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
                        "Weekly"  -> nextWeeklySameWeekday(creation)
                        "Monthly" -> nextMonthlyAnchored(creation, anchorDay)
                        "Yearly"  -> nextYearlyAnchored(creation, anchorMonth0, anchorDay)
                        else      -> nextMonthlyAnchored(creation, anchorDay)
                    }
                    val nextDueDate = fmt(nextDueCal)
                    val startDate = fmt(creation)

                    val template = hashMapOf(
                        // snapshot of core values
                        "title" to if (description.isNotBlank()) description
                        else if (type == "income") "Recurring Income" else "Recurring Expense",
                        "amount" to amount,
                        "currency" to currencyCode,
                        "category" to category,
                        "type" to type, // "income" | "expense"

                        // recurrence inputs
                        "frequency" to frequency,           // DAILY | WEEKLY | MONTHLY | YEARLY
                        "startDate" to startDate,           // yyyy-MM-dd
                        "endDate" to endDateStr,            // nullable string
                        "isActive" to true,

                        // anchors derived from creation date
                        "anchorDay" to anchorDay,           // monthly/yearly
                        "anchorMonth" to (anchorMonth0 + 1),// store 1..12 if you prefer (UI friendly)
                        "dayOfWeek" to isoDow,              // 1=Mon..7=Sun (ISO style)

                        // engine bookkeeping
                        "nextDueDate" to nextDueDate,       // yyyy-MM-dd
                        "lastGeneratedPeriod" to null,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )

                    db.collection("users")
                        .document(uid)
                        .collection("recurringTransactions")
                        .add(template)
                        .addOnSuccessListener {
                            val resultIntent = Intent().apply {
                                putExtra("action", "added")
                                putExtra("newTransaction", transaction)
                            }
                            setResult(Activity.RESULT_OK, resultIntent)
                            setLoading(false)
                            Toast.makeText(this, "Transaction + Recurring saved", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        .addOnFailureListener { e ->
                            val resultIntent = Intent().apply {
                                putExtra("action", "added")
                                putExtra("newTransaction", transaction)
                            }
                            setResult(Activity.RESULT_OK, resultIntent)
                            setLoading(false)
                            Toast.makeText(this, "Transaction saved (recurring setup failed): ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            finish()
                        }
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
    // Read which radio button is selected and map to a string constant


}