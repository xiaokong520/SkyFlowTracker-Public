package com.example.skyflowtracker.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.lifecycle.lifecycleScope
import com.example.skyflowtracker.R
import com.example.skyflowtracker.api.RetrofitClient
import com.example.skyflowtracker.api.model.LoginRequest
import com.example.skyflowtracker.databinding.ActivityLoginBinding
import com.example.skyflowtracker.utils.SDKInitManager
import com.example.skyflowtracker.utils.TokenManager
import com.example.skyflowtracker.utils.Validator
import com.example.skyflowtracker.utils.custom
import com.example.skyflowtracker.utils.required
import com.example.skyflowtracker.utils.showToast
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

/**
 * 登录页面
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private var currentLoginType = 0 // 0=用户名登录, 1=邮箱登录
    private var imgId: String? = null
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initViews()
        loadCaptcha()
    }

    private fun initViews() {
        // Tab 切换
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentLoginType = tab?.position ?: 0
                updateLoginType()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // 验证码图片点击刷新
        binding.ivCaptcha.setOnClickListener { loadCaptcha() }

        // 忘记密码
        binding.tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        // 注册
        binding.tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // 登录按钮
        binding.btnLogin.setOnClickListener { login() }
    }

    private fun updateLoginType() {
        val constraintLayout = binding.constraintLayout
        val constraintSet = androidx.constraintlayout.widget.ConstraintSet()
        constraintSet.clone(constraintLayout)

        if (currentLoginType == 0) {
            constraintSet.setVisibility(R.id.tilUsername, View.VISIBLE)
            constraintSet.setVisibility(R.id.tilEmail, View.GONE)
            constraintSet.clear(R.id.tilPassword, androidx.constraintlayout.widget.ConstraintSet.TOP)
            constraintSet.connect(
                R.id.tilPassword, androidx.constraintlayout.widget.ConstraintSet.TOP,
                R.id.tilUsername, androidx.constraintlayout.widget.ConstraintSet.BOTTOM,
                resources.getDimensionPixelSize(R.dimen.spacing_md)
            )
        } else {
            constraintSet.setVisibility(R.id.tilUsername, View.GONE)
            constraintSet.setVisibility(R.id.tilEmail, View.VISIBLE)
            constraintSet.clear(R.id.tilPassword, androidx.constraintlayout.widget.ConstraintSet.TOP)
            constraintSet.connect(
                R.id.tilPassword, androidx.constraintlayout.widget.ConstraintSet.TOP,
                R.id.tilEmail, androidx.constraintlayout.widget.ConstraintSet.BOTTOM,
                resources.getDimensionPixelSize(R.dimen.spacing_md)
            )
        }
        constraintSet.applyTo(constraintLayout)
    }

    private fun loadCaptcha() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getImgCode()
                if (response.code == 1 && response.data != null) {
                    imgId = response.data.imgId
                    val imageBytes = Base64.decode(response.data.base64Image, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    binding.ivCaptcha.setImageBitmap(bitmap)
                } else {
                    showToast(response.message ?: "获取验证码失败")
                }
            } catch (e: Exception) {
                showToast("获取验证码失败: ${e.message}")
            }
        }
    }

    private fun login() {
        if (isLoading) return

        val username = if (currentLoginType == 0) {
            binding.etUsername.text.toString().trim()
        } else {
            binding.etEmail.text.toString().trim()
        }
        val password = binding.etPassword.text.toString().trim()
        val captcha = binding.etCaptcha.text.toString().trim()
        val rememberMe = binding.cbRememberMe.isChecked

        val error = Validator.validate(
            username.required(if (currentLoginType == 0) "请输入用户名" else "请输入邮箱"),
            password.required("请输入密码"),
            captcha.required("请输入验证码"),
            imgId.custom("验证码未加载，请刷新") { it == null }
        )
        if (error != null) { showToast(error); return }

        isLoading = true
        binding.btnLogin.isEnabled = false

        lifecycleScope.launch {
            try {
                val request = LoginRequest(
                    userName = if (currentLoginType == 0) username else null,
                    email = if (currentLoginType == 1) username else null,
                    password = password,
                    imgCode = captcha,
                    imgId = imgId!!
                )
                val response = RetrofitClient.apiService.login(request)

                if (response.code == 1 && response.data != null) {
                    TokenManager.saveToken(response.data.token, rememberMe)
                    showToast("登录成功，欢迎回来！")
                    // 登录成功后检查 SDK 是否已初始化
                    initSDKAndNavigate()
                } else {
                    val message = response.message ?: "登录失败"
                    when {
                        message.contains("审核") -> showToast("您的账号正在审核中，请耐心等待管理员审核通过后再登录")
                        message.contains("封禁") -> showToast("您的账号已被封禁，如有疑问请联系管理员")
                        else -> showToast(message)
                    }
                    loadCaptcha()
                }
            } catch (e: Exception) {
                showToast("登录失败: ${e.message}")
                loadCaptcha()
            } finally {
                isLoading = false
                binding.btnLogin.isEnabled = true
            }
        }
    }

    /**
     * 登录成功后：如果 SDK 已初始化则直接跳转，否则显示初始化对话框
     */
    private fun initSDKAndNavigate() {
        if (SDKInitManager.isReady()) {
            goToHome()
            return
        }

        // 显示初始化加载对话框
        val dialogView = layoutInflater.inflate(R.layout.dialog_sdk_init, null)
        val tvInitStatus = dialogView.findViewById<android.widget.TextView>(R.id.tvInitStatus)

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        dialog.show()

        // 延迟 1s 后开始初始化（加固库需要）
        Handler(Looper.getMainLooper()).postDelayed({
            SDKInitManager.initSDK(
                context = this,
                onSuccess = {
                    runOnUiThread {
                        dialog.dismiss()
                        goToHome()
                    }
                },
                onFailure = { errorMsg ->
                    runOnUiThread {
                        tvInitStatus.text = "SDK 初始化失败: $errorMsg"
                        showToast("SDK 初始化失败，部分功能可能不可用")
                        // 失败也允许进入主页
                        Handler(Looper.getMainLooper()).postDelayed({
                            dialog.dismiss()
                            goToHome()
                        }, 2000)
                    }
                },
                onProgress = { msg ->
                    runOnUiThread { tvInitStatus.text = msg }
                }
            )
        }, 1000)
    }

    private fun goToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
