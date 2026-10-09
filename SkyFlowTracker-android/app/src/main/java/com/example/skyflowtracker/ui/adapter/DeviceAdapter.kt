package com.example.skyflowtracker.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.skyflowtracker.api.model.Device
import com.example.skyflowtracker.databinding.ItemDeviceBinding
import com.example.skyflowtracker.utils.formatDateTime

/**
 * 设备列表适配器
 */
class DeviceAdapter(
    private val onEdit: (Device) -> Unit,
    private val onDelete: (Device) -> Unit
) : ListAdapter<Device, DeviceAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(val binding: ItemDeviceBinding) : RecyclerView.ViewHolder(binding.root)

    private class DiffCallback : DiffUtil.ItemCallback<Device>() {
        override fun areItemsTheSame(oldItem: Device, newItem: Device) = oldItem.sn == newItem.sn
        override fun areContentsTheSame(oldItem: Device, newItem: Device) = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = getItem(position)
        with(holder.binding) {
            tvSn.text = device.sn
            tvModel.text = device.model ?: "-"
            tvFcSn.text = device.flightControllerSerialNumber ?: "-"
            tvFlightData.text = "${formatDistance(device.accumulatedVoyage)} / ${formatDuration(device.flyTime)}"
            tvCreateTime.text = device.createTime?.formatDateTime() ?: "-"

            btnEdit.setOnClickListener { onEdit(device) }
            btnDelete.setOnClickListener { onDelete(device) }
        }
    }

    /**
     * 格式化航程距离（米 -> 可读格式）
     */
    private fun formatDistance(meters: Long?): String {
        if (meters == null || meters <= 0) return "0m"
        return if (meters >= 1000) {
            "%.2fkm".format(meters / 1000.0)
        } else {
            "${meters}m"
        }
    }

    /**
     * 格式化飞行时长（秒 -> 可读格式）
     */
    private fun formatDuration(seconds: Long?): String {
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
}
