package com.example.skyflowtracker.api

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.example.skyflowtracker.BuildConfig
import com.example.skyflowtracker.MyApplication
import com.example.skyflowtracker.ui.LoginActivity
import com.example.skyflowtracker.utils.TokenManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit 客户端
 */
object RetrofitClient {
    
    // 服务器地址
    const val BASE_URL = BuildConfig.API_BASE_URL

    // Web 前端地址（用于拼接分享链接）
    const val WEB_BASE_URL = BuildConfig.WEB_BASE_URL
    // 防止重复跳转
    @Volatile
    private var isRedirecting = false
    
    // Token 请求拦截器
    private val tokenInterceptor = Interceptor { chain ->
        val original = chain.request()
        val token = TokenManager.getToken()
        
        val request = if (token != null) {
            original.newBuilder()
                .header("Authorization", token)
                .build()
        } else {
            original
        }
        
        chain.proceed(request)
    }

    // Token 过期响应拦截器
    private val tokenExpiredInterceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())

        // 只处理成功的 HTTP 响应（业务层面的 token 过期）
        if (response.isSuccessful) {
            val responseBody = response.peekBody(1024 * 1024)
            try {
                val json = JSONObject(responseBody.string())
                val code = json.optInt("code", -1)
                val message = json.optString("message", "")

                // 检测 token 过期：code=400 且 message 包含 "token"
                if (code == 400 && message.contains("token", ignoreCase = true)) {
                    handleTokenExpired()
                }
            } catch (_: Exception) {
                // JSON 解析失败，忽略
            }
        }

        response
    }
    
    // 日志拦截器
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        // Request URLs, bodies and headers can contain login credentials or tokens.
        level = HttpLoggingInterceptor.Level.NONE
        redactHeader("Authorization")
        redactHeader("Cookie")
        redactHeader("Set-Cookie")
    }
    
    // OkHttp 客户端
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(tokenInterceptor)
        .addInterceptor(loggingInterceptor)
        .addInterceptor(tokenExpiredInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    // Retrofit 实例
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    // API 服务
    val apiService: ApiService = retrofit.create(ApiService::class.java)

    /**
     * 处理 token 过期：清除 token，跳转登录页
     */
    private fun handleTokenExpired() {
        if (isRedirecting) return
        isRedirecting = true

        // 清除 token
        TokenManager.clearToken()

        // 在主线程执行 UI 操作
        Handler(Looper.getMainLooper()).post {
            val context = MyApplication.instance
            Toast.makeText(context, "登录已过期，请重新登录", Toast.LENGTH_SHORT).show()

            val intent = Intent(context, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)

            // 重置标志，允许下次跳转
            isRedirecting = false
        }
    }
}
