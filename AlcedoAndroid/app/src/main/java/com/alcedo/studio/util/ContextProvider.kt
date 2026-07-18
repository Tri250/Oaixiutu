package com.alcedo.studio.util

import android.content.Context

object ContextProvider {

    private lateinit var _context: Context

    fun initialize(context: Context) {
        _context = context.applicationContext
    }

    val context: Context
        get() = _context
}