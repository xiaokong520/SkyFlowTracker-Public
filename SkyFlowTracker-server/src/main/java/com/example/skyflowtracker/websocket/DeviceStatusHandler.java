package com.example.skyflowtracker.websocket;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 向 Web 前端推送设备在线/离线状态变更
 * 连接: ws://host/ws/device-status
 * Web 客户端连接后立即收到当前所有在线设备列表，之后实时接收上/下线事件
 */
@Slf4j
@Component
public class  DeviceStatusHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> subscribers = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Object> sessionLocks = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    // 通过 setter 注入，避免循环依赖
    private VideoStreamHandler videoStreamHandler;

    public DeviceStatusHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void setVideoStreamHandler(VideoStreamHandler videoStreamHandler) {
        this.videoStreamHandler = videoStreamHandler;
    }

    @Override
    @NullMarked
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        subscribers.add(session);
        sessionLocks.put(session.getId(), new Object());
        log.info("设备状态订阅连接建立，sessionId：{}", session.getId());

        // 推送当前所有在线设备
        if (videoStreamHandler != null) {
            Set<String> onlineSns = videoStreamHandler.getOnlineDeviceSns();
            Map<String, Object> initMsg = new HashMap<>();
            initMsg.put("type", "init");
            initMsg.put("onlineDevices", onlineSns);
            sendToSession(session, objectMapper.writeValueAsString(initMsg));
        }
    }

    @Override
    @NullMarked
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        subscribers.remove(session);
        sessionLocks.remove(session.getId());
        log.info("设备状态订阅连接关闭，sessionId：{}", session.getId());
    }

    @Override
    @NullMarked
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("设备状态ws连接发生错误，sessionId：{}，err: {}", session.getId(), exception.getMessage());
    }

    /**
     * 广播设备状态变更给所有订阅者
     */
    public void broadcastStatusChange(String sn, boolean online) {
        if (subscribers.isEmpty()) return;

        try {
            Map<String, Object> msg = new HashMap<>();
            msg.put("type", online ? "online" : "offline");
            msg.put("sn", sn);
            String json = objectMapper.writeValueAsString(msg);

            for (WebSocketSession session : subscribers) {
                sendToSession(session, json);
            }
        } catch (Exception e) {
            log.error("广播设备状态变更失败：{}", e.getMessage());
        }
    }

    private void sendToSession(WebSocketSession session, String json) {
        if (!session.isOpen()) return;
        Object lock = sessionLocks.computeIfAbsent(session.getId(), k -> new Object());
        synchronized (lock) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            } catch (IOException e) {
                log.warn("推送设备状态失败，sessionId: {}，err: {}", session.getId(), e.getMessage());
            }
        }
    }
}
