package com.time.set.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.time.set.ui.theme.时区语言一键切换Theme
import com.time.set.utils.ShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun MainContainer() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    
    var isActivated by remember { mutableStateOf(false) }
    var showMain by remember { mutableStateOf(false) }
    var isEntering by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var initialCheckDone by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val lastModeStr = prefs.getString("last_activation_mode", ShellExecutor.Mode.NONE.name)
        val lastMode = ShellExecutor.Mode.valueOf(lastModeStr ?: ShellExecutor.Mode.NONE.name)
        
        if (lastMode != ShellExecutor.Mode.NONE) {
            val stillWorking = withContext(Dispatchers.IO) {
                ShellExecutor.currentMode = lastMode
                ShellExecutor.isActivated()
            }
            if (stillWorking) {
                isActivated = true
                showMain = true
            } else {
                ShellExecutor.currentMode = ShellExecutor.Mode.NONE
            }
        }
        initialCheckDone = true
    }

    if (!initialCheckDone) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (isEntering) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("正在加载资源...", style = MaterialTheme.typography.bodyMedium)
            }
        }
        LaunchedEffect(Unit) {
            delay(800)
            showMain = true
            isEntering = false
        }
    } else if (!showMain) {
        ActivationScreen(
            isActivated = isActivated,
            onActivated = { mode ->
                ShellExecutor.currentMode = mode
                isActivated = true
                prefs.edit().putString("last_activation_mode", mode.name).apply()
            },
            onNext = { isEntering = true }
        )
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Apps, contentDescription = null) },
                        label = { Text("应用语言") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                        label = { Text("时区设置") }
                    )
                }
            }
        ) { innerPadding ->
            Surface(modifier = Modifier.padding(innerPadding)) {
                when (selectedTab) {
                    0 -> AppListScreen(
                        showTopBar = true,
                        onReset = {
                            // 清除激活信息
                            prefs.edit().remove("last_activation_mode").apply()
                            // 销毁持久 Shell
                            ShellExecutor.destroy()
                            ShellExecutor.currentMode = ShellExecutor.Mode.NONE
                            // 重置状态回到激活页面
                            isActivated = false
                            showMain = false
                            initialCheckDone = true // 已经检查过了，只是回退
                        }
                    )
                    1 -> TimezoneListScreenContent()
                }
            }
        }
    }
}

@Composable
fun TimezoneListScreenContent() {
    TimezoneListScreen(showTopBar = true)
}

@Preview(showBackground = true)
@Composable
fun MainContainerPreview() {
    时区语言一键切换Theme {
        MainContainer()
    }
}
