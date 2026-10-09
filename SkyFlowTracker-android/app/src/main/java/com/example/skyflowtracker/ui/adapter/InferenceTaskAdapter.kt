package com.example.skyflowtracker.ui.adapter

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.skyflowtracker.R
import com.example.skyflowtracker.api.model.InferenceTask
import com.example.skyflowtracker.databinding.ItemInferenceTaskBinding
import com.example.skyflowtracker.utils.formatDateTime

/**
 * 推理任务列表适配器
 */
class InferenceTaskAdapter(
    private val onClick: (InferenceTask) -> Unit,
    private val onLongClick: (InferenceTask) -> Unit
) : ListAdapter<InferenceTask, InferenceTaskAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(val binding: ItemInferenceTaskBinding) : RecyclerView.ViewHolder(binding.root)

    private class DiffCallback : DiffUtil.ItemCallback<InferenceTask>() {
        override fun areItemsTheSame(oldItem: InferenceTask, newItem: InferenceTask) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: InferenceTask, newItem: InferenceTask) = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemInferenceTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val task = getItem(position)
        val context = holder.itemView.context

        with(holder.binding) {
            // 模型名称
            tvModelName.text = task.modelName ?: "未知模型"

            // 状态标签
            val statusText: String
            val statusColorRes: Int
            when (task.status) {
                0 -> { statusText = "进行中"; statusColorRes = R.color.info }
                1 -> { statusText = "已完成"; statusColorRes = R.color.success }
                2 -> { statusText = "失败"; statusColorRes = R.color.error }
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

            // 来源
            if (task.flightId != null) {
                tvSource.text = "飞行记录 #${task.flightId}"
                tvSource.setTextColor(context.getColor(R.color.text_primary))
                tvSource.background = null
            } else {
                tvSource.text = "离线推理"
                tvSource.setTextColor(context.getColor(R.color.secondary))
                val sourceBg = GradientDrawable().apply {
                    cornerRadius = context.resources.getDimension(R.dimen.radius_sm)
                    setColor((context.getColor(R.color.secondary) and 0x00FFFFFF) or 0x1A000000)
                }
                tvSource.background = sourceBg
                tvSource.setPadding(
                    context.resources.getDimensionPixelSize(R.dimen.spacing_sm),
                    2,
                    context.resources.getDimensionPixelSize(R.dimen.spacing_sm),
                    2
                )
            }

            // 开始时间
            tvStartTime.text = task.startTime?.formatDateTime() ?: "-"

            // 检测总数
            tvDetections.text = if (task.totalDetections != null && task.totalDetections > 0) {
                "${task.totalDetections} 辆车"
            } else {
                "-"
            }

            // 点击事件
            root.setOnClickListener { onClick(task) }
            root.setOnLongClickListener { onLongClick(task); true }
        }
    }
}
