package com.example.skyflowtracker.api.model

import com.google.gson.annotations.SerializedName

/**
 * 设备信息
 */
data class Device(
    @SerializedName("sn")
    val sn: String,

    @SerializedName("userId")
    val userId: String?,

    @SerializedName("userName")
    val userName: String?,

    @SerializedName("model")
    val model: String?,

    @SerializedName("flightControllerSerialNumber")
    val flightControllerSerialNumber: String?,

    @SerializedName("accumulatedVoyage")
    val accumulatedVoyage: Long?,

    @SerializedName("flyTime")
    val flyTime: Long?,

    @SerializedName("lastOnlineTime")
    val lastOnlineTime: String?,

    @SerializedName("createTime")
    val createTime: String?,

    @SerializedName("modificationTime")
    val modificationTime: String?
)

/**
 * 设备列表响应
 */
data class DevicesListResponse(
    @SerializedName("devicesList")
    val devicesList: List<Device>?,

    @SerializedName("pagination")
    val pagination: Pagination?
)

/**
 * 分页信息
 */
data class Pagination(
    @SerializedName("page")
    val page: Int,

    @SerializedName("size")
    val size: Int,

    @SerializedName("total")
    val total: Int,

    @SerializedName("totalPages")
    val totalPages: Int
)

/**
 * 添加设备响应
 */
data class AddDevicesResponse(
    @SerializedName("successCount")
    val successCount: String?,

    @SerializedName("failCount")
    val failCount: String?,

    @SerializedName("errorMsg")
    val errorMsg: String?
)

/**
 * 修改设备请求
 */
data class UpdateDeviceRequest(
    @SerializedName("sn")
    val sn: String,

    @SerializedName("model")
    val model: String?,

    @SerializedName("flightControllerSerialNumber")
    val flightControllerSerialNumber: String?,

    @SerializedName("lastOnlineTime")
    val lastOnlineTime: String? = null
)

/**
 * 删除设备请求
 */
data class DeleteDeviceRequest(
    @SerializedName("sns")
    val sns: List<String>
)
