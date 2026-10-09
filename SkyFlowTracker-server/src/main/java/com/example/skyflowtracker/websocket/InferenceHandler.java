package com.example.skyflowtracker.websocket;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 向 Web 前端推送推理结果（标注画面 base64 + 检测数据）
 * 连接时带参数: ws://host/ws/inference?sn=设备SN
 * 多个 Web 客户端可以同时观看同一个设备的推理画面
 */
@Slf4j
@Component
public class InferenceHandler extends TextWebSocketHandler {

    private final ConcurrentHashMap<String, Set<WebSocketSession>> subscribers = new ConcurrentHashMap<>();
    // 每个session一把锁，防止并发sendMessage导致异常
    private final ConcurrentHashMap<String, Object> sessionLocks = new ConcurrentHashMap<>();

    @Override
    @NullMarked
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sn = extractSn(session);
        if (sn == null){
            log.error("推理ws连接缺少sn参数，关闭连接");
            try {
                session.close();
            }catch (Exception e){
                log.warn("关闭 inconsistent session 失败 {}", session.getId(), e);
            }
            return;
        }
        subscribers.computeIfAbsent(sn, k -> ConcurrentHashMap.newKeySet()).add(session);
        sessionLocks.put(session.getId(), new Object());
        log.info("web前端订阅推理，sn：{}，sessionID：{}", sn, session.getId());
    }

    @Override
    @NullMarked
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sn = extractSn(session);
        if (sn != null){
            Set<WebSocketSession> set = subscribers.get(sn);
            if (set != null){
                set.remove(session);
                sessionLocks.remove(session.getId());
                if (set.isEmpty()){
                    subscribers.remove(sn);
                }
            }
            log.info("web前端取消订阅推理，sn：{}，sessionID：{}", sn, session.getId());
        }
    }

    @Override
    @NullMarked
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String sn = extractSn(session);
        log.error("推理ws连接发生错误，sn: {} ,sessionId：{}，err: {}", sn,session.getId(), exception.getMessage());
    }

    //向所有订阅了该设备的客户端推送推理结果（文本JSON，用于离线推理等场景）
    public void pushInferenceResult(String sn,String jsonResult){
        Set<WebSocketSession> set = subscribers.get(sn);
        if (set == null || set.isEmpty()){
            return;
        }
        TextMessage message = new TextMessage(jsonResult);
        for (WebSocketSession session : set){
            if (session.isOpen()){
                Object lock = sessionLocks.computeIfAbsent(session.getId(), k -> new Object());
                synchronized (lock) {
                    try {
                        if (session.isOpen()) {
                            session.sendMessage(message);
                        }
                    } catch (IOException e) {
                        log.warn("向 Web 前端推送推理结果时出错 {}，sn: {}，sessionId: {}", e.getMessage(), sn, session.getId());
                    }
                }
            }
        }
    }

    /**
     * 向所有订阅了该设备的客户端推送二进制帧（实时推理/原始帧）
     * 协议格式: [4字节元数据长度(big-endian)][元数据JSON字节][JPEG图片字节]
     * 相比 Base64+JSON 文本传输，减少约 33% 数据量并省去编解码开销
     */
    public void pushBinaryInferenceResult(String sn, byte[] jpegData, String metadataJson) {
        Set<WebSocketSession> set = subscribers.get(sn);
        if (set == null || set.isEmpty()) {
            return;
        }
        byte[] metaBytes = metadataJson.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(4 + metaBytes.length + jpegData.length);
        buffer.putInt(metaBytes.length);
        buffer.put(metaBytes);
        buffer.put(jpegData);
        buffer.flip();

        BinaryMessage message = new BinaryMessage(buffer);
        for (WebSocketSession session : set) {
            if (session.isOpen()) {
                Object lock = sessionLocks.computeIfAbsent(session.getId(), k -> new Object());
                synchronized (lock) {
                    try {
                        if (session.isOpen()) {
                            session.sendMessage(message);
                        }
                    } catch (IOException e) {
                        log.warn("推送二进制帧失败 {}，sn: {}，sessionId: {}", e.getMessage(), sn, session.getId());
                    }
                }
            }
        }
    }

    //检查是否有web客户端在订阅该设备
    public boolean hasSubscribers(String sn){
        Set<WebSocketSession> set = subscribers.get(sn);
        return set != null && !set.isEmpty();
    }

    //从ws连接的URL查询参数里提取设备 SN。
    public String extractSn(WebSocketSession session) {
        String query = session.getUri() != null ? session.getUri().getQuery() : null;
        if (query != null){
            for (String param : query.split("&")){
                String[] ky = param.split("=",2);
                if ("sn".equals(ky[0]) && ky.length == 2){
                    return ky[1].trim();
                }
            }
        }
        return null;
    }
}
