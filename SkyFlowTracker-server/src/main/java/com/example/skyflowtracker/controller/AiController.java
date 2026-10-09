package com.example.skyflowtracker.controller;

import com.example.skyflowtracker.dto.AiChatDto;
import com.example.skyflowtracker.dto.AiSummaryDto;
import com.example.skyflowtracker.service.inte.AiServiceInte;
import com.example.skyflowtracker.vo.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final AiServiceInte aiService;

    @Autowired
    public AiController(AiServiceInte aiService) {
        this.aiService = aiService;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                           @Valid @RequestBody AiChatDto aiChatDto) throws Exception {
        return aiService.chatStream(token, aiChatDto);
    }

    @PostMapping(value = "/summary", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter summary(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                              @Valid @RequestBody AiSummaryDto aiSummaryDto) throws Exception {
        return aiService.generateTaskSummaryStream(token, aiSummaryDto);
    }

    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse> getConversations(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token) throws Exception {
        List<Map<String, Object>> conversations = aiService.getConversations(token);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success("获取成功", conversations));
    }

    @GetMapping("/conversation/history")
    public ResponseEntity<ApiResponse> getConversationHistory(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @RequestParam("conversationId") String conversationId) throws Exception {
        List<Map<String, Object>> history = aiService.getConversationHistory(token, conversationId);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success("获取成功", history));
    }

    @DeleteMapping("/conversation")
    public ResponseEntity<ApiResponse> deleteConversation(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token,
            @RequestParam("conversationId") String conversationId) throws Exception {
        Map<String, Object> result = aiService.deleteConversation(token, conversationId);
        String message = result.get("message").toString();
        if ("删除成功".equals(message)) {
            return ResponseEntity.status(HttpStatus.OK)
                    .body(ApiResponse.success(message));
        } else {
            return ResponseEntity.status(HttpStatus.OK)
                    .body(ApiResponse.error(message));
        }
    }
}
