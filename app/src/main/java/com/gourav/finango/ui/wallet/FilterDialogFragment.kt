package com.gourav.finango.ui.wallet



import android.os.Bundle
import android.view.*
import androidx.fragment.app.DialogFragment
import com.gourav.finango.R
import com.gourav.finango.databinding.FragmentFilterDialogBinding

class FilterDialogFragment : DialogFragment() {

    private var _binding: FragmentFilterDialogBinding? = null
    private val binding get() = _binding!!

    enum class TxnType { ALL, INCOME, EXPENSE }
    enum class DateRange { LAST_15_DAYS, LAST_MONTH, ALL }

    companion object {
        const val TAG = "FilterDialogFragment"
        const val REQ_KEY = "filter_result_key"
        const val RES_TXN = "res_txn"
        const val RES_DATE = "res_date"

        fun newInstance(
            preTxn: TxnType = TxnType.ALL,
            preDate: DateRange = DateRange.ALL
        ) = FilterDialogFragment().apply {
            arguments = Bundle().apply {
                putString(RES_TXN, preTxn.name)
                putString(RES_DATE, preDate.name)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.AppDialog_ActivitySized)

    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFilterDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            // Important: do NOT set a transparent background here
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.45f }
        }
    }



    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Preselect based on args
        val preTxn = TxnType.valueOf(requireArguments().getString(RES_TXN, TxnType.ALL.name))
        val preDate = DateRange.valueOf(requireArguments().getString(RES_DATE, DateRange.ALL.name))

        when (preTxn) {
            TxnType.ALL -> binding.rbTAll.isChecked = true
            TxnType.INCOME -> binding.rbIncome.isChecked = true
            TxnType.EXPENSE -> binding.rbExpense.isChecked = true
        }

        when (preDate) {
            DateRange.LAST_15_DAYS -> binding.rbLast15.isChecked = true
            DateRange.LAST_MONTH -> binding.rbLastMonth.isChecked = true
            DateRange.ALL -> binding.rbDAll.isChecked = true
        }

        binding.tvReset.setOnClickListener {
            binding.rbTAll.isChecked = true
            binding.rbDAll.isChecked = true
        }

        binding.btnCancel.setOnClickListener { dismiss() }

        binding.btnApply.setOnClickListener {
            val txn = when {
                binding.rbIncome.isChecked -> TxnType.INCOME
                binding.rbExpense.isChecked -> TxnType.EXPENSE
                else -> TxnType.ALL
            }
            val date = when {
                binding.rbLastMonth.isChecked -> DateRange.LAST_MONTH
                binding.rbDAll.isChecked -> DateRange.ALL
                else -> DateRange.LAST_15_DAYS
            }

            parentFragmentManager.setFragmentResult(
                REQ_KEY,
                Bundle().apply {
                    putString(RES_TXN, txn.name)
                    putString(RES_DATE, date.name)
                }
            )
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
