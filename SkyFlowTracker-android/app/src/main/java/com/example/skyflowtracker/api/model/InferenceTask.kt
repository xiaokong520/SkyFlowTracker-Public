package com.example.skyflowtracker.api.model

import com.google.gson.annotations.SerializedName

/**
 * 推理任务列表项
 */
data class InferenceTask(
    val id: Long,
    val flightId: Long?,
    val modelName: String?,
    val status: Int?,
    val totalDetections: Int?,
    val startTime: String?,
    val endTime: String?
)

/**
 * 推理任务列表响应
 */
data class InferenceTasksListResponse(
    val tasksList: List<InferenceTask>?,
    val pagination: InferenceTasksPagination?
)

/**
 * 推理任务分页信息
 */
data class InferenceTasksPagination(
    val page: Int?,
    val size: Int?,
    val total: Int?,
    val totalPage: Int?
)

/**
 * 推理任务详情
 */
data class InferenceTaskDetail(
    val id: Long,
    val flightId: Long?,
    val modelName: String?,
    val videoPath: String?,
    val resultData: InferenceResultData?,
    val status: Int?,
    val totalDetections: Int?,
    val startTime: String?,
    val endTime: String?
)

/**
 * 推理结果数据
 */
data class InferenceResultData(
    @SerializedName("frame_count") val frameCount: Int?,
    @SerializedName("duration") val duration: Double?,
    @SerializedName("class_counts") val classCounts: Map<String, Int>?
)

/**
 * 删除推理任务请求
 */
data class DeleteInferenceTasksRequest(val ids: List<Long>)
