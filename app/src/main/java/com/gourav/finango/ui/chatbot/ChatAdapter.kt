package com.gourav.finango.ui.chatbot



import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gourav.finango.R
import io.noties.markwon.Markwon

class ChatAdapter(
    private val onBotErrorClick: ((ChatMessage) -> Unit)? = null
) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(DIFF) {

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_BOT = 1
        private const val TYPE_TYPING = 2

        val DIFF = object : DiffUtil.ItemCallback<ChatMessage>() {
            override fun areItemsTheSame(o: ChatMessage, n: ChatMessage) = o.id == n.id
            override fun areContentsTheSame(o: ChatMessage, n: ChatMessage) = o == n
        }
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position).role) {
        ChatMessage.Role.USER -> TYPE_USER
        ChatMessage.Role.BOT -> TYPE_BOT
        ChatMessage.Role.TYPING -> TYPE_TYPING
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_USER -> UserVH(inf.inflate(R.layout.item_msg_user, parent, false))
            TYPE_BOT -> BotVH(inf.inflate(R.layout.item_msg_bot, parent, false), onBotErrorClick)
            else -> TypingVH(inf.inflate(R.layout.item_msg_typing, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, pos: Int) {
        val item = getItem(pos)
        when (holder) {
            is UserVH -> holder.bind(item)
            is BotVH  -> holder.bind(item)
            is TypingVH -> Unit
        }
    }

    class UserVH(v: View) : RecyclerView.ViewHolder(v) {
        private val tv: TextView = v.findViewById(R.id.tvMsg)
        fun bind(m: ChatMessage) { tv.text = m.text }
    }

    class BotVH(v: View, private val onClick: ((ChatMessage) -> Unit)?) : RecyclerView.ViewHolder(v) {
        private val tv: TextView = v.findViewById(R.id.tvMsg)
        fun bind(m: ChatMessage) {
            val markwon = Markwon.create(itemView.context)

            val formatted = m.text
                .replace("\\n", "\n")     // convert literal \n to real newline first
                .replace("\n", "  \n")    // markdown hard line break

            markwon.setMarkdown(tv, formatted)

            itemView.setOnClickListener {
                if (m.text.startsWith("⚠️")) onClick?.invoke(m)
            }
        }
    }

    class TypingVH(v: View) : RecyclerView.ViewHolder(v)
}