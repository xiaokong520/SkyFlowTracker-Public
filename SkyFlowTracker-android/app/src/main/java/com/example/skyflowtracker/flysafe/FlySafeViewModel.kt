package com.example.skyflowtracker.flysafe

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.dji.industry.mission.natives.util.NativeCallbackUtils
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.value.common.LocationCoordinate2D
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.et.create
import dji.v5.et.get
import dji.v5.manager.aircraft.flysafe.FlySafeNotificationListener
import dji.v5.manager.aircraft.flysafe.FlyZoneManager
import dji.v5.manager.aircraft.flysafe.info.FlySafeReturnToHomeInformation
import dji.v5.manager.aircraft.flysafe.info.FlySafeSeriousWarningInformation
import dji.v5.manager.aircraft.flysafe.info.FlySafeTipInformation
import dji.v5.manager.aircraft.flysafe.info.FlySafeWarningInformation
import dji.v5.manager.aircraft.flysafe.info.FlyZoneInformation


//显示限飞/禁飞区信息
class FlySafeViewModel: ViewModel() {
    private val TAG = "FlySafeViewModel"
    // 限飞区信息列表
    val flyZoneInformation = MutableLiveData<MutableList<FlyZoneInformation>>()
    //限飞区警告信息
    val warningInformation = MutableLiveData<MutableList<FlyZoneInformation>>()
    //限飞区通知监听器
    private val flySafeNotificationListener = object : FlySafeNotificationListener{
        override fun onTipNotificationUpdate(info: FlySafeTipInformation) {
            Log.d(TAG, "限飞/禁飞区提示信息更新: $info")
        }

        override fun onWarningNotificationUpdate(info: FlySafeWarningInformation) {
            Log.d(TAG, "限飞/禁飞区警告信息更新: $info")
        }

        override fun onSeriousWarningNotificationUpdate(info: FlySafeSeriousWarningInformation) {
            Log.d(TAG, "限飞/禁飞区严重警告信息更新: $info")
        }

        override fun onReturnToHomeNotificationUpdate(info: FlySafeReturnToHomeInformation) {
            Log.d(TAG, "限飞/禁飞区返航信息更新: $info")
        }

        override fun onSurroundingFlyZonesUpdate(infos: List<FlyZoneInformation?>) {
            Log.d(TAG, "限飞/禁飞区周围限飞区信息更新，数量: ${infos.size}")
            // 过滤掉null值并更新LiveData
            val validFlyZones = infos.filterNotNull().toMutableList()
            flyZoneInformation.postValue(validFlyZones)
        }
    }

    //初始化监听器
    fun initListener(){
        Log.d(TAG, "初始化限飞/禁飞区监听器")
        FlyZoneManager.getInstance().addFlySafeNotificationListener(flySafeNotificationListener)
    }

    //获取指定位置周围的限飞区
    fun getFlyZonesInSurroundingArea(location: LocationCoordinate2D){
        Log.d(TAG, "获取指定位置周围的限飞区: $location")
        FlyZoneManager.getInstance().getFlyZonesInSurroundingArea(location,object : CommonCallbacks.CompletionCallbackWithParam<MutableList<FlyZoneInformation>>{
            override fun onSuccess(p0: MutableList<FlyZoneInformation>?) {
                Log.d(TAG, "获取指定位置周围的限飞区成功，数量: ${p0?.size}")
                // 更新LiveData，触发UI更新
                flyZoneInformation.postValue(p0 ?: mutableListOf())
            }

            override fun onFailure(p0: IDJIError) {
                Log.e(TAG, "获取指定位置周围的限飞区失败: ${p0.errorType()}, ${p0.description()}")
            }
        })
    }

    //获取飞机当前位置的限飞区
    fun getFlyZonesAtAircraftLocation(){
        Log.d(TAG, "开始获取飞机当前位置...")
        // 使用异步回调方式获取飞机位置
        FlightControllerKey.KeyAircraftLocation.create().get(
            onSuccess = { location ->
                if (location != null && location.latitude != 0.0 && location.longitude != 0.0){
                    Log.d(TAG, "获取飞机位置成功: 纬度=${location.latitude}, 经度=${location.longitude}")
                    getFlyZonesInSurroundingArea(location)
                }else{
                    Log.w(TAG, "飞机位置无效或未获取到GPS信号: $location (可能GPS未定位)")
                }
            },
            onFailure = { error ->
                // CORE 错误通常表示飞机未连接或GPS未定位，这是正常现象，降低日志级别
                if (error.errorType().toString() == "CORE") {
                    Log.d(TAG, "飞机未连接或GPS未定位，跳过限飞区查询")
                } else {
                    Log.e(TAG, "获取飞机位置失败: ${error.errorType()}, ${error.description()}")
                }
            }
        )
    }

    //解除限飞/禁飞区
    fun unlockAuthorizationFlyZone(flyZoneID: Int, callback: CommonCallbacks.CompletionCallback){
        Log.d(TAG, "移除限飞/禁飞区监听器")
        FlyZoneManager.getInstance().unlockAuthorizationFlyZone(flyZoneID,callback)
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "移除限飞/禁飞区监听器")
        FlyZoneManager.getInstance().removeFlySafeNotificationListener(flySafeNotificationListener)
    }

}