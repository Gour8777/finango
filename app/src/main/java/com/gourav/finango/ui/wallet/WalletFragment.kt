// WalletFragment.kt - Fixed version with proper totals update timing
package com.gourav.finango.ui.wallet

import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gourav.finango.databinding.FragmentWalletBinding
import com.gourav.finango.ui.addtransaction.AddTransaction
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.gourav.finango.R
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
    private val txnLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null) return@registerForActivityResult

        when (result.data!!.getStringExtra("action")) {
            // ---------- ADD ----------
            "added" -> {
                val tAdd = result.data!!.getParcelableExtraCompat<Transactionadd>("newTransaction") ?: return@registerForActivityResult

                val t = Transactionget(
                    documentId = tAdd.documentId,
                    date       = tAdd.date,
                    amount     = tAdd.amount,
                    type       = tAdd.type,
                    category   = tAdd.category,
                    description= tAdd.description,
                    timestamp  = tAdd.timestamp
                )

                transactionList.add(0, t)
                adapter.notifyItemInserted(0)
                binding.recyclerTransactions.scrollToPosition(0)
                updateTotalsForCurrentFilters()
                Toast.makeText(context, "Transaction added successfully", Toast.LENGTH_SHORT).show()
            }

            // ---------- UPDATE ----------
            "updated" -> {
                val uAdd = result.data!!.getParcelableExtraCompat<Transactionadd>("updatedTransaction") ?: return@registerForActivityResult

                // Convert to your list item model
                val updated = Transactionget(
                    documentId = uAdd.documentId,
                    date       = uAdd.date,
                    amount     = uAdd.amount,
                    type       = uAdd.type,
                    category   = uAdd.category,
                    description= uAdd.description,
                    timestamp  = uAdd.timestamp // usually unchanged on edit; ok to keep
                )

                val idx = transactionList.indexOfFirst { it.documentId == updated.documentId }
                if (idx != -1) {
                    transactionList[idx] = updated
                    adapter.notifyItemChanged(idx)
                    // If your list is strictly sorted (e.g., by timestamp desc), re-sort + submit:
                    // resortIfNeededAndRefresh()
                } else {
                    // Fallback: if not found (e.g., filtered out earlier), insert at top
                    transactionList.add(0, updated)
                    adapter.notifyItemInserted(0)
                }

                updateTotalsForCurrentFilters()
                Toast.makeText(context, "Transaction updated", Toast.LENGTH_SHORT).show()
            }

            // (Optional) DELETE case for future:
            "deleted" -> {
                val id = result.data!!.getStringExtra("documentId") ?: return@registerForActivityResult
                val idx = transactionList.indexOfFirst { it.documentId == id }
                if (idx != -1) {
                    transactionList.removeAt(idx)
                    adapter.notifyItemRemoved(idx)
                    updateTotalsForCurrentFilters()
                    Toast.makeText(context, "Transaction deleted", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    override fun onCreateView(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWalletBinding.inflate(inflater, container, false)
        recyclerView = binding.recyclerTransactions
        transactionList = mutableListOf()
        adapter = TransactionAdapter(requireContext(), transactionList) { item ->
            // Use the SAME launcher so result returns to WalletFragment
            val intent = Intent(requireContext(), com.gourav.finango.ui.TransactionDetails.TransactionDetailActivity::class.java).apply {
                putExtra("documentId", item.documentId)
                putExtra("date",        item.date)
                putExtra("amount",      item.amount)
                putExtra("type",        item.type)
                putExtra("category",    item.category)
                putExtra("description", item.description)
                putExtra("timestampSeconds", item.timestamp?.seconds ?: 0L)
                putExtra("timestampNanos",   item.timestamp?.nanoseconds ?: 0)
                // If you store currency on Transactionget, also pass it:
                // putExtra("currency", item.currency)
            }
            txnLauncher.launch(intent)
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(BottomMarginDecoration(100))
        attachSwipeToDelete()

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
            txnLauncher.launch(intent)
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
    private fun attachSwipeToDelete() {
        val icon: Drawable? = ContextCompat.getDrawable(requireContext(), R.drawable.delete_24) // add vector asset if missing
        val bgPaint = Paint().apply { color = Color.parseColor("#FEE2E2") } // light red bg
        val iconTint = Paint().apply { color = Color.parseColor("#EF4444") } // red tint if you draw manually

        val itemTouchHelper =
            ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean = false

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    @Suppress("DEPRECATION")
                    val pos = viewHolder.adapterPosition
                    if (pos == RecyclerView.NO_POSITION) return

                    val item = transactionList[pos]

                    // 1) Optimistic remove from list
                    transactionList.removeAt(pos)
                    adapter.notifyItemRemoved(pos)
                    updateTotalsForCurrentFilters()

                    // 2) Show Snackbar with UNDO; only delete from Firestore if NOT undone
                    var undone = false
                    val sb = com.google.android.material.snackbar.Snackbar
                        .make(binding.root, "Transaction deleted", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                        .setAction("UNDO") {
                            undone = true
                            // Put it back in the same position
                            val safePos = pos.coerceAtMost(transactionList.size)
                            transactionList.add(safePos, item)
                            adapter.notifyItemInserted(safePos)
                            binding.recyclerTransactions.scrollToPosition(safePos)
                            updateTotalsForCurrentFilters()
                        }

                    sb.addCallback(object : com.google.android.material.snackbar.Snackbar.Callback() {
                        override fun onDismissed(transientBottomBar: com.google.android.material.snackbar.Snackbar?, event: Int) {
                            if (!undone) {
                                // 3) Commit delete in Firestore only now
                                commitDeleteInFirestore(item,
                                    onSuccess = {
                                        // decrement aggregated totals in user doc
                                        val uid = auth.currentUser?.uid
                                        if (uid != null) adjustUserTotalsOnDelete(uid, item.type, item.amount ?: 0.0)
                                    },
                                    onError = { e ->
                                        // If delete fails, restore the row to keep UI consistent
                                        transactionList.add(0, item)
                                        adapter.notifyItemInserted(0)
                                        updateTotalsForCurrentFilters()
                                        android.widget.Toast.makeText(context, "Delete failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        }
                    })
                    sb.show()
                }


                override fun onChildDraw(
                    c: Canvas,
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    dX: Float,
                    dY: Float,
                    actionState: Int,
                    isCurrentlyActive: Boolean
                ) {
                    val itemView = viewHolder.itemView

                    if (dX > 0) { // swiping to the RIGHT
                        // draw light red background from left to swipe distance
                        val bg = RectF(
                            itemView.left.toFloat(),
                            itemView.top.toFloat(),
                            itemView.left + dX,
                            itemView.bottom.toFloat()
                        )
                        c.drawRect(bg, bgPaint)

                        // place delete icon on the LEFT, centered vertically
                        icon?.let {
                            val iconSize = dpToPx(24f).toInt()
                            val margin = dpToPx(16f).toInt()
                            val top = itemView.top + (itemView.height - iconSize) / 2
                            val left = itemView.left + margin
                            val right = left + iconSize
                            val bottom = top + iconSize
                            it.setBounds(left, top, right, bottom)
                            it.draw(c) // if your vector isn't tinted, ensure it's red in asset or use setTint
                        }
                    }

                    // Keep default swipe animation
                    super.onChildDraw(
                        c,
                        recyclerView,
                        viewHolder,
                        dX,
                        dY,
                        actionState,
                        isCurrentlyActive
                    )
                }
            })

        itemTouchHelper.attachToRecyclerView(recyclerView)
    }private fun commitDeleteInFirestore(
        item: Transactionget,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        val docId = item.documentId
        if (uid.isNullOrEmpty() || docId.isNullOrEmpty()) {
            onError(IllegalStateException("Invalid user or documentId"))
            return
        }

        db.collection("users")
            .document(uid)
            .collection("transactions")
            .document(docId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e) }
    }

    private fun adjustUserTotalsOnDelete(uid: String, type: String?, amount: Double) {
        val ref = db.collection("users").document(uid)
        when (type?.lowercase()) {
            "income"  -> ref.update("total_income",  com.google.firebase.firestore.FieldValue.increment(-amount))
            "expense" -> ref.update("total_expense", com.google.firebase.firestore.FieldValue.increment(-amount))
        }
    }


    



    private fun dpToPx(dp: Float): Float {
        val metrics = resources.displayMetrics
        return dp * metrics.density
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