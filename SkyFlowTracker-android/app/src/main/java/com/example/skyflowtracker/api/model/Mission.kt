package com.example.skyflowtracker.api.model

/**
 * 航线任务相关数据模型
 */

/** 任务列表项 */
data class Mission(
    val id: Long,
    val name: String?,
    val description: String?,
    val status: Int?,
    val creatorId: String?,
    val creatorName: String?,
    val assigneeId: String?,
    val assigneeName: String?,
    val totalDistance: Double?,
    val estimatedTime: Int?,
    val createTime: String?,
    val modificationTime: String?
)

/** 任务列表响应 */
data class MissionsListResponse(
    val missionsList: List<Mission>?,
    val pagination: MissionsPagination?
)

/** 任务列表分页 */
data class MissionsPagination(
    val page: Int,
    val size: Int,
    val total: Int,
    val totalPage: Int
)

/** 任务详情（含航点数据） */
data class MissionDetail(
    val id: Long,
    val name: String?,
    val description: String?,
    val waypoints: List<Waypoint>?,
    val creatorId: String?,
    val assigneeId: String?,
    val status: Int?,
    val totalDistance: Double?,
    val estimatedTime: Int?,
    val createTime: String?,
    val modificationTime: String?
)

/** 航点 */
data class Waypoint(
    val seq: Int?,
    val lat: Double,
    val lng: Double,
    val alt: Double?,
    val speed: Double?,
    val heading: Double?,
    val hoverTime: Int?
)

/** 更新任务状态请求 */
data class UpdateMissionStatusRequest(
    val missionId: Long,
    val status: Int
)
