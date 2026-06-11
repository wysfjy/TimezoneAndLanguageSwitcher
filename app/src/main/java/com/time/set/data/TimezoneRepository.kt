package com.time.set.data

import com.time.set.ui.screens.TimezoneItem
import java.util.*

object TimezoneRepository {
    fun getTimezones(): List<TimezoneItem> {
        val ids = TimeZone.getAvailableIDs()
        val result = mutableListOf<TimezoneItem>()
        
        for (id in ids) {
            val tz = TimeZone.getTimeZone(id)
            val displayName = tz.getDisplayName(Locale.getDefault())
            
            // 获取 UTC 偏移量格式如 UTC+08:00
            val rawOffset = tz.rawOffset
            val hours = rawOffset / (1000 * 60 * 60)
            val minutes = Math.abs(rawOffset / (1000 * 60) % 60)
            val offsetStr = String.format("UTC%+03d:%02d", hours, minutes)
            
            result.add(TimezoneItem(id, displayName, offsetStr))
        }
        
        // 默认按 ID 排序
        return result.distinctBy { it.id }.sortedBy { it.id }
    }
}
