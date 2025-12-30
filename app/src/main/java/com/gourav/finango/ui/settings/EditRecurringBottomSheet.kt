package com.gourav.finango.ui.settings

import android.app.DatePickerDialog
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.gourav.finango.R
import com.gourav.finango.ui.addtransaction.SelectListFragment
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class EditRecurringBottomSheet : BottomSheetDialogFragment() {

    var onSaved: ((RecurringTransaction) -> Unit)? = null
    private lateinit var model: RecurringTransaction

    // Safe for API 24
    private val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    companion object {
        private const val KEY_MODEL = "key_model"
        fun newInstance(item: RecurringTransaction) = EditRecurringBottomSheet().apply {
            arguments = Bundle().apply { putParcelable(KEY_MODEL, item) }  // requires @Parcelize
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get the Parcelable safely across API levels
        model = requireNotNull(
            if (Build.VERSION.SDK_INT >= 33) {
                requireArguments().getParcelable(KEY_MODEL, RecurringTransaction::class.java)
            } else {
                @Suppress("DEPRECATION")
                requireArguments().getParcelable(KEY_MODEL)
            }
        ) { "Missing RecurringTransaction in arguments" }
    }override fun getTheme(): Int = R.style.ThemeOverlay_Finango_BottomSheet


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_edit_recurring, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Views
        val etTitle = view.findViewById<EditText>(R.id.etTitle)
        val etAmount = view.findViewById<EditText>(R.id.etAmount)
        val etCategory = view.findViewById<TextView>(R.id.etCategory)
        val actFrequency = view.findViewById<AutoCompleteTextView>(R.id.actFrequency)
        val etStartDate = view.findViewById<TextView>(R.id.etStartDate)
        val etEndDate = view.findViewById<TextView>(R.id.etEndDate)
        val switchActive = view.findViewById<SwitchMaterial>(R.id.switchActive)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btnCancel)
        val btnSave = view.findViewById<MaterialButton>(R.id.btnSave)
        val progress = view.findViewById<ProgressBar>(R.id.progress)

        // Prefill
        etTitle.setText(model.title)
        etAmount.setText(model.amount.toString())
        etCategory.setText(model.category)
        // Prefer a start date if you store it; otherwise use nextDueDate or today as fallback
        etStartDate.setText(model.nextDueDate ?: todayString())
        etEndDate.setText(model.endDate ?: "")
        switchActive.isChecked = model.isActive
        updateActiveLabel(switchActive)

        // Frequency dropdown
        val freqs = listOf("Daily", "Weekly", "Monthly", "Yearly")

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, freqs)
        actFrequency.setAdapter(adapter)
        actFrequency.threshold = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            actFrequency.showSoftInputOnFocus = false
        } else {
            // fallback for older apis
            actFrequency.isFocusable = false
            actFrequency.isFocusableInTouchMode = true
        }

        actFrequency.setOnClickListener {
            actFrequency.showDropDown()
        }
        actFrequency.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) actFrequency.showDropDown()
        }
        actFrequency.setText(model.frequency, false)


        etStartDate.setOnClickListener {
            openDatePicker(etStartDate, allowPast = true)
        }

        etEndDate.setOnClickListener {
            openDatePicker(etEndDate, allowPast = true)
        }
        etCategory.setOnClickListener {
            // show the dialog with TYPE_CATEGORY
            SelectListFragment.newInstance(SelectListFragment.TYPE_CATEGORY)
                .show(parentFragmentManager, SelectListFragment.TAG)
        }

// 2) Listen for selection result
        parentFragmentManager.setFragmentResultListener(
            SelectListFragment.REQ_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val resType = bundle.getString(SelectListFragment.BUNDLE_TYPE)
            val name = bundle.getString(SelectListFragment.BUNDLE_NAME)
            val code = bundle.getString(SelectListFragment.BUNDLE_CODE) // null for categories
            val icon = bundle.getInt(SelectListFragment.BUNDLE_ICON, 0)

            if (resType == SelectListFragment.TYPE_CATEGORY && !name.isNullOrBlank()) {
                etCategory.setText(name)
                // store icon/code somewhere if needed, e.g. a var in the bottom sheet:
                // selectedCategoryIcon = if (icon != 0) icon else null
                // selectedCategoryCode = code
            }
        }


        btnCancel.setOnClickListener { dismiss() }

        btnSave.setOnClickListener {
            val title = etTitle.text?.toString()?.trim().orEmpty()
            val amount = etAmount.text?.toString()?.toLongOrNull() ?: 0L
            val category = etCategory.text?.toString()?.trim().orEmpty().ifBlank { "General" }
            val frequency = actFrequency.text.toString()
                .trim()
                .ifBlank { "Daily" }

            val startDateStr = etStartDate.text?.toString()?.trim().orEmpty()
            val endDateStr = etEndDate.text?.toString()?.trim().orEmpty().ifBlank { null }
            val isActive = switchActive.isChecked

            // Validation
            if (title.isEmpty()) { etTitle.error = "Title required"; return@setOnClickListener }
            if (amount <= 0) { etAmount.error = "Enter valid amount"; return@setOnClickListener }
            if (!isValidYMD(startDateStr)) { etStartDate.error = "Invalid date"; return@setOnClickListener }

            btnSave.isEnabled = false
            progress.visibility = View.VISIBLE

            // Auto anchors
            val startDate = df.parse(startDateStr)!!
            val anchorFields = computeAnchors(startDate)

            // Optionally recompute nextDueDate based on start + freq + today (respecting endDate)
            val computedNext = computeNextDueDate(startDate, frequency, endDateStr)

            val update = hashMapOf<String, Any?>(
                "title" to title,
                "amount" to amount,
                "category" to category,
                // "type" removed from update to preserve existing value
                "frequency" to frequency,
                "endDate" to endDateStr,
                "isActive" to isActive,
                "startDate" to startDateStr,
                "anchorDay" to anchorFields["anchorDay"],
                "anchorMonth" to anchorFields["anchorMonth"],
                "dayOfWeek" to anchorFields["dayOfWeek"],
                "nextDueDate" to computedNext,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid == null) {
                Toast.makeText(requireContext(), "Not signed in", Toast.LENGTH_SHORT).show()
                progress.visibility = View.GONE
                btnSave.isEnabled = true
                return@setOnClickListener
            }

            FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("recurringTransactions").document(model.id)
                .set(update, SetOptions.merge())
                .addOnSuccessListener {
                    val updated = model.copy(
                        title = title,
                        amount = amount,
                        category = category,
                        frequency = frequency,
                        endDate = endDateStr,
                        nextDueDate = computedNext,
                        isActive = isActive
                    )
                    onSaved?.invoke(updated)
                    dismiss()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
                .addOnCompleteListener {
                    progress.visibility = View.GONE
                    btnSave.isEnabled = true
                }
        }
    }
    private fun updateActiveLabel(sw: SwitchMaterial) {
        sw.text = if (sw.isChecked) "Active" else "Inactive"
    }


    // --- Helpers ---

    private fun openDatePicker(target: TextView, allowPast: Boolean = true) {
        val cal = Calendar.getInstance()
        val existing = target.text?.toString()?.trim()
        if (!existing.isNullOrEmpty() && isValidYMD(existing)) {
            val d = df.parse(existing)!!
            cal.time = d
        }
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH)
        val d = cal.get(Calendar.DAY_OF_MONTH)

        val dlg = DatePickerDialog(requireContext(), { _, yy, mm, dd ->
            val picked = Calendar.getInstance().apply {
                set(yy, mm, dd, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            target.setText(df.format(picked.time))
        }, y, m, d)

        if (!allowPast) dlg.datePicker.minDate = System.currentTimeMillis()
        dlg.show()
    }

    private fun isValidYMD(s: String): Boolean = try {
        df.isLenient = false
        df.parse(s)
        true
    } catch (_: Exception) { false }

    /** Derive anchors from chosen start date */
    private fun computeAnchors(date: Date): Map<String, Int> {
        val c = Calendar.getInstance().apply { time = date }
        val anchorDay = c.get(Calendar.DAY_OF_MONTH)    // 1..31
        val anchorMonth = c.get(Calendar.MONTH) + 1     // 1..12
        val dayOfWeek = c.get(Calendar.DAY_OF_WEEK)     // 1=Sun..7=Sat
        return mapOf(
            "anchorDay" to anchorDay,
            "anchorMonth" to anchorMonth,
            "dayOfWeek" to dayOfWeek
        )
    }

    /**
     * Compute nextDueDate "yyyy-MM-dd"
     * - Start at startDate (00:00)
     * - If <= today, advance by frequency until > today
     * - If endDate exists and next > endDate → return null
     */
    private fun computeNextDueDate(startDate: Date, frequency: String, endDateStr: String?): String? {
        val today = clearTime(Date())
        val endDate: Date? = endDateStr?.let { safeParse(it) }

        val cur = Calendar.getInstance().apply { time = clearTime(startDate) }
        while (!cur.time.after(today)) {
            when (frequency.uppercase(Locale.US)) {
                "DAILY" -> cur.add(Calendar.DAY_OF_YEAR, 1)
                "WEEKLY" -> cur.add(Calendar.WEEK_OF_YEAR, 1)
                "MONTHLY" -> cur.add(Calendar.MONTH, 1)
                "YEARLY" -> cur.add(Calendar.YEAR, 1)
                else -> cur.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        val result = cur.time
        if (endDate != null && result.after(endDate)) return null
        return df.format(result)
    }

    private fun todayString(): String = df.format(clearTime(Date()))

    private fun clearTime(d: Date): Date {
        val c = Calendar.getInstance().apply {
            time = d
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return c.time
    }

    private fun safeParse(s: String): Date? = try {
        df.isLenient = false
        df.parse(s)
    } catch (_: Exception) { null }
}
