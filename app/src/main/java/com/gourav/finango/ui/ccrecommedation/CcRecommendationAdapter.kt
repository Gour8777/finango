package com.gourav.finango.ui.ccrecommedation

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gourav.finango.R

class CcRecommendationAdapter(
    private var items: List<CreditCardRecommendation> = emptyList()
) : RecyclerView.Adapter<CcRecommendationAdapter.CcViewHolder>() {

    inner class CcViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCardName: TextView = itemView.findViewById(R.id.tvCardName)
        val tvBank: TextView = itemView.findViewById(R.id.tvBank)
        val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
        val tvScore: TextView = itemView.findViewById(R.id.tvScore)
        val tvJoiningFee: TextView = itemView.findViewById(R.id.tvJoiningFee)
        val tvAnnualFee: TextView = itemView.findViewById(R.id.tvAnnualFee)
        val tvApr: TextView = itemView.findViewById(R.id.tvApr)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CcViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cc_recommendation, parent, false)
        return CcViewHolder(view)
    }

    override fun onBindViewHolder(holder: CcViewHolder, position: Int) {
        val item = items[position]

        holder.tvCardName.text = item.card_name
        holder.tvBank.text = item.card_bank
        holder.tvCategory.text = item.card_reward_category.uppercase()
        holder.tvScore.text = String.format("Score: %.2f", item.score)
        holder.tvJoiningFee.text = "Joining: ₹${item.joining_fee.toInt()}"
        holder.tvAnnualFee.text = "Annual: ₹${item.annual_fee.toInt()}"
        holder.tvApr.text = "APR: ${item.apr}%"
        val category = item.card_reward_category.lowercase()

        holder.tvCategory.text = category.replace("_", " ").uppercase()

        // 🌈 APPLY COLOR PER CATEGORY
        val bg = holder.tvCategory.background.mutate()

        val color = when (category) {
            "travel", "travel_lounge", "airport_lounge" -> Color.parseColor("#0288D1") // blue
            "shopping" -> Color.parseColor("#8E24AA")   // purple
            "cashback" -> Color.parseColor("#2E7D32")   // green
            "fuel" -> Color.parseColor("#EF6C00")       // orange
            "movies" -> Color.parseColor("#D81B60")     // pink
            "rewards" -> Color.parseColor("#5D4037")    // brown
            else -> Color.parseColor("#455A64")         // default
        }

        (bg as android.graphics.drawable.GradientDrawable).setColor(color)
        holder.tvCategory.background = bg
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<CreditCardRecommendation>) {
        items = newItems
        notifyDataSetChanged()
    }
}
data class CreditCardRecommendation(
    val card_name: String,
    val card_bank: String,
    val card_reward_category: String,
    val score: Double,
    val annual_fee: Double,
    val joining_fee: Double,
    val apr: Double
)