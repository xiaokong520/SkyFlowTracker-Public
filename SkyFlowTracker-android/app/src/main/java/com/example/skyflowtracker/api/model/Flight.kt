package com.example.skyflowtracker.api.model

import com.google.gson.annotations.SerializedName

// ==================== 飞行操作请求模型 ====================

/**
 * 飞行启动请求
 */
data class FlightStartRequest(
    val sn: String,
    val home_latitude: Double?,
    val home_longitude: Double?
)

/**
 * 飞行启动响应（返回 flightId）
 */
data class FlightStartResponse(
    val flightId: Long
)

/**
 * 结束飞行请求
 */
data class FlightEndRequest(
    val flightId: Long,
    val maxAltitude: Double?,
    val maxSpeed: Double?,
    val distance: Double?,
    val status: Int? // 1=已完成 2=异常终止
)

/**
 * 更新飞行航线请求
 */
data class FlightUpdatePathRequest(
    val flightId: Long,
    val points: List<PathPoint>
)

/**
 * 航线坐标点
 */
data class PathPoint(
    val lat: Double,   // 纬度
    val lng: Double,   // 经度
    val alt: Double?,  // 高度
    val speed: Double?, // 速度
    val heading: Double?, // 航向角
    val ts: Long       // 时间戳（毫秒）
)

// ==================== 飞行记录列表/详情模型 ====================

/**
 * 飞行记录信息
 */
data class Flight(
    @SerializedName("id")
    val id: Long,

    @SerializedName("userId")
    val userId: String?,

    @SerializedName("userName")
    val userName: String?,

    @SerializedName("sn")
    val sn: String?,

    @SerializedName("startTime")
    val startTime: String?,

    @SerializedName("endTime")
    val endTime: String?,

    @SerializedName("duration")
    val duration: Int?,

    @SerializedName("maxAltitude")
    val maxAltitude: Double?,

    @SerializedName("maxSpeed")
    val maxSpeed: Double?,

    @SerializedName("distance")
    val distance: Double?,

    @SerializedName("status")
    val status: Int?,

    @SerializedName("homeLatitude")
    val homeLatitude: Double?,

    @SerializedName("homeLongitude")
    val homeLongitude: Double?,

    @SerializedName("createTime")
    val createTime: String?,

    @SerializedName("modificationTime")
    val modificationTime: String?
)

/**
 * 飞行记录列表响应
 */
data class FlightsListResponse(
    @SerializedName("flightsList")
    val flightsList: List<Flight>?,

    @SerializedName("pagination")
    val pagination: FlightsPagination?
)

/**
 * 飞行记录分页信息
 */
data class FlightsPagination(
    @SerializedName("page")
    val page: Int,

    @SerializedName("size")
    val size: Int,

    @SerializedName("total")
    val total: Int,

    @SerializedName("totalPage")
    val totalPage: Int
)

/**
 * 飞行记录详情响应
 */
data class FlightDetail(
    @SerializedName("id")
    val id: Long,

    @SerializedName("userId")
    val userId: String?,

    @SerializedName("sn")
    val sn: String?,

    @SerializedName("flightPath")
    val flightPath: List<PathPoint>?,

    @SerializedName("homeLatitude")
    val homeLatitude: Double?,

    @SerializedName("homeLongitude")
    val homeLongitude: Double?,

    @SerializedName("startTime")
    val startTime: String?,

    @SerializedName("endTime")
    val endTime: String?,

    @SerializedName("duration")
    val duration: Int?,

    @SerializedName("maxAltitude")
    val maxAltitude: Double?,

    @SerializedName("maxSpeed")
    val maxSpeed: Double?,

    @SerializedName("distance")
    val distance: Double?,

    @SerializedName("status")
    val status: Int?
)
