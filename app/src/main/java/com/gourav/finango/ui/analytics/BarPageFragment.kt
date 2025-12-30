package com.gourav.finango.ui.analytics


import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.firestore
import com.gourav.finango.R
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class BarPageFragment : Fragment(R.layout.fragment_page_bar) {

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private lateinit var barChart: BarChart
    private lateinit var barChartManager: BarChartManager

    // ---- Shared helpers (same signatures as your AnalyticsFragment) ----

    private fun txCol() = db.collection("users")
        .document(requireNotNull(auth.currentUser?.uid) { "No user logged in" })
        .collection("transactions")

    private fun readAmount(doc: DocumentSnapshot): Double =
        when (val v = doc.get("amount")) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }

    private fun readDate(doc: DocumentSnapshot): Date? {
        return when (val v = doc.get("timestamp")) {
            is com.google.firebase.Timestamp -> v.toDate()
            is Number -> Date(v.toLong())
            is String -> {
                val formats = listOf(
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()),
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                )
                formats.firstNotNullOfOrNull { f -> runCatching { f.parse(v) }.getOrNull() }
            }
            else -> null
        }
    }

    private fun readTypeLower(doc: DocumentSnapshot): String =
        (doc.getString("type") ?: "").trim().lowercase(Locale.getDefault())

    // Currency formatters compatible with your BarChartManager constructor
    private fun inrFormatter(decimals: Int) = object : ValueFormatter() {
        private val nf = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            currency = Currency.getInstance("INR")
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
        }
        override fun getFormattedValue(value: Float): String = nf.format(value.toDouble())
    }
    private val INR0 = inrFormatter(0)

    private val hideZeroValuesOrINR0 = object : ValueFormatter() {
        override fun getBarLabel(barEntry: com.github.mikephil.charting.data.BarEntry?): String {
            val y = barEntry?.y ?: 0f
            return if (y == 0f) "" else INR0.getFormattedValue(y)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        barChart = view.findViewById(R.id.barMonthlyIncomeExpense)

        // Use your existing manager classes
        barChartManager = BarChartManager(
            requireContext(),
            barChart,
            hideZeroValuesOrINR0,
            CurrencyMarkerView(requireContext(), INR0) // <- your existing MarkerView impl
        )

        // Time window: last 3 months (including current)
        val end = Date()
        val start = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, -2)
        }.time

        loadMonthlyIncomeExpense(start, end)
    }

    private fun loadMonthlyIncomeExpense(start: Date, end: Date) {
        txCol()
            .whereGreaterThanOrEqualTo("timestamp", start)
            .whereLessThanOrEqualTo("timestamp", end)
            .get()
            .addOnSuccessListener { snap ->
                barChartManager.setupChart(
                    snap.documents,
                    start,
                    end,
                    ::readAmount,
                    ::readDate,
                    ::readTypeLower
                )
            }
            .addOnFailureListener { Log.e("Analytics", "Monthly query failed", it) }
    }
}
