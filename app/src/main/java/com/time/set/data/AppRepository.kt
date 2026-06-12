package com.time.set.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class FullAppItem(
    val name: String,
    val packageName: String,
    val isSystemApp: Boolean = false
)

object AppRepository {
    fun getInstalledApps(context: Context, userId: Int = 0, includeSystemApps: Boolean = true): List<FullAppItem> {
        val pm = context.packageManager
        
        // 只有非主用户才需要查询用户包列表
        val userPackages: Set<String>? = if (userId != 0) {
            try {
                val result = com.time.set.utils.ShellExecutor.exec("pm list packages --user $userId")
                result.lines().mapNotNull { line ->
                    if (line.startsWith("package:")) line.substringAfter("package:").trim() else null
                }.toSet()
            } catch (e: Exception) {
                null
            }
        } else null

        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        
        return apps.filter { appInfo ->
            // 主用户显示全部；其他用户只显示该用户有的
            (userPackages == null || userPackages.contains(appInfo.packageName))
        }.filter { appInfo ->
            // 根据 includeSystemApps 参数过滤系统应用
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            includeSystemApps || !isSystem
        }.map { appInfo ->
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            FullAppItem(
                name = appInfo.loadLabel(pm).toString(),
                packageName = appInfo.packageName,
                isSystemApp = isSystem
            )
        }.sortedBy { it.name }
    }

    /**
     * 图标内存缓存（LRU，最多 200 个）。
     * MIUI IconCustomizer 对每个图标做全尺寸 bitmap 解码，
     * 缓存可避免重复解码带来的卡顿和 GC 压力。
     */
    private val iconCache = object : LinkedHashMap<String, Drawable?>(200, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Drawable?>): Boolean {
            return size > 200
        }
    }

    /**
     * 按需加载单个应用图标（带内存缓存）。
     */
    fun loadAppIcon(context: Context, packageName: String): Drawable? {
        iconCache[packageName]?.let { return it }

        val drawable = try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            appInfo.loadIcon(pm)
        } catch (e: Exception) {
            null
        }

        iconCache[packageName] = drawable
        return drawable
    }

    /**
     * 批量预加载图标，用于快速滚动时的预取。
     * 每批加载 batchSize 个，通过 onProgress 回调实时更新 UI。
     */
    suspend fun preloadIcons(
        context: Context,
        packageNames: List<String>,
        batchSize: Int = 8,
        onProgress: () -> Unit = {}
    ) {
        packageNames.chunked(batchSize).forEach { chunk ->
            chunk.forEach { pkg ->
                if (iconCache[pkg] == null) {
                    loadAppIcon(context, pkg)
                }
            }
            onProgress()
        }
    }
}
