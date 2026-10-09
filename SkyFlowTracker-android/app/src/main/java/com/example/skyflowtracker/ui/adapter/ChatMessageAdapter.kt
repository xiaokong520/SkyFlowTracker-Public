package com.example.skyflowtracker.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.skyflowtracker.api.model.ChatMessage
import com.example.skyflowtracker.databinding.ItemChatAssistantBinding
import com.example.skyflowtracker.databinding.ItemChatUserBinding
import io.noties.markwon.Markwon

/**
 * 聊天消息列表适配器（双 ViewType：用户/助手）
 */
class ChatMessageAdapter(
    private val markwon: Markwon
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_ASSISTANT = 1
    }

    private val items = mutableListOf<ChatMessage>()

    class UserViewHolder(val binding: ItemChatUserBinding) : RecyclerView.ViewHolder(binding.root)
    class AssistantViewHolder(val binding: ItemChatAssistantBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        return if (items[position].role == "user") TYPE_USER else TYPE_ASSISTANT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_USER) {
            UserViewHolder(
                ItemChatUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        } else {
            AssistantViewHolder(
                ItemChatAssistantBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = items[position]
        when (holder) {
            is UserViewHolder -> {
                holder.binding.tvContent.text = msg.content
            }
            is AssistantViewHolder -> {
                if (msg.isStreaming && msg.content.isEmpty()) {
                    holder.binding.llThinking.visibility = View.VISIBLE
                    holder.binding.tvContent.visibility = View.GONE
                } else {
                    holder.binding.llThinking.visibility = View.GONE
                    holder.binding.tvContent.visibility = View.VISIBLE
                    if (msg.isStreaming) {
                        holder.binding.tvContent.text = msg.content
                    } else {
                        markwon.setMarkdown(holder.binding.tvContent, msg.content)
                    }
                }
            }
        }
    }

    /**
     * 替换整个列表
     */
    fun submitList(list: List<ChatMessage>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    /**
     * 添加一条消息
     */
    fun addMessage(message: ChatMessage) {
        items.add(message)
        notifyItemInserted(items.lastIndex)
    }

    /**
     * 更新最后一条消息（流式追加时调用，直接 notifyItemChanged 避免 DiffUtil 开销）
     */
    fun updateLastMessage(content: String, isStreaming: Boolean) {
        if (items.isEmpty()) return
        items[items.lastIndex] = items.last().copy(content = content, isStreaming = isStreaming)
        notifyItemChanged(items.lastIndex)
    }
}
