package com.example.skyflowtracker.config;

import com.example.skyflowtracker.websocket.DeviceStatusHandler;
import com.example.skyflowtracker.websocket.InferenceHandler;
import com.example.skyflowtracker.websocket.VideoStreamHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final VideoStreamHandler videoStreamHandler;
    private final InferenceHandler inferenceHandler;
    private final DeviceStatusHandler deviceStatusHandler;

    @Autowired
    public WebSocketConfig(VideoStreamHandler videoStreamHandler, InferenceHandler inferenceHandler,
                           DeviceStatusHandler deviceStatusHandler) {
        this.videoStreamHandler = videoStreamHandler;
        this.inferenceHandler = inferenceHandler;
        this.deviceStatusHandler = deviceStatusHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        //用于安卓端发送视频帧
        registry.addHandler((WebSocketHandler) videoStreamHandler,"/ws/video-stream")
                .setAllowedOrigins("*");

        //用于web端或者安卓端接收推理结果
        registry.addHandler((WebSocketHandler) inferenceHandler,"/ws/inference")
                .setAllowedOrigins("*");

        //用于web端接收设备在线状态变更
        registry.addHandler((WebSocketHandler) deviceStatusHandler,"/ws/device-status")
                .setAllowedOrigins("*");
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        //最大文本消息2mb，最大二进制消息1mb（二进制帧直推需要更大缓冲区）
        container.setMaxTextMessageBufferSize(2 * 1024 * 1024);
        container.setMaxBinaryMessageBufferSize(1024 * 1024);
        return container;
    }
}
