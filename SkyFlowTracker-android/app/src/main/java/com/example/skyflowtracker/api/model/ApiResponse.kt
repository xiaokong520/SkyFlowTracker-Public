package com.example.skyflowtracker.api.model

import com.google.gson.annotations.SerializedName

/**
 * API 统一响应格式
 */
data class ApiResponse<T>(
    @SerializedName("code")
    val code: Int,
    
    @SerializedName("message")
    val message: String?,
    
    @SerializedName("data")
    val data: T?
) {
    fun isSuccess(): Boolean = code == 1
}
