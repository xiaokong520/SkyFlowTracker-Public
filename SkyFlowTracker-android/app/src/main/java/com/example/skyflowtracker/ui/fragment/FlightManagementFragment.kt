package com.example.skyflowtracker.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.skyflowtracker.R
import com.example.skyflowtracker.api.RetrofitClient
import com.example.skyflowtracker.api.model.Flight
import com.example.skyflowtracker.api.model.FlightDetail
import com.example.skyflowtracker.databinding.FragmentFlightManagementBinding
import com.example.skyflowtracker.ui.FlightTrajectoryActivity
import com.example.skyflowtracker.ui.adapter.FlightAdapter
import com.example.skyflowtracker.utils.formatDateTime
import com.example.skyflowtracker.utils.showToast
import kotlinx.coroutines.launch

/**
 * 飞行记录管理 Fragment
 */
class FlightManagementFragment : Fragment() {

    private var _binding: FragmentFlightManagementBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: FlightAdapter
    private var currentPage = 1
    private var totalPages = 1
    private var isLoading = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFlightManagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
        loadFlights()
    }

    private fun initViews() {
        // 初始化 RecyclerView
        adapter = FlightAdapter { flight -> loadFlightDetail(flight.id) }
        binding.rvFlights.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFlights.adapter = adapter

        // 滚动加载更多
        binding.rvFlights.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!recyclerView.canScrollVertically(1) && !isLoading && currentPage < totalPages) {
                    currentPage++
                    loadFlights(append = true)
                }
            }
        })

        // 下拉刷新
        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
        binding.swipeRefresh.setOnRefreshListener {
            currentPage = 1
            loadFlights()
        }
    }

    /**
     * 加载飞行记录列表
     */
    private fun loadFlights(append: Boolean = false) {
        if (isLoading) return
        isLoading = true
        if (!append) binding.swipeRefresh.isRefreshing = true

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getFlightsList(
                    page = currentPage, pageSize = 20
                )
                if (response.code == 1 && response.data != null) {
                    val list = response.data.flightsList ?: emptyList()
                    totalPages = response.data.pagination?.totalPage ?: 1

                    if (append) {
                        val current = adapter.currentList.toMutableList()
                        current.addAll(list)
                        adapter.submitList(current)
                    } else {
                        adapter.submitList(list)
                    }

                    // 空状态
                    binding.llEmpty.visibility = if (adapter.currentList.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvFlights.visibility = if (adapter.currentList.isEmpty()) View.GONE else View.VISIBLE
                } else {
                    requireContext().showToast(response.message ?: "获取飞行记录失败")
                }
            } catch (e: Exception) {
                requireContext().showToast("获取飞行记录失败: ${e.message}")
            } finally {
                isLoading = false
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    /**
     * 加载飞行记录详情并显示弹窗
     */
    private fun loadFlightDetail(flightId: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getFlightDetail(flightId)
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

    /**
     * 显示飞行记录详情弹窗（含地图占位）
     */
    private fun showDetailDialog(detail: FlightDetail) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_flight_detail, null)

        // 状态标签
        val tvStatus = dialogView.findViewById<android.widget.TextView>(R.id.tvStatus)
        val statusText: String
        val statusColorRes: Int
        when (detail.status) {
            0 -> { statusText = "进行中"; statusColorRes = R.color.warning }
            1 -> { statusText = "已完成"; statusColorRes = R.color.success }
            2 -> { statusText = "异常终止"; statusColorRes = R.color.error }
            else -> { statusText = "未知"; statusColorRes = R.color.text_secondary }
        }
        tvStatus.text = statusText
        tvStatus.setTextColor(requireContext().getColor(statusColorRes))
        val bg = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = resources.getDimension(R.dimen.radius_sm)
            setColor((requireContext().getColor(statusColorRes) and 0x00FFFFFF) or 0x1A000000)
        }
        tvStatus.background = bg

        // 填充数据
        dialogView.findViewById<android.widget.TextView>(R.id.tvSn).text = detail.sn ?: "-"
        dialogView.findViewById<android.widget.TextView>(R.id.tvStartTime).text = detail.startTime?.toString()?.formatDateTime() ?: "-"
        dialogView.findViewById<android.widget.TextView>(R.id.tvEndTime).text = detail.endTime?.toString()?.formatDateTime() ?: "-"
        dialogView.findViewById<android.widget.TextView>(R.id.tvDuration).text = formatDuration(detail.duration)
        dialogView.findViewById<android.widget.TextView>(R.id.tvDistance).text = formatDistance(detail.distance)
        dialogView.findViewById<android.widget.TextView>(R.id.tvMaxAltitude).text = detail.maxAltitude?.let { "%.1fm".format(it) } ?: "-"
        dialogView.findViewById<android.widget.TextView>(R.id.tvMaxSpeed).text = detail.maxSpeed?.let { "%.1fm/s".format(it) } ?: "-"
        dialogView.findViewById<android.widget.TextView>(R.id.tvHomeCoord).text =
            if (detail.homeLatitude != null && detail.homeLongitude != null)
                "%.6f, %.6f".format(detail.homeLatitude, detail.homeLongitude)
            else "-"

        // 航线坐标点数量
        val pathCount = detail.flightPath?.size ?: 0
        dialogView.findViewById<android.widget.TextView>(R.id.tvPathCount).text = "共 ${pathCount} 个坐标点"

        // 地图区域点击跳转到轨迹回放
        val mapCard = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardMapArea)
        var alertDialog: AlertDialog? = null
        if (pathCount > 0) {
            // 更新提示文字
            val tvMapHint = dialogView.findViewById<android.widget.TextView>(R.id.tvMapHint)
            tvMapHint?.text = "点击查看航线轨迹回放"
            mapCard.setOnClickListener {
                alertDialog?.dismiss()
                val intent = Intent(requireContext(), FlightTrajectoryActivity::class.java)
                intent.putExtra(FlightTrajectoryActivity.EXTRA_FLIGHT_ID, detail.id)
                startActivity(intent)
            }
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("飞行记录详情")
            .setView(dialogView)
            .setPositiveButton("关闭", null)
            .create()

        alertDialog = dialog
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(requireContext().getColor(R.color.primary))
    }

    /**
     * 格式化飞行时长
     */
    private fun formatDuration(seconds: Int?): String {
        if (seconds == null || seconds <= 0) return "0秒"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return buildString {
            if (h > 0) append("${h}时")
            if (m > 0) append("${m}分")
            append("${s}秒")
        }
    }

    /**
     * 格式化飞行距离
     */
    private fun formatDistance(meters: Double?): String {
        if (meters == null || meters <= 0) return "0m"
        return if (meters >= 1000) {
            "%.2fkm".format(meters / 1000)
        } else {
            "%.0fm".format(meters)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
