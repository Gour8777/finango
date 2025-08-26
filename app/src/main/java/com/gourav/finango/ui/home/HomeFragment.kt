package com.gourav.finango.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gourav.finango.databinding.FragmentHomeBinding
import com.google.android.material.button.MaterialButton
import android.widget.LinearLayout
import android.widget.Toast

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // Replace with your real adapter
    private lateinit var transactionsAdapter: RecyclerView.Adapter<*>

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

        // Views from your XML
        val recyclerView: RecyclerView = binding.rvTransactions
        val emptyState: LinearLayout = binding.emptyStateLayout

        // Setup RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        transactionsAdapter = createOrGetTransactionsAdapter()
        recyclerView.adapter = transactionsAdapter

        // Add button action
        emptyState.setOnClickListener {
            // TODO: navigate to your "Add Transaction" screen
            Toast.makeText(requireContext(), "Add Transaction clicked", Toast.LENGTH_SHORT).show()
        }

        // Toggle logic
        fun updateEmptyState() {
            val hasItems = transactionsAdapter.itemCount > 0
            recyclerView.visibility = if (hasItems) View.VISIBLE else View.GONE
            emptyState.visibility = if (hasItems) View.GONE else View.VISIBLE
        }

        // Observe adapter changes
        transactionsAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() = updateEmptyState()
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = updateEmptyState()
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = updateEmptyState()
        })

        // Initial state (after you set initial data into the adapter)
        updateEmptyState()
    }

    private fun createOrGetTransactionsAdapter(): RecyclerView.Adapter<*> {
        // Replace this with your real adapter instance, e.g.:
        // return TransactionsAdapter(mutableListOf())
        return object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                // Minimal no-op placeholder; replace with your real ViewHolder
                val v = View(parent.context)
                v.layoutParams = ViewGroup.LayoutParams(0, 0)
                return object : RecyclerView.ViewHolder(v) {}
            }
            override fun getItemCount(): Int = 0 // Start empty; set real count in your adapter
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
