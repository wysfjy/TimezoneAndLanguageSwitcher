package com.time.set.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.*

enum class AppSortOrder {
    NAME, PACKAGE_NAME
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(showTopBar: Boolean = true) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(AppSortOrder.NAME) }
    var isAscending by remember { mutableStateOf(true) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showSystemApps by remember { mutableStateOf(false) }
    
    var showLocaleSheet by remember { mutableStateOf(false) }
    var showSystemAppWarning by remember { mutableStateOf(false) }
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

    LaunchedEffect(selectedUser?.id, refreshTrigger) {
        isLoadingApps = true
        apps = withContext(Dispatchers.IO) {
            AppRepository.getInstalledApps(context, selectedUser?.id ?: 0)
        }
        isLoadingApps = false
    }

    val sortedAndFilteredApps = remember(searchQuery, sortOrder, isAscending, showSystemApps, apps) {
        val collator = Collator.getInstance(Locale.CHINA)
        val filtered = apps.filter {
            val matchesSearch = it.name.contains(searchQuery, ignoreCase = true) || 
                               it.packageName.contains(searchQuery, ignoreCase = true)
            
            val matchesSystemFilter = if (showSystemApps) true else !it.isSystemApp
            
            matchesSearch && matchesSystemFilter
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
                    title = { Text("应用语言设置") },
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

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(sortedAndFilteredApps, key = { it.packageName }) { app ->
                    var currentLocale by remember(app.packageName, refreshTrigger, selectedUser?.id) { mutableStateOf("...") }
                    var isFetchingLocale by remember(app.packageName, refreshTrigger, selectedUser?.id) { mutableStateOf(false) }
                    
                    LaunchedEffect(app.packageName, refreshTrigger, selectedUser?.id) {
                        selectedUser?.let { user ->
                            isFetchingLocale = true
                            currentLocale = withContext(Dispatchers.IO) {
                                ActionManager.getAppLocale(app.packageName, user.id)
                            }
                            isFetchingLocale = false
                        }
                    }

                    ListItem(
                        headlineContent = { Text(app.name) },
                        supportingContent = { Text(app.packageName) },
                        trailingContent = { 
                            Box(contentAlignment = Alignment.Center) {
                                if (isFetchingLocale) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(
                                        text = currentLocale,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        leadingContent = {
                            if (app.icon != null) {
                                Image(
                                    bitmap = app.icon.toBitmap().asImageBitmap(),
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
                                    scope.launch {
                                        isSettingLocale = true
                                        val result = withContext(Dispatchers.IO) {
                                            ActionManager.setAppLocale(
                                                selectedApp!!.packageName, 
                                                code, 
                                                selectedUser?.id ?: 0
                                            )
                                        }
                                        
                                        if (selectedApp?.isSystemApp == true) {
                                            showSystemAppWarning = true
                                        }
                                        
                                        snackbarHostState.showSnackbar("设置结果: $result")
                                        isSettingLocale = false
                                        showLocaleSheet = false
                                        refreshTrigger++
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
    }
}
