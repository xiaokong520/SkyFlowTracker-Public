package com.example.skyflowtracker.api.model

import com.google.gson.annotations.SerializedName

/**
 * 用户信息
 */
data class User(
    @SerializedName("userId")
    val userId: String,
    
    @SerializedName("userName")
    val userName: String,
    
    @SerializedName("nickName")
    val nickName: String?,
    
    @SerializedName("email")
    val email: String,
    
    @SerializedName("avatar")
    val avatar: String?,
    
    @SerializedName("role")
    val role: Int, // 0=普通用户, 1=管理员
    
    @SerializedName("status")
    val status: Int, // 0=封禁, 1=正常, 2=待审核
    
    @SerializedName("ip")
    val ip: String?,
    
    @SerializedName("createTime")
    val createTime: String?,
    
    @SerializedName("lastLoginTime")
    val lastLoginTime: String?
)

/**
 * 登录请求
 */
data class LoginRequest(
    @SerializedName("userName")
    val userName: String? = null,
    
    @SerializedName("email")
    val email: String? = null,
    
    @SerializedName("password")
    val password: String,
    
    @SerializedName("imgCode")
    val imgCode: String,
    
    @SerializedName("imgId")
    val imgId: String
)

/**
 * 登录响应
 */
data class LoginResponse(
    @SerializedName("token")
    val token: String,
    
    @SerializedName("role")
    val role: Int
)

/**
 * 注册请求
 */
data class RegisterRequest(
    @SerializedName("userName")
    val userName: String,
    
    @SerializedName("nickName")
    val nickName: String,
    
    @SerializedName("email")
    val email: String,
    
    @SerializedName("password")
    val password: String,
    
    @SerializedName("code")
    val code: String
)

/**
 * 忘记密码请求
 */
data class ForgotPasswordRequest(
    @SerializedName("email")
    val email: String,
    
    @SerializedName("password")
    val password: String,
    
    @SerializedName("code")
    val code: String
)

/**
 * 图片验证码响应
 */
data class CaptchaResponse(
    @SerializedName("base64Image")
    val base64Image: String,
    
    @SerializedName("imgId")
    val imgId: String
)

/**
 * 修改密码请求
 */
data class UpdatePasswordRequest(
    @SerializedName("password")
    val password: String
)

/**
 * 修改用户信息请求
 */
data class UpdateUserInfoRequest(
    @SerializedName("nickName")
    val nickName: String
)

/**
 * 邮箱验证码请求
 */
data class EmailRequest(
    @SerializedName("email")
    val email: String
)
