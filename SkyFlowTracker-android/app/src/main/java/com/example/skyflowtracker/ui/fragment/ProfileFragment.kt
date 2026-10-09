package com.example.skyflowtracker.ui.fragment

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.skyflowtracker.R
import com.example.skyflowtracker.api.RetrofitClient
import com.example.skyflowtracker.api.model.UpdatePasswordRequest
import com.example.skyflowtracker.api.model.UpdateUserInfoRequest
import com.example.skyflowtracker.api.model.User
import com.example.skyflowtracker.databinding.FragmentProfileBinding
import com.example.skyflowtracker.ui.CustomDefaultLayoutActivity
import com.example.skyflowtracker.ui.LoginActivity
import com.example.skyflowtracker.utils.TokenManager
import com.example.skyflowtracker.utils.formatDateTime
import com.example.skyflowtracker.utils.getFileFromUri
import com.example.skyflowtracker.utils.showConfirmDialog
import com.example.skyflowtracker.utils.showInputDialog
import com.example.skyflowtracker.utils.showToast
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody

/**
 * 个人中心 Fragment
 */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    // 图片选择器
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> uploadAvatar(uri) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
        loadUserInfo()
    }

    private fun initViews() {
        // 头像点击上传
        binding.cvAvatar.setOnClickListener { selectImage() }

        // 进入飞行界面
        binding.btnFlight.setOnClickListener {
            startActivity(Intent(requireContext(), CustomDefaultLayoutActivity::class.java))
        }

        // 修改密码
        binding.btnChangePassword.setOnClickListener { showChangePasswordDialog() }

        // 退出登录
        binding.btnLogout.setOnClickListener {
            requireContext().showConfirmDialog(
                title = "退出登录",
                message = "确定要退出登录吗？",
                onConfirm = { logout() }
            )
        }
    }

    private fun loadUserInfo() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getUserInfo()
                if (response.code == 1 && response.data != null) {
                    displayUserInfo(response.data)
                } else {
                    requireContext().showToast(response.message ?: "获取用户信息失败")
                }
            } catch (e: Exception) {
                requireContext().showToast("获取用户信息失败: ${e.message}")
            }
        }
    }

    private fun displayUserInfo(user: User) {
        binding.tvUsername.text = user.userName

        // 显示头像
        if (!user.avatar.isNullOrEmpty()) {
            Glide.with(this)
                .load(user.avatar + "?t=" + System.currentTimeMillis())
                .placeholder(R.mipmap.sky_flow_tracker_new_icon)
                .error(R.mipmap.sky_flow_tracker_new_icon)
                .circleCrop()
                .into(binding.ivAvatar)
        }

        // 字段映射
        val fieldLabels = mapOf(
            "userId" to "用户ID", "userName" to "用户名", "nickName" to "昵称",
            "email" to "邮箱", "role" to "角色", "status" to "状态",
            "createTime" to "注册时间", "lastLoginTime" to "最后登录"
        )

        binding.llInfoContainer.removeAllViews()

        val userMap = mapOf(
            "userId" to user.userId, "userName" to user.userName,
            "nickName" to user.nickName, "email" to user.email,
            "role" to user.role, "status" to user.status,
            "createTime" to user.createTime, "lastLoginTime" to user.lastLoginTime
        )

        userMap.forEach { (key, value) ->
            if (value != null) {
                val label = fieldLabels[key] ?: key
                val displayValue = formatValue(key, value)
                addInfoItem(label, displayValue, key == "nickName")
            }
        }
    }

    private fun formatValue(key: String, value: Any?): String {
        return when (key) {
            "role" -> if (value == 1) "管理员" else "普通用户"
            "status" -> when (value) { 0 -> "封禁"; 1 -> "正常"; 2 -> "待审核"; else -> "未知" }
            "createTime", "lastLoginTime" -> value?.toString()?.formatDateTime() ?: "-"
            else -> value?.toString() ?: "-"
        }
    }

    private fun addInfoItem(label: String, value: String, clickable: Boolean = false) {
        val itemView = LayoutInflater.from(requireContext()).inflate(
            R.layout.item_profile_info, binding.llInfoContainer, false
        )
        itemView.findViewById<TextView>(R.id.tvLabel).text = label
        itemView.findViewById<TextView>(R.id.tvValue).text = value

        if (clickable) {
            itemView.setBackgroundResource(android.R.drawable.list_selector_background)
            itemView.setOnClickListener {
                if (label == "昵称") showEditNicknameDialog(value)
            }
        }
        binding.llInfoContainer.addView(itemView)
    }

    private fun selectImage() {
        val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
        imagePickerLauncher.launch(intent)
    }

    private fun uploadAvatar(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val file = requireContext().getFileFromUri(uri) ?: run {
                    requireContext().showToast("无法读取图片文件"); return@launch
                }
                if (file.length() > 2 * 1024 * 1024) {
                    requireContext().showToast("图片大小不能超过 2MB"); return@launch
                }
                val fileName = file.name.lowercase()
                if (!fileName.endsWith(".jpg") && !fileName.endsWith(".jpeg") &&
                    !fileName.endsWith(".png") && !fileName.endsWith(".gif")
                ) {
                    requireContext().showToast("只支持 JPG、PNG、GIF 格式"); return@launch
                }
                val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
                val response = RetrofitClient.apiService.updateAvatar(body)
                if (response.code == 1) {
                    requireContext().showToast("头像修改成功"); loadUserInfo()
                } else {
                    requireContext().showToast(response.message ?: "头像修改失败")
                }
            } catch (e: Exception) {
                requireContext().showToast("头像修改失败: ${e.message}")
            }
        }
    }

    private fun showEditNicknameDialog(currentNickname: String) {
        requireContext().showInputDialog(
            title = "修改昵称", hint = "请输入新昵称", defaultValue = currentNickname,
            onConfirm = { newNickname ->
                if (newNickname.isEmpty()) { requireContext().showToast("昵称不能为空"); return@showInputDialog }
                if (newNickname.length > 20) { requireContext().showToast("昵称长度不能超过20个字符"); return@showInputDialog }
                updateNickname(newNickname)
            }
        )
    }

    private fun updateNickname(newNickname: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.updateUserInfo(UpdateUserInfoRequest(nickName = newNickname))
                if (response.code == 1) {
                    requireContext().showToast("昵称修改成功"); loadUserInfo()
                } else {
                    requireContext().showToast(response.message ?: "昵称修改失败")
                }
            } catch (e: Exception) {
                requireContext().showToast("昵称修改失败: ${e.message}")
            }
        }
    }

    private fun showChangePasswordDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_change_password, null)
        val etNewPassword = dialogView.findViewById<TextInputEditText>(R.id.etNewPassword)
        val etConfirmPassword = dialogView.findViewById<TextInputEditText>(R.id.etConfirmPassword)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("修改密码")
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                val newPassword = etNewPassword.text.toString().trim()
                val confirmPassword = etConfirmPassword.text.toString().trim()
                when {
                    newPassword.isEmpty() -> requireContext().showToast("请输入新密码")
                    newPassword.length < 6 -> requireContext().showToast("密码长度至少6位")
                    confirmPassword.isEmpty() -> requireContext().showToast("请确认密码")
                    newPassword != confirmPassword -> requireContext().showToast("两次密码不一致")
                    else -> updatePassword(newPassword)
                }
            }
            .setNegativeButton("取消", null)
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(requireContext().getColor(R.color.primary))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(requireContext().getColor(R.color.text_secondary))
    }

    private fun updatePassword(newPassword: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.updatePassword(UpdatePasswordRequest(password = newPassword))
                if (response.code == 1) {
                    requireContext().showToast("密码修改成功，请重新登录")
                    TokenManager.clearToken()
                    binding.root.postDelayed({
                        val intent = Intent(requireContext(), LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                    }, 2000)
                } else {
                    requireContext().showToast(response.message ?: "密码修改失败")
                }
            } catch (e: Exception) {
                requireContext().showToast("密码修改失败: ${e.message}")
            }
        }
    }

    private fun logout() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.logout()
                if (response.code == 1) requireContext().showToast("退出登录成功")
                else requireContext().showToast(response.message ?: "退出登录失败")
            } catch (e: Exception) {
                requireContext().showToast("退出登录失败: ${e.message}")
            } finally {
                TokenManager.clearToken()
                val intent = Intent(requireContext(), LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
