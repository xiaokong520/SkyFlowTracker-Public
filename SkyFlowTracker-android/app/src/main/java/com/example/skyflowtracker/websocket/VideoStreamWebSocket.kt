package com.example.skyflowtracker.websocket

import android.util.Log
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 视频流 WebSocket 管理类
 * 连接到 SpringBoot 的 /ws/video-stream?sn=xxx 端点
 * 发送 JPEG 二进制帧
 */
class VideoStreamWebSocket(
    private val serverUrl: String,
    private val sn: String
) {
    private val TAG = "VideoStreamWS"
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    // 帧ID计数器，用于端到端延迟追踪
    private val frameIdCounter = AtomicInteger(0)

    interface CommandListener {
        fun onStartDemo()
        fun onStopDemo()
    }

    var commandListener: CommandListener? = null

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "WebSocket 连接成功: sn=$sn")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "收到文本消息: $text")
            try {
                val json = JSONObject(text)
                when (json.optString("command")) {
                    "START_DEMO" -> commandListener?.onStartDemo()
                    "STOP_DEMO" -> commandListener?.onStopDemo()
                }
            } catch (e: Exception) {
                Log.w(TAG, "解析服务器指令失败: ${e.message}")
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            Log.d(TAG, "收到二进制消息: ${bytes.size} bytes")
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "WebSocket 正在关闭: code=$code, reason=$reason")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "WebSocket 已关闭: code=$code, reason=$reason")
            this@VideoStreamWebSocket.webSocket = null
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket 连接失败: ${t.message}", t)
            this@VideoStreamWebSocket.webSocket = null
        }
    }

    fun connect() {
        if (webSocket != null) {
            Log.w(TAG, "WebSocket 已连接，跳过重复连接")
            return
        }

        val wsUrl = "$serverUrl/ws/video-stream?sn=$sn"
        Log.i(TAG, "正在连接 WebSocket: $wsUrl")

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, listener)
    }

    private val MAX_QUEUE_SIZE = 1L * 1024 * 1024

    fun sendFrame(jpegData: ByteArray): Boolean {
        val ws = webSocket
        if (ws == null) {
            Log.w(TAG, "WebSocket 未连接，无法发送帧")
            return false
        }

        if (ws.queueSize() > MAX_QUEUE_SIZE) {
            Log.w(TAG, "WebSocket 发送队列积压，丢弃帧")
            return false
        }

        return try {
            val frameId = frameIdCounter.incrementAndGet()
            val captureTs = System.currentTimeMillis()

            // 构建 12 字节头部: [4B frame_id big-endian][8B capture_ts big-endian]
            val header = ByteBuffer.allocate(12)
            header.putInt(frameId)
            header.putLong(captureTs)
            val packet = ByteArray(12 + jpegData.size)
            System.arraycopy(header.array(), 0, packet, 0, 12)
            System.arraycopy(jpegData, 0, packet, 12, jpegData.size)

            // 端到端延迟日志: LATENCY|frame_id|0|capture_ts
            Log.i("LATENCY", "$frameId|0|$captureTs")

            ws.send(ByteString.of(*packet))
            true
        } catch (e: Exception) {
            Log.e(TAG, "发送帧失败: ${e.message}")
            false
        }
    }

    fun sendTextMessage(text: String) {
        webSocket?.send(text)
    }

    fun disconnect() {
        webSocket?.close(1000, "正常关闭")
        webSocket = null
        Log.i(TAG, "WebSocket 已断开")
    }

    fun isConnected(): Boolean {
        return webSocket != null
    }
}
