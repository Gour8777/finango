package com.gourav.finango.ui.settings

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gourav.finango.R
import java.text.NumberFormat
import java.util.Locale

class RecurringAdapter(
    private val onDeleteClick: (RecurringTransaction) -> Unit,
    private val onEditClick: (RecurringTransaction) -> Unit
) : ListAdapter<RecurringTransaction, RecurringAdapter.VH>(DIFF) {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val categoryIcon = v.findViewById<ImageView>(R.id.recurringcategory)
        val title = v.findViewById<TextView>(R.id.recurringtitle)
        val currencyIcon = v.findViewById<ImageView>(R.id.recrringcurrency)
        val amount = v.findViewById<TextView>(R.id.recurringamount)
        val frequency = v.findViewById<TextView>(R.id.frequencytv)
        val endDate = v.findViewById<TextView>(R.id.recurringenddate)
        val dueDate = v.findViewById<TextView>(R.id.recurringduedate)
        val status = v.findViewById<TextView>(R.id.recurringstatus)
        val edit = v.findViewById<TextView>(R.id.recurringedit)
        val delete = v.findViewById<ImageButton>(R.id.recurringdelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recurring_transaction, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val it = getItem(position)

        h.title.text = it.title
        h.frequency.text = it.frequency.ifBlank { "—" }.replaceFirstChar { c -> c.uppercase() }
        h.endDate.text = it.endDate?.let { d -> "End Date- $d" } ?: "End Date- NA"
        h.dueDate.text = it.nextDueDate?.let { d -> "Due Date- $d" } ?: "Due Date- NA"
        h.status.text = if (it.isActive) "Active" else "Ended"

        // Amount (Indian grouping)
        h.amount.text = NumberFormat.getInstance(Locale("en", "IN")).format(it.amount)

        val ctx = h.itemView.context
        val isExpense = it.type.equals("expense", ignoreCase = true)
        val amountColor = ContextCompat.getColor(
            ctx, if (isExpense) R.color.expense_end else R.color.income_end
        )
        h.status.setBackgroundResource(if(it.isActive) R.drawable.bg_income else R.drawable.bg_expense)

        h.amount.setTextColor(amountColor)
        h.currencyIcon.imageTintList = ColorStateList.valueOf(amountColor)

        // Category chip bg per type
        h.categoryIcon.setBackgroundResource(
            if (isExpense) R.drawable.bg_expense else R.drawable.bg_income
        )
        if (it.isActive) {
            h.edit.visibility = View.VISIBLE
            h.edit.setOnClickListener {
                val pos = h.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onEditClick(getItem(pos))
            }
        } else {
            h.edit.visibility = View.GONE
            h.edit.setOnClickListener(null) // important: clear listener when hidden
        }


        // Category icon per category
        h.categoryIcon.setImageResource(categoryIconFor(it.category, isExpense))

        h.delete.setOnClickListener {
            val pos = h.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onDeleteClick(getItem(pos))
        }
    }

    private fun categoryIconFor(category: String, isExpense: Boolean): Int =when(category){
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


    companion object {
        val DIFF = object : DiffUtil.ItemCallback<RecurringTransaction>() {
            override fun areItemsTheSame(o: RecurringTransaction, n: RecurringTransaction) = o.id == n.id
            override fun areContentsTheSame(o: RecurringTransaction, n: RecurringTransaction) = o == n
        }
    }

}
