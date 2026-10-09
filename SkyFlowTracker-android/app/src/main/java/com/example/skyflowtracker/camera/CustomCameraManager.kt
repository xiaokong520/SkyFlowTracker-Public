package com.example.skyflowtracker.camera

import android.util.Log
import android.widget.Toast
import dji.v5.ux.cameracore.widget.fpvinteraction.FPVInteractionWidget
import dji.v5.ux.core.base.SchedulerProvider
import dji.v5.ux.core.util.ViewUtil

/**
 * 自定义相机管理器
 * 用于管理相机对焦等功能
 */
class CustomCameraManager {

    private val TAG = "CustomCameraManager"

    /**
     * 配置 FPV 交互组件，确保对焦功能启用
     *
     * @param fpvInteractionWidget FPV交互组件实例
     */
    fun setupFocusInteraction(fpvInteractionWidget: FPVInteractionWidget?) {
        if (fpvInteractionWidget == null) {
            Log.e(TAG, "FPVInteractionWidget is null")
            return
        }

        // 确保触摸对焦功能启用
        fpvInteractionWidget.isTouchFocusEnabled = true
        Log.i(TAG, "触摸对焦已启用: ${fpvInteractionWidget.isTouchFocusEnabled}")

        // 确保点测光功能启用
        fpvInteractionWidget.isSpotMeteringEnabled = true
        Log.i(TAG, "点测光已启用: ${fpvInteractionWidget.isSpotMeteringEnabled}")

        // 确保云台控制功能启用
        fpvInteractionWidget.isGimbalControlEnabled = true
        Log.i(TAG, "云台控制已启用: ${fpvInteractionWidget.isGimbalControlEnabled}")
    }


    /**
     * 检查对焦功能状态
     *
     * @param fpvInteractionWidget FPV交互组件实例
     * @return 对焦功能是否启用
     */
    fun checkFocusStatus(fpvInteractionWidget: FPVInteractionWidget?): Boolean {
        if (fpvInteractionWidget == null) {
            Log.e(TAG, "FPVInteractionWidget is null")
            return false
        }

        val isEnabled = fpvInteractionWidget.isTouchFocusEnabled
        Log.i(TAG, "当前触摸对焦状态: $isEnabled")
        return isEnabled
    }
}
