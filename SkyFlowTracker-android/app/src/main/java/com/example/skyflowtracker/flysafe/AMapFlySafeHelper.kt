package com.example.skyflowtracker.flysafe

import android.content.Context
import android.graphics.Color
import android.util.Log
import androidx.core.graphics.ColorUtils
import com.amap.api.maps.AMap
import com.amap.api.maps.model.Circle
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.Polygon
import dji.v5.manager.aircraft.flysafe.info.FlyZoneCategory
import java.util.concurrent.ConcurrentHashMap
import androidx.core.graphics.toColorInt
import androidx.transition.Visibility
import com.amap.api.maps.model.CircleOptions
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.PolygonOptions
import dji.v5.manager.aircraft.flysafe.info.FlyZoneInformation
import dji.v5.manager.aircraft.flysafe.info.FlyZoneShape
import dji.v5.manager.aircraft.flysafe.info.MultiPolygonFlyZoneShape
import java.time.ZoneId

//高德地图限飞/禁飞区绘制辅助类
class AMapFlySafeHelper(
    private val context: Context
) {
    private val TAG = "AMapFlySafeHelper"
    private var aMap: AMap? = null

    //存储不同类型限飞区的圆形
    private val warningCircleMap = ConcurrentHashMap<String, Circle>()
    private val enhancedWarningCircleMap = ConcurrentHashMap<String, Circle>()
    private val authorizationCircleMap = ConcurrentHashMap<String, Circle>()
    private val restrictedAreaCircleMap = ConcurrentHashMap<String, Circle>()

    // 存储不同类型限飞区的多边形
    private val warningPolygonMap = ConcurrentHashMap<String, Polygon>()
    private val enhancedWarningPolygonMap = ConcurrentHashMap<String, Polygon>()
    private val authorizationPolygonMap = ConcurrentHashMap<String, Polygon>()
    private val restrictedPolygonMap = ConcurrentHashMap<String, Polygon>()

    // 限飞区标记
    private val flyZoneMarkerMap = ConcurrentHashMap<String, Marker>()

    // 限飞区颜色配置（参考 DJI 官方配色）
    private val flyZoneColorMap = mapOf(
        FlyZoneCategory.WARNING to "#FFD700".toColorInt(), //黄色
        FlyZoneCategory.ENHANCED_WARNING to "#FF8C00".toColorInt(), //橙色
        FlyZoneCategory.AUTHORIZATION to "#4169E1".toColorInt(), //蓝色
        FlyZoneCategory.RESTRICTED to "#DC143C".toColorInt() //红色
    )

    private val DEFAULT_ALPHA = 40  // 透明度 (0-255)
    private val DEFAULT_STROKE_WIDTH = 3f

    //设置要绘制限飞区的地图实例
    fun setMap(map: AMap) {
        this.aMap = map
        Log.i(TAG, "高德地图实例已设置")
    }

    //更新限飞区列表
    fun onFlyZoneListUpdate(flyZoneList: List<FlyZoneInformation>){
        aMap?:run {
            Log.w(TAG, "请先设置高德地图实例")
            return
        }
        Log.i(TAG, "限飞区列表已更新，数量：${flyZoneList.size}")
        //清除旧的限飞区
        removeFlyZones()

        //绘制新的限飞区
        for (flyZone in flyZoneList){
            drawFlyZone(flyZone)
        }
        Log.i(TAG, "限飞区绘制完成")
    }

    //绘制单个限飞区
    private fun drawFlyZone(zone: FlyZoneInformation){
        val zoneId = zone.flyZoneID.toString()
        val category = zone.category
        val color = flyZoneColorMap[category] ?: Color.TRANSPARENT
        Log.i(TAG, "绘制限飞区：$zoneId，类型：$category，颜色：$color")

        when(zone.shape){
            FlyZoneShape.CIRCLE ->
                //绘制圆形限飞区
                drawCircleFlyZone(zone,zoneId,color)
            FlyZoneShape.MULTI_POLYGON ->
                //绘制多边形限飞区
                drawMultiPolygonFlyZone(zone,zoneId,color)
            else ->
                Log.w(TAG, "不支持的限飞区形状: ${zone.shape}")
        }
    }

    //绘制圆形限飞区
    private fun drawCircleFlyZone(zone: FlyZoneInformation, zoneID: String, color: Int) {
        val center = zone.circleCenter
        val radius = zone.circleRadius

        Log.d(TAG, "绘制圆形限飞区: 中心=(${center.latitude}, ${center.longitude}), 半径=${radius}米")

        val circleOptions = CircleOptions()
            .center(LatLng(center.latitude, center.longitude))
            .radius(radius)
            .strokeColor(color)
            .strokeWidth(DEFAULT_STROKE_WIDTH)
            .fillColor(ColorUtils.setAlphaComponent(color, DEFAULT_ALPHA))

        val circle = aMap?.addCircle(circleOptions)
        circle?.let {
            addCircleToMap(zone.category, zoneID, it)
        }
    }

    //绘制多边形限飞区
    private fun drawMultiPolygonFlyZone(zone: FlyZoneInformation, zoneID: String, color: Int) {
        zone.multiPolygonFlyZoneInformation?.forEach { subZone ->
            val subZoneID = "${zone.flyZoneID}_${subZone.flyZoneID}"

            when (subZone.shape) {
                MultiPolygonFlyZoneShape.CYLINDER -> {
                    // 圆柱形（使用圆形绘制）
                    val center = zone.circleCenter
                    val radius = zone.circleRadius

                    Log.d(TAG, "绘制圆柱形子限飞区: ID=$subZoneID")

                    val circleOptions = CircleOptions()
                        .center(LatLng(center.latitude, center.longitude))
                        .radius(radius)
                        .strokeColor(color)
                        .strokeWidth(DEFAULT_STROKE_WIDTH)
                        .fillColor(ColorUtils.setAlphaComponent(color, DEFAULT_ALPHA))

                    val circle = aMap?.addCircle(circleOptions)
                    circle?.let {
                        addCircleToMap(zone.category, subZoneID, it)
                    }
                }
                MultiPolygonFlyZoneShape.POLYGON -> {
                    // 多边形
                    val vertices = subZone.polygonPoints

                    Log.d(TAG, "绘制多边形子限飞区: ID=$subZoneID, 顶点数=${vertices.size}")

                    val polygonOptions = PolygonOptions()

                    vertices.forEach { vertex ->
                        polygonOptions.add(LatLng(vertex.latitude, vertex.longitude))
                    }

                    var finalColor = color
                    // 如果有高度限制，使用不同颜色
                    if (subZone.limitedHeight != 0) {
                        finalColor = "#9370DB".toColorInt()  // 紫色表示高度限制
                        Log.d(TAG, "多边形限飞区有高度限制: ${subZone.limitedHeight}米")
                    }

                    polygonOptions
                        .strokeColor(finalColor)
                        .strokeWidth(DEFAULT_STROKE_WIDTH)
                        .fillColor(ColorUtils.setAlphaComponent(finalColor, DEFAULT_ALPHA))

                    val polygon = aMap?.addPolygon(polygonOptions)
                    polygon?.let {
                        addPolygonToMap(zone.category, subZoneID, it)
                    }
                }
                else -> {
                    Log.w(TAG, "未知的子限飞区形状: ${subZone.shape}")
                }
            }
        }
    }

    //将圆形添加到对应的Map
    private fun addCircleToMap(category: FlyZoneCategory,zoneId: String,circle: Circle){
        when(category){
            FlyZoneCategory.WARNING -> warningCircleMap[zoneId] = circle
            FlyZoneCategory.ENHANCED_WARNING -> enhancedWarningCircleMap[zoneId] = circle
            FlyZoneCategory.AUTHORIZATION -> authorizationCircleMap[zoneId] = circle
            FlyZoneCategory.RESTRICTED -> restrictedAreaCircleMap[zoneId] = circle
            else -> Log.w(TAG, "不支持的限飞区类型：$category")
        }
    }

    //将多边形添加到对应的Map
    private fun addPolygonToMap(category: FlyZoneCategory,zoneId: String,polygon: Polygon){
        when(category){
            FlyZoneCategory.WARNING -> warningPolygonMap[zoneId] = polygon
            FlyZoneCategory.ENHANCED_WARNING -> enhancedWarningPolygonMap[zoneId] = polygon
            FlyZoneCategory.AUTHORIZATION -> authorizationPolygonMap[zoneId] = polygon
            FlyZoneCategory.RESTRICTED -> restrictedPolygonMap[zoneId] = polygon
            else -> Log.w(TAG, "不支持的限飞区类型：$category")
        }
    }

    //清除地图上的所有限飞区
    private fun removeFlyZones() {
        Log.i(TAG, "清除地图上的所有限飞区")
        //清除圆形
        removeCircles(warningCircleMap)
        removeCircles(enhancedWarningCircleMap)
        removeCircles(authorizationCircleMap)
        removeCircles(restrictedAreaCircleMap)
        //清除多边形
        removePolygons(warningPolygonMap)
        removePolygons(enhancedWarningPolygonMap)
        removePolygons(authorizationPolygonMap)
        removePolygons(restrictedPolygonMap)
        //清除标记
        flyZoneMarkerMap.values.forEach { it.remove() }
        flyZoneMarkerMap.clear()
    }

    //清除圆形限飞区
    private fun removeCircles(circleMap: ConcurrentHashMap<String, Circle>) {
        circleMap.values.forEach { it.remove() }
        circleMap.clear()
    }
    //清除多边形限飞区
    private fun removePolygons(polygonMap: ConcurrentHashMap<String, Polygon>) {
        polygonMap.values.forEach { it.remove() }
        polygonMap.clear()
    }

    //显示/隐藏指定类型的限飞区
    fun setFlyZoneVisible(category: FlyZoneCategory,visibility: Boolean){
        Log.i(TAG, "设置${category}限飞区可见性：$visibility")
        when(category){
            FlyZoneCategory.WARNING ->
                warningCircleMap.values.forEach { it.isVisible = visibility }
            FlyZoneCategory.ENHANCED_WARNING ->
                enhancedWarningCircleMap.values.forEach { it.isVisible = visibility }
            FlyZoneCategory.AUTHORIZATION ->
                authorizationCircleMap.values.forEach { it.isVisible = visibility }
            FlyZoneCategory.RESTRICTED ->
                restrictedAreaCircleMap.values.forEach { it.isVisible = visibility }
            else ->
                Log.w(TAG, "不支持的限飞区类型：$category")
        }
    }

    //清理资源
    fun clearUp() {
        Log.i(TAG, "清理资源")
        removeFlyZones()
        aMap = null
    }
}