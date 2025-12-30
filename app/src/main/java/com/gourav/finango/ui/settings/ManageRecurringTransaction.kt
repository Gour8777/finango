package com.gourav.finango.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.gourav.finango.R
import com.gourav.finango.databinding.FragmentManageRecurringTransactionBinding
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

class ManageRecurringTransaction : Fragment(R.layout.fragment_manage_recurring_transaction) {

    private var _binding: FragmentManageRecurringTransactionBinding? = null
    private val binding get() = _binding!!

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)


    private lateinit var adapter: RecurringAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentManageRecurringTransactionBinding.bind(view)

        // Edge-to-edge padding (optional)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom)
            insets
        }

        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        adapter = RecurringAdapter(
            onDeleteClick = { item -> confirmDelete(item) },
            onEditClick = {item->openEditSheet(item)}
        )

        binding.recurringtransactionrv.layoutManager = LinearLayoutManager(requireContext())
        binding.recurringtransactionrv.adapter = adapter

        loadData()
    }
    private fun openEditSheet(item: RecurringTransaction) {
        EditRecurringBottomSheet.newInstance(item).apply {
            onSaved = { updated ->
                val current = adapter.currentList.toMutableList()
                val idx = current.indexOfFirst { it.id == updated.id }
                if (idx >= 0) {
                    current[idx] = updated
                    // sort by nextDueDate (nulls last)
                    val sorted = current.sortedWith(compareBy(
                        { it.nextDueDate == null },
                        { it.nextDueDate }
                    ))
                    adapter.submitList(sorted)
                }
            }
        }.show(parentFragmentManager, "editRecurring")
    }


    private fun loadData() {
        val uid = auth.currentUser?.uid ?: return
        showOverlay(true)
        db.collection("users").document(uid)
            .collection("recurringTransactions")
            .get()
            .addOnSuccessListener { snap ->
                val items = snap.documents.mapNotNull { d ->
                    try {
                        RecurringTransaction(
                            id = d.id,
                            title = d.getString("title") ?: "",
                            amount = (d.get("amount") as? Number)?.toLong() ?: 0L,
                            currency = d.getString("currency") ?: "INR",
                            category = d.getString("category") ?: "General",
                            type = d.getString("type") ?: "expense",
                            frequency = d.getString("frequency") ?: "DAILY",
                            endDate = d.getString("endDate"),
                            nextDueDate = d.getString("nextDueDate"),
                            isActive = d.getBoolean("isActive") ?: true
                        )
                    } catch (_: Exception) { null }
                }

                // Sort by nearest nextDueDate (nulls last)
                val sorted = items.sortedWith(compareBy(
                    { it.nextDueDate == null }, // false first (non-null), true last (null)
                    { it.nextDueDate?.let { s -> safeParse(s) } }
                ))

                adapter.submitList(sorted)
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Load failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
            .addOnCompleteListener { showOverlay(false) }
    }

    private fun confirmDelete(item: RecurringTransaction) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete recurring")
            .setMessage("Delete “${item.title}” permanently?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ -> performDelete(item) }
            .show()
    }

    private fun performDelete(item: RecurringTransaction) {
        val uid = auth.currentUser?.uid ?: return
        showOverlay(true)

        db.collection("users").document(uid)
            .collection("recurringTransactions")
            .document(item.id)
            .delete()
            .addOnSuccessListener {
                // remove locally without re-fetch
                val current = adapter.currentList.toMutableList()
                val idx = current.indexOfFirst { it.id == item.id }
                if (idx >= 0) {
                    current.removeAt(idx)
                    // keep the same sort invariant
                    val reSorted = current.sortedWith(compareBy(
                        { it.nextDueDate == null },
                        { it.nextDueDate?.let { s -> safeParse(s) } }
                    ))
                    adapter.submitList(reSorted)
                }
                Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Delete failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
            .addOnCompleteListener { showOverlay(false) }
    }

    fun safeParse(date: String): Date? = try {
        dateFmt.parse(date)
    } catch (e: Exception) { null }

    private fun showOverlay(show: Boolean) {
        // Requires a full-screen overlay view with id `loadingOverlay` in your fragment XML
        binding.loadingOverlay?.let { it.visibility = if (show) View.VISIBLE else View.GONE }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
