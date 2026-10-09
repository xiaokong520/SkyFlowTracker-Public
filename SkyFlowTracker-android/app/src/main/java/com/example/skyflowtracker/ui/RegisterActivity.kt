package com.example.skyflowtracker.ui

import android.os.Bundle
import android.os.CountDownTimer
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.skyflowtracker.api.RetrofitClient
import com.example.skyflowtracker.api.model.EmailRequest
import com.example.skyflowtracker.api.model.RegisterRequest
import com.example.skyflowtracker.databinding.ActivityRegisterBinding
import com.example.skyflowtracker.utils.Validator
import com.example.skyflowtracker.utils.custom
import com.example.skyflowtracker.utils.email
import com.example.skyflowtracker.utils.isValidEmail
import com.example.skyflowtracker.utils.minLength
import com.example.skyflowtracker.utils.required
import com.example.skyflowtracker.utils.showToast
import kotlinx.coroutines.launch

/**
 * 注册页面
 */
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private var isLoading = false
    private var countDownTimer: CountDownTimer? = null
    private var isSendingCode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
    }

    private fun initViews() {
        // 返回按钮
        binding.btnBack.setOnClickListener {
            finish()
        }

        // 发送邮箱验证码
        binding.btnSendCode.setOnClickListener {
            sendEmailCode()
        }

        // 注册按钮
        binding.btnRegister.setOnClickListener {
            register()
        }

        // 去登录
        binding.tvLogin.setOnClickListener {
            finish()
        }
    }

    private fun sendEmailCode() {
        if (isSendingCode) return

        val email = binding.etEmail.text.toString().trim()

        if (email.isEmpty()) {
            showToast("请输入邮箱")
            return
        }
        if (!email.isValidEmail()) {
            showToast("邮箱格式不正确")
            return
        }

        isSendingCode = true
        binding.btnSendCode.isEnabled = false

        lifecycleScope.launch {
            try {
                val request = EmailRequest(email)
                val response = RetrofitClient.apiService.getVerificationCode(request)

                if (response.code == 1) {
                    showToast("验证码已发送到您的邮箱")
                    startCountDown()
                } else {
                    showToast(response.message ?: "发送失败")
                    binding.btnSendCode.isEnabled = true
                    isSendingCode = false
                }
            } catch (e: Exception) {
                showToast("发送失败: ${e.message}")
                binding.btnSendCode.isEnabled = true
                isSendingCode = false
            }
        }
    }

    private fun startCountDown() {
        countDownTimer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                binding.btnSendCode.text = "${seconds}秒后重试"
            }

            override fun onFinish() {
                binding.btnSendCode.text = "发送验证码"
                binding.btnSendCode.isEnabled = true
                isSendingCode = false
            }
        }.start()
    }

    private fun register() {
        if (isLoading) return

        val username = binding.etUsername.text.toString().trim()
        val nickname = binding.etNickname.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()
        val emailCode = binding.etEmailCode.text.toString().trim()

        // 参数验证
        val error = Validator.validate(
            username.required("请输入用户名"),
            nickname.required("请输入昵称"),
            email.required("请输入邮箱"),
            email.email("邮箱格式不正确"),
            password.required("请输入密码"),
            password.minLength(8, "密码长度至少8位"),
            confirmPassword.required("请确认密码"),
            confirmPassword.custom("两次密码不一致") { it != password },
            emailCode.required("请输入邮箱验证码")
        )
        
        if (error != null) {
            showToast(error)
            return
        }

        isLoading = true
        binding.btnRegister.isEnabled = false

        lifecycleScope.launch {
            try {
                val request = RegisterRequest(
                    userName = username,
                    nickName = nickname,
                    email = email,
                    password = password,
                    code = emailCode
                )

                val response = RetrofitClient.apiService.register(request)

                if (response.code == 1) {
                    showToast("注册成功，请等待管理员审核后登录")
                    // 延迟3秒后返回登录页面
                    binding.root.postDelayed({
                        finish()
                    }, 3000)
                } else {
                    showToast(response.message ?: "注册失败")
                }
            } catch (e: Exception) {
                showToast("注册失败: ${e.message}")
            } finally {
                isLoading = false
                binding.btnRegister.isEnabled = true
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
