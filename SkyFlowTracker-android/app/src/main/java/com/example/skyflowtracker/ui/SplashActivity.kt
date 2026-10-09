package com.example.skyflowtracker.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.skyflowtracker.R
import com.example.skyflowtracker.api.RetrofitClient
import com.example.skyflowtracker.utils.SDKInitManager
import com.example.skyflowtracker.utils.TokenManager
import com.example.skyflowtracker.utils.showToast
import kotlinx.coroutines.launch

/**
 * 启动页：权限检查 → SDK 初始化 → Token 验证 → 跳转
 */
class SplashActivity : AppCompatActivity() {

    private val PERMISSION_REQUEST_CODE = 1001
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private lateinit var tvStatus: TextView
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 检查是否从桌面重启
        if (!isTaskRoot && intent.hasCategory(Intent.CATEGORY_LAUNCHER)
            && Intent.ACTION_MAIN == intent.action
        ) {
            finish()
            return
        }

        setContentView(R.layout.activity_splash)
        tvStatus = findViewById(R.id.tvStatus)

        // 设置状态栏颜色
        window.statusBarColor = getColor(R.color.primary)

        // 第一步：检查权限
        checkPermissions()
    }

    private fun checkPermissions() {
        tvStatus.text = "检查权限..."
        val needed = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            // 权限已有，延迟 1s 初始化 SDK（加固库需要）
            handler.postDelayed({ startSDKInit() }, 1000)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // 无论是否全部授权，都继续初始化
            handler.postDelayed({ startSDKInit() }, 1000)
        }
    }

    private fun startSDKInit() {
        tvStatus.text = "正在初始化 SDK..."
        SDKInitManager.initSDK(
            context = this,
            onSuccess = {
                runOnUiThread {
                    tvStatus.text = "SDK 初始化完成"
                    checkTokenAndNavigate()
                }
            },
            onFailure = { errorMsg ->
                runOnUiThread {
                    tvStatus.text = "SDK 初始化失败: $errorMsg"
                    showToast("SDK 初始化失败，部分功能可能不可用")
                    // 即使 SDK 初始化失败也继续进入应用
                    handler.postDelayed({ checkTokenAndNavigate() }, 2000)
                }
            },
            onProgress = { msg ->
                runOnUiThread { tvStatus.text = msg }
            }
        )
    }

    /**
     * 验证 token，决定跳转登录页还是主页
     */
    private fun checkTokenAndNavigate() {
        if (!TokenManager.hasToken()) {
            goToLogin()
            return
        }

        tvStatus.text = "验证登录状态..."
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getUserInfo()
                if (response.code == 1) {
                    goToHome()
                } else {
                    TokenManager.clearToken()
                    goToLogin()
                }
            } catch (e: Exception) {
                TokenManager.clearToken()
                goToLogin()
            }
        }
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun goToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
