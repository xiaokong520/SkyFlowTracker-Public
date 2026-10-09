package com.example.skyflowtracker.ui.adapter

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.skyflowtracker.R
import com.example.skyflowtracker.api.model.Flight
import com.example.skyflowtracker.databinding.ItemFlightBinding
import com.example.skyflowtracker.utils.formatDateTime

/**
 * 飞行记录列表适配器
 */
class FlightAdapter(
    private val onClick: (Flight) -> Unit
) : ListAdapter<Flight, FlightAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(val binding: ItemFlightBinding) : RecyclerView.ViewHolder(binding.root)

    private class DiffCallback : DiffUtil.ItemCallback<Flight>() {
        override fun areItemsTheSame(oldItem: Flight, newItem: Flight) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Flight, newItem: Flight) = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFlightBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val flight = getItem(position)
        val context = holder.itemView.context

        with(holder.binding) {
            // SN码
            tvSn.text = flight.sn ?: "-"

            // 状态标签
            val statusText: String
            val statusColorRes: Int
            when (flight.status) {
                0 -> { statusText = "进行中"; statusColorRes = R.color.warning }
                1 -> { statusText = "已完成"; statusColorRes = R.color.success }
                2 -> { statusText = "异常终止"; statusColorRes = R.color.error }
                else -> { statusText = "未知"; statusColorRes = R.color.text_secondary }
            }
            tvStatus.text = statusText
            tvStatus.setTextColor(context.getColor(statusColorRes))
            // 设置圆角背景（10%透明度）
            val statusColor = context.getColor(statusColorRes)
            val bgColor = (statusColor and 0x00FFFFFF) or 0x1A000000
            val bg = GradientDrawable().apply {
                cornerRadius = context.resources.getDimension(R.dimen.radius_sm)
                setColor(bgColor)
            }
            tvStatus.background = bg

            // 起飞时间
            tvStartTime.text = flight.startTime?.formatDateTime() ?: "-"

            // 飞行数据：时长 / 距离 / 最大高度
            val durationStr = formatDuration(flight.duration)
            val distanceStr = formatDistance(flight.distance)
            val altitudeStr = if (flight.maxAltitude != null) "%.1fm".format(flight.maxAltitude) else "-"
            tvFlightData.text = "$durationStr / $distanceStr / $altitudeStr"

            // 点击事件
            root.setOnClickListener { onClick(flight) }
        }
    }

    /**
     * 格式化飞行时长（秒 -> 可读格式）
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
     * 格式化飞行距离（米）
     */
    private fun formatDistance(meters: Double?): String {
        if (meters == null || meters <= 0) return "0m"
        return if (meters >= 1000) {
            "%.2fkm".format(meters / 1000)
        } else {
            "%.0fm".format(meters)
        }
    }
}
