package com.example.skyflowtracker.api

import com.example.skyflowtracker.api.model.*
import okhttp3.MultipartBody
import retrofit2.http.*

/**
 * API 服务接口
 */
interface ApiService {
    
    /**
     * 获取图片验证码
     */
    @GET("users/getImgCode")
    suspend fun getImgCode(): ApiResponse<CaptchaResponse>
    
    /**
     * 获取邮箱验证码
     */
    @POST("users/getVerificationCode")
    suspend fun getVerificationCode(@Body request: EmailRequest): ApiResponse<String>
    
    /**
     * 登录
     */
    @POST("users/login")
    suspend fun login(@Body request: LoginRequest): ApiResponse<LoginResponse>
    
    /**
     * 注册
     */
    @POST("users/register")
    suspend fun register(@Body request: RegisterRequest): ApiResponse<String>
    
    /**
     * 忘记密码
     */
    @PUT("users/forgotPassword")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): ApiResponse<String>
    
    /**
     * 获取用户信息
     */
    @GET("users/getUserInfo")
    suspend fun getUserInfo(): ApiResponse<User>
    
    /**
     * 修改密码
     */
    @PUT("users/updatePassword")
    suspend fun updatePassword(@Body request: UpdatePasswordRequest): ApiResponse<String>
    
    /**
     * 修改用户信息
     */
    @PUT("users/updateUserInfo")
    suspend fun updateUserInfo(@Body request: UpdateUserInfoRequest): ApiResponse<String>
    
    /**
     * 修改头像
     */
    @Multipart
    @PUT("users/updateAvatar")
    suspend fun updateAvatar(@Part file: MultipartBody.Part): ApiResponse<String>
    
    /**
     * 退出登录
     */
    @POST("users/logout")
    suspend fun logout(): ApiResponse<String>

    /**
     * 获取设备列表
     */
    @GET("devices/getDevicesList")
    suspend fun getDevicesList(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 10,
        @Query("snCode") snCode: String? = null
    ): ApiResponse<DevicesListResponse>

    /**
     * 添加设备（SN码）
     */
    @Multipart
    @POST("devices/addDevices")
    suspend fun addDevicesBySn(
        @Part("snCode") snCode: okhttp3.RequestBody
    ): ApiResponse<AddDevicesResponse>

    /**
     * 自动绑定设备（通过 deviceData JSON）
     */
    @Multipart
    @POST("devices/addDevices")
    suspend fun addDevicesByData(
        @Part("deviceData") deviceData: okhttp3.RequestBody
    ): ApiResponse<AddDevicesResponse>

    /**
     * 添加设备（二维码图片）
     */
    @Multipart
    @POST("devices/addDevices")
    suspend fun addDevicesByQrCode(
        @Part file: MultipartBody.Part
    ): ApiResponse<AddDevicesResponse>

    /**
     * 检查设备是否绑定到当前用户
     */
    @GET("devices/checkDeviceBind")
    suspend fun checkDeviceBind(@Query("sn") sn: String): ApiResponse<Boolean>

    /**
     * 修改设备信息
     */
    @PUT("devices/updateDevice")
    suspend fun updateDevice(@Body request: UpdateDeviceRequest): ApiResponse<String>

    /**
     * 删除设备
     */
    @HTTP(method = "DELETE", path = "devices/deleteDevice", hasBody = true)
    suspend fun deleteDevice(@Body request: DeleteDeviceRequest): ApiResponse<String>

    /**
     * 飞行启动
     */
    @POST("flights/start")
    suspend fun startFlight(@Body request: FlightStartRequest): ApiResponse<FlightStartResponse>

    /**
     * 结束飞行
     */
    @PUT("flights/end")
    suspend fun endFlight(@Body request: FlightEndRequest): ApiResponse<String>

    /**
     * 更新飞行航线
     */
    @PUT("flights/updatePath")
    suspend fun updateFlightPath(@Body request: FlightUpdatePathRequest): ApiResponse<String>

    /**
     * 获取飞行记录列表
     */
    @GET("flights/getFlightsList")
    suspend fun getFlightsList(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): ApiResponse<FlightsListResponse>

    /**
     * 获取飞行记录详情
     */
    @GET("flights/getFlightDetail")
    suspend fun getFlightDetail(
        @Query("flightId") flightId: Long
    ): ApiResponse<FlightDetail>

    // ==================== AI 会话管理 ====================

    /**
     * 获取会话列表
     */
    @GET("ai/conversations")
    suspend fun getConversations(): ApiResponse<List<Conversation>>

    /**
     * 获取会话历史消息
     */
    @GET("ai/conversation/history")
    suspend fun getConversationHistory(
        @Query("conversationId") conversationId: String
    ): ApiResponse<List<ConversationMessage>>

    /**
     * 删除会话
     */
    @DELETE("ai/conversation")
    suspend fun deleteConversation(
        @Query("conversationId") conversationId: String
    ): ApiResponse<String>

    // ==================== 推理任务管理 ====================

    /**
     * 获取推理任务列表
     */
    @GET("inference/getInferenceTasksList")
    suspend fun getInferenceTasksList(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): ApiResponse<InferenceTasksListResponse>

    /**
     * 获取推理任务详情
     */
    @GET("inference/getInferenceTasksDetail")
    suspend fun getInferenceTasksDetail(
        @Query("taskId") taskId: Long
    ): ApiResponse<InferenceTaskDetail>

    /**
     * 删除推理任务
     */
    @HTTP(method = "DELETE", path = "inference/deleteInferenceTasks", hasBody = true)
    suspend fun deleteInferenceTasks(
        @Body request: DeleteInferenceTasksRequest
    ): ApiResponse<String>

    // ==================== 视频分享 ====================

    /**
     * 创建分享链接
     */
    @POST("share/create")
    suspend fun createShare(
        @Body request: CreateShareRequest
    ): ApiResponse<CreateShareResponse>

    // ==================== 航线任务 ====================

    /**
     * 获取航线任务列表
     */
    @GET("missions/getMissionsList")
    suspend fun getMissionsList(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 10
    ): ApiResponse<MissionsListResponse>

    /**
     * 获取航线任务详情
     */
    @GET("missions/getMissionDetail")
    suspend fun getMissionDetail(
        @Query("missionId") missionId: Long
    ): ApiResponse<MissionDetail>

    /**
     * 更新任务状态
     */
    @PUT("missions/updateStatus")
    suspend fun updateMissionStatus(
        @Body request: UpdateMissionStatusRequest
    ): ApiResponse<String>

    // ==================== 禁飞区同步 ====================

    /**
     * 批量同步禁飞区数据到后端
     */
    @POST("flyZones/sync")
    suspend fun syncFlyZones(
        @Body request: FlyZoneSyncRequest
    ): ApiResponse<FlyZoneSyncResponse>
}
