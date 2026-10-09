package com.example.skyflowtracker.utils

import com.google.android.material.textfield.TextInputLayout

/**
 * 表单验证工具类
 */
object Validator {
    
    /**
     * 验证规则
     */
    data class Rule(
        val value: String?,
        val errorMessage: String,
        val inputLayout: TextInputLayout? = null,
        val condition: (String?) -> Boolean = { it.isNullOrEmpty() }
    )
    
    /**
     * 执行验证
     * @return 第一个验证失败的错误信息，如果全部通过则返回 null
     */
    fun validate(vararg rules: Rule): String? {
        // 先清除所有错误
        rules.forEach { it.inputLayout?.error = null }
        
        // 找到第一个错误
        val failedRule = rules.firstOrNull { it.condition(it.value) }
        
        // 如果有错误，设置到对应的 TextInputLayout
        failedRule?.inputLayout?.error = failedRule?.errorMessage
        
        return failedRule?.errorMessage
    }
    
    /**
     * 验证并显示 Toast
     * @return 是否验证通过
     */
    fun validateAndShow(context: android.content.Context, vararg rules: Rule): Boolean {
        val error = validate(*rules)
        if (error != null) {
            context.showToast(error)
            return false
        }
        return true
    }
}

/**
 * 扩展函数：非空验证
 */
fun String?.required(message: String, inputLayout: TextInputLayout? = null) = 
    Validator.Rule(this, message, inputLayout) { it.isNullOrEmpty() }

/**
 * 扩展函数：最小长度验证
 */
fun String?.minLength(length: Int, message: String, inputLayout: TextInputLayout? = null) = 
    Validator.Rule(this, message, inputLayout) { 
        it.isNullOrEmpty() || it.length < length 
    }

/**
 * 扩展函数：邮箱格式验证
 */
fun String?.email(message: String, inputLayout: TextInputLayout? = null) = 
    Validator.Rule(this, message, inputLayout) { 
        it.isNullOrEmpty() || !it.isValidEmail() 
    }

/**
 * 扩展函数：自定义验证
 */
fun String?.custom(message: String, inputLayout: TextInputLayout? = null, condition: (String?) -> Boolean) = 
    Validator.Rule(this, message, inputLayout, condition)

