package com.gourav.finango.ui.analytics



import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class ChartsPagerAdapter(parent: Fragment) : FragmentStateAdapter(parent) {
    override fun getItemCount(): Int = 4
    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> BarPageFragment()
        1 -> PiePageFragment()
        2 -> GaugePageFragment()
        else -> CashFlowPageFragment()
    }
}
