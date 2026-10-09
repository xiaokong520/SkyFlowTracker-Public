package com.example.skyflowtracker.ui

import android.os.Bundle
import android.os.CountDownTimer
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.skyflowtracker.api.RetrofitClient
import com.example.skyflowtracker.api.model.EmailRequest
import com.example.skyflowtracker.api.model.ForgotPasswordRequest
import com.example.skyflowtracker.databinding.ActivityForgotPasswordBinding
import com.example.skyflowtracker.utils.Validator
import com.example.skyflowtracker.utils.custom
import com.example.skyflowtracker.utils.email
import com.example.skyflowtracker.utils.isValidEmail
import com.example.skyflowtracker.utils.minLength
import com.example.skyflowtracker.utils.required
import com.example.skyflowtracker.utils.showToast
import kotlinx.coroutines.launch

/**
 * 忘记密码页面
 */
class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForgotPasswordBinding
    private var isLoading = false
    private var countDownTimer: CountDownTimer? = null
    private var isSendingCode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
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

        // 重置密码按钮
        binding.btnResetPassword.setOnClickListener {
            resetPassword()
        }

        // 返回登录
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

    private fun resetPassword() {
        if (isLoading) return

        val email = binding.etEmail.text.toString().trim()
        val newPassword = binding.etNewPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()
        val emailCode = binding.etEmailCode.text.toString().trim()

        // 参数验证
        val error = Validator.validate(
            email.required("请输入邮箱"),
            email.email("邮箱格式不正确"),
            newPassword.required("请输入新密码"),
            newPassword.minLength(6, "密码长度至少6位"),
            confirmPassword.required("请确认密码"),
            confirmPassword.custom("两次密码不一致") { it != newPassword },
            emailCode.required("请输入邮箱验证码")
        )
        
        if (error != null) {
            showToast(error)
            return
        }

        isLoading = true
        binding.btnResetPassword.isEnabled = false

        lifecycleScope.launch {
            try {
                val request = ForgotPasswordRequest(
                    email = email,
                    password = newPassword,
                    code = emailCode
                )

                val response = RetrofitClient.apiService.forgotPassword(request)

                if (response.code == 1) {
                    showToast("密码重置成功，请使用新密码登录")
                    // 延迟2秒后返回登录页面
                    binding.root.postDelayed({
                        finish()
                    }, 2000)
                } else {
                    showToast(response.message ?: "重置失败")
                }
            } catch (e: Exception) {
                showToast("重置失败: ${e.message}")
            } finally {
                isLoading = false
                binding.btnResetPassword.isEnabled = true
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
