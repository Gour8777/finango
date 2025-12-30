package com.gourav.finango.ui.analytics

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.firestore
import com.gourav.finango.R
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class LinePageFragment : Fragment(R.layout.fragment_page_line) {

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private lateinit var lineChart: LineChart

    private val inr0: ValueFormatter = object : ValueFormatter() {
        private val nf = NumberFormat.getCurrencyInstance(Locale("en","IN")).apply {
            currency = Currency.getInstance("INR"); maximumFractionDigits = 0
        }
        override fun getFormattedValue(value: Float): String = nf.format(value.toDouble())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lineChart = view.findViewById(R.id.lineBalance)

        val end = Date()
        val start = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -29)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time

        loadBalanceLine(start, end)
    }

    private fun txCol() = db.collection("users")
        .document(requireNotNull(auth.currentUser?.uid) { "No user logged in" })
        .collection("transactions")

    private fun readAmount(doc: DocumentSnapshot): Double =
        when (val v = doc.get("amount")) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }

    private fun readDate(doc: DocumentSnapshot): Date? =
        when (val v = doc.get("timestamp")) {
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

    private fun readTypeLower(doc: DocumentSnapshot): String =
        (doc.getString("type") ?: "").trim().lowercase(Locale.getDefault())

    private fun loadBalanceLine(start: Date, end: Date) {
        txCol()
            .whereGreaterThanOrEqualTo("timestamp", start)
            .whereLessThanOrEqualTo("timestamp", end)
            .get()
            .addOnSuccessListener { snap ->
                val fmtDay = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val perDay = sortedMapOf<String, Double>()

                // seed days
                val c = Calendar.getInstance().apply { time = start }
                val e = Calendar.getInstance().apply { time = end }
                while (!c.after(e)) {
                    perDay[fmtDay.format(c.time)] = 0.0
                    c.add(Calendar.DAY_OF_YEAR, 1)
                }

                for (doc in snap.documents) {
                    val d = readDate(doc) ?: continue
                    val key = fmtDay.format(d)
                    val amt = readAmount(doc)
                    val sign = when (readTypeLower(doc)) {
                        "income" -> 1
                        "expense" -> -1
                        else -> 0
                    }
                    perDay[key] = (perDay[key] ?: 0.0) + sign * amt
                }

                var running = 0.0
                val entries = perDay.entries.mapIndexed { i, e ->
                    running += e.value
                    Entry(i.toFloat(), running.toFloat())
                }

                val ds = LineDataSet(entries, "Balance (last 30 days)").apply {
                    setDrawFilled(true)
                    setDrawCircles(false)
                    lineWidth = 2f
                    valueTextSize = 9f
                    color = Color.BLACK
                    fillAlpha = 64
                }

                lineChart.data = LineData(ds).apply { setValueFormatter(inr0) }
                lineChart.xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    granularity = 1f
                    valueFormatter = IndexAxisValueFormatter(perDay.keys.map { it.substring(5) }) // MM-dd
                    setLabelCount(6, true)
                    setDrawGridLines(false)
                }
                lineChart.axisRight.isEnabled = false
                lineChart.axisLeft.valueFormatter = inr0
                lineChart.description.isEnabled = false
                lineChart.setTouchEnabled(true)
                lineChart.invalidate()
            }
            .addOnFailureListener { Log.e("Analytics", "Line query failed", it) }
    }
}
