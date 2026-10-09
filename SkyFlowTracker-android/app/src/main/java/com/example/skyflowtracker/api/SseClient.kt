package com.example.skyflowtracker.api

import com.example.skyflowtracker.utils.TokenManager
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * SSE 流式请求客户端
 */
object SseClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 发送 SSE 流式聊天请求
     * @param message 用户消息
     * @param conversationId 会话ID（可选）
     * @param onChunk 收到文本片段回调
     * @param onConversationId 收到会话ID回调
     * @param onDone 完成回调
     * @param onError 错误回调
     * @return Call 对象，可调用 cancel() 取消请求
     */
    fun chatStream(
        message: String,
        conversationId: String?,
        onChunk: (String) -> Unit,
        onConversationId: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ): Call {
        val json = JSONObject().apply {
            put("message", message)
            if (!conversationId.isNullOrEmpty()) {
                put("conversationId", conversationId)
            }
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val token = TokenManager.getToken() ?: ""

        val request = Request.Builder()
            .url("${RetrofitClient.BASE_URL}ai/chat")
            .post(body)
            .addHeader("Authorization", token)
            .addHeader("Accept", "text/event-stream")
            .build()

        val call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!call.isCanceled()) {
                    onError(e.message ?: "网络请求失败")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    onError("请求失败: ${response.code}")
                    response.close()
                    return
                }

                try {
                    val reader = BufferedReader(
                        InputStreamReader(response.body?.byteStream() ?: return)
                    )
                    var currentEvent = ""
                    var dataBuffer = StringBuilder()
                    var hasData = false
                    var line: String?

                    while (reader.readLine().also { line = it } != null) {
                        if (call.isCanceled()) break

                        val l = line ?: continue
                        when {
                            l.isEmpty() -> {
                                // 空行 = SSE 事件边界，分发累积的数据
                                val data = dataBuffer.toString()
                                when (currentEvent) {
                                    "conversationId" -> onConversationId(data)
                                    "chunk" -> onChunk(data)
                                    "done" -> onDone()
                                    "error" -> onError(data)
                                }
                                currentEvent = ""
                                dataBuffer.clear()
                                hasData = false
                            }
                            l.startsWith("event:") -> {
                                currentEvent = l.removePrefix("event:").trim()
                            }
                            l.startsWith("data:") -> {
                                val payload = l.removePrefix("data:")
                                if (hasData) {
                                    dataBuffer.append("\n")
                                }
                                dataBuffer.append(payload)
                                hasData = true
                            }
                        }
                    }
                    reader.close()
                } catch (e: Exception) {
                    if (!call.isCanceled()) {
                        onError(e.message ?: "读取响应失败")
                    }
                } finally {
                    response.close()
                }
            }
        })

        return call
    }

    /**
     * 发送 SSE 流式摘要生成请求
     * @param taskId 推理任务ID
     * @param onChunk 收到文本片段回调
     * @param onDone 完成回调
     * @param onError 错误回调
     * @return Call 对象，可调用 cancel() 取消请求
     */
    fun summaryStream(
        taskId: Long,
        onChunk: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ): Call {
        val json = JSONObject().apply {
            put("taskId", taskId)
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val token = TokenManager.getToken() ?: ""

        val request = Request.Builder()
            .url("${RetrofitClient.BASE_URL}ai/summary")
            .post(body)
            .addHeader("Authorization", token)
            .addHeader("Accept", "text/event-stream")
            .build()

        val call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!call.isCanceled()) {
                    onError(e.message ?: "网络请求失败")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    onError("请求失败: ${response.code}")
                    response.close()
                    return
                }

                try {
                    val reader = BufferedReader(
                        InputStreamReader(response.body?.byteStream() ?: return)
                    )
                    var currentEvent = ""
                    var dataBuffer = StringBuilder()
                    var hasData = false
                    var line: String?

                    while (reader.readLine().also { line = it } != null) {
                        if (call.isCanceled()) break

                        val l = line ?: continue
                        when {
                            l.isEmpty() -> {
                                val data = dataBuffer.toString()
                                when (currentEvent) {
                                    "chunk" -> onChunk(data)
                                    "done" -> onDone()
                                    "error" -> onError(data)
                                }
                                currentEvent = ""
                                dataBuffer.clear()
                                hasData = false
                            }
                            l.startsWith("event:") -> {
                                currentEvent = l.removePrefix("event:").trim()
                            }
                            l.startsWith("data:") -> {
                                val payload = l.removePrefix("data:")
                                if (hasData) {
                                    dataBuffer.append("\n")
                                }
                                dataBuffer.append(payload)
                                hasData = true
                            }
                        }
                    }
                    reader.close()
                } catch (e: Exception) {
                    if (!call.isCanceled()) {
                        onError(e.message ?: "读取响应失败")
                    }
                } finally {
                    response.close()
                }
            }
        })

        return call
    }
}
