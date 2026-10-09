package com.example.skyflowtracker.api.model

/**
 * 禁飞区同步相关数据模型
 */

// 同步请求
data class FlyZoneSyncRequest(
    val zones: List<FlyZoneItem>
)

// 禁飞区数据项
data class FlyZoneItem(
    val djiFlyZoneId: Int,
    val name: String,
    val category: Int,        // 0=WARNING 1=ENHANCED_WARNING 2=AUTHORIZATION 3=RESTRICTED
    val shape: Int,           // 0=圆形 1=多边形
    val centerLat: Double?,
    val centerLng: Double?,
    val radius: Double?,
    val polygonPoints: List<LatLngPoint>?,
    val maxAltitude: Double?
)

// 经纬度坐标点
data class LatLngPoint(
    val lat: Double,
    val lng: Double
)

// 同步响应
data class FlyZoneSyncResponse(
    val inserted: Int,
    val skipped: Int
)
