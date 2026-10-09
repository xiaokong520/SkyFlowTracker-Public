package com.example.skyflowtracker.flysafe

import android.content.Context
import android.util.Log
import com.example.skyflowtracker.api.RetrofitClient
import com.example.skyflowtracker.api.model.FlyZoneItem
import com.example.skyflowtracker.api.model.FlyZoneSyncRequest
import com.example.skyflowtracker.api.model.LatLngPoint
import dji.sdk.keyvalue.value.common.LocationCoordinate2D
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.manager.aircraft.flysafe.FlyZoneManager
import dji.v5.manager.aircraft.flysafe.info.FlyZoneCategory
import dji.v5.manager.aircraft.flysafe.info.FlyZoneInformation
import dji.v5.manager.aircraft.flysafe.info.FlyZoneShape
import dji.v5.manager.aircraft.flysafe.info.MultiPolygonFlyZoneShape
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * 禁飞区同步管理器
 * 从 DJI SDK 获取中国主要城市/机场的禁飞区数据，批量上传到后端
 */
class FlyZoneSyncManager(private val context: Context) {

    companion object {
        private const val TAG = "FlyZoneSyncManager"
        private const val PREF_NAME = "fly_zone_sync"
        private const val KEY_SYNCED = "fly_zone_synced"
        private const val KEY_SYNC_TIME = "fly_zone_sync_time"
        private const val SYNC_INTERVAL_DAYS = 30L
        // 每次查询间隔（毫秒），避免请求过快
        private const val QUERY_DELAY_MS = 500L
        // 每批上传的禁飞区数量
        private const val BATCH_SIZE = 100

        /**
         * 中国主要城市/机场坐标列表（WGS-84坐标系）
         * 覆盖省会城市和主要国际/国内机场
         */
        val MAJOR_COORDINATES = listOf(
            // 直辖市
            LocationCoordinate2D(39.9042, 116.4074),  // 北京
            LocationCoordinate2D(40.0799, 116.5844),  // 北京首都机场
            LocationCoordinate2D(39.5098, 116.4105),  // 北京大兴机场
            LocationCoordinate2D(31.2304, 121.4737),  // 上海
            LocationCoordinate2D(31.1443, 121.8052),  // 上海浦东机场
            LocationCoordinate2D(31.1979, 121.3363),  // 上海虹桥机场
            LocationCoordinate2D(29.5630, 106.5516),  // 重庆
            LocationCoordinate2D(39.0842, 117.2009),  // 天津
            // 华东
            LocationCoordinate2D(30.2741, 120.1551),  // 杭州
            LocationCoordinate2D(32.0603, 118.7969),  // 南京
            LocationCoordinate2D(31.4912, 117.2700),  // 合肥
            LocationCoordinate2D(26.0745, 119.2965),  // 福州
            LocationCoordinate2D(24.4795, 118.0894),  // 厦门
            LocationCoordinate2D(36.6512, 117.1201),  // 济南
            LocationCoordinate2D(36.0671, 120.3826),  // 青岛
            LocationCoordinate2D(28.6820, 115.8579),  // 南昌
            // 华南
            LocationCoordinate2D(23.1291, 113.2644),  // 广州
            LocationCoordinate2D(23.3924, 113.2988),  // 广州白云机场
            LocationCoordinate2D(22.5431, 114.0579),  // 深圳
            LocationCoordinate2D(22.6395, 108.2294),  // 南宁
            LocationCoordinate2D(20.0174, 110.3492),  // 海口
            LocationCoordinate2D(18.2528, 109.5120),  // 三亚
            LocationCoordinate2D(22.3193, 114.1694),  // 香港
            LocationCoordinate2D(22.1987, 113.5439),  // 澳门
            // 华中
            LocationCoordinate2D(30.5928, 114.3055),  // 武汉
            LocationCoordinate2D(28.2280, 112.9388),  // 长沙
            LocationCoordinate2D(34.7466, 113.6253),  // 郑州
            // 华北
            LocationCoordinate2D(37.8706, 112.5489),  // 太原
            LocationCoordinate2D(38.0428, 114.5149),  // 石家庄
            LocationCoordinate2D(40.8426, 111.7500),  // 呼和浩特
            // 东北
            LocationCoordinate2D(41.8057, 123.4315),  // 沈阳
            LocationCoordinate2D(43.8171, 125.3235),  // 长春
            LocationCoordinate2D(45.7565, 126.6527),  // 哈尔滨
            LocationCoordinate2D(38.9140, 121.6147),  // 大连
            // 西南
            LocationCoordinate2D(30.5728, 104.0668),  // 成都
            LocationCoordinate2D(30.5785, 103.9471),  // 成都双流机场
            LocationCoordinate2D(30.3147, 104.4412),  // 成都天府机场
            LocationCoordinate2D(25.0389, 102.7183),  // 昆明
            LocationCoordinate2D(26.6470, 106.6302),  // 贵阳
            LocationCoordinate2D(29.4316, 106.9123),  // 重庆江北机场
            LocationCoordinate2D(22.7866, 108.3524),  // 南宁吴圩机场
            // 西北
            LocationCoordinate2D(34.2658, 108.9541),  // 西安
            LocationCoordinate2D(36.0611, 103.8343),  // 兰州
            LocationCoordinate2D(36.6171, 101.7691),  // 西宁
            LocationCoordinate2D(38.4872, 106.2309),  // 银川
            LocationCoordinate2D(43.7930, 87.6271),   // 乌鲁木齐
            LocationCoordinate2D(29.6500, 91.1409),   // 拉萨
            // 其他重要机场
            LocationCoordinate2D(27.8943, 112.6075),  // 长沙黄花机场
            LocationCoordinate2D(34.4371, 108.7517),  // 西安咸阳机场
            LocationCoordinate2D(30.5805, 103.9480),  // 成都双流
            LocationCoordinate2D(23.3959, 113.3067),  // 广州白云
            LocationCoordinate2D(22.6361, 113.8106),  // 深圳宝安机场
            LocationCoordinate2D(25.1008, 102.9419),  // 昆明长水机场
        )
    }

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /**
     * 检查是否需要同步
     */
    fun needsSync(): Boolean {
        val synced = prefs.getBoolean(KEY_SYNCED, false)
        if (!synced) return true

        val lastSyncTime = prefs.getLong(KEY_SYNC_TIME, 0)
        val daysSinceSync = (System.currentTimeMillis() - lastSyncTime) / (1000 * 60 * 60 * 24)
        return daysSinceSync >= SYNC_INTERVAL_DAYS
    }

    /**
     * 执行禁飞区数据同步
     * 在协程中调用
     */
    suspend fun performSync() {
        Log.i(TAG, "开始同步禁飞区数据，共 ${MAJOR_COORDINATES.size} 个坐标点")

        // 收集所有禁飞区数据，按 flyZoneID 去重
        val allZones = mutableMapOf<Int, FlyZoneItem>()
        var queryCount = 0
        var errorCount = 0

        for (coordinate in MAJOR_COORDINATES) {
            try {
                val zones = queryFlyZones(coordinate)
                for (zone in zones) {
                    for (item in convertToFlyZoneItems(zone)) {
                        if (!allZones.containsKey(item.djiFlyZoneId)) {
                            allZones[item.djiFlyZoneId] = item
                        }
                    }
                }
                queryCount++
                Log.d(TAG, "查询进度: $queryCount/${MAJOR_COORDINATES.size}，累计发现 ${allZones.size} 个禁飞区")
                delay(QUERY_DELAY_MS)
            } catch (e: Exception) {
                errorCount++
                Log.w(TAG, "查询坐标 (${coordinate.latitude}, ${coordinate.longitude}) 失败: ${e.message}")
            }
        }

        Log.i(TAG, "禁飞区数据收集完成，共 ${allZones.size} 个唯一禁飞区，${errorCount} 个查询失败")

        if (allZones.isEmpty()) {
            Log.w(TAG, "未获取到任何禁飞区数据，跳过上传")
            return
        }

        // 分批上传到后端
        val zonesList = allZones.values.toList()
        var uploadedCount = 0
        for (batch in zonesList.chunked(BATCH_SIZE)) {
            try {
                val request = FlyZoneSyncRequest(zones = batch)
                val response = RetrofitClient.apiService.syncFlyZones(request)
                if (response.code == 1) {
                    val data = response.data
                    uploadedCount += data?.inserted ?: 0
                    Log.i(TAG, "批次上传成功: 新增 ${data?.inserted}，跳过 ${data?.skipped}")
                } else {
                    Log.w(TAG, "批次上传失败: ${response.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "批次上传异常: ${e.message}", e)
            }
        }

        Log.i(TAG, "禁飞区同步完成，共上传 $uploadedCount 个新禁飞区")

        // 标记同步完成
        prefs.edit()
            .putBoolean(KEY_SYNCED, true)
            .putLong(KEY_SYNC_TIME, System.currentTimeMillis())
            .apply()
    }

    /**
     * 查询指定坐标周围的禁飞区（使用 suspendCoroutine 将回调转为挂起）
     */
    private suspend fun queryFlyZones(location: LocationCoordinate2D): List<FlyZoneInformation> {
        return suspendCoroutine { continuation ->
            FlyZoneManager.getInstance().getFlyZonesInSurroundingArea(
                location,
                object : CommonCallbacks.CompletionCallbackWithParam<MutableList<FlyZoneInformation>> {
                    override fun onSuccess(zones: MutableList<FlyZoneInformation>?) {
                        continuation.resume(zones ?: emptyList())
                    }

                    override fun onFailure(error: IDJIError) {
                        Log.w(TAG, "查询禁飞区失败: ${error.description()}")
                        continuation.resume(emptyList())
                    }
                }
            )
        }
    }

    /**
     * 将 DJI FlyZoneInformation 转换为上传用的 FlyZoneItem 列表
     * MULTI_POLYGON 类型会展开所有子区域，每个子区域生成独立的 FlyZoneItem
     * 坐标保持 WGS-84 不做转换
     */
    private fun convertToFlyZoneItems(zone: FlyZoneInformation): List<FlyZoneItem> {
        val categoryInt = when (zone.category) {
            FlyZoneCategory.WARNING -> 0
            FlyZoneCategory.ENHANCED_WARNING -> 1
            FlyZoneCategory.AUTHORIZATION -> 2
            FlyZoneCategory.RESTRICTED -> 3
            else -> return emptyList()
        }

        return when (zone.shape) {
            FlyZoneShape.CIRCLE -> {
                listOf(FlyZoneItem(
                    djiFlyZoneId = zone.flyZoneID,
                    name = zone.name ?: "未命名区域",
                    category = categoryInt,
                    shape = 0, // 圆形
                    centerLat = zone.circleCenter.latitude,
                    centerLng = zone.circleCenter.longitude,
                    radius = zone.circleRadius,
                    polygonPoints = null,
                    maxAltitude = null
                ))
            }
            FlyZoneShape.MULTI_POLYGON -> {
                // 遍历所有子区域，每个子区域生成独立的 FlyZoneItem
                val subZones = zone.multiPolygonFlyZoneInformation
                if (subZones.isNullOrEmpty()) return emptyList()

                val items = mutableListOf<FlyZoneItem>()
                subZones.forEachIndexed { index, subZone ->
                    // 第一个子区域沿用父级 flyZoneID（向后兼容），后续子区域编码为 flyZoneID * 100 + index
                    val subDjiFlyZoneId = if (index == 0) zone.flyZoneID else zone.flyZoneID * 100 + index

                    // 限高子区域属于授权区而非禁飞区（可在限高以下飞行）
                    val subCategoryInt = if (categoryInt == 3 && subZone.limitedHeight != 0) 2 else categoryInt

                    when (subZone.shape) {
                        MultiPolygonFlyZoneShape.POLYGON -> {
                            val points = subZone.polygonPoints?.map {
                                LatLngPoint(lat = it.latitude, lng = it.longitude)
                            }
                            if (!points.isNullOrEmpty()) {
                                items.add(FlyZoneItem(
                                    djiFlyZoneId = subDjiFlyZoneId,
                                    name = zone.name ?: "未命名区域",
                                    category = subCategoryInt,
                                    shape = 1, // 多边形
                                    centerLat = null,
                                    centerLng = null,
                                    radius = null,
                                    polygonPoints = points,
                                    maxAltitude = if (subZone.limitedHeight != 0) subZone.limitedHeight.toDouble() else null
                                ))
                            }
                        }
                        MultiPolygonFlyZoneShape.CYLINDER -> {
                            // 圆柱形用圆形表示（与 AMapFlySafeHelper 逻辑一致，使用父级 zone 的数据）
                            items.add(FlyZoneItem(
                                djiFlyZoneId = subDjiFlyZoneId,
                                name = zone.name ?: "未命名区域",
                                category = subCategoryInt,
                                shape = 0, // 圆形
                                centerLat = zone.circleCenter.latitude,
                                centerLng = zone.circleCenter.longitude,
                                radius = zone.circleRadius,
                                polygonPoints = null,
                                maxAltitude = if (subZone.limitedHeight != 0) subZone.limitedHeight.toDouble() else null
                            ))
                        }
                        else -> Log.w(TAG, "未知的子限飞区形状: ${subZone.shape}")
                    }
                }
                items
            }
            else -> emptyList()
        }
    }

    /**
     * 从飞行界面获取的实时限飞区数据同步到后端
     * 解决批量同步时 SDK 未就绪导致 WARNING/ENHANCED_WARNING 区域缺失的问题
     */
    suspend fun syncFromLiveData(zones: List<FlyZoneInformation>) {
        if (zones.isEmpty()) return

        val items = mutableListOf<FlyZoneItem>()
        for (zone in zones) {
            items.addAll(convertToFlyZoneItems(zone))
        }

        if (items.isEmpty()) {
            Log.d(TAG, "实时同步：无有效限飞区数据")
            return
        }

        Log.i(TAG, "实时同步：准备上传 ${items.size} 个限飞区")
        try {
            val request = FlyZoneSyncRequest(zones = items)
            val response = RetrofitClient.apiService.syncFlyZones(request)
            if (response.code == 1) {
                val data = response.data
                if ((data?.inserted ?: 0) > 0) {
                    Log.i(TAG, "实时同步成功: 新增 ${data?.inserted}，跳过 ${data?.skipped}")
                }
            } else {
                Log.w(TAG, "实时同步失败: ${response.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "实时同步异常: ${e.message}")
        }
    }

    /**
     * 重置同步状态，使下次批量同步重新执行
     */
    fun resetSyncState() {
        prefs.edit()
            .putBoolean(KEY_SYNCED, false)
            .remove(KEY_SYNC_TIME)
            .apply()
        Log.i(TAG, "同步状态已重置")
    }
}
