package com.time.set.utils

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val PREFS_NAME = "app_prefs"
    private const val KEY_DETAILED_LOG = "detailed_log"
    
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var isDetailedLogEnabled: Boolean
        get() = prefs.getBoolean(KEY_DETAILED_LOG, false)
        set(value) = prefs.edit().putBoolean(KEY_DETAILED_LOG, value).apply()
}
