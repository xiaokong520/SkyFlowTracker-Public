package com.example.skyflowtracker.service.inte;

import com.example.skyflowtracker.dto.AiChatDto;
import com.example.skyflowtracker.dto.AiSummaryDto;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;
import java.util.Map;

public interface AiServiceInte {
    SseEmitter chatStream(String token, AiChatDto aiChatDto) throws Exception;
    SseEmitter generateTaskSummaryStream(String token, AiSummaryDto aiSummaryDto) throws Exception;
    List<Map<String, Object>> getConversations(String token) throws Exception;
    List<Map<String, Object>> getConversationHistory(String token, String conversationId) throws Exception;
    Map<String, Object> deleteConversation(String token, String conversationId) throws Exception;
}
