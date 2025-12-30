package com.gourav.finango.ui.notifications

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.gourav.finango.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
class NotificationsAdapter(
    private var items: MutableList<AppNotification>,
    private val onAnomalyAction: (notification: AppNotification, confirmed: Boolean) -> Unit
) : RecyclerView.Adapter<NotificationsAdapter.NotificationViewHolder>() {

    inner class NotificationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: com.google.android.material.card.MaterialCardView = view.findViewById(R.id.rootnoticard)
        val tvTitle: TextView = view.findViewById(R.id.notititle)
        val tvBody: TextView = view.findViewById(R.id.notibody)
        val tvTime: TextView = view.findViewById(R.id.notitime)

        // anomaly section
        val layoutAnomalyDetails: View = view.findViewById(R.id.layoutAnomalyDetails)
        val tvAnomalyReasons: TextView = view.findViewById(R.id.tvAnomalyReasons)
        val btnYes: Button = view.findViewById(R.id.btnAnomalyYes)
        val btnNo: Button = view.findViewById(R.id.btnAnomalyNo)
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val item = items[position]

        holder.tvTitle.text = item.title
        holder.tvBody.text = item.body
        holder.tvTime.text = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            .format(Date(item.timestamp))

        val ctx = holder.itemView.context

        // ---- Base background by type + severity ----
        val baseColorRes = when {
            item.type == "anomaly" && item.severity.equals("high", true) -> R.color.anomalyHigh
            item.type == "anomaly" && item.severity.equals("med", true)  -> R.color.anomalyMedium
            item.type == "anomaly" && item.severity.equals("low", true)  -> R.color.anomalyLow
            item.type == "anomaly"                                       -> R.color.anomalyDefault
            else                                                         -> android.R.color.white
        }
        holder.card.setCardBackgroundColor(ctx.getColor(baseColorRes))

        // make sure anomaly area is reset every bind
        holder.layoutAnomalyDetails.visibility = View.GONE
        holder.btnYes.visibility = View.VISIBLE
        holder.btnNo.visibility = View.VISIBLE
        holder.itemView.setOnClickListener(null)

        // ---------- ANOMALY NOTIFICATIONS ----------
        if (item.type == "anomaly") {

            // 1) If disputed → special UI, no expand, no buttons
            if (item.status == "disputed") {
                holder.card.setCardBackgroundColor(ctx.getColor(R.color.anomalyHigh))
                holder.layoutAnomalyDetails.visibility = View.VISIBLE
                holder.btnYes.visibility = View.GONE
                holder.btnNo.visibility = View.GONE
                holder.tvAnomalyReasons.text =
                    "• You marked this as disputed. Please check with your bank or card provider."

                // no click to expand/collapse for disputed
                holder.itemView.setOnClickListener(null)
                return
            }

            // 2) Normal anomaly (open / not disputed): show expandable reasons + buttons
            holder.layoutAnomalyDetails.visibility =
                if (item.isExpanded) View.VISIBLE else View.GONE

            val reasonsText = if (item.reasons.isNotEmpty()) {
                item.reasons.joinToString("\n") { reason ->
                    "• " + (reasonMessages[reason] ?: "Unusual activity detected.")
                }
            } else {
                "• This transaction looks unusual compared to your previous pattern."
            }
            holder.tvAnomalyReasons.text = reasonsText

            // Expand / collapse with animation
            holder.itemView.setOnClickListener {
                val expand = !item.isExpanded
                item.isExpanded = expand

                TransitionManager.beginDelayedTransition(holder.card, AutoTransition())
                holder.layoutAnomalyDetails.visibility =
                    if (expand) View.VISIBLE else View.GONE
            }

            // Buttons for confirm / disputed
            holder.btnYes.setOnClickListener {
                onAnomalyAction(item, true)   // confirmed
            }
            holder.btnNo.setOnClickListener {
                onAnomalyAction(item, false)  // disputed
            }
        } else {
            // ---------- NON-ANOMALY (recurring / others) ----------
            holder.layoutAnomalyDetails.visibility = View.GONE
            holder.itemView.setOnClickListener(null)
        }
    }

    private val reasonMessages = mapOf(
        "amount_spike_category" to "Higher than your usual spend in this category.",
        "amount_high_overall" to "Amount is higher than your normal spending.",
        "amount_high_type" to "Unusual amount for this type of transaction.",
        "new_category" to "You rarely spend in this category.",
        "odd_time" to "Transaction happened at an unusual time.",
        "frequency_spike" to "Too many payments in a short time."
    )



    override fun getItemCount(): Int = items.size

    fun updateList(newItems: MutableList<AppNotification>) {
        items = newItems
        notifyDataSetChanged()
    }
}
