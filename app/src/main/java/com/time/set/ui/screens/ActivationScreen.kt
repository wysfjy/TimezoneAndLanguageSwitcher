package com.time.set.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.time.set.utils.ShellExecutor
import rikka.shizuku.Shizuku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivationScreen(
    isActivated: Boolean = false,
    onActivated: (ShellExecutor.Mode) -> Unit = {},
    onNext: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(title = { Text("激活工具") })
        },
        bottomBar = {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                Button(
                    onClick = onNext,
                    enabled = isActivated,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Text("下一步")
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.NavigateNext, null)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = if (isActivated) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (isActivated) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = if (isActivated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (isActivated) "已成功激活" else "未激活权限",
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (isActivated) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = if (isActivated) "权限已就绪，点击下方按钮进入主界面" else "需要 Root 或 Shizuku 权限以修改系统设置",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (!isActivated) {
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            try {
                                if (Shizuku.pingBinder()) {
                                    if (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                        onActivated(ShellExecutor.Mode.SHIZUKU)
                                    } else {
                                        Shizuku.requestPermission(0)
                                        snackbarHostState.showSnackbar("请在弹出的对话框中允许 Shizuku 权限")
                                    }
                                } else {
                                    snackbarHostState.showSnackbar("Shizuku 未运行")
                                }
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("Shizuku 错误: ${e.message}")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("通过 Shizuku 激活")
                }
                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                try {
                                    val process = Runtime.getRuntime().exec(arrayOf("su", "-v"))
                                    process.waitFor() == 0
                                } catch (e: Exception) {
                                    false
                                }
                            }
                            if (result) {
                                onActivated(ShellExecutor.Mode.ROOT)
                            } else {
                                snackbarHostState.showSnackbar("Root 权限获取失败，请确保设备已 Root")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("通过 Root 激活")
                }
            }
        }
    }
}
