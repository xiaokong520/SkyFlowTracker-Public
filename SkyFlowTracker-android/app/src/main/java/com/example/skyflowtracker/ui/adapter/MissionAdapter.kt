package com.example.skyflowtracker.ui.adapter

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.skyflowtracker.R
import com.example.skyflowtracker.api.model.Mission
import com.example.skyflowtracker.databinding.ItemMissionBinding
import com.example.skyflowtracker.utils.formatDateTime

/**
 * 航线任务列表适配器
 */
class MissionAdapter(
    private val onClick: (Mission) -> Unit
) : ListAdapter<Mission, MissionAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(val binding: ItemMissionBinding) : RecyclerView.ViewHolder(binding.root)

    private class DiffCallback : DiffUtil.ItemCallback<Mission>() {
        override fun areItemsTheSame(oldItem: Mission, newItem: Mission) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Mission, newItem: Mission) = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMissionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val mission = getItem(position)
        val context = holder.itemView.context

        with(holder.binding) {
            // 任务名称
            tvName.text = mission.name ?: "未命名任务"

            // 状态标签
            val statusText: String
            val statusColorRes: Int
            when (mission.status) {
                0 -> { statusText = "草稿"; statusColorRes = R.color.text_secondary }
                1 -> { statusText = "已发布"; statusColorRes = R.color.info }
                2 -> { statusText = "待执行"; statusColorRes = R.color.warning }
                3 -> { statusText = "执行中"; statusColorRes = R.color.primary }
                4 -> { statusText = "已完成"; statusColorRes = R.color.success }
                5 -> { statusText = "已取消"; statusColorRes = R.color.error }
                else -> { statusText = "未知"; statusColorRes = R.color.text_secondary }
            }
            tvStatus.text = statusText
            tvStatus.setTextColor(context.getColor(statusColorRes))
            val statusColor = context.getColor(statusColorRes)
            val bgColor = (statusColor and 0x00FFFFFF) or 0x1A000000
            val bg = GradientDrawable().apply {
                cornerRadius = context.resources.getDimension(R.dimen.radius_sm)
                setColor(bgColor)
            }
            tvStatus.background = bg

            // 飞行数据：距离 / 预计时长
            val distanceStr = formatDistance(mission.totalDistance)
            val timeStr = formatEstimatedTime(mission.estimatedTime)
            tvFlightData.text = "$distanceStr / $timeStr"

            // 创建时间
            tvCreateTime.text = mission.createTime?.formatDateTime() ?: "-"

            // 点击事件
            root.setOnClickListener { onClick(mission) }
        }
    }

    private fun formatDistance(meters: Double?): String {
        if (meters == null || meters <= 0) return "0m"
        return if (meters >= 1000) {
            "%.2fkm".format(meters / 1000)
        } else {
            "%.0fm".format(meters)
        }
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
