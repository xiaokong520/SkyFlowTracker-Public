package com.example.skyflowtracker.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Token 管理工具
 */
object TokenManager {
    
    private const val PREF_NAME = "sky_flow_tracker_prefs"
    private const val KEY_TOKEN = "token"
    private const val KEY_REMEMBER_ME = "remember_me"
    
    private lateinit var prefs: SharedPreferences
    
    /**
     * 初始化
     */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * 保存 Token
     * @param token Token 值
     * @param rememberMe 是否记住我
     */
    fun saveToken(token: String, rememberMe: Boolean = false) {
        prefs.edit().apply {
            putString(KEY_TOKEN, token)
            putBoolean(KEY_REMEMBER_ME, rememberMe)
            apply()
        }
    }
    
    /**
     * 获取 Token
     */
    fun getToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }
    
    /**
     * 是否有 Token
     */
    fun hasToken(): Boolean {
        return !getToken().isNullOrEmpty()
    }
    
    /**
     * 清除 Token
     */
    fun clearToken() {
        prefs.edit().apply {
            remove(KEY_TOKEN)
            remove(KEY_REMEMBER_ME)
            apply()
        }
    }
    
    /**
     * 是否记住我
     */
    fun isRememberMe(): Boolean {
        return prefs.getBoolean(KEY_REMEMBER_ME, false)
    }
}
