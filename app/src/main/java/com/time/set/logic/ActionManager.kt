package com.time.set.logic

import android.util.Log
import com.time.set.utils.Prefs
import com.time.set.utils.ShellExecutor

object ActionManager {
    private const val TAG = "ActionManager"
    
    fun setTimezone(timezoneId: String): String {
        // 使用用户提供的命令：service call alarm 3 s16 'America/New_York'
        val command = "service call alarm 3 s16 '$timezoneId'"
        return ShellExecutor.exec(command)
    }
    
    fun setAppLocale(packageName: String, locale: String, userId: Int = 0): String {
        if (Prefs.isDetailedLogEnabled) {
            Log.d(TAG, "Setting locale for $packageName to '$locale' (user $userId)")
        }
        // 使用引号包裹 locale 以支持空字符串（重置为默认）
        val command = "cmd locale set-app-locales $packageName --locales '$locale' --user $userId"
        val result = ShellExecutor.exec(command)
        
        // 修改成功后尝试强行停止应用以使语言生效
        if (result.contains("成功") || result.isEmpty()) {
            // 增加 0.3s 延迟，确保系统已处理完语言变更持久化
            Thread.sleep(300)

            if (Prefs.isDetailedLogEnabled) {
                Log.d(TAG, "Force stopping $packageName")
            }
            ShellExecutor.exec("am force-stop $packageName --user $userId")
        }
        
        return result
    }

    fun getAppLocale(packageName: String, userId: Int = 0): String {
        // 使用用户提供的命令：cmd locale get-app-locales <PKG> --user <USER_ID>
        val command = "cmd locale get-app-locales $packageName --user $userId"
        val result = ShellExecutor.exec(command)
        
        // 解析输出，例如: Locales for com.tencent.mm for user 0 are [en-US]
        val regex = "\\[(.*)]".toRegex()
        val match = regex.find(result)
        return match?.groupValues?.get(1)?.ifEmpty { "默认" } ?: "默认"
    }

    data class UserInfo(val id: Int, val name: String, val isRunning: Boolean)

    fun getUsers(): List<UserInfo> {
        val result = ShellExecutor.exec("pm list users")
        val users = mutableListOf<UserInfo>()
        // 匹配格式: UserInfo{0:也要一下:4c13} running 或 UserInfo{999:XSpace:801010} running
        val regex = "UserInfo\\{(\\d+):([^:]+):[^}]+\\}\\s*(running)?".toRegex()
        
        regex.findAll(result).forEach { match ->
            val id = match.groupValues[1].toInt()
            var name = match.groupValues[2]
            val isRunning = match.groupValues[3].isNotEmpty()
            
            if (name == "XSpace") {
                name = "分身"
            }
            
            users.add(UserInfo(id, name, isRunning))
        }
        return if (users.isEmpty()) listOf(UserInfo(0, "主用户", true)) else users
    }
}
