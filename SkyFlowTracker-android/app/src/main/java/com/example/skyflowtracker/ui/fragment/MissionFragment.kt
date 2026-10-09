package com.example.skyflowtracker.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.skyflowtracker.R
import com.example.skyflowtracker.api.RetrofitClient
import com.example.skyflowtracker.api.model.Mission
import com.example.skyflowtracker.api.model.MissionDetail
import com.example.skyflowtracker.api.model.UpdateMissionStatusRequest
import com.example.skyflowtracker.databinding.FragmentMissionBinding
import com.example.skyflowtracker.ui.CustomDefaultLayoutActivity
import com.example.skyflowtracker.ui.adapter.MissionAdapter
import com.example.skyflowtracker.utils.formatDateTime
import com.example.skyflowtracker.utils.showToast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * 航线任务 Fragment
 */
class MissionFragment : Fragment() {

    private var _binding: FragmentMissionBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: MissionAdapter
    private var currentPage = 1
    private var totalPages = 1
    private var isLoading = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMissionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
        loadMissions()
    }

    private fun initViews() {
        adapter = MissionAdapter { mission -> showMissionDetail(mission) }
        binding.rvMissions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMissions.adapter = adapter

        // 滚动加载更多
        binding.rvMissions.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!recyclerView.canScrollVertically(1) && !isLoading && currentPage < totalPages) {
                    currentPage++
                    loadMissions(append = true)
                }
            }
        })

        // 下拉刷新
        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
        binding.swipeRefresh.setOnRefreshListener {
            currentPage = 1
            loadMissions()
        }
    }

    private fun loadMissions(append: Boolean = false) {
        if (isLoading) return
        isLoading = true
        if (!append) binding.swipeRefresh.isRefreshing = true

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getMissionsList(
                    page = currentPage, pageSize = 10
                )
                if (response.code == 1 && response.data != null) {
                    val list = response.data.missionsList ?: emptyList()
                    totalPages = response.data.pagination?.totalPage ?: 1

                    if (append) {
                        val current = adapter.currentList.toMutableList()
                        current.addAll(list)
                        adapter.submitList(current)
                    } else {
                        adapter.submitList(list)
                    }

                    binding.llEmpty.visibility = if (adapter.currentList.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvMissions.visibility = if (adapter.currentList.isEmpty()) View.GONE else View.VISIBLE
                } else {
                    requireContext().showToast(response.message ?: "获取任务列表失败")
                }
            } catch (e: Exception) {
                requireContext().showToast("获取任务列表失败: ${e.message}")
            } finally {
                isLoading = false
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    /**
     * 显示任务详情弹窗
     */
    private fun showMissionDetail(mission: Mission) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getMissionDetail(mission.id)
                if (response.code == 1 && response.data != null) {
                    showDetailDialog(response.data)
                } else {
                    requireContext().showToast(response.message ?: "获取详情失败")
                }
            } catch (e: Exception) {
                requireContext().showToast("获取详情失败: ${e.message}")
            }
        }
    }

    private fun showDetailDialog(detail: MissionDetail) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_mission_detail, null)

        // 任务名称
        dialogView.findViewById<TextView>(R.id.tvMissionName).text = detail.name ?: "未命名任务"

        // 状态
        val tvStatus = dialogView.findViewById<TextView>(R.id.tvMissionStatus)
        val statusText: String
        val statusColorRes: Int
        when (detail.status) {
            0 -> { statusText = "草稿"; statusColorRes = R.color.text_secondary }
            1 -> { statusText = "已发布"; statusColorRes = R.color.info }
            2 -> { statusText = "待执行"; statusColorRes = R.color.warning }
            3 -> { statusText = "执行中"; statusColorRes = R.color.primary }
            4 -> { statusText = "已完成"; statusColorRes = R.color.success }
            5 -> { statusText = "已取消"; statusColorRes = R.color.error }
            else -> { statusText = "未知"; statusColorRes = R.color.text_secondary }
        }
        tvStatus.text = statusText
        tvStatus.setTextColor(requireContext().getColor(statusColorRes))
        val bg = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = resources.getDimension(R.dimen.radius_sm)
            setColor((requireContext().getColor(statusColorRes) and 0x00FFFFFF) or 0x1A000000)
        }
        tvStatus.background = bg

        // 描述
        dialogView.findViewById<TextView>(R.id.tvDescription).text = detail.description ?: "无描述"

        // 航点数量
        val waypointCount = detail.waypoints?.size ?: 0
        dialogView.findViewById<TextView>(R.id.tvWaypointCount).text = "${waypointCount} 个航点"

        // 距离
        dialogView.findViewById<TextView>(R.id.tvDistance).text = formatDistance(detail.totalDistance)

        // 预计时长
        dialogView.findViewById<TextView>(R.id.tvEstimatedTime).text = formatEstimatedTime(detail.estimatedTime)

        // 创建时间
        dialogView.findViewById<TextView>(R.id.tvCreateTime).text = detail.createTime?.formatDateTime() ?: "-"

        // 按钮区域
        val btnStartFlight = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnStartFlight)

        val canStart = detail.status == 2 || detail.status == 3
        btnStartFlight.visibility = if (canStart && waypointCount > 0) View.VISIBLE else View.GONE

        if (detail.status == 3) {
            btnStartFlight.text = "继续飞行"
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setNegativeButton("关闭", null)
            .create()

        btnStartFlight.setOnClickListener {
            dialog.dismiss()
            startFlightWithMission(detail)
        }

        dialog.show()
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)
            ?.setTextColor(requireContext().getColor(R.color.primary))
    }

    /**
     * 开始飞行（携带 missionId 跳转飞行界面）
     */
    private fun startFlightWithMission(detail: MissionDetail) {
        // 如果是待执行状态，先更新为执行中
        if (detail.status == 2) {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val response = RetrofitClient.apiService.updateMissionStatus(
                        UpdateMissionStatusRequest(detail.id, 3)
                    )
                    if (response.code != 1) {
                        requireContext().showToast(response.message ?: "更新状态失败")
                        return@launch
                    }
                } catch (e: Exception) {
                    requireContext().showToast("更新状态失败: ${e.message}")
                    return@launch
                }
                launchFlightActivity(detail.id)
            }
        } else {
            launchFlightActivity(detail.id)
        }
    }

    private fun launchFlightActivity(missionId: Long) {
        val intent = Intent(requireContext(), CustomDefaultLayoutActivity::class.java)
        intent.putExtra("mission_id", missionId)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        // 从飞行界面返回时刷新列表
        currentPage = 1
        loadMissions()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun formatDistance(meters: Double?): String {
        if (meters == null || meters <= 0) return "0m"
        return if (meters >= 1000) "%.2fkm".format(meters / 1000) else "%.0fm".format(meters)
    }

    private fun formatEstimatedTime(seconds: Int?): String {
        if (seconds == null || seconds <= 0) return "0秒"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return buildString {
            if (h > 0) append("${h}时")
            if (m > 0) append("${m}分")
            if (s > 0 || (h == 0 && m == 0)) append("${s}秒")
        }
    }
}
