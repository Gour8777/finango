package com.gourav.finango.ui.analytics

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.gourav.finango.R

class AnalyticsFragment : Fragment(R.layout.fragment_analytics) {

    private lateinit var viewPager: ViewPager2
    private lateinit var tvTitle: TextView
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton

    private val pageTitles = listOf(
        "Income vs Expense",
        "Spend by Category",
        "Budget vs Actual",
        "Cash Flow Summary"
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewPager = view.findViewById(R.id.viewPagerCharts)
        tvTitle   = view.findViewById(R.id.tvPagerTitle)
        btnPrev   = view.findViewById(R.id.btnPrev)
        btnNext   = view.findViewById(R.id.btnNext)

        viewPager.adapter = ChartsPagerAdapter(this)
        viewPager.offscreenPageLimit = 1

        tvTitle.text = pageTitles[0]
        updateArrows(0)

        btnPrev.setOnClickListener {
            val i = viewPager.currentItem
            if (i > 0) viewPager.currentItem = i - 1
        }
        btnNext.setOnClickListener {
            val i = viewPager.currentItem
            if (i < pageTitles.lastIndex) viewPager.currentItem = i + 1
        }

        viewPager.registerOnPageChangeCallback(object: ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                tvTitle.text = pageTitles[position]
                updateArrows(position)
            }
        })
    }

    private fun updateArrows(position: Int) {
        btnPrev.isEnabled = position > 0
        btnNext.isEnabled = position < 3
        btnPrev.alpha = if (btnPrev.isEnabled) 1f else 0.3f
        btnNext.alpha = if (btnNext.isEnabled) 1f else 0.3f
    }
}
