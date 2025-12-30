package com.gourav.finango.ui.wallet

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gourav.finango.R

class TransactionAdapter(
    private val context: Context,
    private val transactionList: MutableList<Transactionget>,
    private val onItemClick: (Transactionget) -> Unit   // <-- NEW: callback
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    private val monthNames = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val itemView = LayoutInflater.from(context).inflate(R.layout.transaction_item, parent, false)
        return TransactionViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transaction = transactionList[position]

        // Launching is delegated to the Fragment via this callback (NO startActivity here)
        holder.itemView.setOnClickListener { onItemClick(transaction) }

        Log.d("RecyclerView", "Binding transaction: ${transaction.description}, Timestamp: ${transaction.timestamp}")
        transaction.timestamp?.let { Log.d("RecyclerView", "Timestamp seconds: ${it.seconds}") }

        // date format: yyyy-MM-dd
        val parts = transaction.date.split("-")
        val monthIndex = parts.getOrNull(1)?.toIntOrNull()?.minus(1)?.coerceIn(0, 11) ?: 0
        holder.monthTextView.text = monthNames[monthIndex]
        holder.dateTextView.text = parts.getOrNull(2) ?: "--"

        holder.categoryImageView.setImageResource(getCategoryIcon(transaction.category))
        holder.descriptionTextView.text = transaction.description
        holder.amountTextView.text = "₹ ${transaction.amount}"

        val isIncome = transaction.type.equals("income", ignoreCase = true)
        holder.amountTextView.setTextColor(Color.parseColor(if (isIncome) "#4CAF50" else "#F44336"))
        holder.categoryImageView.setBackgroundResource(if (isIncome) R.drawable.bg_income else R.drawable.bg_expense)
    }

    override fun getItemCount(): Int = transactionList.size

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val monthTextView: TextView = itemView.findViewById(R.id.transactionmonth)
        val dateTextView: TextView = itemView.findViewById(R.id.transactionDate)
        val categoryImageView: ImageView = itemView.findViewById(R.id.transactionCategory)
        val descriptionTextView: TextView = itemView.findViewById(R.id.transactionDescription)
        val amountTextView: TextView = itemView.findViewById(R.id.transactionAmount)
    }

    private fun getCategoryIcon(category: String): Int = when (category) {
        "Groceries"     -> R.drawable.grocery
        "Food & Drinks" -> R.drawable.foodanddrink
        "Furniture"->R.drawable.furniture
        "Maintenance"->R.drawable.maintenance
        "Electricity"->R.drawable.electricity
        "Rent"->R.drawable.rent
        "Gifts"->R.drawable.gifts
        "Medical"->R.drawable.medical
        "Travel"->R.drawable.travel
        "Water"->R.drawable.water
        "Movies"->R.drawable.movies
        "Donation"->R.drawable.donation
        else->R.drawable.transaction

    }
}
