package com.example.skyflowtracker.ui

import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.Transformation
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.constraintlayout.widget.ConstraintLayout
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.Polyline
import com.amap.api.maps.model.PolylineOptions
import com.example.skyflowtracker.camera.CustomCameraManager
import com.example.skyflowtracker.flysafe.AMapFlySafeHelper
import com.example.skyflowtracker.flysafe.FlySafeViewModel
import com.example.skyflowtracker.flysafe.FlyZoneSyncManager
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.key.GimbalKey
import dji.sdk.keyvalue.key.ProductKey
import dji.sdk.keyvalue.key.RemoteControllerKey
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.sdk.keyvalue.value.gimbal.GimbalAngleRotation
import dji.sdk.keyvalue.value.gimbal.GimbalAngleRotationMode
import dji.sdk.keyvalue.value.common.Attitude
import dji.sdk.keyvalue.value.common.EmptyMsg
import dji.sdk.keyvalue.value.common.LocationCoordinate2D
import dji.sdk.keyvalue.value.flightcontroller.FlightMode
import dji.sdk.keyvalue.value.product.ProductType
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.manager.KeyManager
import dji.v5.ux.core.widget.altitude.AGLAltitudeWidget
import dji.v5.ux.core.widget.distancehome.DistanceHomeWidget
import dji.v5.ux.core.widget.horizontalvelocity.HorizontalVelocityWidget
import dji.v5.ux.core.widget.verticalvelocity.VerticalVelocityWidget
import dji.v5.ux.sample.showcase.defaultlayout.DefaultLayoutActivity
import dji.v5.ux.util.TTSHelper
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.util.concurrent.TimeUnit
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.example.skyflowtracker.api.RetrofitClient
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.skyflowtracker.R
import com.example.skyflowtracker.api.model.FlightStartRequest
import com.example.skyflowtracker.api.model.FlightEndRequest
import com.example.skyflowtracker.api.model.FlightUpdatePathRequest
import com.example.skyflowtracker.api.model.PathPoint
import com.example.skyflowtracker.api.model.UpdateDeviceRequest
import com.example.skyflowtracker.api.model.Waypoint
import com.example.skyflowtracker.utils.CoordinateConverter
import dji.sdk.keyvalue.key.KeyTools
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.math.max
import com.example.skyflowtracker.websocket.VideoStreamWebSocket
import dji.sdk.keyvalue.key.CameraKey
import dji.v5.common.video.channel.VideoChannelType
import dji.v5.common.video.interfaces.IVideoFrame
import dji.v5.common.video.stream.StreamSource
import dji.v5.manager.datacenter.MediaDataCenter
import java.io.ByteArrayOutputStream
import dji.v5.ux.core.util.ViewUtil

// 飞行界面
class CustomDefaultLayoutActivity : DefaultLayoutActivity() {

    private val TAG = "CustomDefaultLayout"
    private lateinit var flightDataContainer: LinearLayout
    private val rxDisposables = CompositeDisposable()

    // 地图是否为小窗模式（默认地图小窗，FPV全屏）
    private var isMapMini = true

    // FPV小窗时的触摸覆盖层（拦截触摸事件，防止 FPVInteractionWidget 消费）
    private lateinit var fpvTouchOverlay: View

    // 小窗尺寸和边距（dp转px后的值）
    private var miniWidth = 0
    private var miniHeight = 0
    private var miniMargin = 0
    // 设备屏幕尺寸（用于 ResizeAnimation）
    private var deviceWidth = 0
    private var deviceHeight = 0

    // 限飞区相关
    private lateinit var flySafeHelper: AMapFlySafeHelper
    private val flySafeViewModel: FlySafeViewModel by viewModels()
    private lateinit var flyZoneSyncManager: FlyZoneSyncManager

    // 航线任务叠加相关
    private var missionId: Long = -1
    private var missionRoutePolyline: Polyline? = null
    private val missionRouteMarkers = mutableListOf<Marker>()
    private var aMapInstance: AMap? = null

    // 相机管理器
    private val cameraManager = CustomCameraManager()

    // 自动绑定设备：防止重复绑定
    private var hasAttemptedBind = false

    // 竖拍模式（云台 roll 轴旋转 90°）
    private var isPortraitMode = false
    private lateinit var portraitToggleBtn: ImageButton

    // 遥控器电池低电量语音提示
    private var lastRcBatteryWarningLevel = 0 // 0=正常, 1=低电量(<30%), 2=严重低电量(<15%)

    // ==================== 飞行记录相关 ====================
    // 当前飞行记录 ID（startFlight 返回）
    private var currentFlightId: Long? = null
    // 飞行是否正在进行
    private var isFlightRecording = false
    // 航线坐标点缓冲区（定时批量上传）
    private val pathPointsBuffer = mutableListOf<PathPoint>()
    // 航线上传定时器
    private var pathUploadDisposable: io.reactivex.rxjava3.disposables.Disposable? = null
    // 飞行过程中记录的最大高度、最大速度、累计距离
    private var flightMaxAltitude = 0.0
    private var flightMaxSpeed = 0.0
    private var flightDistance = 0.0
    // 上一个坐标点（用于计算距离）
    private var lastLat: Double? = null
    private var lastLng: Double? = null
    // 当前飞机 SN（自动绑定时获取）
    private var currentAircraftSn: String? = null

    // WebSocket 视频流推送
    private var videoStreamWs: VideoStreamWebSocket? = null
    private var frameUploadDisposable: io.reactivex.rxjava3.disposables.Disposable? = null
    // 视频帧监听器（持久化，避免重复添加/移除）
    private var cameraFrameListener: dji.v5.manager.interfaces.ICameraStreamManager.CameraFrameListener? = null
    private var isFrameListenerAdded = false
    // 帧率控制：最小发送间隔（毫秒），约 20 fps
    private val FRAME_INTERVAL_MS = 50L
    private var lastFrameSentTime = 0L

    // 演示模式
    private val demoMode = java.util.concurrent.atomic.AtomicBoolean(false)
    private var demoVideoPlayer: com.example.skyflowtracker.utils.DemoVideoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 隐藏系统状态栏（全屏沉浸式）
        hideSystemUI()

        // 读取航线任务 ID（从 MissionFragment 跳转携带）
        missionId = intent.getLongExtra("mission_id", -1)

        initDimensions()
        addFightDataWidgets()
        hideCameraName()
        initCameraFocus()
        initFlySafeFeature()
        observePanelVisibility()

        // 设置地图和FPV的点击互换
        window.decorView.post {
            setupMapFpvSwitch()
        }

        // 竖拍/横拍切换按钮
        initPortraitToggle()

        // 自动绑定设备
        initAutoBindDevice()

        // 遥控器电池低电量语音提示
        initRcBatteryMonitor()

        // 飞行记录：监听飞行状态
        initFlightRecordMonitor()
    }

    // 计算小窗尺寸和屏幕尺寸
    private fun initDimensions() {
        val density = resources.displayMetrics.density
        miniWidth = (150 * density).toInt()
        miniHeight = (100 * density).toInt()
        miniMargin = (12 * density).toInt()

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val point = Point()
        wm.defaultDisplay.getRealSize(point)
        deviceWidth = point.x
        deviceHeight = point.y
    }

    // ==================== 地图/FPV 互换逻辑 ====================

    // 设置地图和FPV的点击互换监听
    private fun setupMapFpvSwitch() {
        // 地图点击监听：点击小窗地图 → 地图全屏，FPV缩小
        mapWidget.post {
            val djiMap = mapWidget.map
            if (djiMap != null) {
                djiMap.setOnMapClickListener {
                    onViewClick(mapWidget)
                }
                Log.i(TAG, "地图点击监听器设置成功")
            } else {
                mapWidget.postDelayed({ setupMapFpvSwitch() }, 500)
            }
        }

        // FPV点击监听：用透明覆盖层拦截小窗FPV的触摸事件
        // 不能用 setOnClickListener/setOnTouchListener，因为子 View FPVInteractionWidget
        // 会在 dispatchTouchEvent 阶段消费触摸事件，父 View 的监听器收不到
        fpvTouchOverlay = View(this).apply {
            isClickable = true
            setOnClickListener {
                onViewClick(fpvParentView)
            }
        }
        // 初始状态：地图小窗，FPV全屏，不需要覆盖层
    }

    // 核心切换逻辑（参考官方 CompleteWidgetActivity.onViewClick）
    private fun onViewClick(view: View) {
        if (view == fpvParentView && !isMapMini) {
            // 点击了小窗FPV → FPV恢复全屏，地图缩回小窗
            Log.i(TAG, "切换: FPV全屏，地图小窗")

            // 移除FPV上的触摸覆盖层，恢复正常交互（对焦/测光）
            fpvParentView.removeView(fpvTouchOverlay)

            // FPV恢复全屏（对应官方 resizeFPVWidget(MATCH_PARENT, MATCH_PARENT, 0, 0)）
            resizeFpvHolder(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                0
            )

            // 地图缩小到小窗（带动画，对应官方 ResizeAnimation）
            val anim = ResizeAnimation(mapWidget, deviceWidth, deviceHeight, miniWidth, miniHeight, miniMargin)
            anim.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}
                override fun onAnimationRepeat(animation: Animation?) {}
                override fun onAnimationEnd(animation: Animation?) {
                    // 动画结束后设置地图为小窗约束
                    setMapConstraints(false)
                }
            })
            mapWidget.startAnimation(anim)

            isMapMini = true

            // 恢复顶部栏和飞行数据
            topBarPanel?.let { it.visibility = View.VISIBLE }
            flightDataContainer.visibility = View.VISIBLE
            portraitToggleBtn.visibility = View.VISIBLE

        } else if (view == mapWidget && isMapMini) {
            // 点击了小窗地图 → 地图全屏，FPV缩到右下角小窗
            Log.i(TAG, "切换: 地图全屏，FPV小窗")

            // 隐藏面板（对应官方 hidePanels()）
            hidePanels()

            // FPV缩小到右下角小窗（对应官方 resizeFPVWidget(width, height, margin, 12)）
            resizeFpvHolder(miniWidth, miniHeight, miniMargin)

            // 在FPV上添加触摸覆盖层，拦截触摸事件防止 FPVInteractionWidget 消费
            fpvParentView.addView(fpvTouchOverlay, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))

            // 地图放大到全屏（带动画）
            // 先设置全屏约束，让动画在正确的约束下进行
            setMapConstraints(true)
            val anim = ResizeAnimation(mapWidget, miniWidth, miniHeight, deviceWidth, deviceHeight, 0)
            mapWidget.startAnimation(anim)

            isMapMini = false

            // 隐藏顶部栏和飞行数据
            topBarPanel?.let { it.visibility = View.GONE }
            flightDataContainer.visibility = View.GONE
            portraitToggleBtn.visibility = View.GONE
        }
    }

    // 设置地图的 ConstraintLayout 约束
    private fun setMapConstraints(fullScreen: Boolean) {
        val params = mapWidget.layoutParams as? ConstraintLayout.LayoutParams ?: return

        // 清除可能冲突的约束
        params.startToStart = ConstraintLayout.LayoutParams.UNSET
        params.topToTop = ConstraintLayout.LayoutParams.UNSET

        if (fullScreen) {
            // 地图全屏：四边约束 + match_constraint
            params.width = 0
            params.height = 0
            params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            params.rightMargin = 0
            params.bottomMargin = 0
        } else {
            // 地图小窗：右下角
            params.width = miniWidth
            params.height = miniHeight
            params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            params.rightMargin = miniMargin
            params.bottomMargin = miniMargin
        }

        mapWidget.layoutParams = params
    }

    // 调整FPV容器大小和位置（对应官方 resizeFPVWidget）
    private fun resizeFpvHolder(width: Int, height: Int, margin: Int) {
        val params = fpvParentView.layoutParams as? ConstraintLayout.LayoutParams ?: return
        val outerParent = fpvParentView.parent as? ConstraintLayout ?: return

        // 清除所有约束
        params.startToStart = ConstraintLayout.LayoutParams.UNSET
        params.endToEnd = ConstraintLayout.LayoutParams.UNSET
        params.topToTop = ConstraintLayout.LayoutParams.UNSET
        params.topToBottom = ConstraintLayout.LayoutParams.UNSET
        params.bottomToBottom = ConstraintLayout.LayoutParams.UNSET

        if (isMapMini) {
            // 当前地图是小窗 → FPV要缩小到右下角
            params.width = width
            params.height = height
            params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            params.rightMargin = margin
            params.bottomMargin = margin
        } else {
            // 当前地图是全屏 → FPV要恢复全屏
            params.width = 0
            params.height = 0
            params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            params.topToBottom = dji.v5.ux.R.id.panel_top_bar
            params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            params.rightMargin = 0
            params.bottomMargin = 0
        }

        fpvParentView.layoutParams = params

        // 通过 removeView/addView 控制 z-order（对应官方的 fpvInsertPosition 逻辑）
        val index = outerParent.indexOfChild(fpvParentView)
        outerParent.removeView(fpvParentView)
        if (isMapMini) {
            // FPV缩小时放到最上层（最后添加）
            outerParent.addView(fpvParentView)
        } else {
            // FPV恢复全屏时放到底层（第一个添加，index=0）
            outerParent.addView(fpvParentView, 0)
        }
    }

    // 隐藏面板（切换到地图全屏时调用）
    private fun hidePanels() {
        systemStatusListPanelWidget?.let { it.visibility = View.GONE }
        simulatorControlWidget?.let { it.visibility = View.GONE }
    }

    // 缩放动画（对应官方 CompleteWidgetActivity.ResizeAnimation）
    private inner class ResizeAnimation(
        private val mView: View,
        private val mFromWidth: Int,
        private val mFromHeight: Int,
        private val mToWidth: Int,
        private val mToHeight: Int,
        private val mMargin: Int
    ) : Animation() {
        init {
            duration = 300
        }

        override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
            val height = ((mToHeight - mFromHeight) * interpolatedTime + mFromHeight).toInt()
            val width = ((mToWidth - mFromWidth) * interpolatedTime + mFromWidth).toInt()
            val p = mView.layoutParams as ConstraintLayout.LayoutParams
            p.height = height
            p.width = width
            p.rightMargin = mMargin
            p.bottomMargin = mMargin
            mView.requestLayout()
        }
    }

    // ==================== 竖拍/横拍切换 ====================

    // 初始化竖拍切换按钮
    private fun initPortraitToggle() {
        val density = resources.displayMetrics.density
        val btnSize = (40 * density).toInt()
        val margin = (12 * density).toInt()

        portraitToggleBtn = ImageButton(this).apply {
            setImageResource(R.drawable.ic_screen_rotation)
            setBackgroundColor(0x80000000.toInt())
            setPadding(
                (8 * density).toInt(),
                (8 * density).toInt(),
                (8 * density).toInt(),
                (8 * density).toInt()
            )
            contentDescription = "切换竖拍/横拍"
            setOnClickListener { togglePortraitMode() }
        }

        // 放在 FPV 左上角（topBarPanel 下方）
        val params = FrameLayout.LayoutParams(btnSize, btnSize).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = margin
            topMargin = (56 * density).toInt() // 顶部栏下方
        }
        window.decorView.findViewById<FrameLayout>(android.R.id.content)
            .addView(portraitToggleBtn, params)
    }

    // 切换竖拍/横拍模式
    private fun togglePortraitMode() {
        val modeName = if (isPortraitMode) "横拍" else "竖拍"
        Log.i(TAG, "尝试切换到${modeName}模式")
        

        // 先查询云台 roll 轴范围，确认当前机型是否支持竖拍
        KeyManager.getInstance().getValue(
            KeyTools.createKey(GimbalKey.KeyGimbalAttitudeRange, ComponentIndexType.LEFT_OR_MAIN),
            object : CommonCallbacks.CompletionCallbackWithParam<dji.sdk.keyvalue.value.gimbal.GimbalAttitudeRange> {
                override fun onSuccess(range: dji.sdk.keyvalue.value.gimbal.GimbalAttitudeRange?) {
                    val rollRange = range?.roll
                    val rollMin = rollRange?.min ?: 0.0
                    val rollMax = rollRange?.max ?: 0.0
                    Log.i(TAG, "云台 roll 轴范围: min=$rollMin, max=$rollMax")

                    // 检查是否支持竖拍（roll 范围需要覆盖 -90 或 90）
                    if (rollMin >= 0.0 && rollMax <= 0.0) {
                        runOnUiThread {
                            Toast.makeText(
                                this@CustomDefaultLayoutActivity,
                                "当前机型不支持竖拍（roll 范围: $rollMin ~ $rollMax）",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        return
                    }

                    // 竖拍目标角度：使用 roll 范围的最小值
                    val targetRoll = if (isPortraitMode) 0.0 else rollMin
                    Log.i(TAG, "目标 roll: $targetRoll")
                    readAttitudeAndRotate(targetRoll, modeName)
                }

                override fun onFailure(error: IDJIError) {
                    Log.w(TAG, "获取云台范围失败: ${error.description()}")
                    runOnUiThread {
                        Toast.makeText(
                            this@CustomDefaultLayoutActivity,
                            "获取云台范围失败: ${error.description()}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }

    // 读取当前云台姿态，保留 pitch 后执行旋转
    private fun readAttitudeAndRotate(targetRoll: Double, modeName: String) {
        KeyManager.getInstance().getValue(
            KeyTools.createKey(GimbalKey.KeyGimbalAttitude, ComponentIndexType.LEFT_OR_MAIN),
            object : CommonCallbacks.CompletionCallbackWithParam<Attitude> {
                override fun onSuccess(attitude: Attitude?) {
                    val currentPitch = attitude?.pitch ?: 0.0
                    Log.i(TAG, "当前云台 pitch=$currentPitch, roll=${attitude?.roll}")
                    executeGimbalRotation(currentPitch, targetRoll, modeName)
                }

                override fun onFailure(error: IDJIError) {
                    Log.w(TAG, "读取云台姿态失败: ${error.description()}, 使用 pitch=0")
                    executeGimbalRotation(0.0, targetRoll, modeName)
                }
            }
        )
    }

    // 执行云台旋转
    private fun executeGimbalRotation(pitch: Double, roll: Double, modeName: String) {
        val rotation = GimbalAngleRotation().apply {
            mode = GimbalAngleRotationMode.ABSOLUTE_ANGLE
            this.pitch = pitch
            this.roll = roll
            this.yaw = 0.0
            this.duration = 1.0
        }

        Log.i(TAG, "执行云台旋转: pitch=$pitch, roll=$roll")

        KeyManager.getInstance().performAction(
            KeyTools.createKey(GimbalKey.KeyRotateByAngle, ComponentIndexType.LEFT_OR_MAIN),
            rotation,
            object : CommonCallbacks.CompletionCallbackWithParam<EmptyMsg> {
                override fun onSuccess(t: EmptyMsg?) {
                    isPortraitMode = !isPortraitMode
                    Log.i(TAG, "切换到${modeName}模式成功")
                    runOnUiThread {
                        Toast.makeText(
                            this@CustomDefaultLayoutActivity,
                            "已切换到${modeName}模式",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onFailure(error: IDJIError) {
                    Log.e(TAG, "切换${modeName}失败: ${error.description()}")
                    runOnUiThread {
                        Toast.makeText(
                            this@CustomDefaultLayoutActivity,
                            "切换${modeName}失败: ${error.description()}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }

    // ==================== 相机对焦 ====================

    private fun initCameraFocus() {
        Log.d(TAG, "初始化相机对焦功能")
        Toast.makeText(this, "开始初始化相机对焦", Toast.LENGTH_SHORT).show()
        try {
            if (fpvInteractionWidget != null) {
                cameraManager.setupFocusInteraction(fpvInteractionWidget)
                Log.i(TAG, "相机对焦功能初始化成功")
            } else {
                Log.e(TAG, "fpvInteractionWidget 为 null，延迟初始化")
                window.decorView.postDelayed({ initCameraFocus() }, 500)
            }
        } catch (e: Exception) {
            Log.e(TAG, "初始化相机对焦功能失败", e)
            e.printStackTrace()
        }
    }

    // ==================== 限飞区 ====================

    private fun initFlySafeFeature() {
        Log.d(TAG, "初始化限飞区功能")
        waitForMapAndInitFlySafe()
    }

    private fun waitForMapAndInitFlySafe(retryCount: Int = 0) {
        val maxRetries = 20 // 最多重试20次，每次间隔300ms，共6秒
        val djiMap = mapWidget.map
        if (djiMap != null) {
            try {
                val aMap = djiMap.map as? AMap
                if (aMap != null) {
                    // 初始化限飞区辅助类并设置地图
                    flySafeHelper = AMapFlySafeHelper(this)
                    flySafeHelper.setMap(aMap)
                    // 初始化限飞区同步管理器（用于实时同步到后端）
                    flyZoneSyncManager = FlyZoneSyncManager(this)
                    // 初始化限飞区通知监听（飞机连接时的推送通知）
                    flySafeViewModel.initListener()
                    // 订阅限飞区数据变化，绘制到地图上，并同步到后端
                    flySafeViewModel.flyZoneInformation.observe(this) { flyZones ->
                        if (flyZones != null) {
                            Log.i(TAG, "限飞区数据更新，数量: ${flyZones.size}")
                            flySafeHelper.onFlyZoneListUpdate(flyZones)
                            // 将实时获取的限飞区数据同步到后端（补充批量同步可能遗漏的区域）
                            if (flyZones.isNotEmpty()) {
                                lifecycleScope.launch {
                                    try {
                                        flyZoneSyncManager.syncFromLiveData(flyZones)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "实时同步限飞区数据失败: ${e.message}")
                                    }
                                }
                            }
                        }
                    }
                    // 注册地图相机停止移动监听器：定位后或手动移动地图时重新查询禁飞区
                    mapWidget.setOnCameraStopListener { lat, lng ->
                        Log.i(TAG, "地图相机停止移动，重新查询禁飞区: lat=$lat, lng=$lng")
                        flySafeViewModel.getFlyZonesInSurroundingArea(
                            LocationCoordinate2D(lat, lng)
                        )
                    }
                    // 获取飞机当前位置的限飞区
                    flySafeViewModel.getFlyZonesAtAircraftLocation()
                    Log.i(TAG, "限飞区功能初始化成功")

                    // 保存 AMap 实例引用
                    aMapInstance = aMap

                    // 如果携带了航线任务 ID，加载并绘制航线
                    if (missionId > 0) {
                        loadAndDrawMissionRoute(aMap)
                    }
                } else {
                    Log.e(TAG, "AMap is null")
                }
            } catch (e: Exception) {
                Log.e(TAG, "初始化限飞区功能失败", e)
            }
        } else if (retryCount < maxRetries) {
            Log.d(TAG, "地图未就绪，第 ${retryCount + 1}/$maxRetries 次重试...")
            mapWidget.postDelayed({ waitForMapAndInitFlySafe(retryCount + 1) }, 300)
        } else {
            Log.e(TAG, "等待地图就绪超时，限飞区功能初始化失败")
        }
    }

    // ==================== 隐藏相机名称 ====================

    private fun hideCameraName() {
        try {
            primaryFpvWidget.isCameraSourceNameVisible = false
            primaryFpvWidget.isCameraSourceSideVisible = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ==================== 飞行数据显示 ====================

    private fun addFightDataWidgets() {
        flightDataContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0x80000000.toInt())
            setPadding(10, 10, 10, 10)
        }
        flightDataContainer.addView(AGLAltitudeWidget(this))
        flightDataContainer.addView(DistanceHomeWidget(this))
        flightDataContainer.addView(VerticalVelocityWidget(this))
        flightDataContainer.addView(HorizontalVelocityWidget(this))

        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            leftMargin = 16
            bottomMargin = 10
        }
        window.decorView.findViewById<FrameLayout>(android.R.id.content).addView(flightDataContainer, params)
    }

    // ==================== 面板可见性监听 ====================

    private fun checkPanelVisibility() {
        try {
            // 地图全屏模式时隐藏飞行数据和竖拍按钮
            if (!isMapMini) {
                if (flightDataContainer.visibility != View.GONE) {
                    flightDataContainer.visibility = View.GONE
                }
                if (portraitToggleBtn.visibility != View.GONE) {
                    portraitToggleBtn.visibility = View.GONE
                }
                return
            }

            val systemStatusVisible = systemStatusListPanelWidget?.visibility == View.VISIBLE
            val simulatorVisible = simulatorControlWidget?.visibility == View.VISIBLE
            val drawerLayout = findViewById<androidx.drawerlayout.widget.DrawerLayout>(dji.v5.ux.R.id.root_view)
            val drawerOpen = drawerLayout?.isDrawerOpen(androidx.core.view.GravityCompat.END) == true
            val shouldHide = systemStatusVisible || simulatorVisible || drawerOpen

            if (shouldHide) {
                if (flightDataContainer.visibility != View.GONE) {
                    flightDataContainer.visibility = View.GONE
                }
                if (portraitToggleBtn.visibility != View.GONE) {
                    portraitToggleBtn.visibility = View.GONE
                }
            } else {
                if (flightDataContainer.visibility != View.VISIBLE) {
                    flightDataContainer.visibility = View.VISIBLE
                }
                if (portraitToggleBtn.visibility != View.VISIBLE) {
                    portraitToggleBtn.visibility = View.VISIBLE
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun observePanelVisibility() {
        val disposable = Observable.interval(100, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ checkPanelVisibility() }, { it.printStackTrace() })
        rxDisposables.add(disposable)
    }

    // ==================== 遥控器电池监控 ====================

    // 监听遥控器电池电量，低电量时语音提示
    private fun initRcBatteryMonitor() {
        KeyManager.getInstance().listen(
            KeyTools.createKey(RemoteControllerKey.KeyBatteryInfo),
            this
        ) { _, newValue ->
            val percent = newValue?.batteryPercent ?: return@listen
            val warningLevel = when {
                percent < 15 -> 2
                percent < 30 -> 1
                else -> 0
            }

            // 只在警告级别升高时播报（避免重复播报）
            if (warningLevel > lastRcBatteryWarningLevel) {
                lastRcBatteryWarningLevel = warningLevel
                val ttsHelper = TTSHelper.getInstance(this)
                if (ttsHelper.isAvailable()) {
                    val message = when (warningLevel) {
                        2 -> "遥控器电量严重不足，当前电量${percent}%，请尽快降落"
                        1 -> "遥控器电量低，当前电量${percent}%，请注意"
                        else -> return@listen
                    }
                    Log.w(TAG, "遥控器低电量警告: $message")
                    ttsHelper.speakNow(message)
                }
            }

            // 电量恢复时重置（比如换电池或充电）
            if (warningLevel < lastRcBatteryWarningLevel) {
                lastRcBatteryWarningLevel = warningLevel
            }
        }
    }

    // ==================== 自动绑定设备 ====================

    private fun initAutoBindDevice() {
        // 监听飞控连接状态
        KeyManager.getInstance().listen(
            KeyTools.createKey(FlightControllerKey.KeyConnection), this
        ) { _, newValue ->
            val isConnected = newValue == true
            Log.i(TAG, "飞控连接状态: $isConnected")

            if (isConnected && !hasAttemptedBind) {
                hasAttemptedBind = true
                // 延迟 2 秒等待 SN 数据就绪
                window.decorView.postDelayed({ fetchSnAndBind() }, 2000)
            } else if (!isConnected) {
                // 断开连接时清理 WebSocket 和帧监听，并重置标志
                disconnectVideoStream()
                hasAttemptedBind = false
            }
        }

        // 监听产品级连接状态（USB 断开重连时更可靠）
        KeyManager.getInstance().listen(
            KeyTools.createKey(ProductKey.KeyConnection), this
        ) { _, newValue ->
            val isConnected = newValue == true
            Log.i(TAG, "产品连接状态: $isConnected")

            if (!isConnected) {
                // USB 断开时重置标志
                hasAttemptedBind = false
            }
        }
    }

    private fun fetchSnAndBind() {
        // 先获取机型，再根据机型决定用哪个 Key 获取飞机 SN
        // Mini 3 系列需要用 ProductKey.KeySerialNumber
        // 其他机型飞机 SN 和飞控 SN 相同，用 FlightControllerKey.KeySerialNumber
        KeyManager.getInstance().getValue(
            KeyTools.createKey(ProductKey.KeyProductType),
            object : CommonCallbacks.CompletionCallbackWithParam<ProductType> {
                override fun onSuccess(productType: ProductType?) {
                    val model = productType?.name ?: "UNKNOWN"
                    Log.i(TAG, "机型: $model")

                    val isMini3 = model.contains("MINI_3", ignoreCase = true)
                    if (isMini3) {
                        Log.i(TAG, "Mini 3 系列，使用 ProductKey.KeySerialNumber 获取飞机SN")
                        getSnByProductKey(model)
                    } else {
                        Log.i(TAG, "非 Mini 3 系列，使用 FlightControllerKey.KeySerialNumber 获取飞机SN")
                        getSnByFlightControllerKey(model)
                    }
                }
                override fun onFailure(error: IDJIError) {
                    Log.w(TAG, "获取机型失败: ${error.description()}, 尝试 FlightControllerKey")
                    getSnByFlightControllerKey("UNKNOWN")
                }
            }
        )
    }

    // Mini 3 系列：通过 ProductKey.KeySerialNumber 获取飞机 SN
    private fun getSnByProductKey(model: String) {
        KeyManager.getInstance().getValue(
            KeyTools.createKey(ProductKey.KeySerialNumber),
            object : CommonCallbacks.CompletionCallbackWithParam<String> {
                override fun onSuccess(sn: String?) {
                    Log.i(TAG, "ProductKey 获取飞机SN: $sn")
                    if (sn.isNullOrEmpty()) {
                        Log.w(TAG, "ProductKey SN为空，回退到 FlightControllerKey")
                        getSnByFlightControllerKey(model)
                        return
                    }
                    // Mini 3 系列：飞机SN已获取，再获取飞控SN
                    getFlightControllerSn(sn, model)
                }
                override fun onFailure(error: IDJIError) {
                    Log.w(TAG, "ProductKey 获取SN失败: ${error.description()}, 回退到 FlightControllerKey")
                    getSnByFlightControllerKey(model)
                }
            }
        )
    }

    // 其他机型：通过 FlightControllerKey.KeySerialNumber 获取飞机 SN（飞机SN和飞控SN相同）
    private fun getSnByFlightControllerKey(model: String) {
        KeyManager.getInstance().getValue(
            KeyTools.createKey(FlightControllerKey.KeySerialNumber),
            object : CommonCallbacks.CompletionCallbackWithParam<String> {
                override fun onSuccess(sn: String?) {
                    Log.i(TAG, "FlightControllerKey 获取飞机SN: $sn")
                    if (sn.isNullOrEmpty()) {
                        Log.w(TAG, "飞机SN为空，跳过自动绑定")
                        hasAttemptedBind = false
                        return
                    }
                    // 非 Mini 3 系列：飞机SN和飞控SN相同
                    checkAndBindDevice(sn, model, sn)
                }
                override fun onFailure(error: IDJIError) {
                    Log.e(TAG, "获取飞机SN失败: ${error.description()}")
                    hasAttemptedBind = false
                }
            }
        )
    }

    // 获取飞控SN（Mini 3 系列需要单独获取）
    private fun getFlightControllerSn(aircraftSn: String, model: String) {
        KeyManager.getInstance().getValue(
            KeyTools.createKey(FlightControllerKey.KeySerialNumber),
            object : CommonCallbacks.CompletionCallbackWithParam<String> {
                override fun onSuccess(fcSn: String?) {
                    Log.i(TAG, "飞控SN: $fcSn")
                    checkAndBindDevice(aircraftSn, model, fcSn ?: "")
                }
                override fun onFailure(error: IDJIError) {
                    Log.w(TAG, "获取飞控SN失败: ${error.description()}, 飞控SN留空")
                    checkAndBindDevice(aircraftSn, model, "")
                }
            }
        )
    }

    // 查询设备绑定状态，决定后续操作
    private fun checkAndBindDevice(sn: String, model: String, flightControllerSn: String) {
        // 保存当前飞机 SN，供飞行记录使用
        currentAircraftSn = sn
        lifecycleScope.launch {
            try {
                // 用 checkDeviceBind 接口精确查询该设备是否属于当前用户
                val response = RetrofitClient.apiService.checkDeviceBind(sn)
                if (response.code == 1) {
                    val isBound = response.data == true
                    if (isBound) {
                        // 设备已绑定到当前用户，静默更新数据
                        Log.i(TAG, "设备已绑定到当前用户，更新设备数据: $sn")
                        updateDeviceData(sn, model, flightControllerSn)
                        // 立即启动 WebSocket 连接（飞机连上就连接，不等起飞）
                        startVideoStreamConnection()
                    } else {
                        // 设备未绑定到当前用户，弹出确认对话框
                        Log.i(TAG, "设备未绑定到当前用户，弹出绑定确认对话框: $sn")
                        runOnUiThread {
                            showBindConfirmDialog(sn, model, flightControllerSn)
                        }
                    }
                } else {
                    Log.w(TAG, "查询设备绑定状态失败: ${response.message}")
                    runOnUiThread {
                        showBindConfirmDialog(sn, model, flightControllerSn)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "查询设备绑定状态异常: ${e.message}")
                runOnUiThread {
                    showBindConfirmDialog(sn, model, flightControllerSn)
                }
            }
        }
    }

    // 静默更新已绑定设备的数据
    private fun updateDeviceData(sn: String, model: String, flightControllerSn: String) {
        lifecycleScope.launch {
            try {
                val request = UpdateDeviceRequest(
                    sn = sn,
                    model = model,
                    flightControllerSerialNumber = flightControllerSn
                )
                val response = RetrofitClient.apiService.updateDevice(request)
                if (response.code == 1) {
                    Log.i(TAG, "设备数据更新成功: $sn")
                } else {
                    // 更新失败（可能设备不属于当前用户），弹出绑定对话框
                    Log.w(TAG, "设备数据更新失败: ${response.message}，尝试绑定")
                    runOnUiThread {
                        showBindConfirmDialog(sn, model, flightControllerSn)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "更新设备数据异常: ${e.message}")
            }
        }
    }

    // 弹出绑定确认对话框
    private fun showBindConfirmDialog(sn: String, model: String, flightControllerSn: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("设备未绑定")
            .setMessage("检测到无人机 SN: $sn\n机型: $model\n\n该设备尚未绑定到您的账号，是否立即绑定？\n\n注意：未绑定设备无法起飞。")
            .setCancelable(false)
            .setPositiveButton("绑定") { _, _ ->
                bindDevice(sn, model, flightControllerSn)
            }
            .setNegativeButton("取消") { _, _ ->
                Log.w(TAG, "用户取消绑定设备，退出飞行界面")
                Toast.makeText(this, "未绑定设备，无法起飞", Toast.LENGTH_LONG).show()
                finish()
            }
            .show()
    }

    private fun bindDevice(sn: String, model: String, flightControllerSn: String) {
        // 构造 deviceData JSON
        val deviceData = org.json.JSONObject().apply {
            put("sn", sn)
            put("model", model)
            put("flightControllerSerialNumber", flightControllerSn)
        }.toString()

        val requestBody = deviceData.toRequestBody("text/plain".toMediaTypeOrNull())

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.addDevicesByData(requestBody)
                if (response.code == 1) {
                    val data = response.data
                    val errorMsg = data?.errorMsg ?: ""
                    if (errorMsg.isNotEmpty()) {
                        // 部分成功或有警告信息
                        Log.w(TAG, "绑定设备部分成功: $errorMsg")
                        runOnUiThread {
                            MaterialAlertDialogBuilder(this@CustomDefaultLayoutActivity)
                                .setTitle("绑定提示")
                                .setMessage(errorMsg)
                                .setPositiveButton("确定", null)
                                .show()
                        }
                    } else {
                        Log.i(TAG, "自动绑定设备成功: $sn")
                        runOnUiThread {
                            Toast.makeText(this@CustomDefaultLayoutActivity, "设备绑定成功: $sn", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    // 绑定失败，显示详细错误原因
                    val errorMsg = response.data?.errorMsg ?: ""
                    val displayMsg = errorMsg.ifEmpty { response.message ?: "绑定失败" }
                    Log.w(TAG, "绑定设备失败: $displayMsg")
                    runOnUiThread {
                        MaterialAlertDialogBuilder(this@CustomDefaultLayoutActivity)
                            .setTitle("绑定失败")
                            .setMessage(displayMsg)
                            .setPositiveButton("确定") { _, _ -> finish() }
                            .setCancelable(false)
                            .show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "绑定设备异常: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this@CustomDefaultLayoutActivity, "绑定异常，无法起飞", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    // ==================== 飞行记录 ====================

    // 初始化飞行状态监听
    private fun initFlightRecordMonitor() {
        // 监听 KeyIsFlying 状态变化
        KeyManager.getInstance().listen(
            KeyTools.createKey(FlightControllerKey.KeyIsFlying),
            this
        ) { _, newValue ->
            val isFlying = newValue == true
            Log.i(TAG, "飞行状态变化: isFlying=$isFlying, isRecording=$isFlightRecording")

            if (isFlying && !isFlightRecording) {
                // 起飞 → 启动飞行记录
                onTakeOff()
            } else if (!isFlying && isFlightRecording) {
                // 降落 → 结束飞行记录
                onLanding()
            }
        }
    }

    // 起飞时调用
    private fun onTakeOff() {
        val sn = currentAircraftSn
        if (sn.isNullOrEmpty()) {
            Log.w(TAG, "飞机 SN 为空，无法启动飞行记录")
            return
        }

        Log.i(TAG, "检测到起飞，启动飞行记录...")

        // 获取起飞点坐标（Home Location）
        KeyManager.getInstance().getValue(
            KeyTools.createKey(FlightControllerKey.KeyHomeLocation),
            object : CommonCallbacks.CompletionCallbackWithParam<LocationCoordinate2D> {
                override fun onSuccess(location: LocationCoordinate2D?) {
                    val homeLat = location?.latitude
                    val homeLng = location?.longitude
                    Log.i(TAG, "起飞点: lat=$homeLat, lng=$homeLng")
                    callStartFlight(sn, homeLat, homeLng)
                }

                override fun onFailure(error: IDJIError) {
                    Log.w(TAG, "获取起飞点失败: ${error.description()}, 不传起飞点坐标")
                    callStartFlight(sn, null, null)
                }
            }
        )
    }

    // 调用飞行启动 API
    private fun callStartFlight(sn: String, homeLat: Double?, homeLng: Double?) {
        lifecycleScope.launch {
            try {
                val request = FlightStartRequest(
                    sn = sn,
                    home_latitude = homeLat,
                    home_longitude = homeLng
                )
                val response = RetrofitClient.apiService.startFlight(request)
                if (response.code == 1 && response.data != null) {
                    currentFlightId = response.data.flightId
                    isFlightRecording = true
                    // 重置飞行数据
                    flightMaxAltitude = 0.0
                    flightMaxSpeed = 0.0
                    flightDistance = 0.0
                    lastLat = null
                    lastLng = null
                    pathPointsBuffer.clear()

                    Log.i(TAG, "飞行记录启动成功, flightId=${currentFlightId}")
                    runOnUiThread {
                        Toast.makeText(this@CustomDefaultLayoutActivity, "飞行记录已启动", Toast.LENGTH_SHORT).show()
                    }

                    // 启动定时采集航线坐标（每 3 秒采集一次，每 15 秒上传一次）
                    startPathCollection()
                } else {
                    Log.w(TAG, "飞行记录启动失败: ${response.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "飞行记录启动异常: ${e.message}")
            }
        }
    }

    // 启动航线坐标定时采集和上传
    private fun startPathCollection() {
        // 每 3 秒采集一次飞机位置数据
        val collectDisposable = Observable.interval(3, TimeUnit.SECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ collectPathPoint() }, { it.printStackTrace() })
        rxDisposables.add(collectDisposable)

        // 每 15 秒批量上传一次航线坐标
        pathUploadDisposable = Observable.interval(15, TimeUnit.SECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ uploadPathPoints() }, { it.printStackTrace() })
        rxDisposables.add(pathUploadDisposable!!)
    }

    // 采集当前飞机位置数据
    private fun collectPathPoint() {
        if (!isFlightRecording) return

        // 获取位置
        KeyManager.getInstance().getValue(
            KeyTools.createKey(FlightControllerKey.KeyAircraftLocation),
            object : CommonCallbacks.CompletionCallbackWithParam<LocationCoordinate2D> {
                override fun onSuccess(location: LocationCoordinate2D?) {
                    if (location == null || (location.latitude == 0.0 && location.longitude == 0.0)) return

                    val lat = location.latitude
                    val lng = location.longitude

                    // 获取高度
                    KeyManager.getInstance().getValue(
                        KeyTools.createKey(FlightControllerKey.KeyAltitude),
                        object : CommonCallbacks.CompletionCallbackWithParam<Double> {
                            override fun onSuccess(altitude: Double?) {
                                val alt = altitude ?: 0.0
                                // 更新最大高度
                                flightMaxAltitude = max(flightMaxAltitude, alt)

                                // 获取速度
                                KeyManager.getInstance().getValue(
                                    KeyTools.createKey(FlightControllerKey.KeyAircraftVelocity),
                                    object : CommonCallbacks.CompletionCallbackWithParam<dji.sdk.keyvalue.value.common.Velocity3D> {
                                        override fun onSuccess(velocity: dji.sdk.keyvalue.value.common.Velocity3D?) {
                                            // 水平速度 = sqrt(x^2 + y^2)
                                            val vx = velocity?.x ?: 0.0
                                            val vy = velocity?.y ?: 0.0
                                            val horizontalSpeed = Math.sqrt(vx * vx + vy * vy)
                                            // 更新最大速度
                                            flightMaxSpeed = max(flightMaxSpeed, horizontalSpeed)

                                            // 获取航向角
                                            KeyManager.getInstance().getValue(
                                                KeyTools.createKey(FlightControllerKey.KeyAircraftAttitude),
                                                object : CommonCallbacks.CompletionCallbackWithParam<Attitude> {
                                                    override fun onSuccess(attitude: Attitude?) {
                                                        val heading = attitude?.yaw ?: 0.0
                                                        addPathPoint(lat, lng, alt, horizontalSpeed, heading)
                                                    }
                                                    override fun onFailure(error: IDJIError) {
                                                        addPathPoint(lat, lng, alt, horizontalSpeed, 0.0)
                                                    }
                                                }
                                            )
                                        }
                                        override fun onFailure(error: IDJIError) {
                                            addPathPoint(lat, lng, alt, 0.0, 0.0)
                                        }
                                    }
                                )
                            }
                            override fun onFailure(error: IDJIError) {
                                addPathPoint(lat, lng, 0.0, 0.0, 0.0)
                            }
                        }
                    )
                }
                override fun onFailure(error: IDJIError) {
                    Log.w(TAG, "采集位置失败: ${error.description()}")
                }
            }
        )
    }

    // 添加坐标点到缓冲区，并计算累计距离
    private fun addPathPoint(lat: Double, lng: Double, alt: Double, speed: Double, heading: Double) {
        // 计算与上一个点的距离（Haversine 公式）
        if (lastLat != null && lastLng != null) {
            val dist = haversineDistance(lastLat!!, lastLng!!, lat, lng)
            flightDistance += dist
        }
        lastLat = lat
        lastLng = lng

        val point = PathPoint(
            lat = lat,
            lng = lng,
            alt = alt,
            speed = speed,
            heading = heading,
            ts = System.currentTimeMillis()
        )
        synchronized(pathPointsBuffer) {
            pathPointsBuffer.add(point)
        }
        Log.d(TAG, "采集坐标点: lat=$lat, lng=$lng, alt=$alt, speed=$speed, heading=$heading")
    }

    // Haversine 公式计算两点间距离（米）
    private fun haversineDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val R = 6371000.0 // 地球半径（米）
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }

    // 批量上传航线坐标
    private fun uploadPathPoints() {
        val flightId = currentFlightId ?: return
        val points: List<PathPoint>
        synchronized(pathPointsBuffer) {
            if (pathPointsBuffer.isEmpty()) return
            points = pathPointsBuffer.toList()
            pathPointsBuffer.clear()
        }

        lifecycleScope.launch {
            try {
                val request = FlightUpdatePathRequest(
                    flightId = flightId,
                    points = points
                )
                val response = RetrofitClient.apiService.updateFlightPath(request)
                if (response.code == 1) {
                    Log.i(TAG, "航线坐标上传成功, ${points.size} 个点")
                } else {
                    Log.w(TAG, "航线坐标上传失败: ${response.message}")
                    // 上传失败，把点放回缓冲区
                    synchronized(pathPointsBuffer) {
                        pathPointsBuffer.addAll(0, points)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "航线坐标上传异常: ${e.message}")
                // 异常时把点放回缓冲区
                synchronized(pathPointsBuffer) {
                    pathPointsBuffer.addAll(0, points)
                }
            }
        }
    }

    // 降落时调用
    private fun onLanding() {
        Log.i(TAG, "检测到降落，结束飞行记录...")
        isFlightRecording = false

        // 停止航线上传定时器
        pathUploadDisposable?.dispose()
        pathUploadDisposable = null

        // 上传剩余的航线坐标
        uploadPathPoints()

        // 调用结束飞行 API
        val flightId = currentFlightId ?: return
        lifecycleScope.launch {
            try {
                val request = FlightEndRequest(
                    flightId = flightId,
                    maxAltitude = flightMaxAltitude,
                    maxSpeed = flightMaxSpeed,
                    distance = flightDistance,
                    status = 1 // 1=已完成
                )
                val response = RetrofitClient.apiService.endFlight(request)
                if (response.code == 1) {
                    Log.i(TAG, "飞行记录结束成功, flightId=$flightId")
                    runOnUiThread {
                        Toast.makeText(this@CustomDefaultLayoutActivity, "飞行记录已保存", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.w(TAG, "飞行记录结束失败: ${response.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "飞行记录结束异常: ${e.message}")
            } finally {
                // 重置状态
                currentFlightId = null
                flightMaxAltitude = 0.0
                flightMaxSpeed = 0.0
                flightDistance = 0.0
                lastLat = null
                lastLng = null
            }
        }
    }

    // ==================== WebSocket 视频流推送 ====================

    /**
     * 建立 WebSocket 连接并启动视频帧采集（飞机连接时调用，不等起飞）
     */
    private fun startVideoStreamConnection() {
        val sn = currentAircraftSn
        if (sn.isNullOrEmpty()) {
            Log.w(TAG, "飞机 SN 为空，无法建立 WebSocket 连接")
            ViewUtil.showToast(this, "飞机 SN 为空", Toast.LENGTH_SHORT)
            return
        }

        // 如果已经连接，不重复连接
        if (videoStreamWs?.isConnected() == true) {
            Log.i(TAG, "WebSocket 已连接，跳过重复连接")
            ViewUtil.showToast(this, "WebSocket 已连接", Toast.LENGTH_SHORT)
            return
        }

        // 从 RetrofitClient 获取服务器地址，转换为 WebSocket URL
        val httpUrl = RetrofitClient.BASE_URL.removeSuffix("/")
        val wsUrl = httpUrl.replace("http://", "ws://")
            .replace("https://", "wss://")
            .replace("/api/v1","")

        Log.i(TAG, "建立 WebSocket 连接: $wsUrl, sn=$sn")
        ViewUtil.showToast(this, "正在连接 WebSocket: $sn", Toast.LENGTH_SHORT)

        videoStreamWs = VideoStreamWebSocket(wsUrl, sn)
        videoStreamWs?.commandListener = object : VideoStreamWebSocket.CommandListener {
            override fun onStartDemo() { startDemoPlayback() }
            override fun onStopDemo() { stopDemoPlayback() }
        }
        videoStreamWs?.connect()
        
        // 延迟 2 秒后检查连接状态，并启动视频帧采集
        window.decorView.postDelayed({
            if (videoStreamWs?.isConnected() == true) {
                ViewUtil.showToast(this, "WebSocket 连接成功，开始推送视频帧", Toast.LENGTH_SHORT)
                // 连接成功后立即启动视频帧采集
                startFrameCapture()
            } else {
                ViewUtil.showToast(this, "WebSocket 连接失败", Toast.LENGTH_LONG)
            }
        }, 2000)
    }

    /**
     * 启动视频帧采集（飞机连接后调用，不等起飞）
     */
    private fun startFrameCapture() {
        Log.i(TAG, "启动视频帧采集")
        ViewUtil.showToast(this, "启动视频帧采集", Toast.LENGTH_SHORT)
        
        if (isFrameListenerAdded) {
            Log.i(TAG, "视频帧监听器已添加，跳过重复添加")
            ViewUtil.showToast(this, "视频帧监听器已添加", Toast.LENGTH_SHORT)
            return
        }

        try {
            val mediaDataCenter = dji.v5.manager.datacenter.MediaDataCenter.getInstance()
            val streamManager = mediaDataCenter.cameraStreamManager
            
            if (streamManager == null) {
                Log.w(TAG, "CameraStreamManager 为 null，无法启动视频帧采集")
                ViewUtil.showToast(this, "CameraStreamManager 为 null", Toast.LENGTH_LONG)
                return
            }
            
            val cameraIndex = dji.sdk.keyvalue.value.common.ComponentIndexType.LEFT_OR_MAIN
            
            // 创建持久化的帧监听器
            cameraFrameListener = object : dji.v5.manager.interfaces.ICameraStreamManager.CameraFrameListener {
                override fun onFrame(
                    bytes: ByteArray,
                    offset: Int,
                    length: Int,
                    width: Int,
                    height: Int,
                    format: dji.v5.manager.interfaces.ICameraStreamManager.FrameFormat
                ) {
                    try {
                        // 演示模式下跳过无人机帧
                        if (demoMode.get()) return

                        // 帧率控制：跳过间隔内的帧，避免网络拥塞
                        val now = System.currentTimeMillis()
                        if (now - lastFrameSentTime < FRAME_INTERVAL_MS) return

                        // 将视频帧转换为 JPEG
                        val jpegData = when (format) {
                            dji.v5.manager.interfaces.ICameraStreamManager.FrameFormat.YUV420_888 -> {
                                yuv420ToJpeg(bytes, width, height)
                            }
                            dji.v5.manager.interfaces.ICameraStreamManager.FrameFormat.RGBA_8888 -> {
                                rgbaToJpeg(bytes, width, height)
                            }
                            else -> {
                                Log.w(TAG, "不支持的视频格式: $format")
                                null
                            }
                        }

                        // 通过 WebSocket 发送
                        if (jpegData != null && videoStreamWs?.isConnected() == true) {
                            videoStreamWs?.sendFrame(jpegData)
                            lastFrameSentTime = now
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "处理视频帧失败: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
            
            // 添加帧监听器（优先使用 RGBA 格式）
            val listener = cameraFrameListener
            if (listener != null) {
                try {
                    streamManager.addFrameListener(
                        cameraIndex,
                        dji.v5.manager.interfaces.ICameraStreamManager.FrameFormat.RGBA_8888,
                        listener
                    )
                    isFrameListenerAdded = true
                    Log.i(TAG, "视频帧监听器添加成功（RGBA 格式）")
                    ViewUtil.showToast(this, "视频帧监听器添加成功", Toast.LENGTH_SHORT)
                } catch (e: Exception) {
                    Log.w(TAG, "添加 RGBA 监听器失败，尝试 YUV420: ${e.message}")
                    try {
                        streamManager.addFrameListener(
                            cameraIndex,
                            dji.v5.manager.interfaces.ICameraStreamManager.FrameFormat.YUV420_888,
                            listener
                        )
                        isFrameListenerAdded = true
                        Log.i(TAG, "视频帧监听器添加成功（YUV420 格式）")
                        ViewUtil.showToast(this, "视频帧监听器添加成功", Toast.LENGTH_SHORT)
                    } catch (e2: Exception) {
                        Log.e(TAG, "添加 YUV420 监听器失败: ${e2.message}")
                        ViewUtil.showToast(this, "添加监听器失败: ${e2.message}", Toast.LENGTH_LONG)
                        e2.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动视频帧采集失败: ${e.message}")
            ViewUtil.showToast(this, "启动视频帧采集失败: ${e.message}", Toast.LENGTH_LONG)
            e.printStackTrace()
        }
    }

    /**
     * 停止视频帧采集（Activity 销毁时调用）
     */
    private fun stopFrameCapture() {
        if (!isFrameListenerAdded || cameraFrameListener == null) {
            Log.i(TAG, "视频帧监听器未添加，无需停止")
            return
        }

        try {
            val mediaDataCenter = dji.v5.manager.datacenter.MediaDataCenter.getInstance()
            val streamManager = mediaDataCenter.cameraStreamManager
            val listener = cameraFrameListener
            
            if (streamManager != null && listener != null) {
                streamManager.removeFrameListener(listener)
                isFrameListenerAdded = false
                Log.i(TAG, "视频帧监听器已移除")
            }
        } catch (e: Exception) {
            Log.e(TAG, "移除视频帧监听器失败: ${e.message}")
            e.printStackTrace()
        }
        
        cameraFrameListener = null
    }

    /**
     * 断开 WebSocket 连接（Activity 销毁时调用）
     */
    private fun disconnectVideoStream() {
        stopDemoPlayback()
        stopFrameCapture()
        videoStreamWs?.disconnect()
        videoStreamWs = null
        Log.i(TAG, "WebSocket 连接已断开")
    }

    private fun startDemoPlayback() {
        Log.i(TAG, "开始演示视频播放")
        demoMode.set(true)
        demoVideoPlayer = com.example.skyflowtracker.utils.DemoVideoPlayer(
            assets, "demo_traffic.mp4",
            onFrame = { jpegData -> videoStreamWs?.sendFrame(jpegData) },
            onComplete = {
                demoMode.set(false)
                videoStreamWs?.sendTextMessage("{\"event\":\"DEMO_VIDEO_ENDED\"}")
                demoVideoPlayer = null
                Log.i(TAG, "演示视频播放结束")
            }
        )
        demoVideoPlayer?.start()
    }

    private fun stopDemoPlayback() {
        demoVideoPlayer?.stop()
        demoVideoPlayer = null
        demoMode.set(false)
    }

    // 流式传输的最大宽度，超过则缩放以减小帧体积
    private val STREAM_MAX_WIDTH = 960

    /**
     * 将 YUV420 数据转换为 JPEG（带缩放，只压缩一次避免画质损失）
     */
    private fun yuv420ToJpeg(yuv: ByteArray, width: Int, height: Int): ByteArray? {
        return try {
            val yuvImage = android.graphics.YuvImage(
                yuv,
                android.graphics.ImageFormat.NV21,
                width,
                height,
                null
            )
            if (width > STREAM_MAX_WIDTH) {
                // 先以高质量压缩一次，再 decode → 缩放 → 重新压缩（共压缩两次，但保留更多细节）
                // 注意：这里第一次用高质量是为了缩放时不丢细节，最终只保留缩放后的结果
                val scale = STREAM_MAX_WIDTH.toFloat() / width
                val targetHeight = (height * scale).toInt()
                val fullOut = java.io.ByteArrayOutputStream()
                yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 90, fullOut)
                val fullBytes = fullOut.toByteArray()
                val fullBitmap = android.graphics.BitmapFactory.decodeByteArray(fullBytes, 0, fullBytes.size)
                val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(
                    fullBitmap, STREAM_MAX_WIDTH, targetHeight, true
                )
                fullBitmap.recycle()
                val out = java.io.ByteArrayOutputStream()
                scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, out)
                scaledBitmap.recycle()
                out.toByteArray()
            } else {
                // 无需缩放，直接一次压缩
                val out = java.io.ByteArrayOutputStream()
                yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 75, out)
                out.toByteArray()
            }
        } catch (e: Exception) {
            Log.e(TAG, "YUV 转 JPEG 失败: ${e.message}")
            null
        }
    }
    
    /**
     * 将 RGBA 数据转换为 JPEG（带缩放）
     */
    private fun rgbaToJpeg(rgba: ByteArray, width: Int, height: Int): ByteArray? {
        return try {
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            val buffer = java.nio.ByteBuffer.wrap(rgba)
            bitmap.copyPixelsFromBuffer(buffer)

            // 缩放以减小帧体积
            val targetBitmap = if (width > STREAM_MAX_WIDTH) {
                val scale = STREAM_MAX_WIDTH.toFloat() / width
                val scaled = android.graphics.Bitmap.createScaledBitmap(
                    bitmap, STREAM_MAX_WIDTH, (height * scale).toInt(), true
                )
                bitmap.recycle()
                scaled
            } else {
                bitmap
            }

            val out = ByteArrayOutputStream()
            targetBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, out)
            targetBitmap.recycle()
            out.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "RGBA 转 JPEG 失败: ${e.message}")
            null
        }
    }

    // ==================== 航线任务路线叠加 ====================

    /**
     * 加载航线任务详情并在地图上绘制航线
     */
    private fun loadAndDrawMissionRoute(aMap: AMap) {
        lifecycleScope.launch {
            try {
                Log.i(TAG, "加载航线任务: missionId=$missionId")
                val response = RetrofitClient.apiService.getMissionDetail(missionId)
                if (response.code == 1 && response.data != null) {
                    val detail = response.data
                    val waypoints = detail.waypoints
                    if (!waypoints.isNullOrEmpty()) {
                        runOnUiThread {
                            drawMissionRoute(aMap, waypoints)
                        }
                        Toast.makeText(
                            this@CustomDefaultLayoutActivity,
                            "已加载航线「${detail.name}」，共 ${waypoints.size} 个航点",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(this@CustomDefaultLayoutActivity, "该任务无航点数据", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(
                        this@CustomDefaultLayoutActivity,
                        response.message ?: "加载航线失败",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载航线任务失败: ${e.message}", e)
                Toast.makeText(this@CustomDefaultLayoutActivity, "加载航线失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 在 AMap 上绘制航线轨迹和航点标记
     */
    private fun drawMissionRoute(aMap: AMap, waypoints: List<Waypoint>) {
        // 清除旧的航线
        clearMissionRoute()

        // WGS-84 → GCJ-02 坐标转换后的航点
        val gcjPoints = mutableListOf<LatLng>()
        for (wp in waypoints) {
            val (gcjLat, gcjLng) = CoordinateConverter.wgs84ToGcj02(wp.lat, wp.lng)
            gcjPoints.add(LatLng(gcjLat, gcjLng))
        }

        if (gcjPoints.isEmpty()) return

        // 绘制蓝色虚线折线
        val polylineOptions = PolylineOptions()
            .addAll(gcjPoints)
            .width(8f)
            .color(0xFF2563EB.toInt()) // primary blue
            .setDottedLine(true)
        missionRoutePolyline = aMap.addPolyline(polylineOptions)

        // 绘制航点标记（带编号圆形图标）
        val density = resources.displayMetrics.density
        val markerSize = (32 * density).toInt()

        for ((index, point) in gcjPoints.withIndex()) {
            val wp = waypoints[index]
            val seq = wp.seq ?: (index + 1)
            val snippet = buildString {
                append("航点 $seq")
                wp.alt?.let { append("\n高度: %.1fm".format(it)) }
                wp.speed?.let { append("\n速度: %.1fm/s".format(it)) }
                wp.hoverTime?.let { if (it > 0) append("\n悬停: ${it}秒") }
            }

            // 起点绿色、终点红色、中间蓝色
            val bgColor = when (index) {
                0 -> 0xFF10B981.toInt()              // green
                gcjPoints.size - 1 -> 0xFFEF4444.toInt()  // red
                else -> 0xFF2563EB.toInt()            // blue
            }
            val icon = createNumberedMarkerBitmap(seq, bgColor, markerSize)

            val markerOptions = MarkerOptions()
                .position(point)
                .title("航点 $seq")
                .snippet(snippet)
                .anchor(0.5f, 0.5f)
                .icon(BitmapDescriptorFactory.fromBitmap(icon))

            val marker = aMap.addMarker(markerOptions)
            missionRouteMarkers.add(marker)
        }

        // 地图视角自适应包含所有航点
        val boundsBuilder = LatLngBounds.Builder()
        for (point in gcjPoints) {
            boundsBuilder.include(point)
        }
        try {
            val bounds = boundsBuilder.build()
            aMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
        } catch (e: Exception) {
            // 如果只有一个点，直接移动到该点
            aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(gcjPoints[0], 16f))
        }

        Log.i(TAG, "航线绘制完成，共 ${gcjPoints.size} 个航点")
    }

    /**
     * 创建带编号的圆形 Marker 图标
     */
    private fun createNumberedMarkerBitmap(number: Int, bgColor: Int, size: Int): android.graphics.Bitmap {
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val cx = size / 2f
        val cy = size / 2f
        val radius = size / 2f - 2f

        // 白色描边
        val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, radius, strokePaint)

        // 彩色圆形背景
        val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, radius - 3f, bgPaint)

        // 编号文字
        val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = size * 0.45f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val textBounds = android.graphics.Rect()
        val text = number.toString()
        textPaint.getTextBounds(text, 0, text.length, textBounds)
        val textY = cy + textBounds.height() / 2f
        canvas.drawText(text, cx, textY, textPaint)

        return bitmap
    }

    /**
     * 清除地图上的航线叠加
     */
    private fun clearMissionRoute() {
        missionRoutePolyline?.remove()
        missionRoutePolyline = null
        for (marker in missionRouteMarkers) {
            marker.remove()
        }
        missionRouteMarkers.clear()
    }

    // ==================== 隐藏系统状态栏 ====================

    /**
     * 隐藏系统状态栏，实现全屏沉浸式体验
     */
    private fun hideSystemUI() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // Android 11 (API 30) 及以上使用 WindowInsetsController
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(android.view.WindowInsets.Type.statusBars())
                controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // Android 11 以下使用 systemUiVisibility
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        }
    }

    // ==================== 生命周期 ====================

    override fun onDestroy() {
        super.onDestroy()
        rxDisposables.clear()

        // 清除航线叠加
        clearMissionRoute()

        // 断开 WebSocket 连接
        disconnectVideoStream()

        // 如果飞行记录还在进行中，异常结束
        if (isFlightRecording && currentFlightId != null) {
            Log.w(TAG, "Activity 销毁时飞行记录仍在进行，标记为异常终止")
            isFlightRecording = false
            pathUploadDisposable?.dispose()
            val flightId = currentFlightId!!
            // 使用新线程发送同步请求（lifecycleScope 已销毁无法使用）
            Thread {
                try {
                    val json = org.json.JSONObject().apply {
                        put("flightId", flightId)
                        put("maxAltitude", flightMaxAltitude)
                        put("maxSpeed", flightMaxSpeed)
                        put("distance", flightDistance)
                        put("status", 2) // 2=异常终止
                    }.toString()
                    val token = com.example.skyflowtracker.utils.TokenManager.getToken() ?: ""
                    val body = json.toRequestBody("application/json".toMediaTypeOrNull())
                    val request = okhttp3.Request.Builder()
                        .url("${RetrofitClient.BASE_URL}flights/end")
                        .put(body)
                        .header("Authorization", token)
                        .build()
                    val client = okhttp3.OkHttpClient()
                    val response = client.newCall(request).execute()
                    Log.w(TAG, "onDestroy 结束飞行记录: flightId=$flightId, code=${response.code}")
                    response.close()
                } catch (e: Exception) {
                    Log.e(TAG, "onDestroy 结束飞行记录失败: ${e.message}")
                }
            }.start()
        }

        // 清理飞控连接监听
        KeyManager.getInstance().cancelListen(this)
    }
}
