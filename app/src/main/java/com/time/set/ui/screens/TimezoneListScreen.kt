package com.time.set.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.time.set.data.TimezoneRepository
import com.time.set.logic.ActionManager
import com.time.set.ui.theme.时区语言一键切换Theme
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.*

enum class TimezoneSortOrder {
    OFFSET, NAME
}

data class TimezoneItem(
    val id: String,
    val displayName: String,
    val offset: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimezoneListScreen(showTopBar: Boolean = true) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(TimezoneSortOrder.OFFSET) }
    var isAscending by remember { mutableStateOf(true) }
    var showSortMenu by remember { mutableStateOf(false) }
    var isAdvancedMode by remember { mutableStateOf(false) }

    val timezones = remember {
        TimezoneRepository.getTimezones()
    }

    val sortedAndFilteredTz = remember(searchQuery, sortOrder, isAscending, isAdvancedMode) {
        val collator = Collator.getInstance(Locale.CHINA)
        val filtered = timezones.filter {
            val matchesSearch = it.id.contains(searchQuery, ignoreCase = true) || 
                               it.displayName.contains(searchQuery, ignoreCase = true) ||
                               it.offset.contains(searchQuery, ignoreCase = true)
            
            if (isAdvancedMode) {
                matchesSearch
            } else {
                val isBasicTz = it.id == "Asia/Shanghai" || 
                                it.id == "Asia/Hong_Kong" || 
                                it.id == "Europe/London"
                matchesSearch && isBasicTz
            }
        }
        
        val comparator = when (sortOrder) {
            TimezoneSortOrder.NAME -> Comparator<TimezoneItem> { a, b -> collator.compare(a.displayName, b.displayName) }
            TimezoneSortOrder.OFFSET -> compareBy { it.offset }
        }
        
        if (isAscending) filtered.sortedWith(comparator) else filtered.sortedWith(comparator.reversed())
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (showTopBar) {
                LargeTopAppBar(
                    title = { Text("系统时区设置") },
                    actions = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "高级模式",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isAdvancedMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Switch(
                                checked = isAdvancedMode,
                                onCheckedChange = { isAdvancedMode = it },
                                modifier = Modifier.padding(horizontal = 8.dp).scale(0.8f)
                            )
                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "排序")
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("以 UTC 偏移量排序") },
                                        onClick = {
                                            sortOrder = TimezoneSortOrder.OFFSET
                                            showSortMenu = false
                                        },
                                        leadingIcon = {
                                            if (sortOrder == TimezoneSortOrder.OFFSET) Icon(Icons.Default.Check, null)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("以时区名称排序") },
                                        onClick = {
                                            sortOrder = TimezoneSortOrder.NAME
                                            showSortMenu = false
                                        },
                                        leadingIcon = {
                                            if (sortOrder == TimezoneSortOrder.NAME) Icon(Icons.Default.Check, null)
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
                                }
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("搜索时区、城市或 UTC...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.medium
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(sortedAndFilteredTz) { tz ->
                    ListItem(
                        headlineContent = { Text(tz.displayName) },
                        supportingContent = { Text(tz.id) },
                        leadingContent = {
                            Text(
                                text = tz.offset,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        },
                        modifier = Modifier.clickable { 
                            scope.launch {
                                val result = ActionManager.setTimezone(tz.id)
                                snackbarHostState.showSnackbar("应用时区: ${tz.displayName} -> $result")
                            }
                        }
                    )
                }
            }
        }
    }
}
