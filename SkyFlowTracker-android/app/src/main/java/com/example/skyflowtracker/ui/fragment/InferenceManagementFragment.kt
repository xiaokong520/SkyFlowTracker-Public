package com.example.skyflowtracker.ui.fragment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.skyflowtracker.R
import com.example.skyflowtracker.api.RetrofitClient
import com.example.skyflowtracker.api.SseClient
import com.example.skyflowtracker.api.model.CreateShareRequest
import com.example.skyflowtracker.api.model.DeleteInferenceTasksRequest
import com.example.skyflowtracker.api.model.InferenceTaskDetail
import com.example.skyflowtracker.databinding.FragmentInferenceManagementBinding
import com.example.skyflowtracker.ui.adapter.InferenceTaskAdapter
import com.example.skyflowtracker.utils.formatDateTime
import com.example.skyflowtracker.utils.showConfirmDialog
import com.example.skyflowtracker.utils.showToast
import com.google.android.material.button.MaterialButton
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import kotlinx.coroutines.launch
import okhttp3.Call

/**
 * 推理任务管理 Fragment
 */
class InferenceManagementFragment : Fragment() {

    private var _binding: FragmentInferenceManagementBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: InferenceTaskAdapter
    private lateinit var markwon: Markwon
    private val mainHandler = Handler(Looper.getMainLooper())
    private var summaryCall: Call? = null
    private var currentPage = 1
    private var totalPages = 1
    private var isLoading = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInferenceManagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initMarkwon()
        initViews()
        loadTasks()
    }

    private fun initMarkwon() {
        markwon = Markwon.builder(requireContext())
            .usePlugin(TablePlugin.create(requireContext()))
            .usePlugin(StrikethroughPlugin.create())
            .build()
    }

    private fun initViews() {
        // 初始化 RecyclerView
        adapter = InferenceTaskAdapter(
            onClick = { task -> loadTaskDetail(task.id) },
            onLongClick = { task -> confirmDeleteTask(task.id) }
        )
        binding.rvTasks.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTasks.adapter = adapter

        // 滚动加载更多
        binding.rvTasks.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!recyclerView.canScrollVertically(1) && !isLoading && currentPage < totalPages) {
                    currentPage++
                    loadTasks(append = true)
                }
            }
        })

        // 下拉刷新
        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
        binding.swipeRefresh.setOnRefreshListener {
            currentPage = 1
            loadTasks()
        }
    }

    /**
     * 加载推理任务列表
     */
    private fun loadTasks(append: Boolean = false) {
        if (isLoading) return
        isLoading = true
        if (!append) binding.swipeRefresh.isRefreshing = true

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getInferenceTasksList(
                    page = currentPage, pageSize = 20
                )
                if (response.code == 1 && response.data != null) {
                    val list = response.data.tasksList ?: emptyList()
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
                    binding.rvTasks.visibility = if (adapter.currentList.isEmpty()) View.GONE else View.VISIBLE
                } else {
                    requireContext().showToast(response.message ?: "获取推理任务失败")
                }
            } catch (e: Exception) {
                requireContext().showToast("获取推理任务失败: ${e.message}")
            } finally {
                isLoading = false
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    /**
     * 加载推理任务详情并显示弹窗
     */
    private fun loadTaskDetail(taskId: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getInferenceTasksDetail(taskId)
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
     * 显示推理任务详情弹窗
     */
    private fun showDetailDialog(detail: InferenceTaskDetail) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_inference_detail, null)

        // 状态标签
        val tvStatus = dialogView.findViewById<TextView>(R.id.tvStatus)
        val statusText: String
        val statusColorRes: Int
        when (detail.status) {
            0 -> { statusText = "进行中"; statusColorRes = R.color.info }
            1 -> { statusText = "已完成"; statusColorRes = R.color.success }
            2 -> { statusText = "失败"; statusColorRes = R.color.error }
            else -> { statusText = "未知"; statusColorRes = R.color.text_secondary }
        }
        tvStatus.text = statusText
        tvStatus.setTextColor(requireContext().getColor(statusColorRes))
        val bg = GradientDrawable().apply {
            cornerRadius = resources.getDimension(R.dimen.radius_sm)
            setColor((requireContext().getColor(statusColorRes) and 0x00FFFFFF) or 0x1A000000)
        }
        tvStatus.background = bg

        // 基本信息
        dialogView.findViewById<TextView>(R.id.tvTaskId).text = detail.id.toString()
        dialogView.findViewById<TextView>(R.id.tvSource).text =
            if (detail.flightId != null) "飞行记录 #${detail.flightId}" else "离线推理"
        dialogView.findViewById<TextView>(R.id.tvModelName).text = detail.modelName ?: "-"
        dialogView.findViewById<TextView>(R.id.tvStartTime).text = detail.startTime?.toString()?.formatDateTime() ?: "-"
        dialogView.findViewById<TextView>(R.id.tvEndTime).text = detail.endTime?.toString()?.formatDateTime() ?: "-"
        dialogView.findViewById<TextView>(R.id.tvDetections).text =
            if (detail.totalDetections != null && detail.totalDetections > 0) "${detail.totalDetections}" else "-"

        // 推理结果数据
        val resultData = detail.resultData
        if (resultData != null) {
            dialogView.findViewById<TextView>(R.id.tvResultTitle).visibility = View.VISIBLE
            val llResultData = dialogView.findViewById<LinearLayout>(R.id.llResultData)
            llResultData.visibility = View.VISIBLE

            dialogView.findViewById<TextView>(R.id.tvFrameCount).text =
                resultData.frameCount?.toString() ?: "-"
            dialogView.findViewById<TextView>(R.id.tvDuration).text =
                resultData.duration?.let { "%.1f 秒".format(it) } ?: "-"

            // 各类别计数
            val llClassCounts = dialogView.findViewById<LinearLayout>(R.id.llClassCounts)
            resultData.classCounts?.forEach { (cls, count) ->
                val row = LinearLayout(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = resources.getDimensionPixelSize(R.dimen.spacing_xs)
                    }
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }

                val labelTv = TextView(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    text = cls
                    setTextColor(requireContext().getColor(R.color.text_secondary))
                    textSize = 14f
                }

                val countTv = TextView(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    text = count.toString()
                    setTextColor(requireContext().getColor(R.color.text_primary))
                    textSize = 14f
                }

                row.addView(labelTv)
                row.addView(countTv)
                llClassCounts.addView(row)
            }
        }

        // 分享区域（仅已完成且有视频的任务显示）
        val llShareSection = dialogView.findViewById<LinearLayout>(R.id.llShareSection)
        val dividerShare = dialogView.findViewById<View>(R.id.dividerShare)
        if (detail.status == 1 && !detail.videoPath.isNullOrEmpty()) {
            llShareSection.visibility = View.VISIBLE
            dividerShare.visibility = View.VISIBLE
            setupShareSection(dialogView, detail.id)
        }

        // AI 摘要区域（仅已完成任务显示）
        val llSummarySection = dialogView.findViewById<LinearLayout>(R.id.llSummarySection)
        val dividerSummary = dialogView.findViewById<View>(R.id.dividerSummary)
        val btnGenerateSummary = dialogView.findViewById<MaterialButton>(R.id.btnGenerateSummary)
        val llSummaryLoading = dialogView.findViewById<LinearLayout>(R.id.llSummaryLoading)
        val tvSummaryContent = dialogView.findViewById<TextView>(R.id.tvSummaryContent)

        if (detail.status == 1) {
            llSummarySection.visibility = View.VISIBLE
            dividerSummary.visibility = View.VISIBLE

            btnGenerateSummary.setOnClickListener {
                // 隐藏按钮，显示加载状态
                btnGenerateSummary.visibility = View.GONE
                llSummaryLoading.visibility = View.VISIBLE
                tvSummaryContent.visibility = View.GONE

                val contentBuilder = StringBuilder()

                summaryCall = SseClient.summaryStream(
                    taskId = detail.id,
                    onChunk = { chunk ->
                        contentBuilder.append(chunk)
                        mainHandler.post {
                            if (_binding != null) {
                                // 流式期间显示原始文本
                                llSummaryLoading.visibility = View.GONE
                                tvSummaryContent.visibility = View.VISIBLE
                                tvSummaryContent.text = contentBuilder.toString()
                            }
                        }
                    },
                    onDone = {
                        mainHandler.post {
                            if (_binding != null) {
                                // 完成后用 Markwon 渲染 Markdown
                                llSummaryLoading.visibility = View.GONE
                                tvSummaryContent.visibility = View.VISIBLE
                                markwon.setMarkdown(tvSummaryContent, contentBuilder.toString())
                            }
                        }
                    },
                    onError = { error ->
                        mainHandler.post {
                            if (_binding != null) {
                                llSummaryLoading.visibility = View.GONE
                                btnGenerateSummary.visibility = View.VISIBLE
                                tvSummaryContent.visibility = View.GONE
                                requireContext().showToast("生成摘要失败: $error")
                            }
                        }
                    }
                )
            }
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("推理任务详情")
            .setView(dialogView)
            .setPositiveButton("关闭", null)
            .create()

        dialog.setOnDismissListener {
            summaryCall?.cancel()
            summaryCall = null
        }

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(requireContext().getColor(R.color.primary))
    }

    /**
     * 设置分享区域的交互逻辑
     */
    private fun setupShareSection(dialogView: View, taskId: Long) {
        val btnCreateShare = dialogView.findViewById<MaterialButton>(R.id.btnCreateShare)
        val llShareResult = dialogView.findViewById<LinearLayout>(R.id.llShareResult)
        val tvShareLink = dialogView.findViewById<TextView>(R.id.tvShareLink)
        val tvExpireTime = dialogView.findViewById<TextView>(R.id.tvExpireTime)
        val btnCopyLink = dialogView.findViewById<MaterialButton>(R.id.btnCopyLink)
        val btnSystemShare = dialogView.findViewById<MaterialButton>(R.id.btnSystemShare)

        btnCreateShare.setOnClickListener {
            val expireOptions = arrayOf("1 小时", "24 小时", "3 天", "7 天")
            val expireHours = intArrayOf(1, 24, 72, 168)

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("选择有效期")
                .setItems(expireOptions) { _, which ->
                    performCreateShare(
                        taskId, expireHours[which],
                        btnCreateShare, llShareResult, tvShareLink, tvExpireTime,
                        btnCopyLink, btnSystemShare
                    )
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    /**
     * 调用 API 创建分享链接
     */
    private fun performCreateShare(
        taskId: Long,
        expireHours: Int,
        btnCreateShare: MaterialButton,
        llShareResult: LinearLayout,
        tvShareLink: TextView,
        tvExpireTime: TextView,
        btnCopyLink: MaterialButton,
        btnSystemShare: MaterialButton
    ) {
        btnCreateShare.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.createShare(
                    CreateShareRequest(taskId, expireHours)
                )
                if (response.code == 1 && response.data != null) {
                    val shareData = response.data
                    val shareUrl = "${RetrofitClient.WEB_BASE_URL}/share/${shareData.shareCode}"

                    btnCreateShare.visibility = View.GONE
                    llShareResult.visibility = View.VISIBLE
                    tvShareLink.text = shareUrl
                    tvExpireTime.text = "有效期至: ${shareData.expireTime.formatDateTime()}"

                    btnCopyLink.setOnClickListener {
                        val clipboard = requireContext().getSystemService(
                            Context.CLIPBOARD_SERVICE
                        ) as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText("share_link", shareUrl)
                        )
                        requireContext().showToast("链接已复制到剪贴板")
                    }

                    btnSystemShare.setOnClickListener {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "查看推理任务分析结果: $shareUrl")
                            type = "text/plain"
                        }
                        startActivity(Intent.createChooser(sendIntent, "分享链接"))
                    }
                } else {
                    requireContext().showToast(response.message ?: "生成分享链接失败")
                    btnCreateShare.isEnabled = true
                }
            } catch (e: Exception) {
                requireContext().showToast("生成分享链接失败: ${e.message}")
                btnCreateShare.isEnabled = true
            }
        }
    }

    /**
     * 确认删除推理任务
     */
    private fun confirmDeleteTask(taskId: Long) {
        requireContext().showConfirmDialog(
            title = "删除推理任务",
            message = "确定要删除推理任务 #${taskId} 吗？此操作不可恢复。"
        ) {
            deleteTask(taskId)
        }
    }

    /**
     * 删除推理任务
     */
    private fun deleteTask(taskId: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.deleteInferenceTasks(
                    DeleteInferenceTasksRequest(listOf(taskId))
                )
                if (response.code == 1) {
                    requireContext().showToast("删除成功")
                    currentPage = 1
                    loadTasks()
                } else {
                    requireContext().showToast(response.message ?: "删除失败")
                }
            } catch (e: Exception) {
                requireContext().showToast("删除失败: ${e.message}")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        summaryCall?.cancel()
        _binding = null
    }
}
