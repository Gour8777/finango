package com.gourav.finango.ui.wallet

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

import com.gourav.finango.R
import com.gourav.finango.ui.TransactionDetails.TransactionDetailActivity

class TransactionAdapter(private val context: Context, private val transactionList: MutableList<Transactionget>) :
    RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

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


        holder.itemView.setOnClickListener {
            val intent = Intent(context, TransactionDetailActivity::class.java).apply {
                putExtra("documentId", transaction.documentId)
                putExtra("date", transaction.date)
                putExtra("amount", transaction.amount)              // Double/Long/Int
                putExtra("type", transaction.type)                  // "income"/"expense"
                putExtra("category", transaction.category)
                putExtra("description", transaction.description)
                putExtra("timestampSeconds", transaction.timestamp?.seconds ?: 0L) // if using Firestore Timestamp
                putExtra("timestampNanos", transaction.timestamp?.nanoseconds ?: 0) // optional
            }
            context.startActivity(intent)
        }

        Log.d("RecyclerView", "Binding transaction: ${transaction.description}, Timestamp: ${transaction.timestamp}")

        // Check if the timestamp is a valid object
        transaction.timestamp?.let {
            Log.d("RecyclerView", "Timestamp seconds: ${it.seconds}")
        }

        val parts = transaction.date.split("-") // yyyy-mm-dd
        val monthIndex = parts[1].toInt() - 1   // "01" → 0
        holder.monthTextView.text = monthNames[monthIndex]
        holder.dateTextView.text = transaction.date.split("-")[2]

        holder.categoryImageView.setImageResource(getCategoryIcon(transaction.category))
        holder.descriptionTextView.text = transaction.description
        holder.amountTextView.text = "₹ ${transaction.amount}"
        if (transaction.type.equals("income", ignoreCase = true)) {
            holder.amountTextView.setTextColor(Color.parseColor("#4CAF50")) // Green
        } else {
            holder.amountTextView.setTextColor(Color.parseColor("#F44336")) // Red
        }
        if (transaction.type.equals("income", ignoreCase = true)) {
            holder.categoryImageView.setBackgroundResource(R.drawable.bg_income) // Light Green circle
        } else {
            holder.categoryImageView.setBackgroundResource(R.drawable.bg_expense) // Light Red circle
        }



    }


    override fun getItemCount(): Int {
        return transactionList.size
    }

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val monthTextView: TextView = itemView.findViewById(R.id.transactionmonth)
        val dateTextView: TextView = itemView.findViewById(R.id.transactionDate)
        val categoryImageView: ImageView = itemView.findViewById(R.id.transactionCategory)
        val descriptionTextView: TextView = itemView.findViewById(R.id.transactionDescription)
        val amountTextView: TextView = itemView.findViewById(R.id.transactionAmount)
    }

    private fun getCategoryIcon(category: String): Int {
        return when (category) {
            "Groceries" -> R.drawable.grocery
            "Food & Drinks" -> R.drawable.foodanddrink
            else -> R.drawable.transaction
        }
    }
}
