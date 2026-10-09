package com.example.skyflowtracker.utils

import android.content.Context
import android.util.Log
import dji.v5.common.error.IDJIError
import dji.v5.common.register.DJISDKInitEvent
import dji.v5.manager.SDKManager
import dji.v5.manager.interfaces.SDKManagerCallback

/**
 * DJI SDK 初始化管理器（单例）
 * 统一管理 SDK 的初始化和注册流程
 */
object SDKInitManager {

    private const val TAG = "SDKInitManager"

    // SDK 是否已注册成功
    @Volatile
    var isRegistered = false
        private set

    // 是否正在初始化中
    @Volatile
    private var isInitializing = false

    /**
     * 检查 SDK 是否已经初始化完成
     */
    fun isReady(): Boolean {
        return try {
            SDKManager.getInstance().isRegistered
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 初始化 SDK
     * @param context Activity context
     * @param onSuccess SDK 注册成功回调
     * @param onFailure SDK 注册失败回调
     * @param onProgress 初始化进度回调
     */
    fun initSDK(
        context: Context,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
        onProgress: ((String) -> Unit)? = null
    ) {
        // 已经注册成功，直接回调
        if (isReady()) {
            Log.i(TAG, "SDK 已注册，直接回调成功")
            isRegistered = true
            onSuccess()
            return
        }

        // 防止重复初始化
        if (isInitializing) {
            Log.w(TAG, "SDK 正在初始化中，忽略重复调用")
            return
        }

        isInitializing = true
        Log.i(TAG, "开始 SDK 初始化...")
        onProgress?.invoke("正在初始化 SDK...")

        SDKManager.getInstance().init(context, object : SDKManagerCallback {
            override fun onInitProcess(event: DJISDKInitEvent?, totalProcess: Int) {
                Log.i(TAG, "onInitProcess: event=$event, totalProcess=$totalProcess")
                onProgress?.invoke("SDK 初始化中...")
                if (event == DJISDKInitEvent.INITIALIZE_COMPLETE) {
                    Log.i(TAG, "SDK 初始化完成，开始注册应用...")
                    onProgress?.invoke("正在注册应用...")
                    SDKManager.getInstance().registerApp()
                }
            }

            override fun onRegisterSuccess() {
                Log.i(TAG, "应用注册成功")
                isRegistered = true
                isInitializing = false
                onSuccess()
            }

            override fun onRegisterFailure(error: IDJIError?) {
                val msg = error?.description() ?: "未知错误"
                Log.e(TAG, "应用注册失败: $msg")
                isInitializing = false
                onFailure(msg)
            }

            override fun onProductConnect(productId: Int) {
                Log.i(TAG, "设备连接: productId=$productId")
            }

            override fun onProductDisconnect(productId: Int) {
                Log.i(TAG, "设备断开: productId=$productId")
            }

            override fun onProductChanged(productId: Int) {
                Log.i(TAG, "设备变更: productId=$productId")
            }

            override fun onDatabaseDownloadProgress(current: Long, total: Long) {
                val progress = if (total > 0) (current * 100 / total) else 0
                Log.i(TAG, "数据库下载: $progress%")
                onProgress?.invoke("下载数据库 $progress%...")
            }
        })
    }
}
