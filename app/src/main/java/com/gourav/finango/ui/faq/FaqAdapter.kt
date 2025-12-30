package com.gourav.finango.ui.faq

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import android.transition.AutoTransition
import android.transition.TransitionManager
import com.gourav.finango.R


data class FaqItem(
    val question: String,
    val answer: String,
    var isExpanded: Boolean = false
)


class FaqAdapter(
    private val items: MutableList<FaqItem>
) : RecyclerView.Adapter<FaqAdapter.FaqVH>() {

    inner class FaqVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView as MaterialCardView
        val rowQuestion: View = card.findViewById(R.id.rowQuestion)
        val tvQuestion: TextView = card.findViewById(R.id.tvQuestion)
        val tvAnswer: TextView = card.findViewById(R.id.tvAnswer)
        val ivArrow: ImageView = card.findViewById(R.id.ivArrow)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FaqVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_faq, parent, false)
        return FaqVH(v)
    }

    override fun onBindViewHolder(holder: FaqVH, position: Int) {
        val item = items[position]
        holder.tvQuestion.text = item.question
        holder.tvAnswer.text = item.answer

        // Set visibility & arrow rotation according to state
        holder.tvAnswer.visibility = if (item.isExpanded) View.VISIBLE else View.GONE
        holder.ivArrow.rotation = if (item.isExpanded) 180f else 0f

        // Toggle on row tap (or whole card)
        val toggle = {
            item.isExpanded = !item.isExpanded
            // Animate the layout change
            TransitionManager.beginDelayedTransition(holder.card, AutoTransition().setDuration(150))
            notifyItemChanged(position)
        }

        holder.rowQuestion.setOnClickListener { toggle() }
        holder.card.setOnClickListener { toggle() }
        holder.ivArrow.setOnClickListener { toggle() }
    }

    override fun getItemCount(): Int = items.size
}