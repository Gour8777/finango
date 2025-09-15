package com.gourav.finango.ui.home

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gourav.finango.databinding.FragmentHomeBinding
import com.google.android.material.button.MaterialButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.gourav.finango.R
import com.gourav.finango.ui.addtransaction.AddTransaction
import com.gourav.finango.ui.notifications.NotificationsFragment

import com.gourav.finango.ui.wallet.TransactionAdapter
import com.gourav.finango.ui.wallet.Transactionget
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale



class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: TransactionAdapter
    private lateinit var transactionList: MutableList<Transactionget>
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var tvBalance: TextView
    private lateinit var incomeText: TextView
    private lateinit var expenseText: TextView
    private data class DateFilter(val start: Date, val endExclusive: Date, val label: String)
    private var activeFilter: DateFilter? = null

    private lateinit var tvGreeting: TextView
    private lateinit var tvUsername: TextView
    private lateinit var chipFilter: com.google.android.material.chip.Chip
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvGreeting = view.findViewById(R.id.homegreeting)
        tvUsername = view.findViewById(R.id.homeusername)
        tvGreeting.text = currentGreeting()
        loadUserName()



        tvBalance = view.findViewById(R.id.tvBalance)
        incomeText = view.findViewById(R.id.incometext)
        expenseText = view.findViewById(R.id.expensetext)
        loadTotals()
        transactionList = mutableListOf()
        adapter = TransactionAdapter(requireContext(), transactionList)

        binding.rvTransactions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTransactions.adapter = adapter

        loadTop3()
        binding.seealltransaction.setOnClickListener {
            // Find BottomNavigationView in your Activity
            val bottomNav = activity?.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.nav_view)
            bottomNav?.selectedItemId = R.id.navigation_wallet
        }
        binding.emptyStateLayout.setOnClickListener {
            val intent= Intent(context, AddTransaction::class.java)
            startActivity(intent)
        }
        chipFilter = binding.root.findViewById(R.id.chipFilter)
        binding.ivMore?.setOnClickListener { showFilterSheet() }
        chipFilter.setOnClickListener { clearFilter() }
        chipFilter.setOnCloseIconClickListener { clearFilter() }
        updateFilterChip(null)

        binding.btnBell.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_notificationsFragment)
        }

    }




    private fun currentGreeting(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 4..11  -> "Good Morning,"
            in 12..16 -> "Good Afternoon,"
            in 17..20 -> "Good Evening,"
            else      -> "Good Night,"
        }
    }
    private fun loadUserName() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val raw = doc.getString("name")
                    ?: doc.getString("fullName")
                    ?: doc.getString("username")
                    ?: auth.currentUser?.displayName
                    ?: "User"

                tvUsername.text = toFirstName(raw)
            }
            .addOnFailureListener {
                tvUsername.text = "User"
            }
    }
    private fun toFirstName(s: String): String {
        val first = s.trim().split("\\s+".toRegex()).firstOrNull().orEmpty()
        return first.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
    }


    private fun updateFilterChip(filter: DateFilter?) {
        val ivMore = view?.findViewById<View>(R.id.ivMore)
        if (filter == null) {
            chipFilter.visibility = View.GONE
            ivMore?.visibility = View.VISIBLE
        } else {
            chipFilter.text = buildChipLabel(filter)
            chipFilter.visibility = View.VISIBLE
            ivMore?.visibility = View.GONE
        }
    }

    private fun applyFilter(filter: DateFilter) {
        activeFilter = filter
        loadTotals(filter)       // only totals are filtered
        updateFilterChip(filter)
    }

    private fun clearFilter() {
        activeFilter = null
        loadTotals()             // unfiltered totals
        updateFilterChip(null)
        Toast.makeText(requireContext(), "Filter cleared", Toast.LENGTH_SHORT).show()
    }


    private fun buildChipLabel(filter: DateFilter): String {
        val sdf = java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault())
        return when (filter.label) {
            "Custom" -> "${sdf.format(filter.start)}–${sdf.format(java.util.Date(filter.endExclusive.time - 1))}"
            else -> filter.label
        }
    }


    private fun showFilterSheet() {
        val dialog = BottomSheetDialog(requireContext())
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val sheet = layoutInflater.inflate(R.layout.bottom_sheet_filter, null)
        dialog.setContentView(sheet)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setOnShowListener {
            val bottomSheet =
                dialog.findViewById<FrameLayout>(R.id.design_bottom_sheet) // 👈 use app R.id
            bottomSheet?.setBackgroundColor(Color.TRANSPARENT)
        }

        dialog.show()


        dialog.show()

        val rg = sheet.findViewById<android.widget.RadioGroup>(R.id.rgPreset)
        val rbToday = sheet.findViewById<android.widget.RadioButton>(R.id.rbToday)
        val rbLast7 = sheet.findViewById<android.widget.RadioButton>(R.id.rbLast7)
        val rbLast15 = sheet.findViewById<android.widget.RadioButton>(R.id.rbLast15)
        val rbLast30 = sheet.findViewById<android.widget.RadioButton>(R.id.rbLast30)
        val rbCustom = sheet.findViewById<android.widget.RadioButton>(R.id.rbCustom)
        val tvChosenRange = sheet.findViewById<TextView>(R.id.tvChosenRange)
        val btnClear = sheet.findViewById<View>(R.id.btnClear)
        val btnCancel = sheet.findViewById<View>(R.id.btnCancel)
        val btnApply = sheet.findViewById<View>(R.id.btnApply)

        var customStart: Date? = null
        var customEndExclusive: Date? = null

        fun fmt(d: Date) = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(d)

        rg.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbCustom) {
                val picker = MaterialDatePicker.Builder.dateRangePicker()
                    .setTitleText("Select date range")
                    .build()
                picker.addOnPositiveButtonClickListener { pair ->
                    val startUtc = pair.first ?: return@addOnPositiveButtonClickListener
                    val endUtc = pair.second ?: return@addOnPositiveButtonClickListener
                    // Convert millis to Date; make end exclusive (next day 00:00)
                    customStart = atStartOfDay(Date(startUtc))
                    customEndExclusive = nextDayStart(Date(endUtc))
                    tvChosenRange.text = "${fmt(customStart!!)} — ${fmt(Date(endUtc))}"
                }
                picker.show(parentFragmentManager, "rangePicker")
            }
        }



        btnClear.setOnClickListener {
            clearFilter()         // <- use helper
            dialog.dismiss()
        }
        btnCancel.setOnClickListener { dialog.dismiss() }

        btnApply.setOnClickListener {
            val filter = when {
                rbToday.isChecked  -> buildPreset(daysBack = 0,  label = "Today")
                rbLast7.isChecked  -> buildPreset(daysBack = 6,  label = "Last 7 days")
                rbLast15.isChecked -> buildPreset(daysBack = 14, label = "Last 15 days")
                rbLast30.isChecked -> buildPreset(daysBack = 29, label = "Last 30 days")
                rbCustom.isChecked -> {
                    if (customStart == null || customEndExclusive == null) {
                        Toast.makeText(requireContext(), "Select a custom range", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    DateFilter(customStart!!, customEndExclusive!!, "Custom")
                }
                else -> null
            } ?: return@setOnClickListener

            applyFilter(filter)   // <- use helper
            Toast.makeText(requireContext(), "Applied: ${filter.label}", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }
    private fun buildPreset(daysBack: Int, label: String): DateFilter {
        val now = Calendar.getInstance()
        val endExclusive = nextDayStart(now.time)            // tomorrow 00:00
        val start = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -daysBack)
        }.time
        return DateFilter(atStartOfDay(start), endExclusive, label)
    }

    private fun atStartOfDay(d: Date): Date {
        val c = Calendar.getInstance()
        c.time = d
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.time
    }

    private fun nextDayStart(d: Date): Date {
        val c = Calendar.getInstance()
        c.time = d
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.DAY_OF_YEAR, 1)
        return c.time
    }

    private fun loadTop3() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        showTxnLoading(true)
        db.collection("users")
            .document(uid)
            .collection("transactions")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(3)
            .get()
            .addOnSuccessListener { snap ->
                transactionList.clear()
                transactionList.addAll(
                    snap.documents.mapNotNull { d ->
                        d.toObject(Transactionget::class.java)?.copy(documentId = d.id)
                    }
                )
                adapter.notifyDataSetChanged()
                showTxnLoading(false)
                showEmpty(transactionList.isEmpty())
            }
            .addOnFailureListener {
                showEmpty(true)
                showTxnLoading(false)
            }
    }

    private fun showEmpty(isEmpty: Boolean) {
        binding.emptyStateLayout.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvTransactions.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }
    private fun showTxnLoading(show: Boolean) {
        binding.progressOverlayTxn.visibility = if (show) View.VISIBLE else View.GONE
    }
    private fun loadTotals() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("users")
            .document(userId)
            .collection("transactions")
            .get()
            .addOnSuccessListener { result ->
                var totalIncome = 0.0
                var totalExpense = 0.0

                for (doc in result) {
                    val amount = doc.getDouble("amount") ?: 0.0
                    val type = doc.getString("type") ?: ""

                    if (type == "income") {
                        totalIncome += amount
                    } else if (type == "expense") {
                        totalExpense += amount
                    }
                }

                val totalBalance = totalIncome - totalExpense

                // Update UI
                incomeText.text = "₹ %.2f".format(totalIncome)
                expenseText.text = "₹ %.2f".format(totalExpense)
                tvBalance.text = "₹ %.2f".format(totalBalance)
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    private fun loadTotals(filter: DateFilter) {
        val userId = auth.currentUser?.uid ?: return
        val col = db.collection("users").document(userId).collection("transactions")
        col.whereGreaterThanOrEqualTo("timestamp", Timestamp(filter.start))
            .whereLessThan("timestamp", Timestamp(filter.endExclusive))
            .get()
            .addOnSuccessListener { result ->
                var totalIncome = 0.0
                var totalExpense = 0.0
                for (doc in result) {
                    val amount = doc.getDouble("amount") ?: 0.0
                    when (doc.getString("type") ?: "") {
                        "income" -> totalIncome += amount
                        "expense" -> totalExpense += amount
                    }
                }
                updateTotalsUI(totalIncome, totalExpense)
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    private fun updateTotalsUI(totalIncome: Double, totalExpense: Double) {
        val totalBalance = totalIncome - totalExpense
        incomeText.text = "₹ %.2f".format(totalIncome)
        expenseText.text = "₹ %.2f".format(totalExpense)
        tvBalance.text = "₹ %.2f".format(totalBalance)
    }




    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
