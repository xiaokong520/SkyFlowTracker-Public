package com.example.skyflowtracker

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.ContextCompat
import com.amap.api.maps.MapsInitializer
import com.example.skyflowtracker.utils.TokenManager
import dji.v5.ux.mapkit.amap.utils.FileLogger
import dji.v5.ux.mapkit.core.Mapkit
import dji.v5.ux.util.TTSHelper

class MyApplication: Application() {

    private val TAG = this::class.simpleName

    companion object {
        lateinit var instance: MyApplication
            private set
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        // 在调用 install 前，请勿调用任何 MSDK 相关接口
        // MSDK v5.10.0 及之后的版本使用 com.cySdkyc.clx.Helper.install(this)
        com.cySdkyc.clx.Helper.install(this)
        Log.i(TAG, "Helper.install completed in attachBaseContext")

    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 初始化 TokenManager
        TokenManager.init(this)
        Log.i(TAG, "TokenManager 初始化完成")

        // 初始化文件日志
        FileLogger.init(this)
        FileLogger.i("MyApplication", "应用启动，日志文件路径: " + FileLogger.getLogFilePath())

        // 不要在这里初始化 SDKManager！
        // 根据 issue #623 的解决方案，应该在 SplashActivity 中延迟初始化
        Log.i(TAG, "Application创建成功，SDK初始化将在SplashActivity中延迟进行")
        //设置使用高德地图
        Mapkit.mapProvider(Mapkit.MapProviderConstant.AMAP_PROVIDER)
        Log.i(TAG, "设置地图提供商为高德地图: ${Mapkit.getMapProvider()}")
        FileLogger.i("MyApplication", "地图提供商设置为: 高德地图")
        //高德地图隐私合规设置
        MapsInitializer.updatePrivacyShow(this,true,true)
        MapsInitializer.updatePrivacyAgree(this,true)
        //设置是否是中国大陆(用于坐标纠偏)
        Mapkit.inMainlandChina(true)
        Mapkit.inHongKong(false)
        Mapkit.inMacau(false)
        //初始化TTS
        TTSHelper.getInstance(this)

    }

    override fun onTerminate() {
        super.onTerminate()
        //释放TTS资源
        TTSHelper.getInstance(this).shutDown()
    }


}
