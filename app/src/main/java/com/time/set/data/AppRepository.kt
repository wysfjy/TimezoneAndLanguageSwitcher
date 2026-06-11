package com.time.set.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class FullAppItem(
    val name: String,
    val packageName: String,
    val icon: Drawable?,
    val currentLocale: String = "默认",
    val isSystemApp: Boolean = false
)

object AppRepository {
    fun getInstalledApps(context: Context, userId: Int = 0): List<FullAppItem> {
        val pm = context.packageManager
        
        // 获取指定用户安装的所有包名
        val userPackages = try {
            val result = com.time.set.utils.ShellExecutor.exec("pm list packages --user $userId")
            result.lines().mapNotNull { line ->
                if (line.startsWith("package:")) line.substringAfter("package:").trim() else null
            }.toSet()
        } catch (e: Exception) {
            emptySet<String>()
        }

        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        
        return apps.filter { 
            // 如果是主用户(0)，则显示全部；如果是其他用户，只显示该用户有的
            if (userId == 0) true else userPackages.contains(it.packageName)
        }.map { appInfo ->
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            FullAppItem(
                name = appInfo.loadLabel(pm).toString(),
                packageName = appInfo.packageName,
                icon = appInfo.loadIcon(pm),
                isSystemApp = isSystem
            )
        }.sortedBy { it.name }
    }
}
