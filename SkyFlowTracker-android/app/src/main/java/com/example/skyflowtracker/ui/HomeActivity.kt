package com.example.skyflowtracker.ui

import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.skyflowtracker.R
import com.example.skyflowtracker.databinding.ActivityHomeBinding
import com.example.skyflowtracker.flysafe.FlyZoneSyncManager
import kotlinx.coroutines.launch

/**
 * 主页（底部导航：个人中心 + 设备管理）
 */
class HomeActivity : AppCompatActivity() {

    private val TAG = "HomeActivity"
    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 设置状态栏颜色
        window.statusBarColor = getColor(R.color.primary)
        window.navigationBarColor = getColor(R.color.surface)

        // 设置 Navigation
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNav.setupWithNavController(navController)

        // 减小底部导航栏图标和文字之间的间距
        binding.bottomNav.post {
            val menuView = binding.bottomNav.getChildAt(0) as? ViewGroup ?: return@post
            for (i in 0 until menuView.childCount) {
                val item = menuView.getChildAt(i)
                // 减小每个 item 的上下内边距
                item.setPadding(item.paddingLeft, 0, item.paddingRight, 0)
            }
        }

        // 进入主页后同步禁飞区数据到后端
        triggerFlyZoneSync()
    }

    private fun triggerFlyZoneSync() {
        val syncManager = FlyZoneSyncManager(this)
        // 版本号用于在修复同步逻辑后强制重新同步
        val prefs = getSharedPreferences("fly_zone_sync", MODE_PRIVATE)
        val currentVersion = 2 // 每次修复同步逻辑后递增
        if (prefs.getInt("sync_version", 0) < currentVersion) {
            Log.i(TAG, "同步逻辑已更新（v$currentVersion），重置同步状态")
            syncManager.resetSyncState()
            prefs.edit().putInt("sync_version", currentVersion).apply()
        }
        if (syncManager.needsSync()) {
            Log.i(TAG, "需要同步禁飞区数据到后端")
            lifecycleScope.launch {
                try {
                    syncManager.performSync()
                    Log.i(TAG, "禁飞区数据同步完成")
                } catch (e: Exception) {
                    Log.e(TAG, "禁飞区数据同步失败: ${e.message}", e)
                }
            }
        } else {
            Log.d(TAG, "禁飞区数据已同步，无需重复同步")
        }
    }
}
