package com.gourav.finango.ui.analytics

import android.content.Context
import android.graphics.Color
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.firebase.firestore.DocumentSnapshot
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class BarChartHelper(
    private val context: Context,
    private val GREEN_D: Int = Color.parseColor("#059669"),
    private val RED_D: Int = Color.parseColor("#DC2626")
) {

    // Data processing
    fun processMonthlyData(
        documents: List<DocumentSnapshot>,
        start: Date,
        end: Date,
        readAmount: (DocumentSnapshot) -> Double,
        readDate: (DocumentSnapshot) -> Date?,
        readTypeLower: (DocumentSnapshot) -> String
    ): Triple<List<String>, Map<String, Double>, Map<String, Double>> {

        val keyFmt = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        val income = linkedMapOf<String, Double>()
        val expense = linkedMapOf<String, Double>()

        // Seed months
        seedMonths(start, end, keyFmt, income, expense)

        // Aggregate data
        for (doc in documents) {
            val date = readDate(doc) ?: continue
            val key = keyFmt.format(date)
            val amount = readAmount(doc)

            when (readTypeLower(doc)) {
                "income" -> income[key] = (income[key] ?: 0.0) + amount
                "expense" -> expense[key] = (expense[key] ?: 0.0) + amount
            }
        }

        return Triple(income.keys.toList(), income, expense)
    }

    private fun seedMonths(
        start: Date,
        end: Date,
        keyFmt: SimpleDateFormat,
        income: LinkedHashMap<String, Double>,
        expense: LinkedHashMap<String, Double>
    ) {
        val calendar = Calendar.getInstance().apply {
            time = start
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val endCalendar = Calendar.getInstance().apply {
            time = end
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        while (!calendar.after(endCalendar)) {
            val key = keyFmt.format(calendar.time)
            income[key] = 0.0
            expense[key] = 0.0
            calendar.add(Calendar.MONTH, 1)
        }
    }

    // Create bar entries
    fun createBarEntries(
        months: List<String>,
        income: Map<String, Double>,
        expense: Map<String, Double>
    ): Pair<List<BarEntry>, List<BarEntry>> {

        val incomeEntries = months.mapIndexed { index, month ->
            BarEntry(index.toFloat(), (income[month] ?: 0.0).toFloat())
        }

        val expenseEntries = months.mapIndexed { index, month ->
            BarEntry(index.toFloat(), (expense[month] ?: 0.0).toFloat())
        }

        return Pair(incomeEntries, expenseEntries)
    }

    // Create datasets
    fun createDatasets(
        incomeEntries: List<BarEntry>,
        expenseEntries: List<BarEntry>,
        valueFormatter: ValueFormatter
    ): Pair<BarDataSet, BarDataSet> {

        val incomeDataSet = BarDataSet(incomeEntries, "Income").apply {
            color = GREEN_D
            setDrawValues(true)
            this.valueFormatter = valueFormatter
        }

        val expenseDataSet = BarDataSet(expenseEntries, "Expense").apply {
            color = RED_D
            setDrawValues(true)
            this.valueFormatter = valueFormatter
        }

        return Pair(incomeDataSet, expenseDataSet)
    }

    // Generate month labels
    fun generateMonthLabels(months: List<String>): List<String> {
        val displayFormat = SimpleDateFormat("MMM", Locale.getDefault())

        return months.map { yearMonth ->
            val (year, month) = yearMonth.split("-")
            val calendar = Calendar.getInstance().apply {
                set(Calendar.YEAR, year.toInt())
                set(Calendar.MONTH, month.toInt() - 1)
                set(Calendar.DAY_OF_MONTH, 1)
            }
            displayFormat.format(calendar.time).uppercase()
        }
    }
}

// 2. BarChartStyler.kt - Separate class for styling
class BarChartStyler {

    fun configureXAxis(
        xAxis: XAxis,
        monthLabels: List<String>,
        monthsSize: Int
    ) {
        xAxis.apply {
            valueFormatter = IndexAxisValueFormatter(monthLabels)
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            isGranularityEnabled = true
            setDrawGridLines(false)
            setDrawAxisLine(false)
            setCenterAxisLabels(true)

            axisMinimum = 0f
            axisMaximum = monthsSize.toFloat()
            labelCount = monthsSize
            setLabelCount(monthsSize, false)
        }
    }

    fun configureLegend(legend: Legend) {
        legend.apply {
            isEnabled = true
            form = Legend.LegendForm.SQUARE
            textColor = Color.BLACK
            textSize = 12f
            horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
            verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
            orientation = Legend.LegendOrientation.HORIZONTAL
            setDrawInside(false)
            xEntrySpace = 10f
            yEntrySpace = 5f
        }
    }

    fun applyGeneralStyle(chart: BarChart) {
        chart.apply {
            setDrawGridBackground(false)
            setBackgroundColor(Color.TRANSPARENT)
            description.isEnabled = false
            setScaleEnabled(false)
            setDrawValueAboveBar(true)
            animateY(800)

            // Hide Y-axes
            axisLeft.isEnabled = false
            axisRight.isEnabled = false

            // Add padding for legend
            setExtraOffsets(10f, 10f, 10f, 50f)
        }
    }
}

// 3. BarChartManager.kt - Main manager class
class BarChartManager(
    private val context: Context,
    private val barChart: BarChart,
    private val hideZeroValuesFormatter: ValueFormatter,
    private val currencyMarkerView: MarkerView
) {
    private val helper = BarChartHelper(context)
    private val styler = BarChartStyler()

    // Bar chart configuration constants
    private companion object {
        const val GROUP_SPACE = 0.3f
        const val BAR_SPACE = 0.05f
        const val BAR_WIDTH = 0.3f
    }

    fun setupChart(
        documents: List<DocumentSnapshot>,
        start: Date,
        end: Date,
        readAmount: (DocumentSnapshot) -> Double,
        readDate: (DocumentSnapshot) -> Date?,
        readTypeLower: (DocumentSnapshot) -> String
    ) {
        // Process data
        val (months, income, expense) = helper.processMonthlyData(
            documents, start, end, readAmount, readDate, readTypeLower
        )

        // Create entries
        val (incomeEntries, expenseEntries) = helper.createBarEntries(months, income, expense)

        // Create datasets
        val (incomeDataSet, expenseDataSet) = helper.createDatasets(
            incomeEntries, expenseEntries, hideZeroValuesFormatter
        )

        // Create bar data
        val barData = BarData(incomeDataSet, expenseDataSet).apply {
            barWidth = BAR_WIDTH
        }

        // Apply data to chart
        barChart.data = barData

        // Generate and configure labels
        val monthLabels = helper.generateMonthLabels(months)
        styler.configureXAxis(barChart.xAxis, monthLabels, months.size)

        // Group bars
        barChart.groupBars(0f, GROUP_SPACE, BAR_SPACE)

        // Configure styling
        styler.configureLegend(barChart.legend)
        styler.applyGeneralStyle(barChart)

        // Set marker
        barChart.marker = currencyMarkerView

        // Refresh chart
        barChart.invalidate()
    }
}
