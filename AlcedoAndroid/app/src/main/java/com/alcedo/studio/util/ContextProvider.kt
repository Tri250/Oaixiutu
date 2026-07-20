package com.alcedo.studio.util

import android.content.Context
import android.util.Log

object ContextProvider {

    private const val TAG = "ContextProvider"
    private var _context: Context? = null
    private var initialized = false

    fun initialize(context: Context) {
        _context = context.applicationContext
        initialized = true
    }

    /**
     * 修复: 原使用 lateinit var，未初始化时访问会抛 UninitializedPropertyAccessException 导致崩溃。
     * 现改为可空类型 + 安全检查，未初始化时抛出带清晰信息的 IllegalStateException。
     */
    val context: Context
        get() = _context ?: throw IllegalStateException(
            "ContextProvider not initialized. Call ContextProvider.initialize(context) in Application.onCreate() first."
        )

    fun isInitialized(): Boolean = initialized
}