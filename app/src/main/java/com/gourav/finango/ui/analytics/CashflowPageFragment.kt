package com.gourav.finango.ui.analytics

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.gourav.finango.R
import java.text.NumberFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

class CashFlowPageFragment : Fragment(R.layout.fragment_page_cashflow) {

    private val db = Firebase.firestore
    private val auth = Firebase.auth

    private lateinit var tvVerdictLine: TextView

    private lateinit var tvIncomeAmount: TextView
    private lateinit var tvIncomeChange: TextView
    private lateinit var tvIncomeSummary: TextView
    private lateinit var pbIncomeVsLast: ProgressBar

    private lateinit var tvExpenseAmount: TextView
    private lateinit var tvExpenseChange: TextView
    private lateinit var tvExpenseSummary: TextView
    private lateinit var pbExpenseVsLast: ProgressBar

    private val nf by lazy {
        NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            currency = Currency.getInstance("INR")
            maximumFractionDigits = 0
        }
    }

    private fun userDoc() = db.collection("users")
        .document(requireNotNull(auth.currentUser?.uid) { "No user" })
    private fun txCol() = userDoc().collection("transactions")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvVerdictLine = view.findViewById(R.id.tvVerdictLine)

        tvIncomeAmount = view.findViewById(R.id.tvIncomeAmount)
        tvIncomeChange = view.findViewById(R.id.tvIncomeChange)
        tvIncomeSummary = view.findViewById(R.id.tvIncomeSummary)
        pbIncomeVsLast = view.findViewById(R.id.pbIncomeVsLast)

        tvExpenseAmount = view.findViewById(R.id.tvExpenseAmount)
        tvExpenseChange = view.findViewById(R.id.tvExpenseChange)
        tvExpenseSummary = view.findViewById(R.id.tvExpenseSummary)
        pbExpenseVsLast = view.findViewById(R.id.pbExpenseVsLast)

        loadCashFlow()
    }

    private fun monthRange(offsetMonths: Int = 0): Pair<Date, Date> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, offsetMonths)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.time
        cal.add(Calendar.MONTH, 1)
        cal.add(Calendar.MILLISECOND, -1)
        val end = cal.time
        return start to end
    }

    private fun loadCashFlow() {
        val (curStart, curEnd) = monthRange(0)
        val (prevStart, prevEnd) = monthRange(-1)

        txCol()
            .whereGreaterThanOrEqualTo("timestamp", curStart)
            .whereLessThanOrEqualTo("timestamp", curEnd)
            .get()
            .addOnSuccessListener { curSnap ->
                txCol()
                    .whereGreaterThanOrEqualTo("timestamp", prevStart)
                    .whereLessThanOrEqualTo("timestamp", prevEnd)
                    .get()
                    .addOnSuccessListener { prevSnap ->

                        var curIncome = 0.0
                        var curExpense = 0.0
                        var prevIncome = 0.0
                        var prevExpense = 0.0

                        fun toAmt(v: Any?): Double = when (v) {
                            is Number -> v.toDouble()
                            is String -> v.toDoubleOrNull() ?: 0.0
                            else -> 0.0
                        }

                        for (d in curSnap.documents) {
                            when ((d.getString("type") ?: "").lowercase(Locale.getDefault())) {
                                "income" -> curIncome += toAmt(d.get("amount"))
                                "expense" -> curExpense += toAmt(d.get("amount"))
                            }
                        }
                        for (d in prevSnap.documents) {
                            when ((d.getString("type") ?: "").lowercase(Locale.getDefault())) {
                                "income" -> prevIncome += toAmt(d.get("amount"))
                                "expense" -> prevExpense += toAmt(d.get("amount"))
                            }
                        }

                        bindIncome(curIncome, prevIncome)
                        bindExpense(curExpense, prevExpense)
                        bindVerdict(curIncome, curExpense, prevIncome, prevExpense)
                    }
            }
    }

    private fun pctDelta(now: Double, before: Double): Double {
        return if (before == 0.0) {
            if (now == 0.0) 0.0 else 100.0
        } else ((now - before) / before) * 100.0
    }

    private fun clampProgress(pct: Double): Int {
        // Progress bars are 0..200 (so 100% = same as last month)
        val v = (100 + pct).coerceIn(0.0, 200.0)
        return v.roundToInt()
    }

    private fun bindIncome(cur: Double, prev: Double) {
        val pct = pctDelta(cur, prev)
        val arrow = if (pct >= 0) "▲" else "▼"
        val pctStr = "${abs(pct).roundToInt()}% vs last month"

        tvIncomeAmount.text = nf.format(cur)
        tvIncomeChange.text = "$arrow  $pctStr"
        tvIncomeChange.setTextColor(Color.parseColor(if (pct >= 0) "#10B981" else "#EF4444"))
        tvIncomeSummary.text =
            if (pct >= 0) "You earned more than last month." else "You earned less than last month."

        pbIncomeVsLast.progress = clampProgress(pct)
        pbIncomeVsLast.progressTintList =
            ContextCompat.getColorStateList(requireContext(), android.R.color.holo_green_dark)
    }

    private fun bindExpense(cur: Double, prev: Double) {
        val pct = pctDelta(cur, prev)
        val arrow = if (pct >= 0) "▲" else "▼"
        val pctStr = "${abs(pct).roundToInt()}% vs last month"

        tvExpenseAmount.text = nf.format(cur)
        tvExpenseChange.text = "$arrow  $pctStr"

        // For expense, GREEN when dropped, RED when increased (more intuitive)
        val isGood = pct < 0
        tvExpenseChange.setTextColor(Color.parseColor(if (isGood) "#10B981" else "#EF4444"))
        tvExpenseSummary.text =
            if (isGood) "Great! You spent less than last month."
            else "You spent more than last month."

        pbExpenseVsLast.progress = clampProgress(pct)
        pbExpenseVsLast.progressTintList = ContextCompat.getColorStateList(
            requireContext(),
            if (isGood) android.R.color.holo_green_dark else android.R.color.holo_red_dark
        )
    }

    private fun bindVerdict(curIncome: Double, curExpense: Double, prevIncome: Double, prevExpense: Double) {
        val savingNow = curIncome - curExpense
        val savingPrev = prevIncome - prevExpense
        val better = savingNow >= savingPrev

        tvVerdictLine.text =
            if (better) "You are better off than last month."
            else "You are worse off than last month."

        tvVerdictLine.setTextColor(Color.parseColor(if (better) "#0EA5E9" else "#EF4444"))
    }
}
