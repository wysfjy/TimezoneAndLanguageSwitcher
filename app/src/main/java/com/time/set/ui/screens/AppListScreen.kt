package com.time.set.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.time.set.data.AppRepository
import com.time.set.data.FullAppItem
import com.time.set.logic.ActionManager
import com.time.set.utils.LocaleHelper
import com.time.set.utils.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.*

enum class AppSortOrder {
    NAME, PACKAGE_NAME
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperToolsDialog(
    onDismissRequest: () -> Unit,
    onResetActivation: () -> Unit
) {
    var isDetailedLogEnabled by remember { mutableStateOf(Prefs.isDetailedLogEnabled) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("开发者工具") },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text("详细日志输出 (Logcat)") },
                    trailingContent = {
                        Switch(
                            checked = isDetailedLogEnabled,
                            onCheckedChange = {
                                isDetailedLogEnabled = it
                                Prefs.isDetailedLogEnabled = it
                            }
                        )
                    }
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("重置激活状态", color = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable {
                        onResetActivation()
                        onDismissRequest()
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("关闭")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(showTopBar: Boolean = true, onReset: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // 连点计数器
    var titleClickCount by remember { mutableIntStateOf(0) }
    var lastClickTime by remember { mutableLongStateOf(0L) }
    
    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(AppSortOrder.NAME) }
    var isAscending by remember { mutableStateOf(true) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showSystemApps by remember { mutableStateOf(false) }
    
    var showLocaleSheet by remember { mutableStateOf(false) }
    var showSystemAppWarning by remember { mutableStateOf(false) }
    var showDeveloperTools by remember { mutableStateOf(false) }
    var selectedApp by remember { mutableStateOf<FullAppItem?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // 多用户支持
    var users by remember { mutableStateOf<List<ActionManager.UserInfo>>(emptyList()) }
    var selectedUser by remember { mutableStateOf<ActionManager.UserInfo?>(null) }
    var showUserMenu by remember { mutableStateOf(false) }
    var isLoadingUsers by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        users = withContext(Dispatchers.IO) { ActionManager.getUsers() }
        selectedUser = users.firstOrNull()
        isLoadingUsers = false
    }

    var isLoadingApps by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf<List<FullAppItem>>(emptyList()) }
    // 存储每个应用的语言信息
    val localeMap = remember { mutableStateMapOf<String, String>() }
    var isLoadingLocales by remember { mutableStateOf(false) }

    LaunchedEffect(selectedUser?.id, refreshTrigger, showSystemApps) {
        isLoadingApps = true
        localeMap.clear()
        apps = withContext(Dispatchers.IO) {
            AppRepository.getInstalledApps(context, selectedUser?.id ?: 0, showSystemApps)
        }
        isLoadingApps = false

        // 批量加载语言信息，限制并发数为 5
        isLoadingLocales = true
        val userId = selectedUser?.id ?: 0
        val concurrency = 5
        val mutableLocaleMap = mutableMapOf<String, String>()
        withContext(Dispatchers.IO) {
            apps.chunked(concurrency).forEach { chunk ->
                val results = chunk.map { app ->
                    async {
                        app.packageName to ActionManager.getAppLocale(app.packageName, userId)
                    }
                }.awaitAll()
                results.forEach { (pkg, locale) ->
                    mutableLocaleMap[pkg] = locale
                }
                mutableLocaleMap.forEach { (pkg, locale) ->
                    localeMap[pkg] = locale
                }
            }
        }
        isLoadingLocales = false
    }

    val sortedAndFilteredApps = remember(searchQuery, sortOrder, isAscending, apps) {
        val collator = Collator.getInstance(Locale.CHINA)
        val filtered = apps.filter {
            it.name.contains(searchQuery, ignoreCase = true) || 
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
        
        val comparator = when (sortOrder) {
            AppSortOrder.NAME -> Comparator<FullAppItem> { a, b -> collator.compare(a.name, b.name) }
            AppSortOrder.PACKAGE_NAME -> compareBy { it.packageName }
        }
        
        if (isAscending) filtered.sortedWith(comparator) else filtered.sortedWith(comparator.reversed())
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (showTopBar) {
                CenterAlignedTopAppBar(
                    title = { 
                        Text(
                            text = "应用语言设置",
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clickable {
                                    val now = System.currentTimeMillis()
                                    if (now - lastClickTime < 500) {
                                        titleClickCount++
                                    } else {
                                        titleClickCount = 1
                                    }
                                    lastClickTime = now
                                    if (titleClickCount >= 10) {
                                        showDeveloperTools = true
                                        titleClickCount = 0
                                    }
                                }
                        ) 
                    },
                    actions = {
                        // 用户切换按钮
                        Box {
                            IconButton(onClick = { showUserMenu = true }) {
                                Icon(Icons.Default.Person, contentDescription = "切换用户")
                            }
                            DropdownMenu(
                                expanded = showUserMenu,
                                onDismissRequest = { showUserMenu = false }
                            ) {
                                users.forEach { user ->
                                    DropdownMenuItem(
                                        text = { Text("${user.name} (ID: ${user.id})") },
                                        onClick = {
                                            selectedUser = user
                                            showUserMenu = false
                                            refreshTrigger++
                                        },
                                        leadingIcon = {
                                            if (selectedUser?.id == user.id) Icon(Icons.Default.Check, null)
                                        }
                                    )
                                }
                            }
                        }
                        
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.Sort, contentDescription = "排序")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("以应用名称排序") },
                                    onClick = {
                                        sortOrder = AppSortOrder.NAME
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (sortOrder == AppSortOrder.NAME) Icon(Icons.Default.Check, null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("以包名排序") },
                                    onClick = {
                                        sortOrder = AppSortOrder.PACKAGE_NAME
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (sortOrder == AppSortOrder.PACKAGE_NAME) Icon(Icons.Default.Check, null)
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(if (isAscending) "当前：正序" else "当前：倒序") },
                                    onClick = {
                                        isAscending = !isAscending
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (isAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                            null
                                        )
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(if (showSystemApps) "隐藏系统应用" else "显示系统应用") },
                                    onClick = {
                                        showSystemApps = !showSystemApps
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (showSystemApps) Icon(Icons.Default.Check, null)
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("刷新") },
                                    onClick = {
                                        showSortMenu = false
                                        refreshTrigger++
                                        scope.launch {
                                            val currentUserId = selectedUser?.id ?: 0
                                            users = withContext(Dispatchers.IO) { ActionManager.getUsers() }
                                            // 刷新后尽量保持当前选中的用户
                                            selectedUser = users.find { it.id == currentUserId } ?: users.firstOrNull()
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Refresh, null)
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("查看应用权限") },
                                    onClick = {
                                        showSortMenu = false
                                        val intent = android.content.Intent(
                                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                        ).apply {
                                            data = android.net.Uri.fromParts("package", context.packageName, null)
                                        }
                                        context.startActivity(intent)
                                    }
                                )
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // 加载用户或应用进度条
            if (isLoadingUsers || isLoadingApps) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // 当前选中的用户提示
            selectedUser?.let { user ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "当前正在设置: ${user.name} (ID: ${user.id})",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            SearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = {},
                active = false,
                onActiveChange = {},
                placeholder = { Text("搜索应用名或包名...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {}

            val listState = rememberLazyListState()
            // 图标缓存：mutableStateMapOf 确保加载完自动触发 recomposition
            val iconMap = remember { mutableStateMapOf<String, android.graphics.drawable.Drawable?>() }

            // 监听滚动位置，批量预取当前可见区域 + 前后各 20 个图标的图标
            LaunchedEffect(listState.isScrollInProgress, sortedAndFilteredApps) {
                if (sortedAndFilteredApps.isEmpty()) return@LaunchedEffect
                val visible = listState.layoutInfo.visibleItemsInfo
                if (visible.isEmpty()) return@LaunchedEffect
                val first = (visible.first().index - 20).coerceAtLeast(0)
                val last = (visible.last().index + 20).coerceAtMost(sortedAndFilteredApps.lastIndex)
                val toLoad = (first..last).mapNotNull { i ->
                    val pkg = sortedAndFilteredApps[i].packageName
                    if (pkg !in iconMap) pkg else null
                }
                if (toLoad.isEmpty()) return@LaunchedEffect
                // 单协程、分批加载，避免并发竞争
                withContext(Dispatchers.IO) {
                    toLoad.chunked(4).forEach { chunk ->
                        chunk.forEach { pkg ->
                            iconMap[pkg] = AppRepository.loadAppIcon(context, pkg)
                        }
                    }
                }
            }

            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(sortedAndFilteredApps, key = { it.packageName }) { app ->
                    val currentLocale = localeMap[app.packageName]
                    val isFetchingLocale = currentLocale == null && isLoadingLocales
                    val icon = iconMap[app.packageName]

                    ListItem(
                        headlineContent = { Text(app.name) },
                        supportingContent = { Text(app.packageName) },
                        trailingContent = { 
                            Box(contentAlignment = Alignment.Center) {
                                if (isFetchingLocale) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(
                                        text = LocaleHelper.getLocaleName(currentLocale ?: "未知"),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        leadingContent = {
                            if (icon != null) {
                                Image(
                                    bitmap = icon.toBitmap().asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp)
                                )
                            } else {
                                Surface(
                                    modifier = Modifier.size(40.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.small
                                ) {}
                            }
                        },
                        modifier = Modifier.clickable { 
                            selectedApp = app
                            showLocaleSheet = true
                        }
                    )
                }
            }
        }
        
        if (showLocaleSheet && selectedApp != null) {
            var isSettingLocale by remember { mutableStateOf(false) }

            ModalBottomSheet(
                onDismissRequest = { if (!isSettingLocale) showLocaleSheet = false }
            ) {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                    Text(
                        text = "选择语言: ${selectedApp?.name}",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "作用于: ${selectedUser?.name} (ID: ${selectedUser?.id})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    if (isSettingLocale) {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        val locales = listOf(
                            "默认" to "",
                            "简体中文" to "zh-CN",
                            "繁体中文 (台湾)" to "zh-TW",
                            "繁体中文 (香港)" to "zh-HK",
                            "English (US)" to "en-US",
                            "English (UK)" to "en-GB",
                            "日本語" to "ja-JP"
                        )
                        
                        locales.forEach { (label, code) ->
                            ListItem(
                                headlineContent = { Text(label) },
                                modifier = Modifier.clickable {
                                    // 1. 立即关闭 BottomSheet 面板，提升体感速度
                                    showLocaleSheet = false
                                    
                                    scope.launch {
                                        // 2. 立即更新本地 UI 状态（乐观更新）
                                        selectedApp?.packageName?.let { pkg ->
                                            localeMap[pkg] = label
                                        }

                                        // 3. 在后台执行 Shell 命令
                                        val result = withContext(Dispatchers.IO) {
                                            ActionManager.setAppLocale(
                                                selectedApp!!.packageName, 
                                                code, 
                                                selectedUser?.id ?: 0
                                            )
                                        }
                                        
                                        // 4. 处理系统应用警告和结果提示
                                        if (selectedApp?.isSystemApp == true) {
                                            showSystemAppWarning = true
                                        }
                                        
                                        snackbarHostState.showSnackbar("设置结果: $result")
                                    }
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }

        if (showSystemAppWarning) {
            AlertDialog(
                onDismissRequest = { showSystemAppWarning = false },
                title = { Text("提示") },
                text = { Text("系统应用语言修改后，可能需要手动重启应用或重启系统才能完全生效。") },
                confirmButton = {
                    TextButton(onClick = { showSystemAppWarning = false }) {
                        Text("确定")
                    }
                }
            )
        }

        if (showDeveloperTools) {
            DeveloperToolsDialog(
                onDismissRequest = { showDeveloperTools = false },
                onResetActivation = onReset
            )
        }
    }
}
