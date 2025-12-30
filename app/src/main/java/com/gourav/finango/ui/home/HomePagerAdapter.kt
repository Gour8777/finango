package com.gourav.finango.ui.home

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter


class HomePagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount() = 2  // Gauge + Statistics

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> GaugeForHome()   // your gauge chart
            else -> StatisticsFragment()  // your list
        }
    }
}
