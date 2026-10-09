package com.example.skyflowtracker.dto;

import jakarta.validation.constraints.NotBlank;

public class AiChatDto {
    @NotBlank(message = "消息内容不能为空")
    private String message;
    private String conversationId;

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
}
