package com.example.skyflowtracker.service.impl;

import com.example.skyflowtracker.dto.AiChatDto;
import com.example.skyflowtracker.dto.AiSummaryDto;
import com.example.skyflowtracker.exception.AppException;
import com.example.skyflowtracker.exception.AppExceptionCodeMsg;
import com.example.skyflowtracker.mapper.AiChatMessageMapper;
import com.example.skyflowtracker.mapper.DashboardMapper;
import com.example.skyflowtracker.mapper.InferenceTasksMapper;
import com.example.skyflowtracker.mapper.UsersMapper;
import com.example.skyflowtracker.pojo.AiChatMessage;
import com.example.skyflowtracker.pojo.InferenceTasks;
import com.example.skyflowtracker.pojo.Users;
import com.example.skyflowtracker.service.inte.AiServiceInte;
import com.example.skyflowtracker.utils.TokenUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AiService implements AiServiceInte {

    private final ChatClient chatClient;
    private final TokenUtil tokenUtil;
    private final UsersMapper usersMapper;
    private final DashboardMapper dashboardMapper;
    private final InferenceTasksMapper inferenceTasksMapper;
    private final AiChatMessageMapper aiChatMessageMapper;

    @Autowired
    public AiService(ChatClient.Builder chatClientBuilder, TokenUtil tokenUtil,
                     UsersMapper usersMapper, DashboardMapper dashboardMapper,
                     InferenceTasksMapper inferenceTasksMapper,
                     AiChatMessageMapper aiChatMessageMapper) {
        this.chatClient = chatClientBuilder.build();
        this.tokenUtil = tokenUtil;
        this.usersMapper = usersMapper;
        this.dashboardMapper = dashboardMapper;
        this.inferenceTasksMapper = inferenceTasksMapper;
        this.aiChatMessageMapper = aiChatMessageMapper;
    }

    @Override
    public SseEmitter chatStream(String token, AiChatDto aiChatDto) throws Exception {
        Map<String, Object> checkToken = tokenUtil.checkToken(token);
        String userId = checkToken.get("userId").toString();
        Users user = usersMapper.selectUserByUserId(userId);
        if (user == null) {
            throw new AppException(AppExceptionCodeMsg.USER_NOT_EXIST);
        }

        String conversationId = aiChatDto.getConversationId();
        if (conversationId == null || conversationId.isEmpty()) {
            conversationId = "conv_" + userId + "_" + System.currentTimeMillis();
        }

        // 先保存用户消息
        LocalDateTime now = LocalDateTime.now();
        AiChatMessage userMsg = new AiChatMessage();
        userMsg.setUserId(userId);
        userMsg.setConversationId(conversationId);
        userMsg.setRole("user");
        userMsg.setContent(aiChatDto.getMessage());
        userMsg.setCreateTime(now);
        aiChatMessageMapper.insert(userMsg);

        // 构建消息列表
        String systemPrompt = buildSystemPrompt(userId);
        List<AiChatMessage> history = aiChatMessageMapper.selectByConversationId(conversationId);
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        for (AiChatMessage msg : history) {
            if ("user".equals(msg.getRole())) {
                messages.add(new UserMessage(msg.getContent()));
            } else if ("assistant".equals(msg.getRole())) {
                messages.add(new AssistantMessage(msg.getContent()));
            }
        }

        SseEmitter emitter = new SseEmitter(120000L);
        final String convId = conversationId;

        // 先发送 conversationId
        emitter.send(SseEmitter.event().name("conversationId").data(convId));

        // 流式调用
        Flux<String> flux = chatClient.prompt()
                .messages(messages)
                .stream()
                .content();

        StringBuilder fullReply = new StringBuilder();

        flux.subscribe(
                chunk -> {
                    try {
                        fullReply.append(chunk);
                        emitter.send(SseEmitter.event().name("chunk").data(chunk));
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                },
                error -> {
                    log.error("AI流式调用失败: {}", error.getMessage());
                    try {
                        emitter.send(SseEmitter.event().name("error").data("AI服务异常"));
                    } catch (Exception ignored) {}
                    emitter.completeWithError(error);
                },
                () -> {
                    try {
                        // 流结束，保存完整回复
                        AiChatMessage assistantMsg = new AiChatMessage();
                        assistantMsg.setUserId(userId);
                        assistantMsg.setConversationId(convId);
                        assistantMsg.setRole("assistant");
                        assistantMsg.setContent(fullReply.toString());
                        assistantMsg.setCreateTime(LocalDateTime.now());
                        aiChatMessageMapper.insert(assistantMsg);

                        emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                        emitter.complete();
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                }
        );

        emitter.onTimeout(emitter::complete);
        emitter.onError(t -> log.warn("SSE连接异常: {}", t.getMessage()));

        return emitter;
    }

    private String buildSystemPrompt(String userId) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是SkyFlowTracker智能交通分析助手，专门帮助用户分析无人机交通监控数据。\n");
        sb.append("请用中文回答，语言简洁专业。\n");
        sb.append("当前时间: ").append(LocalDateTime.now()).append("\n\n");
        try {
            int totalFlights = dashboardMapper.countTotalFlights(userId);
            Long totalDetections = dashboardMapper.sumTotalDetections(userId);
            Long flightDuration = dashboardMapper.sumFlightDuration(userId);
            int totalDevices = dashboardMapper.countTotalDevices(userId);
            sb.append("【用户数据概览】\n");
            sb.append("- 总飞行次数: ").append(totalFlights).append("\n");
            sb.append("- 总检测数量: ").append(totalDetections != null ? totalDetections : 0).append("\n");
            sb.append("- 总飞行时长: ").append(flightDuration != null ? flightDuration : 0).append(" 秒\n");
            sb.append("- 设备数量: ").append(totalDevices).append("\n\n");
            List<Map<String, Object>> trend = dashboardMapper.selectDetectionTrend(userId, "30d");
            if (trend != null && !trend.isEmpty()) {
                sb.append("【近30天检测趋势】\n");
                for (Map<String, Object> item : trend) {
                    sb.append("- ").append(item.get("date")).append(": ")
                      .append(item.get("detections")).append(" 次检测\n");
                }
                sb.append("\n");
            }
            List<InferenceTasks> completedTasks = dashboardMapper.selectCompletedTasksResultData(userId);
            if (completedTasks != null && !completedTasks.isEmpty()) {
                sb.append("【已完成推理任务摘要】\n");
                int count = 0;
                for (InferenceTasks task : completedTasks) {
                    if (task.getResultData() != null && count < 5) {
                        sb.append("- 任务#").append(task.getId())
                          .append(" 检测数:").append(task.getTotalDetections() != null ? task.getTotalDetections() : 0)
                          .append(" 结果:").append(task.getResultData()).append("\n");
                        count++;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("构建AI上下文数据失败: {}", e.getMessage());
            sb.append("（暂无法获取用户数据）\n");
        }
        return sb.toString();
    }

    @Override
    public SseEmitter generateTaskSummaryStream(String token, AiSummaryDto aiSummaryDto) throws Exception {
        Map<String, Object> checkToken = tokenUtil.checkToken(token);
        String userId = checkToken.get("userId").toString();
        Users user = usersMapper.selectUserByUserId(userId);
        if (user == null) {
            throw new AppException(AppExceptionCodeMsg.USER_NOT_EXIST);
        }

        InferenceTasks task = inferenceTasksMapper.selectTasksById(aiSummaryDto.getTaskId());
        SseEmitter emitter = new SseEmitter(120000L);

        if (task == null) {
            emitter.send(SseEmitter.event().name("error").data("任务不存在"));
            emitter.complete();
            return emitter;
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("请对以下无人机交通检测推理任务生成一份简洁的智能分析报告（中文），包括：\n");
        prompt.append("1. 任务概述\n2. 检测结果分析\n3. 交通状况评估\n4. 建议\n\n");
        prompt.append("【任务信息】\n");
        prompt.append("- 任务ID: ").append(task.getId()).append("\n");
        prompt.append("- 模型: ").append(task.getModelName()).append("\n");
        prompt.append("- 开始时间: ").append(task.getStartTime()).append("\n");
        prompt.append("- 结束时间: ").append(task.getEndTime()).append("\n");
        prompt.append("- 检测总数: ").append(task.getTotalDetections() != null ? task.getTotalDetections() : 0).append("\n");
        if (task.getResultData() != null) {
            prompt.append("- 详细结果: ").append(task.getResultData()).append("\n");
        }

        Flux<String> flux = chatClient.prompt()
                .user(prompt.toString())
                .stream()
                .content();

        flux.subscribe(
                chunk -> {
                    try {
                        emitter.send(SseEmitter.event().name("chunk").data(chunk));
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                },
                error -> {
                    log.error("AI摘要流式调用失败: {}", error.getMessage());
                    try {
                        emitter.send(SseEmitter.event().name("error").data("AI服务异常"));
                    } catch (Exception ignored) {}
                    emitter.completeWithError(error);
                },
                () -> {
                    try {
                        emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                        emitter.complete();
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                }
        );

        emitter.onTimeout(emitter::complete);
        emitter.onError(t -> log.warn("SSE摘要连接异常: {}", t.getMessage()));

        return emitter;
    }

    @Override
    public List<Map<String, Object>> getConversations(String token) throws Exception {
        Map<String, Object> checkToken = tokenUtil.checkToken(token);
        String userId = checkToken.get("userId").toString();
        return aiChatMessageMapper.selectConversationsByUserId(userId);
    }

    @Override
    public List<Map<String, Object>> getConversationHistory(String token, String conversationId) throws Exception {
        Map<String, Object> checkToken = tokenUtil.checkToken(token);
        String userId = checkToken.get("userId").toString();
        List<AiChatMessage> messages = aiChatMessageMapper.selectByConversationId(conversationId);
        return messages.stream().map(msg -> {
            Map<String, Object> map = new HashMap<>();
            map.put("role", msg.getRole());
            map.put("content", msg.getContent());
            map.put("createTime", msg.getCreateTime());
            return map;
        }).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> deleteConversation(String token, String conversationId) throws Exception {
        Map<String, Object> checkToken = tokenUtil.checkToken(token);
        String userId = checkToken.get("userId").toString();
        int rows = aiChatMessageMapper.deleteConversation(conversationId, userId);
        Map<String, Object> result = new HashMap<>();
        result.put("message", rows > 0 ? "删除成功" : "会话不存在");
        return result;
    }
}
