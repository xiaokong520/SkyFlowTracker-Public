package com.example.skyflowtracker.utils

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Kotlin 扩展函数
 */

/**
 * 显示 Toast
 */
fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

/**
 * 显示加载对话框
 */
fun Context.showLoadingDialog(message: String = "加载中..."): AlertDialog {
    return MaterialAlertDialogBuilder(this)
        .setMessage(message)
        .setCancelable(false)
        .create()
        .apply { show() }
}

/**
 * 隐藏软键盘
 */
fun View.hideKeyboard() {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(windowToken, 0)
}

/**
 * 显示软键盘
 */
fun View.showKeyboard() {
    requestFocus()
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
}

/**
 * 设置视图可见性
 */
fun View.visible() {
    visibility = View.VISIBLE
}

fun View.invisible() {
    visibility = View.INVISIBLE
}

fun View.gone() {
    visibility = View.GONE
}

/**
 * 验证邮箱格式
 */
fun String.isValidEmail(): Boolean {
    val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
    return this.matches(emailRegex)
}

/**
 * 格式化角色
 */
fun Int.formatRole(): String {
    return when (this) {
        1 -> "管理员"
        else -> "普通用户"
    }
}

/**
 * 格式化状态
 */
fun Int.formatStatus(): String {
    return when (this) {
        0 -> "封禁"
        1 -> "正常"
        2 -> "待审核"
        else -> "未知"
    }
}

/**
 * 获取状态颜色资源 ID
 */
fun Int.getStatusColorRes(): Int {
    return when (this) {
        0 -> com.example.skyflowtracker.R.color.status_blocked
        1 -> com.example.skyflowtracker.R.color.status_normal
        2 -> com.example.skyflowtracker.R.color.status_pending
        else -> com.example.skyflowtracker.R.color.text_secondary
    }
}

/**
 * 格式化日期时间
 */
fun String.formatDateTime(): String {
    // 简单格式化，如果需要更复杂的格式化可以使用 SimpleDateFormat
    return this.replace("T", " ").substringBefore(".")
}

/**
 * 显示确认对话框
 */
fun Context.showConfirmDialog(
    title: String,
    message: String,
    positiveText: String = "确定",
    negativeText: String = "取消",
    onConfirm: () -> Unit
) {
    MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(positiveText) { _, _ ->
            onConfirm()
        }
        .setNegativeButton(negativeText, null)
        .show()
}

/**
 * 显示输入对话框
 */
fun Context.showInputDialog(
    title: String,
    hint: String,
    defaultValue: String = "",
    onConfirm: (String) -> Unit
) {
    val editText = com.google.android.material.textfield.TextInputEditText(this).apply {
        setText(defaultValue)
        this.hint = hint
        setSingleLine()
    }

    val container = android.widget.FrameLayout(this).apply {
        val params = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(48, 16, 48, 16)
        addView(editText, params)
    }

    MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setView(container)
        .setPositiveButton("确定") { _, _ ->
            onConfirm(editText.text.toString().trim())
        }
        .setNegativeButton("取消", null)
        .show()

    editText.postDelayed({
        editText.showKeyboard()
    }, 200)
}

/**
 * 从 Uri 获取文件
 */
fun Context.getFileFromUri(uri: android.net.Uri): java.io.File? {
    return try {
        val inputStream = contentResolver.openInputStream(uri) ?: return null
        val file = java.io.File(cacheDir, "temp_image_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { outputStream ->
            inputStream.copyTo(outputStream)
        }
        file
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
