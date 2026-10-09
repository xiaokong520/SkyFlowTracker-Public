package com.example.skyflowtracker.api.model

/**
 * 创建分享请求
 */
data class CreateShareRequest(
    val taskId: Long,
    val expireHours: Int
)

/**
 * 创建分享响应
 */
data class CreateShareResponse(
    val shareCode: String,
    val shareId: Long,
    val expireTime: String
)
