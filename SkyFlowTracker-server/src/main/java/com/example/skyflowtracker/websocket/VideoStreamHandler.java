package com.example.skyflowtracker.websocket;

import com.example.skyflowtracker.service.inte.InferenceTasksServiceInte;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 接收 Android 端发送的视频帧（JPEG 二进制）
 * 连接时带参数: ws://host/ws/video-stream?sn=设备SN
 * Android 持续发送 BinaryMessage（每帧一个 JPEG）
 */
@Slf4j
@Component
public class VideoStreamHandler extends AbstractWebSocketHandler {
    //sn -> WebSocketSession,一个设备一个连接
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    //sn -> 最新一帧视频数据
    private final ConcurrentHashMap<String,byte[]> latestFrames = new ConcurrentHashMap<>();
    //演示模式中的设备SN集合
    private final Set<String> demoModeDevices = ConcurrentHashMap.newKeySet();

    // 端到端延迟追踪: frame_id -> capture_ts(Android采集时间)
    private final ConcurrentHashMap<Long, Long> frameCaptureTs = new ConcurrentHashMap<>();
    // sn -> latest frame_id（关联forwardFrames中正在处理的帧）
    private final ConcurrentHashMap<String, Long> latestFrameIds = new ConcurrentHashMap<>();
    private final InferenceHandler inferenceHandler;
    private final InferenceTasksServiceInte inferenceTasksService;
    private final DeviceStatusHandler deviceStatusHandler;

    @Autowired
    public VideoStreamHandler(InferenceHandler inferenceHandler,
                              @Lazy InferenceTasksServiceInte inferenceTasksService,
                              DeviceStatusHandler deviceStatusHandler) {
        this.inferenceHandler = inferenceHandler;
        this.inferenceTasksService = inferenceTasksService;
        this.deviceStatusHandler = deviceStatusHandler;
        // 通过 setter 注入避免循环依赖
        deviceStatusHandler.setVideoStreamHandler(this);
    }


    @Override
    @NullMarked
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sn = extractSn(session);
        if (sn == null){
            log.warn("ws连接参数缺少sn参数，关闭连接");
            try {
                session.close();
            }catch (Exception e){
                log.warn("关闭ws连接时出错 {}", e.getMessage());
            }
            return;
        }
        log.info("安卓视频连接建立，sn: {} ,sessionId：{}", sn,session.getId());
        sessions.put(sn, session);
        deviceStatusHandler.broadcastStatusChange(sn, true);
    }

    @Override
    @NullMarked
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        String sn = extractSn(session);
        if (sn == null){
            return;
        }
        if (demoModeDevices.contains(sn)) {
            return;
        }
        byte[] rawData = message.getPayload().array();
        long recvTs = System.currentTimeMillis();

        // 解析 12 字节头部: [4B frame_id big-endian][8B capture_ts big-endian]
        long frameId = 0;
        long captureTs = 0;
        byte[] frameData;
        if (rawData.length > 12) {
            ByteBuffer header = ByteBuffer.wrap(rawData, 0, 12);
            frameId = header.getInt() & 0xFFFFFFFFL;
            captureTs = header.getLong();
            frameData = new byte[rawData.length - 12];
            System.arraycopy(rawData, 12, frameData, 0, frameData.length);

            // 存储延迟追踪元数据
            frameCaptureTs.put(frameId, captureTs);
            latestFrameIds.put(sn, frameId);
            // 端到端延迟日志: LATENCY|frame_id|1|recv_ts
            log.info("LATENCY|{}|1|{}", frameId, recvTs);
        } else {
            // 兼容旧版无头部协议
            frameData = rawData;
        }

        //更新最新帧
        latestFrames.put(sn, frameData);
        //只有在没有推理任务的情况下才会推送原始帧给web前端
        if (inferenceTasksService != null && !inferenceTasksService.hasRunningTask(sn)){
            pushOriginalFrameToWeb(sn, frameData);
        }
    }

    @Override
    @NullMarked
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sn = extractSn(session);
        if (sn != null){
            sessions.remove(sn);
            latestFrames.remove(sn);
            deviceStatusHandler.broadcastStatusChange(sn, false);
            log.info("安卓视频连接关闭，sn: {} ,sessionId：{}", sn,session.getId());
        }
    }

    @Override
    @NullMarked
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String sn = extractSn(session);
        log.error("安卓视频连接发生错误，sn: {} ,sessionId：{}", sn,session.getId(), exception);
    }

    //推送原始帧给web前端（二进制协议，避免Base64编码开销）
    public void pushOriginalFrameToWeb(String sn,byte[] frameData){
        try {
            Long frameId = latestFrameIds.get(sn);
            String metadataJson;
            if (frameId != null) {
                metadataJson = "{\"code\":1,\"data\":{\"total\":0,\"detections\":[],\"_frame_id\":" + frameId + "}}";
            } else {
                metadataJson = "{\"code\":1,\"data\":{\"total\":0,\"detections\":[]}}";
            }
            inferenceHandler.pushBinaryInferenceResult(sn, frameData, metadataJson);
        }catch (Exception e){
            log.error("推送原始帧给web端失败： {}", e.getMessage());
        }
    }

    //获取最新帧
    public byte[] consumeLatestFrame(WebSocketSession session) {
        return latestFrames.remove(session.getId());
    }

    //根据SN获取并消费最新帧（取后删除，避免重复发送）
    public byte[] consumeLatestFrame(String sn) {
        return latestFrames.remove(sn);
    }

    //检查设备是否在线
    public boolean isOnline(String sn){
        WebSocketSession session = sessions.get(sn);
        return session != null && session.isOpen();
    }

    //获取所有在线设备的SN
    public Set<String> getOnlineDeviceSns() {
        return sessions.entrySet().stream()
                .filter(e -> e.getValue().isOpen())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
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

    public byte[] getLatestFrame(String sn) {
        return latestFrames.get(sn);
    }

    /** 获取指定SN的最新帧ID（用于延迟追踪） */
    public Long getLatestFrameId(String sn) {
        return latestFrameIds.get(sn);
    }

    /** 获取指定帧ID的Android采集时间戳 */
    public Long getFrameCaptureTs(long frameId) {
        return frameCaptureTs.get(frameId);
    }

    /** 清理帧追踪元数据（避免内存积累） */
    public void clearFrameMeta(long frameId) {
        frameCaptureTs.remove(frameId);
    }

    public void setDemoMode(String sn, boolean enabled) {
        if (enabled) demoModeDevices.add(sn);
        else demoModeDevices.remove(sn);
    }

    public void injectDemoFrame(String sn, byte[] jpegData) {
        latestFrames.put(sn, jpegData);
        if (inferenceTasksService == null || !inferenceTasksService.hasRunningTask(sn)) {
            pushOriginalFrameToWeb(sn, jpegData);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sn = extractSn(session);
        if (sn == null) return;
        log.debug("收到Android文本消息, sn={}: {}", sn, message.getPayload());
    }

    public void sendCommandToDevice(String sn, String jsonCommand) {
        WebSocketSession session = sessions.get(sn);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(jsonCommand));
            } catch (IOException e) {
                log.error("发送命令到设备失败, sn={}: {}", sn, e.getMessage());
            }
        }
    }
}
