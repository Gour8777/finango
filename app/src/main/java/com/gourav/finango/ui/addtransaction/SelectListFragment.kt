package com.gourav.finango.ui.addtransaction

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.core.os.bundleOf
import androidx.core.widget.addTextChangedListener

import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.gourav.finango.R
import com.gourav.finango.databinding.FragmentSelectListBinding

class SelectListFragment : DialogFragment() {

    companion object {
        const val TAG = "SelectListFragment"
        const val TYPE_CATEGORY = "CATEGORY"
        const val TYPE_CURRENCY = "CURRENCY"

        const val ARG_TYPE = "arg_type"

        const val REQ_KEY = "select_list_result"
        const val BUNDLE_TYPE = "res_type"
        const val BUNDLE_NAME = "res_name"
        const val BUNDLE_CODE = "res_code"
        const val BUNDLE_ICON = "res_icon"

        fun newInstance(type: String) = SelectListFragment().apply {
            arguments = bundleOf(ARG_TYPE to type)
        }
    }

    private var _binding: FragmentSelectListBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: SelectListAdapter
    private lateinit var type: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        type = requireArguments().getString(ARG_TYPE) ?: TYPE_CATEGORY
    }
    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        // optional: remove default dialog rounded corners/background
        dialog?.window?.setBackgroundDrawableResource(android.R.color.white)
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSelectListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.tvTitle.text = if (type == TYPE_CATEGORY) "Select Category" else "Select Currency"
        binding.btnClose.setOnClickListener { dismiss() }

        val items = if (type == TYPE_CATEGORY) categoryItems() else currencyItems()

        adapter = SelectListAdapter(items) { item ->
            // Send result back
            parentFragmentManager.setFragmentResult(
                REQ_KEY,
                bundleOf(
                    BUNDLE_TYPE to type,
                    BUNDLE_NAME to item.name,
                    BUNDLE_CODE to item.code,           // null for categories
                    BUNDLE_ICON to (item.icon ?: 0)
                )
            )
            dismiss()
        }

        binding.recycler.adapter = adapter
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())

        // Live search
        binding.search.addTextChangedListener { editable ->
            adapter.filter(editable?.toString().orEmpty())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ------- Sample data (replace with your own if needed) -------

    private fun categoryItems(): List<SelectItem> = listOf(
        SelectItem(name = "General",   icon = R.drawable.transaction),
        SelectItem(name = "Groceries",   icon = R.drawable.grocery),
        SelectItem(name = "Food & Drinks", icon = R.drawable.foodanddrink),
        SelectItem(name = "Furniture",   icon = R.drawable.furniture),
        SelectItem(name = "Rent",   icon = R.drawable.rent),
        SelectItem(name = "Water",   icon = R.drawable.water),
        SelectItem(name = "Gifts",   icon = R.drawable.gifts),
        SelectItem(name = "Medical",   icon = R.drawable.medical),
        SelectItem(name = "Maintenance",   icon = R.drawable.maintenance),
        SelectItem(name = "Travel",   icon = R.drawable.travel),
        SelectItem(name = "Movies",   icon = R.drawable.movies),
        SelectItem(name = "Electricity",   icon = R.drawable.electricity),
        SelectItem(name = "Donation",   icon = R.drawable.donation),
    )

    private fun currencyItems(): List<SelectItem> = listOf(
        SelectItem(name = "Indian Rupee", code = "INR", icon = R.drawable.rupee),
        //SelectItem(name = "US Dollar",    code = "USD", icon = R.drawable.dollar),
    )
}

data class SelectItem(
    val name: String,
    val code: String? = null,      // used for currency (e.g., "INR")
    @DrawableRes val icon: Int? = null
)

