// WalletFragment.kt - Fixed version with proper totals update timing
package com.gourav.finango.ui.wallet

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gourav.finango.databinding.FragmentWalletBinding
import com.gourav.finango.ui.addtransaction.AddTransaction
import com.google.android.material.chip.Chip
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.gourav.finango.ui.addtransaction.Transactionadd
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class WalletFragment : Fragment() {
    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TransactionAdapter
    private lateinit var transactionList: MutableList<Transactionget>
    private var lastVisible: DocumentSnapshot? = null
    private var isLoading = false

    private var _binding: FragmentWalletBinding? = null
    private val binding get() = _binding!!

    // Current filter state
    private var txnType = FilterDialogFragment.TxnType.ALL
    private var dateRange = FilterDialogFragment.DateRange.ALL

    // Activity Result Launcher for AddTransaction
    private val addTransactionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val newTransactionAdd = result.data?.getParcelableExtraCompat<Transactionadd>("newTransaction")
            newTransactionAdd?.let { transactionAdd ->
                // Convert Transactionadd to Transaction (with empty documentId for now)
                val transaction = Transactionget(
                    documentId = transactionAdd.documentId, // Will be empty until we save to Firestore and get the ID
                    date = transactionAdd.date,
                    amount = transactionAdd.amount,
                    type = transactionAdd.type,
                    category = transactionAdd.category,
                    description = transactionAdd.description,
                    timestamp = transactionAdd.timestamp
                )

                transactionList.add(0, transaction)
                adapter.notifyItemInserted(0)
                binding.recyclerTransactions.scrollToPosition(0)
                updateTotalsForCurrentFilters()
                Toast.makeText(context, "Transaction added successfully", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWalletBinding.inflate(inflater, container, false)
        recyclerView = binding.recyclerTransactions
        transactionList = mutableListOf()
        adapter = TransactionAdapter(requireContext(), transactionList)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(BottomMarginDecoration(100))

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val totalItemCount = layoutManager.itemCount
                val lastVisibleItem = layoutManager.findLastVisibleItemPosition()

                if (!isLoading && totalItemCount <= (lastVisibleItem + 5)) {
                    fetchTransactionsWithFilters(firstPage = false)
                }
            }
        })
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()

        binding.fabAddTransaction.setOnClickListener {
            val intent = Intent(context, AddTransaction::class.java)
            addTransactionLauncher.launch(intent)
        }

        binding.transactionfilter.setOnClickListener {
            FilterDialogFragment
                .newInstance(txnType, dateRange)
                .show(parentFragmentManager, FilterDialogFragment.TAG)
        }

        parentFragmentManager.setFragmentResultListener(
            FilterDialogFragment.REQ_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            txnType = FilterDialogFragment.TxnType.valueOf(
                bundle.getString(FilterDialogFragment.RES_TXN, FilterDialogFragment.TxnType.ALL.name)
            )
            dateRange = FilterDialogFragment.DateRange.valueOf(
                bundle.getString(FilterDialogFragment.RES_DATE, FilterDialogFragment.DateRange.ALL.name)
            )

            Log.d("FilterDebug", "Filter changed to txnType=$txnType, dateRange=$dateRange")

            renderFilterChips()
            applyFiltersAndRefresh()
        }

        renderFilterChips()
        applyFiltersAndRefresh()
    }

    // SOLUTION: Fetch all transactions and filter client-side to avoid index issues
    private fun fetchTransactionsWithFilters(firstPage: Boolean) {
        if (isLoading) return

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Toast.makeText(context, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d("FetchDebug", "Fetching transactions - firstPage: $firstPage, filters: type=$txnType, date=$dateRange")

        // Use simple query that doesn't require composite index
        val baseQuery = db.collection("users").document(uid).collection("transactions")
            .orderBy("timestamp", Query.Direction.DESCENDING)

        val pagedQuery = if (firstPage || lastVisible == null) {
            baseQuery.limit(50) // Fetch more to account for client-side filtering
        } else {
            baseQuery.startAfter(lastVisible!!).limit(50)
        }

        isLoading = true
        handlingProgress(true)

        pagedQuery.get().addOnCompleteListener { task ->
            handlingProgress(false)
            isLoading = false

            if (!task.isSuccessful) {
                val error = task.exception
                Log.e("FetchDebug", "Query failed", error)
                Toast.makeText(context, "Error loading transactions", Toast.LENGTH_SHORT).show()
                return@addOnCompleteListener
            }

            val docs = task.result!!
            Log.d("FetchDebug", "Query returned ${docs.size()} documents")

            if (!docs.isEmpty) {
                val startPosition = transactionList.size
                val lowerBound = getLowerBoundForDateRange()
                var addedCount = 0

                // Apply filters client-side
                for (document in docs.documents) {
                    val transaction = document.toObject(Transactionget::class.java)
                    if (transaction != null && shouldIncludeTransaction(transaction, lowerBound)) {
                        transaction.documentId = document.id
                        transactionList.add(transaction)
                        addedCount++
                        Log.d("FilterDebug", "Added transaction: ${transaction.type}, ${transaction.amount}")
                    }
                }

                // Update UI
                if (firstPage) {
                    adapter.notifyDataSetChanged()
                } else if (addedCount > 0) {
                    adapter.notifyItemRangeInserted(startPosition, addedCount)
                }

                lastVisible = docs.documents.lastOrNull()
                Log.d("FetchDebug", "Added $addedCount transactions after filtering. Total: ${transactionList.size}")

                // FIXED: Update totals after transactions are added to the list
                updateTotalsForCurrentFilters()

                // If no transactions were added after filtering and we have more data, fetch more
                if (addedCount == 0 && docs.size() >= 50 && hasActiveFilters()) {
                    Log.d("FetchDebug", "No matching transactions, fetching more...")
                    fetchTransactionsWithFilters(firstPage = false)
                    return@addOnCompleteListener
                }
            } else {
                Log.d("FetchDebug", "No more documents available")
                if (firstPage) {
                    // FIXED: Update totals even when no transactions found (to show zeros)
                    updateTotalsForCurrentFilters()
                    Toast.makeText(context, "No transactions found", Toast.LENGTH_SHORT).show()
                }
            }

            // Stop pagination if we got less than requested
            if (docs.size() < 50) {
                isLoading = true // Prevent further loading
                Log.d("FetchDebug", "Reached end of data")
            }
        }
    }

    // Check if transaction should be included based on current filters
    private fun shouldIncludeTransaction(transaction: Transactionget, lowerBound: Date?): Boolean {
        Log.d("FilterDebug", "=== Checking transaction ===")
        Log.d("FilterDebug", "Transaction: type=${transaction.type}, amount=${transaction.amount}")
        Log.d("FilterDebug", "Current filters: txnType=$txnType, dateRange=$dateRange")

        // Type filter
        val passesTypeFilter = when (txnType) {
            FilterDialogFragment.TxnType.ALL -> {
                Log.d("FilterDebug", "Type filter: ALL - passes")
                true
            }
            FilterDialogFragment.TxnType.INCOME -> {
                val passes = transaction.type?.lowercase() == "income"
                Log.d("FilterDebug", "Type filter: INCOME - transaction.type='${transaction.type}', passes=$passes")
                passes
            }
            FilterDialogFragment.TxnType.EXPENSE -> {
                val passes = transaction.type?.lowercase() == "expense"
                Log.d("FilterDebug", "Type filter: EXPENSE - transaction.type='${transaction.type}', passes=$passes")
                passes
            }
        }

        if (!passesTypeFilter) {
            Log.d("FilterDebug", "❌ Transaction filtered out by type")
            return false
        }

        // Date filter - using timestamp field (Firestore Timestamp)
        val passesDateFilter = if (lowerBound != null) {
            val transactionDate = transaction.timestamp?.toDate() // Convert Firestore Timestamp to Date
            val passes = transactionDate != null && transactionDate.after(lowerBound)

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            Log.d("FilterDebug", "Date filter check:")
            Log.d("FilterDebug", "  Transaction timestamp: ${transaction.timestamp}")
            Log.d("FilterDebug", "  Transaction date: ${if (transactionDate != null) dateFormat.format(transactionDate) else "null"}")
            Log.d("FilterDebug", "  Lower bound: ${dateFormat.format(lowerBound)}")
            Log.d("FilterDebug", "  Passes filter: $passes")

            passes
        } else {
            Log.d("FilterDebug", "No date filter applied")
            true
        }

        val result = passesTypeFilter && passesDateFilter
        Log.d("FilterDebug", if (result) "✅ Transaction INCLUDED" else "❌ Transaction EXCLUDED")
        Log.d("FilterDebug", "========================")
        return result
    }

    // Check if any filters are currently active
    private fun hasActiveFilters(): Boolean {
        return txnType != FilterDialogFragment.TxnType.ALL ||
                dateRange != FilterDialogFragment.DateRange.ALL
    }

    private fun getLowerBoundForDateRange(): Date? {
        val cal = Calendar.getInstance()
        val result = when (dateRange) {
            FilterDialogFragment.DateRange.ALL -> null
            FilterDialogFragment.DateRange.LAST_15_DAYS -> {
                cal.add(Calendar.DAY_OF_YEAR, -15)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.time
            }
            FilterDialogFragment.DateRange.LAST_MONTH -> {
                cal.add(Calendar.MONTH, -1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.time
            }
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        Log.d("DateFilter", "Date range: $dateRange")
        Log.d("DateFilter", "Lower bound: ${if (result != null) dateFormat.format(result) else "null"}")

        return result
    }

    private fun applyFiltersAndRefresh() {
        Log.d("FilterDebug", "Applying filters and refreshing...")

        lastVisible = null
        isLoading = false
        transactionList.clear()
        adapter.notifyDataSetChanged()

        // FIXED: Reset totals to zero immediately when clearing list
        binding.tvincometext.text = "₹ 0.00"
        binding.tvexpensetext.text = "₹ 0.00"

        // Fetch transactions - totals will be updated inside the fetch callback
        fetchTransactionsWithFilters(firstPage = true)
        // REMOVED: updateTotalsForCurrentFilters() - this was causing the zero display
    }

    private fun updateTotalsForCurrentFilters() {
        var totalIncome = 0.0
        var totalExpense = 0.0

        Log.d("TotalsDebug", "Calculating totals from ${transactionList.size} transactions")

        for ((index, transaction) in transactionList.withIndex()) {
            val amount = transaction.amount ?: 0.0
            val type = transaction.type?.lowercase()

            Log.d("TotalsDebug", "Transaction $index: type=$type, amount=$amount")

            when (type) {
                "income" -> {
                    totalIncome += amount
                    Log.d("TotalsDebug", "Added to income: $amount, new total: $totalIncome")
                }
                "expense" -> {
                    totalExpense += amount
                    Log.d("TotalsDebug", "Added to expense: $amount, new total: $totalExpense")
                }
                else -> {
                    Log.d("TotalsDebug", "Unknown type: $type")
                }
            }
        }

        // Update UI
        binding.tvincometext.text = "₹ ${String.format("%,.2f", totalIncome)}"
        binding.tvexpensetext.text = "₹ ${String.format("%,.2f", totalExpense)}"

        Log.d("TotalsDebug", "Final totals - Income: $totalIncome, Expense: $totalExpense")
        Log.d("TotalsDebug", "Updated UI - Income text: ${binding.tvincometext.text}, Expense text: ${binding.tvexpensetext.text}")
    }

    private fun makeChip(label: String, onClose: () -> Unit): Chip {
        return Chip(requireContext()).apply {
            text = label
            isCloseIconVisible = true
            isCheckable = false
            setOnCloseIconClickListener { onClose() }
        }
    }

    private fun renderFilterChips() {
        val chipGroup = binding.chipGroupFilters
        chipGroup.removeAllViews()

        if (txnType != FilterDialogFragment.TxnType.ALL) {
            val label = when (txnType) {
                FilterDialogFragment.TxnType.INCOME -> "Income"
                FilterDialogFragment.TxnType.EXPENSE -> "Expense"
                else -> "All"
            }
            chipGroup.addView(makeChip(label) {
                txnType = FilterDialogFragment.TxnType.ALL
                renderFilterChips()
                applyFiltersAndRefresh()
            })
        }

        if (dateRange != FilterDialogFragment.DateRange.ALL) {
            val label = when (dateRange) {
                FilterDialogFragment.DateRange.LAST_15_DAYS -> "Last 15 days"
                FilterDialogFragment.DateRange.LAST_MONTH -> "Last month"
                else -> "All"
            }
            chipGroup.addView(makeChip(label) {
                dateRange = FilterDialogFragment.DateRange.ALL
                renderFilterChips()
                applyFiltersAndRefresh()
            })
        }

        chipGroup.isVisible = chipGroup.childCount > 0
    }

    private fun handlingProgress(show: Boolean) {
        binding.progressforwallet.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inline fun <reified T : Parcelable> Intent.getParcelableExtraCompat(key: String): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(key, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(key) as? T
        }
    }
}