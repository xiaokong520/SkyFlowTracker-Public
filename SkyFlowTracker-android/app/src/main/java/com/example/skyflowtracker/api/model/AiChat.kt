package com.example.skyflowtracker.api.model

import com.google.gson.annotations.SerializedName
import java.util.UUID

/**
 * 聊天消息（本地 UI 模型）
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false
)

/**
 * 会话列表项
 */
data class Conversation(
    @SerializedName("conversation_id")
    val conversationId: String,
    @SerializedName("last_message")
    val lastMessage: String?,
    @SerializedName("last_time")
    val lastTime: String?
)

/**
 * 会话历史消息项
 */
data class ConversationMessage(
    val role: String,
    val content: String,
    @SerializedName("createTime")
    val createTime: String?
)
