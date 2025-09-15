package com.gourav.finango.ui.wallet

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class BottomMarginDecoration(private val bottomMargin: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position == state.itemCount - 1) {
            outRect.bottom = bottomMargin // only last item gets margin
        }
    }
}
