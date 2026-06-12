package com.time.set.utils

import java.util.Locale

object LocaleHelper {
    fun getLocaleName(localeCode: String): String {
        if (localeCode.isBlank() || localeCode == "默认") return "默认"
        
        return try {
            val locale = if (localeCode.contains("-")) {
                val parts = localeCode.split("-")
                Locale(parts[0], parts[1])
            } else {
                Locale(localeCode)
            }
            
            // 获取显示名称并首字母大写
            val displayName = locale.getDisplayName(Locale.getDefault())
            
            // 针对常见中文区域进行优化显示
            when (localeCode.lowercase()) {
                "zh-cn", "zh-hans" -> "简体中文"
                "zh-tw", "zh-hant", "zh-hk", "zh-mo" -> "繁体中文"
                "en-us" -> "英语 (美国)"
                "en-gb" -> "英语 (英国)"
                "ja-jp", "ja" -> "日语"
                "ko-kr", "ko" -> "韩语"
                else -> displayName
            }
        } catch (e: Exception) {
            localeCode
        }
    }
}
