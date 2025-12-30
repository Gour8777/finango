package com.gourav.finango.ui.analytics

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.LegendEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.gourav.finango.R
import java.text.NumberFormat
import java.util.*

class PiePageFragment : Fragment(R.layout.fragment_page_pie) {

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private lateinit var pieChart: PieChart

    // ---------- INR formatter (no decimals) ----------
    private fun inrFormatter(decimals: Int) = object : ValueFormatter() {
        private val nf = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            currency = Currency.getInstance("INR")
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
        }
        override fun getFormattedValue(value: Float): String = nf.format(value.toDouble())
    }
    private val inr0 = inrFormatter(0)

    // ---------- Firestore collection ----------
    private fun txCol() = db.collection("users")
        .document(requireNotNull(auth.currentUser?.uid) { "No user logged in" })
        .collection("transactions")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pieChart = view.findViewById(R.id.pieCategoryThisMonth)
        configurePieChartAppearance()

        // time range: this month to now
        val startOfThisMonth = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
        val now = Date()

        loadCategoryPie(startOfThisMonth, now)
    }

    // ---------- Main loader ----------
    // ---------- Main loader (legend shows amounts; no slice labels) ----------
    // ---------- Main loader (legend shows amounts; no slice labels) ----------
    private fun loadCategoryPie(start: Date, end: Date) {
        txCol()
            .whereGreaterThanOrEqualTo("timestamp", start)
            .whereLessThanOrEqualTo("timestamp", end)
            .get()
            .addOnSuccessListener { snap ->
                val byCat = mutableMapOf<String, Double>()

                for (doc in snap.documents) {
                    val type = (doc.getString("type") ?: "").trim().lowercase(Locale.getDefault())
                    if (type != "expense") continue

                    val cat = (doc.getString("category") ?: "Other")
                        .trim()
                        .ifBlank { "Other" }

                    val amt = when (val v = doc.get("amount")) {
                        is Number -> v.toDouble()
                        is String -> v.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                    if (amt > 0.0) byCat[cat] = (byCat[cat] ?: 0.0) + amt
                }

                if (byCat.isEmpty()) {
                    pieChart.clear()
                    pieChart.centerText = "No data"
                    pieChart.invalidate()
                    return@addOnSuccessListener
                }

                // Build entries: show all if categories <= N; else top N + Others
                val maxTopCats = 7
                val sorted = byCat.entries.sortedByDescending { it.value }

                val entries = mutableListOf<PieEntry>()
                if (sorted.size <= maxTopCats) {
                    sorted.forEach { entries += PieEntry(it.value.toFloat(), it.key) }
                } else {
                    val top = sorted.take(maxTopCats)
                    val othersSum = sorted.drop(maxTopCats).sumOf { it.value }
                    top.forEach { entries += PieEntry(it.value.toFloat(), it.key) }
                    if (othersSum > 0) entries += PieEntry(othersSum.toFloat(), "Others")
                }

                // Bright palette
                val brightPalette = listOf(
                    Color.parseColor("#FF3B30"), // red
                    Color.parseColor("#FF9500"), // orange
                    Color.parseColor("#FFCC00"), // yellow
                    Color.parseColor("#34C759"), // green
                    Color.parseColor("#00C7BE"), // teal
                    Color.parseColor("#007AFF"), // blue
                    Color.parseColor("#5856D6"), // indigo
                    Color.parseColor("#AF52DE"), // purple
                    Color.parseColor("#5AC8FA"), // light blue
                    Color.parseColor("#FF2D55")  // pink
                )

                // Dataset: NO values drawn on slices (prevents overlap)
                val ds = PieDataSet(entries, "").apply {
                    colors = if (entries.size <= brightPalette.size) {
                        brightPalette.take(entries.size)
                    } else {
                        entries.mapIndexed { i, _ -> brightPalette[i % brightPalette.size] }
                    }
                    sliceSpace = 2f
                    selectionShift = 7f
                    setDrawValues(false)              // <- hide slice value texts
                }

                // Apply dataset & hide entry labels around chart entirely
                pieChart.data = PieData(ds)
                pieChart.setDrawEntryLabels(false)

                // ---- Custom legend: "Category – ₹Amount" ----
                val legendEntries = mutableListOf<LegendEntry>()
                entries.forEachIndexed { i, entry ->
                    val color = ds.colors[i % ds.colors.size]
                    val label = "${entry.label} – ${inr0.getFormattedValue(entry.value)}"
                    legendEntries += LegendEntry(
                        label,
                        Legend.LegendForm.SQUARE,
                        Float.NaN,   // use default size
                        Float.NaN,   // default line width
                        null,
                        color
                    )
                }
                pieChart.legend.setCustom(legendEntries)

                // Center text defaults
                pieChart.centerText = "This month’s spend"
                pieChart.setCenterTextSize(13f)
                pieChart.setCenterTextColor(Color.parseColor("#374151"))

                pieChart.setExtraOffsets(16f, 16f, 16f, 20f)
                pieChart.invalidate()
            }
            .addOnFailureListener { Log.e("Analytics", "Pie query failed", it) }
    }



    // ---------- One-time chart appearance ----------
    private fun configurePieChartAppearance() = pieChart.apply {
        setUsePercentValues(false)
        description.isEnabled = false

        // Donut look
        isDrawHoleEnabled = true
        holeRadius = 60f                 // a bit smaller so it fits with legend
        transparentCircleRadius = 66f
        setHoleColor(Color.WHITE)
        setTransparentCircleAlpha(180)
        setDrawRoundedSlices(true)

        // We show amounts in legend, so no entry labels around the pie
        setDrawEntryLabels(false)

        legend.apply {
            isEnabled = true
            horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT   // right side
            verticalAlignment = Legend.LegendVerticalAlignment.TOP
            orientation = Legend.LegendOrientation.VERTICAL                // vertical list
            setDrawInside(false)
            setMaxSizePercent(0.38f)                                       // ✅ cap legend width
            textColor = Color.parseColor("#111827")
            textSize = 12f
            xEntrySpace = 8f
            yEntrySpace = 6f
            isWordWrapEnabled = true
            xOffset = 10f       // space between legend and pie
            yOffset = -20f
        // nudge away from pie
        }

        // Give the chart a little extra breathing room (esp. on the right)
        setExtraOffsets(12f, 12f, 24f, 50f)                                // ✅ avoid clipping
    }


}


