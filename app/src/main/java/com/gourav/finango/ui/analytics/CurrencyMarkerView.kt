package com.gourav.finango.ui.analytics

import android.content.Context
import android.widget.TextView
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import com.gourav.finango.R
import com.github.mikephil.charting.formatter.ValueFormatter

class CurrencyMarkerView(
    context: Context,
    private val formatter: ValueFormatter
) : MarkerView(context, R.layout.marker_currency) {
    private val tv: TextView = findViewById(R.id.tvValue)
    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        val y = e?.y ?: 0f
        tv.text = formatter.getFormattedValue(y)
        super.refreshContent(e, highlight)
    }
    override fun getOffset(): MPPointF = MPPointF(-(width/2f), -height.toFloat() - 16f)
}

