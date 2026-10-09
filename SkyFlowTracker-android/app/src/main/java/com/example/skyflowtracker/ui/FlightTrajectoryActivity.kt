package com.example.skyflowtracker.ui

import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.*
import com.example.skyflowtracker.R
import com.example.skyflowtracker.api.RetrofitClient
import com.example.skyflowtracker.api.model.FlightDetail
import com.example.skyflowtracker.api.model.PathPoint
import com.example.skyflowtracker.utils.CoordinateConverter
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

/**
 * 飞行轨迹回放 Activity
 * 使用高德地图显示航线轨迹，支持播放控制
 */
class FlightTrajectoryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FLIGHT_ID = "flight_id"
    }

    // 地图相关
    private lateinit var mapView: MapView
    private var aMap: AMap? = null
    private var fullPolyline: Polyline? = null    // 完整航线（灰色虚线）
    private var activePolyline: Polyline? = null   // 已播放航线（蓝色实线）
    private var droneMarker: Marker? = null        // 无人机图标
    private var homeMarker: Marker? = null         // 起飞点标记

    // 播放相关
    private var flightPath: List<PathPoint> = emptyList()
    private var isPlaying = false
    private var playSpeed = 1.0f
    private var currentFlightTimeMs = 0.0  // 当前播放到的飞行时间（相对于第一个点，ms）
    private var animator: ValueAnimator? = null

    // 倍速选项
    private val speedOptions = floatArrayOf(0.5f, 1f, 2f, 4f, 8f)
    private var speedIndex = 1 // 默认 1x

    // UI 控件
    private lateinit var seekBar: SeekBar
    private lateinit var btnPlayPause: ImageView
    private lateinit var tvTime: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var tvInfoAlt: TextView
    private lateinit var tvInfoSpeed: TextView
    private lateinit var tvInfoHeading: TextView
    private lateinit var cardInfo: MaterialCardView
    private lateinit var cardControls: MaterialCardView
    private lateinit var llNoPath: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flight_trajectory)

        // 初始化地图
        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        aMap = mapView.map
        aMap?.uiSettings?.apply {
            isZoomControlsEnabled = false
            isMyLocationButtonEnabled = false
        }

        initViews()
        loadFlightDetail()
    }

    private fun initViews() {
        seekBar = findViewById(R.id.seekBar)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        tvTime = findViewById(R.id.tvTime)
        tvSpeed = findViewById(R.id.tvSpeed)
        tvInfoAlt = findViewById(R.id.tvInfoAlt)
        tvInfoSpeed = findViewById(R.id.tvInfoSpeed)
        tvInfoHeading = findViewById(R.id.tvInfoHeading)
        cardInfo = findViewById(R.id.cardInfo)
        cardControls = findViewById(R.id.cardControls)
        llNoPath = findViewById(R.id.llNoPath)

        // 返回按钮
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        // 播放/暂停
        btnPlayPause.setOnClickListener { togglePlay() }

        // 跳到开头
        findViewById<ImageView>(R.id.btnSkipStart).setOnClickListener { skipToStart() }

        // 跳到结尾
        findViewById<ImageView>(R.id.btnSkipEnd).setOnClickListener { skipToEnd() }

        // 倍速切换
        tvSpeed.setOnClickListener {
            speedIndex = (speedIndex + 1) % speedOptions.size
            playSpeed = speedOptions[speedIndex]
            tvSpeed.text = if (playSpeed == playSpeed.toLong().toFloat()) "${playSpeed.toInt()}x" else "${playSpeed}x"
            // 如果正在播放，重启动画
            if (isPlaying) {
                startAnimation()
            }
        }

        // 进度条拖拽
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            private var wasPlaying = false
            override fun onStartTrackingTouch(sb: SeekBar) {
                wasPlaying = isPlaying
                pausePlay()
            }
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser && flightPath.isNotEmpty()) {
                    val totalMs = getTotalDurationMs()
                    currentFlightTimeMs = (progress / 1000.0) * totalMs
                    updateMapState()
                    updateTimeDisplay()
                }
            }
            override fun onStopTrackingTouch(sb: SeekBar) {
                if (wasPlaying) startPlay()
            }
        })
    }

    /**
     * 加载飞行详情
     */
    private fun loadFlightDetail() {
        val flightId = intent.getLongExtra(EXTRA_FLIGHT_ID, -1)
        if (flightId == -1L) {
            Toast.makeText(this, "无效的飞行记录ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getFlightDetail(flightId)
                if (response.code == 1 && response.data != null) {
                    onDetailLoaded(response.data)
                } else {
                    Toast.makeText(this@FlightTrajectoryActivity, response.message ?: "获取详情失败", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Toast.makeText(this@FlightTrajectoryActivity, "获取详情失败: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    /**
     * 详情加载完成，渲染地图
     */
    private fun onDetailLoaded(detail: FlightDetail) {
        val path = detail.flightPath
        if (path.isNullOrEmpty()) {
            llNoPath.visibility = View.VISIBLE
            cardControls.visibility = View.GONE
            cardInfo.visibility = View.GONE
            return
        }

        flightPath = path
        cardInfo.visibility = View.VISIBLE
        renderFlightOnMap(detail)
        updateMapState()
        updateTimeDisplay()
    }

    /**
     * 渲染航线到地图
     */
    private fun renderFlightOnMap(detail: FlightDetail) {
        val map = aMap ?: return
        if (flightPath.isEmpty()) return

        // WGS-84 → GCJ-02 坐标转换（高德地图使用 GCJ-02）
        val points = flightPath.map {
            val (gcjLat, gcjLng) = CoordinateConverter.wgs84ToGcj02(it.lat, it.lng)
            LatLng(gcjLat, gcjLng)
        }

        // 完整航线（灰色虚线）
        fullPolyline = map.addPolyline(
            PolylineOptions()
                .addAll(points)
                .width(6f)
                .color(Color.parseColor("#9CA3AF"))
                .setDottedLine(true)
        )

        // 已播放航线（蓝色实线）
        activePolyline = map.addPolyline(
            PolylineOptions()
                .add(points[0])
                .width(8f)
                .color(Color.parseColor("#2563EB"))
        )

        // 起飞点 H 标记（也需要坐标转换）
        val homeLat = detail.homeLatitude ?: flightPath[0].lat
        val homeLng = detail.homeLongitude ?: flightPath[0].lng
        val (gcjHomeLat, gcjHomeLng) = CoordinateConverter.wgs84ToGcj02(homeLat, homeLng)
        homeMarker = map.addMarker(
            MarkerOptions()
                .position(LatLng(gcjHomeLat, gcjHomeLng))
                .title("起飞点")
                .anchor(0.5f, 0.5f)
                .icon(BitmapDescriptorFactory.fromBitmap(createHomeIcon()))
        )

        // 无人机图标
        droneMarker = map.addMarker(
            MarkerOptions()
                .position(points[0])
                .anchor(0.5f, 0.5f)
                .setFlat(true)
                .icon(BitmapDescriptorFactory.fromBitmap(createDroneIcon()))
        )

        // 适配视图
        val boundsBuilder = LatLngBounds.Builder()
        points.forEach { boundsBuilder.include(it) }
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100))
    }

    /**
     * 创建起飞点 H 图标
     */
    private fun createHomeIcon(): android.graphics.Bitmap {
        val size = (36 * resources.displayMetrics.density).toInt()
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

        // 黄色圆形背景
        paint.color = Color.parseColor("#F59E0B")
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        // 白色边框
        paint.style = android.graphics.Paint.Style.STROKE
        paint.color = Color.WHITE
        paint.strokeWidth = 3f * resources.displayMetrics.density
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - paint.strokeWidth / 2, paint)

        // H 文字
        paint.style = android.graphics.Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = 18f * resources.displayMetrics.density
        paint.textAlign = android.graphics.Paint.Align.CENTER
        val textY = size / 2f - (paint.descent() + paint.ascent()) / 2
        canvas.drawText("H", size / 2f, textY, paint)

        return bitmap
    }

    /**
     * 创建无人机图标（蓝色三角箭头）
     */
    private fun createDroneIcon(): android.graphics.Bitmap {
        val size = (32 * resources.displayMetrics.density).toInt()
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

        paint.color = Color.parseColor("#2563EB")
        paint.style = android.graphics.Paint.Style.FILL

        val path = android.graphics.Path()
        val cx = size / 2f
        // 箭头朝上
        path.moveTo(cx, size * 0.1f)           // 顶部
        path.lineTo(size * 0.25f, size * 0.85f) // 左下
        path.lineTo(cx, size * 0.7f)            // 中间凹
        path.lineTo(size * 0.75f, size * 0.85f) // 右下
        path.close()
        canvas.drawPath(path, paint)

        return bitmap
    }

    // ==================== 播放控制 ====================

    private fun getTotalDurationMs(): Double {
        if (flightPath.size < 2) return 0.0
        return (flightPath.last().ts - flightPath.first().ts).toDouble()
    }

    private fun togglePlay() {
        if (flightPath.isEmpty()) return
        if (isPlaying) pausePlay() else startPlay()
    }

    private fun startPlay() {
        val totalMs = getTotalDurationMs()
        if (totalMs <= 0) return
        // 如果已到末尾，从头开始
        if (currentFlightTimeMs >= totalMs) {
            currentFlightTimeMs = 0.0
        }
        isPlaying = true
        btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
        startAnimation()
    }

    private fun pausePlay() {
        isPlaying = false
        animator?.cancel()
        animator = null
        btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
    }

    private fun skipToStart() {
        pausePlay()
        currentFlightTimeMs = 0.0
        seekBar.progress = 0
        updateMapState()
        updateTimeDisplay()
    }

    private fun skipToEnd() {
        pausePlay()
        currentFlightTimeMs = getTotalDurationMs()
        seekBar.progress = 1000
        updateMapState()
        updateTimeDisplay()
    }

    /**
     * 使用 ValueAnimator 驱动平滑播放
     */
    private fun startAnimation() {
        animator?.cancel()
        val totalMs = getTotalDurationMs()
        if (totalMs <= 0) return

        val remainMs = totalMs - currentFlightTimeMs
        val animDuration = (remainMs / playSpeed).toLong().coerceAtLeast(100)
        val startVal = currentFlightTimeMs.toFloat()
        val endVal = totalMs.toFloat()

        animator = ValueAnimator.ofFloat(startVal, endVal).apply {
            duration = animDuration
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                currentFlightTimeMs = (anim.animatedValue as Float).toDouble()
                val progress = ((currentFlightTimeMs / totalMs) * 1000).toInt().coerceIn(0, 1000)
                seekBar.progress = progress
                updateMapState()
                updateTimeDisplay()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (isPlaying) {
                        isPlaying = false
                        btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                    }
                }
            })
            start()
        }
    }

    /**
     * 在两个原始点之间线性插值，更新地图标记
     */
    private fun updateMapState() {
        if (flightPath.isEmpty()) return
        val pts = flightPath
        val t0 = pts[0].ts
        val absTime = t0 + currentFlightTimeMs

        // 找到当前时间所在的区间
        var i = 0
        for (idx in 0 until pts.size - 1) {
            if (pts[idx + 1].ts >= absTime) { i = idx; break }
            i = idx
        }
        if (i >= pts.size - 1) i = pts.size - 2
        if (i < 0) i = 0

        val p0 = pts[i]
        val p1 = pts[(i + 1).coerceAtMost(pts.size - 1)]
        val segDur = (p1.ts - p0.ts).toDouble()
        val ratio = if (segDur > 0) ((absTime - p0.ts) / segDur).coerceIn(0.0, 1.0) else 0.0

        val curLat = p0.lat + (p1.lat - p0.lat) * ratio
        val curLng = p0.lng + (p1.lng - p0.lng) * ratio
        val curAlt = lerpNullable(p0.alt, p1.alt, ratio)
        val curSpeed = lerpNullable(p0.speed, p1.speed, ratio)
        val curHeading = lerpNullable(p0.heading, p1.heading, ratio)

        // WGS-84 → GCJ-02 坐标转换
        val (gcjLat, gcjLng) = CoordinateConverter.wgs84ToGcj02(curLat, curLng)
        val curPos = LatLng(gcjLat, gcjLng)

        // 更新无人机位置和旋转
        droneMarker?.position = curPos
        if (curHeading != null) {
            droneMarker?.rotateAngle = -curHeading.toFloat() // 高德地图旋转角度是逆时针
        }

        // 更新已播放航线（坐标也需要转换）
        val activePts = mutableListOf<LatLng>()
        for (p in pts) {
            if (p.ts <= absTime) {
                val (aLat, aLng) = CoordinateConverter.wgs84ToGcj02(p.lat, p.lng)
                activePts.add(LatLng(aLat, aLng))
            }
            else break
        }
        activePts.add(curPos)
        activePolyline?.points = activePts

        // 更新信息面板
        tvInfoAlt.text = curAlt?.let { "%.1fm".format(it) } ?: "-"
        tvInfoSpeed.text = curSpeed?.let { "%.1fm/s".format(it) } ?: "-"
        tvInfoHeading.text = curHeading?.let { "%.0f°".format(it) } ?: "-"
    }

    private fun lerpNullable(a: Double?, b: Double?, ratio: Double): Double? {
        if (a == null || b == null) return a ?: b
        return a + (b - a) * ratio
    }

    /**
     * 更新时间显示
     */
    private fun updateTimeDisplay() {
        val totalSec = (getTotalDurationMs() / 1000).toInt()
        val curSec = (currentFlightTimeMs / 1000).toInt()
        tvTime.text = "${formatTime(curSec)} / ${formatTime(totalSec)}"
    }

    private fun formatTime(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%02d:%02d".format(m, s)
    }

    // ==================== 生命周期 ====================

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        pausePlay()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        animator?.cancel()
        mapView.onDestroy()
    }
}
