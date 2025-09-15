package com.gourav.finango.ui.addtransaction

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.gourav.finango.databinding.RowSelectItemBinding

class SelectListAdapter(
    private val original: List<SelectItem>,
    private val onClick: (SelectItem) -> Unit
) : RecyclerView.Adapter<SelectListAdapter.VH>() {

    private val items = original.toMutableList()

    inner class VH(val binding: RowSelectItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SelectItem) {
            binding.tvName.text = item.name + (item.code?.let { " ($it)" } ?: "")
            if (item.icon != null) {
                binding.ivIcon.isVisible = true
                binding.ivIcon.setImageResource(item.icon)
            } else {
                binding.ivIcon.isVisible = false
            }
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = RowSelectItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    fun filter(query: String) {
        val q = query.trim().lowercase()
        items.clear()
        if (q.isEmpty()) {
            items.addAll(original)
        } else {
            items.addAll(original.filter { it.name.lowercase().contains(q) || (it.code?.lowercase()?.contains(q) == true) })
        }
        notifyDataSetChanged()
    }
}
