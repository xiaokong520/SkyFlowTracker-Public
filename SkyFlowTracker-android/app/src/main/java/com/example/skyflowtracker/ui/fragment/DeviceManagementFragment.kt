package com.example.skyflowtracker.ui.fragment

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.skyflowtracker.R
import com.example.skyflowtracker.api.RetrofitClient
import com.example.skyflowtracker.api.model.DeleteDeviceRequest
import com.example.skyflowtracker.api.model.Device
import com.example.skyflowtracker.api.model.UpdateDeviceRequest
import com.example.skyflowtracker.databinding.FragmentDeviceManagementBinding
import com.example.skyflowtracker.ui.adapter.DeviceAdapter
import com.example.skyflowtracker.utils.getFileFromUri
import com.example.skyflowtracker.utils.showConfirmDialog
import com.example.skyflowtracker.utils.showToast
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 设备管理 Fragment（普通用户功能）
 */
class DeviceManagementFragment : Fragment() {

    private var _binding: FragmentDeviceManagementBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: DeviceAdapter
    private var currentPage = 1
    private var totalPages = 1
    private var isLoading = false
    private var searchSn: String? = null

    // 二维码图片选择（添加设备用）
    private var qrFileUri: Uri? = null
    private var qrFileNameView: android.widget.TextView? = null

    private val qrPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                qrFileUri = uri
                qrFileNameView?.text = "已选择图片"
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDeviceManagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
        loadDevices()
    }

    private fun initViews() {
        // 初始化 RecyclerView
        adapter = DeviceAdapter(
            onEdit = { showEditDialog(it) },
            onDelete = { confirmDelete(it) }
        )
        binding.rvDevices.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDevices.adapter = adapter

        // 滚动加载更多
        binding.rvDevices.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!recyclerView.canScrollVertically(1) && !isLoading && currentPage < totalPages) {
                    currentPage++
                    loadDevices(append = true)
                }
            }
        })

        // 下拉刷新
        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
        binding.swipeRefresh.setOnRefreshListener {
            currentPage = 1
            loadDevices()
        }

        // 搜索
        binding.btnSearch.setOnClickListener { doSearch() }
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { doSearch(); true } else false
        }

        // 添加设备
        binding.btnAdd.setOnClickListener { showAddDialog() }
    }

    private fun doSearch() {
        val keyword = binding.etSearch.text.toString().trim()
        searchSn = keyword.ifEmpty { null }
        currentPage = 1
        loadDevices()
    }

    private fun loadDevices(append: Boolean = false) {
        if (isLoading) return
        isLoading = true
        if (!append) binding.swipeRefresh.isRefreshing = true

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getDevicesList(
                    page = currentPage, size = 20, snCode = searchSn
                )
                if (response.code == 1 && response.data != null) {
                    val list = response.data.devicesList ?: emptyList()
                    totalPages = response.data.pagination?.totalPages ?: 1

                    if (append) {
                        val current = adapter.currentList.toMutableList()
                        current.addAll(list)
                        adapter.submitList(current)
                    } else {
                        adapter.submitList(list)
                    }

                    // 空状态
                    binding.llEmpty.visibility = if (adapter.currentList.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvDevices.visibility = if (adapter.currentList.isEmpty()) View.GONE else View.VISIBLE
                } else {
                    requireContext().showToast(response.message ?: "获取设备列表失败")
                }
            } catch (e: Exception) {
                requireContext().showToast("获取设备列表失败: ${e.message}")
            } finally {
                isLoading = false
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    // ==================== 添加设备 ====================
    private fun showAddDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_device, null)
        val toggleGroup = dialogView.findViewById<MaterialButtonToggleGroup>(R.id.toggleGroup)
        val tilSnCode = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilSnCode)
        val etSnCode = dialogView.findViewById<TextInputEditText>(R.id.etSnCode)
        val llQrCode = dialogView.findViewById<View>(R.id.llQrCode)
        val tvFileName = dialogView.findViewById<android.widget.TextView>(R.id.tvFileName)
        val btnSelectFile = dialogView.findViewById<View>(R.id.btnSelectFile)

        qrFileUri = null
        qrFileNameView = tvFileName

        // 切换添加方式
        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                if (checkedId == R.id.btnModeSn) {
                    tilSnCode.visibility = View.VISIBLE
                    llQrCode.visibility = View.GONE
                } else {
                    tilSnCode.visibility = View.GONE
                    llQrCode.visibility = View.VISIBLE
                }
            }
        }

        // 选择二维码图片
        btnSelectFile.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
            qrPickerLauncher.launch(intent)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("添加设备")
            .setView(dialogView)
            .setPositiveButton("确认添加", null) // 先设为 null，后面手动处理
            .setNegativeButton("取消", null)
            .create()

        dialog.show()

        // 设置按钮颜色
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(requireContext().getColor(R.color.primary))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(requireContext().getColor(R.color.text_secondary))

        // 手动处理确认按钮，避免自动关闭
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            val isSn = toggleGroup.checkedButtonId == R.id.btnModeSn
            if (isSn) {
                val snCode = etSnCode.text.toString().trim()
                if (snCode.isEmpty()) {
                    requireContext().showToast("请输入SN码")
                    return@setOnClickListener
                }
                addDeviceBySn(snCode, dialog)
            } else {
                if (qrFileUri == null) {
                    requireContext().showToast("请选择二维码图片")
                    return@setOnClickListener
                }
                addDeviceByQrCode(qrFileUri!!, dialog)
            }
        }
    }

    private fun addDeviceBySn(snCode: String, dialog: AlertDialog) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val snBody = snCode.toRequestBody("text/plain".toMediaTypeOrNull())
                val response = RetrofitClient.apiService.addDevicesBySn(snBody)
                handleAddResult(response, dialog)
            } catch (e: Exception) {
                requireContext().showToast("添加失败: ${e.message}")
            }
        }
    }

    private fun addDeviceByQrCode(uri: Uri, dialog: AlertDialog) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val file = requireContext().getFileFromUri(uri)
                if (file == null) {
                    requireContext().showToast("无法读取图片文件")
                    return@launch
                }
                val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
                val response = RetrofitClient.apiService.addDevicesByQrCode(body)
                handleAddResult(response, dialog)
            } catch (e: Exception) {
                requireContext().showToast("添加失败: ${e.message}")
            }
        }
    }

    private fun handleAddResult(
        response: com.example.skyflowtracker.api.model.ApiResponse<com.example.skyflowtracker.api.model.AddDevicesResponse>,
        dialog: AlertDialog
    ) {
        if (response.code == 1) {
            val data = response.data
            val errorMsg = data?.errorMsg ?: ""
            if (errorMsg.isNotEmpty()) {
                requireContext().showToast("部分成功：成功${data?.successCount}个，失败${data?.failCount}个")
            } else {
                requireContext().showToast("成功添加${data?.successCount ?: ""}个设备")
            }
            dialog.dismiss()
            currentPage = 1
            loadDevices()
        } else {
            val errorMsg = response.data?.errorMsg ?: ""
            requireContext().showToast(errorMsg.ifEmpty { response.message ?: "添加失败" })
        }
    }

    // ==================== 编辑设备 ====================

    private fun showEditDialog(device: Device) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_device, null)
        val etSn = dialogView.findViewById<TextInputEditText>(R.id.etSn)
        val etModel = dialogView.findViewById<TextInputEditText>(R.id.etModel)
        val etFcSn = dialogView.findViewById<TextInputEditText>(R.id.etFcSn)

        etSn.setText(device.sn)
        etModel.setText(device.model ?: "")
        etFcSn.setText(device.flightControllerSerialNumber ?: "")

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("编辑设备")
            .setView(dialogView)
            .setPositiveButton("确认修改") { _, _ ->
                val request = UpdateDeviceRequest(
                    sn = device.sn,
                    model = etModel.text.toString().trim(),
                    flightControllerSerialNumber = etFcSn.text.toString().trim()
                )
                updateDevice(request)
            }
            .setNegativeButton("取消", null)
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(requireContext().getColor(R.color.primary))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(requireContext().getColor(R.color.text_secondary))
    }

    private fun updateDevice(request: UpdateDeviceRequest) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.updateDevice(request)
                if (response.code == 1) {
                    requireContext().showToast("修改成功")
                    currentPage = 1
                    loadDevices()
                } else {
                    requireContext().showToast(response.message ?: "修改失败")
                }
            } catch (e: Exception) {
                requireContext().showToast("修改失败: ${e.message}")
            }
        }
    }

    // ==================== 删除设备 ====================

    private fun confirmDelete(device: Device) {
        requireContext().showConfirmDialog(
            title = "删除设备",
            message = "确定要删除设备 \"${device.sn}\" 吗？",
            onConfirm = { deleteDevice(device) }
        )
    }

    private fun deleteDevice(device: Device) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // 普通用户不传 userId
                val request = DeleteDeviceRequest(sns = listOf(device.sn))
                val response = RetrofitClient.apiService.deleteDevice(request)
                if (response.code == 1) {
                    requireContext().showToast("删除成功")
                    currentPage = 1
                    loadDevices()
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
        qrFileNameView = null
        _binding = null
    }
}
