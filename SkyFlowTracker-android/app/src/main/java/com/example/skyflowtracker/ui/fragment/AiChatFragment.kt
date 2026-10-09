package com.example.skyflowtracker.ui.fragment

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.skyflowtracker.R
import com.example.skyflowtracker.api.RetrofitClient
import com.example.skyflowtracker.api.SseClient
import com.example.skyflowtracker.api.model.ChatMessage
import com.example.skyflowtracker.api.model.Conversation
import com.example.skyflowtracker.databinding.DialogConversationListBinding
import com.example.skyflowtracker.databinding.FragmentAiChatBinding
import com.example.skyflowtracker.ui.adapter.ChatMessageAdapter
import com.example.skyflowtracker.ui.adapter.ConversationAdapter
import com.example.skyflowtracker.utils.showConfirmDialog
import com.example.skyflowtracker.utils.showToast
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import kotlinx.coroutines.launch
import okhttp3.Call

/**
 * AI 助手聊天 Fragment
 */
class AiChatFragment : Fragment() {

    private var _binding: FragmentAiChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var markwon: Markwon
    private lateinit var chatAdapter: ChatMessageAdapter

    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentCall: Call? = null
    private var conversationId: String? = null
    private var isSending = false

    // 流式内容累积
    private val contentBuilder = StringBuilder()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAiChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initMarkwon()
        initViews()
    }

    private fun initMarkwon() {
        markwon = Markwon.builder(requireContext())
            .usePlugin(TablePlugin.create(requireContext()))
            .usePlugin(StrikethroughPlugin.create())
            .build()
    }

    private fun initViews() {
        chatAdapter = ChatMessageAdapter(markwon)
        binding.rvMessages.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        binding.rvMessages.itemAnimator = null
        binding.rvMessages.adapter = chatAdapter

        // 发送按钮
        binding.btnSend.setOnClickListener { handleSend() }

        // 新建会话
        binding.btnNewChat.setOnClickListener { newConversation() }

        // 会话列表
        binding.btnConversations.setOnClickListener { showConversationList() }
    }

    private fun handleSend() {
        val message = binding.etInput.text.toString().trim()
        if (message.isEmpty() || isSending) return

        isSending = true
        binding.btnSend.isEnabled = false
        binding.etInput.text?.clear()
        binding.llEmpty.visibility = View.GONE

        // 添加用户消息
        val userMsg = ChatMessage(role = "user", content = message)
        chatAdapter.addMessage(userMsg)

        // 添加空的助手消息（占位，显示"正在思考"）
        val assistantMsg = ChatMessage(role = "assistant", content = "", isStreaming = true)
        chatAdapter.addMessage(assistantMsg)
        scrollToBottom()

        // 清空内容累积器
        contentBuilder.clear()

        // 发送 SSE 请求
        currentCall = SseClient.chatStream(
            message = message,
            conversationId = conversationId,
            onChunk = { chunk ->
                contentBuilder.append(chunk)
                mainHandler.post {
                    if (_binding != null) {
                        chatAdapter.updateLastMessage(contentBuilder.toString(), true)
                        scrollToBottom()
                    }
                }
            },
            onConversationId = { id ->
                conversationId = id
            },
            onDone = {
                mainHandler.post {
                    if (_binding != null) {
                        chatAdapter.updateLastMessage(contentBuilder.toString(), false)
                        scrollToBottom()
                        isSending = false
                        binding.btnSend.isEnabled = true
                    }
                }
            },
            onError = { error ->
                mainHandler.post {
                    if (_binding != null) {
                        val errorContent = contentBuilder.toString().ifEmpty { "请求失败: $error" }
                        chatAdapter.updateLastMessage(errorContent, false)
                        isSending = false
                        binding.btnSend.isEnabled = true
                        requireContext().showToast("请求失败: $error")
                    }
                }
            }
        )
    }

    private fun newConversation() {
        currentCall?.cancel()
        conversationId = null
        chatAdapter.submitList(emptyList())
        isSending = false
        binding.btnSend.isEnabled = true
        contentBuilder.clear()
        binding.llEmpty.visibility = View.VISIBLE
    }

    private fun showConversationList() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getConversations()
                if (response.code == 1 && response.data != null) {
                    showConversationDialog(response.data)
                } else {
                    requireContext().showToast(response.message ?: "获取会话列表失败")
                }
            } catch (e: Exception) {
                requireContext().showToast("获取会话列表失败: ${e.message}")
            }
        }
    }

    private fun showConversationDialog(conversations: List<Conversation>) {
        val dialogBinding = DialogConversationListBinding.inflate(
            LayoutInflater.from(requireContext())
        )

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setNegativeButton("关闭", null)
            .create()

        val convAdapter = ConversationAdapter(
            onClick = { conversation ->
                dialog.dismiss()
                loadConversation(conversation.conversationId)
            },
            onDelete = { conversation ->
                requireContext().showConfirmDialog(
                    title = "删除会话",
                    message = "确定要删除这个会话吗？删除后无法恢复。",
                    onConfirm = {
                        deleteConversation(conversation.conversationId) {
                            val adapter = dialogBinding.rvConversations.adapter as? ConversationAdapter
                            val updated = adapter?.currentList
                                ?.filter { it.conversationId != conversation.conversationId }
                                ?: emptyList()
                            adapter?.submitList(updated)

                            if (updated.isEmpty()) {
                                dialogBinding.tvEmpty.visibility = View.VISIBLE
                                dialogBinding.rvConversations.visibility = View.GONE
                            }

                            if (conversation.conversationId == conversationId) {
                                newConversation()
                            }
                        }
                    }
                )
            }
        )

        dialogBinding.rvConversations.layoutManager = LinearLayoutManager(requireContext())
        dialogBinding.rvConversations.adapter = convAdapter

        if (conversations.isEmpty()) {
            dialogBinding.tvEmpty.visibility = View.VISIBLE
            dialogBinding.rvConversations.visibility = View.GONE
        } else {
            dialogBinding.tvEmpty.visibility = View.GONE
            dialogBinding.rvConversations.visibility = View.VISIBLE
            convAdapter.submitList(conversations)
        }

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(
            requireContext().getColor(R.color.text_secondary)
        )
    }

    private fun loadConversation(targetConversationId: String) {
        currentCall?.cancel()
        isSending = false
        binding.btnSend.isEnabled = true

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getConversationHistory(targetConversationId)
                if (response.code == 1 && response.data != null) {
                    conversationId = targetConversationId
                    val messages = response.data.map { msg ->
                        ChatMessage(
                            role = msg.role,
                            content = msg.content,
                            isStreaming = false
                        )
                    }
                    chatAdapter.submitList(messages)
                    binding.llEmpty.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
                    scrollToBottom()
                } else {
                    requireContext().showToast(response.message ?: "加载会话失败")
                }
            } catch (e: Exception) {
                requireContext().showToast("加载会话失败: ${e.message}")
            }
        }
    }

    private fun deleteConversation(targetConversationId: String, onSuccess: () -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.deleteConversation(targetConversationId)
                if (response.code == 1) {
                    requireContext().showToast("删除成功")
                    onSuccess()
                } else {
                    requireContext().showToast(response.message ?: "删除失败")
                }
            } catch (e: Exception) {
                requireContext().showToast("删除失败: ${e.message}")
            }
        }
    }

    private fun scrollToBottom() {
        val itemCount = chatAdapter.itemCount
        if (itemCount > 0) {
            binding.rvMessages.smoothScrollToPosition(itemCount - 1)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        currentCall?.cancel()
        _binding = null
    }
}
