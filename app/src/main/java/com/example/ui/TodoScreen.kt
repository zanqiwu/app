package com.example.ui

import android.app.DatePickerDialog
import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.TodoItem
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(
    viewModel: TodoViewModel,
    modifier: Modifier = Modifier
) {
    val items by viewModel.todoItemsState.collectAsStateWithLifecycle()
    val stats by viewModel.taskStatsState.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    var showAddSheet by remember { mutableStateOf(false) }
    var showAiAssistant by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<TodoItem?>(null) }

    val context = LocalContext.current
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    // Dialog & sheet state
    var showDeleteConfirmDialog by remember { mutableStateOf<TodoItem?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                // Title and clear buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "待办清单",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.tertiary
                                            )
                                        )
                                    )
                                    .clickable { showAiAssistant = true }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI 助理",
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "AI 规划",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = remember {
                                val cal = Calendar.getInstance()
                                val dayOfWeek = when (cal.get(Calendar.DAY_OF_WEEK)) {
                                    Calendar.SUNDAY -> "星期日"
                                    Calendar.MONDAY -> "星期一"
                                    Calendar.TUESDAY -> "星期二"
                                    Calendar.WEDNESDAY -> "星期三"
                                    Calendar.THURSDAY -> "星期四"
                                    Calendar.FRIDAY -> "星期五"
                                    Calendar.SATURDAY -> "星期六"
                                    else -> ""
                                }
                                val sdf = SimpleDateFormat("M月d日", Locale.getDefault())
                                "${sdf.format(cal.time)} · $dayOfWeek"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Clear completed button
                    if (stats.completed > 0) {
                        TextButton(
                            onClick = { viewModel.deleteCompleted() },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.testTag("clear_completed_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "清除已完成",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("清除已完成", fontSize = 13.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("搜索待办事项...", fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "搜索",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "清除搜索"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { /* Collapse keyboard */ }),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("search_bar")
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingItem = null
                    showAddSheet = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp)
                    .testTag("add_todo_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "添加任务",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Stats Panel
            StatsCard(stats = stats)

            // Horizontal Categories Selector
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(CategoryConfig.categories) { category ->
                    val isSelected = selectedFilter == category.name
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setCategoryFilter(category.name) },
                        label = {
                            Text(
                                text = category.name,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = category.icon,
                                contentDescription = category.name,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = category.color.copy(alpha = 0.15f),
                            selectedLabelColor = category.color,
                            selectedLeadingIconColor = category.color,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            iconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            selectedBorderColor = category.color,
                            borderColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("category_chip_${category.name}")
                    )
                }
            }

            // Todo List Core
            if (items.isEmpty()) {
                EmptyStateView(
                    isFiltering = selectedFilter != "全部" || searchQuery.isNotEmpty(),
                    onClearFilters = {
                        viewModel.setCategoryFilter("全部")
                        viewModel.setSearchQuery("")
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .testTag("todo_list"),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        TodoItemRow(
                            item = item,
                            onToggleComplete = { viewModel.toggleComplete(item) },
                            onToggleImportant = { viewModel.toggleImportant(item) },
                            onEdit = {
                                editingItem = item
                                showAddSheet = true
                            },
                            onDelete = { showDeleteConfirmDialog = item }
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation Dialog
    showDeleteConfirmDialog?.let { item ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("确认删除") },
            text = { Text("您确定要删除待办事项“${item.title}”吗？此操作不可撤销。") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTodo(item)
                        showDeleteConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmDialog = null },
                    modifier = Modifier.testTag("cancel_delete_button")
                ) {
                    Text("取消")
                }
            }
        )
    }

    // Add or Edit Bottom Sheet
    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showAddSheet = false
                editingItem = null
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.testTag("add_edit_bottom_sheet")
        ) {
            AddEditTodoContent(
                editingItem = editingItem,
                onSave = { title, description, category, isImportant, dueDate, syncToCalendar ->
                    if (editingItem != null) {
                        viewModel.updateTodo(
                            editingItem!!.copy(
                                title = title,
                                description = description,
                                category = category,
                                isImportant = isImportant,
                                dueDate = dueDate
                            ),
                            syncToCalendar = syncToCalendar
                        )
                    } else {
                        viewModel.insertTodo(title, description, category, isImportant, dueDate, syncToCalendar = syncToCalendar)
                    }
                    showAddSheet = false
                    editingItem = null
                },
                onCancel = {
                    showAddSheet = false
                    editingItem = null
                }
            )
        }
    }

    // AI Planner Assistant Bottom Sheet
    if (showAiAssistant) {
        ModalBottomSheet(
            onDismissRequest = {
                showAiAssistant = false
                viewModel.clearAiGeneratedItems()
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.testTag("ai_assistant_bottom_sheet")
        ) {
            AiAssistantSheetContent(
                viewModel = viewModel,
                onDismiss = {
                    showAiAssistant = false
                    viewModel.clearAiGeneratedItems()
                }
            )
        }
    }
}

@Composable
fun StatsCard(stats: TaskStats) {
    val completionRatio = if (stats.filteredTotal > 0) {
        stats.filteredCompleted.toFloat() / stats.filteredTotal.toFloat()
    } else {
        0f
    }

    val animatedProgress by animateFloatAsState(
        targetValue = completionRatio,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "progress"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "今日完成进度",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when {
                        stats.filteredTotal == 0 -> "没有找到待办事项，开始新的一天吧！"
                        completionRatio >= 1.0f -> "做得太棒了！所有任务均已完成！🎉"
                        completionRatio >= 0.7f -> "太给力了，大半的任务都完成了！✨"
                        completionRatio >= 0.4f -> "正在稳步前进，继续加油！💪"
                        else -> "今日事今日毕，开始行动吧！🚀"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${stats.filteredCompleted}/${stats.filteredTotal}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        text = "已完成",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
fun TodoItemRow(
    item: TodoItem,
    onToggleComplete: () -> Unit,
    onToggleImportant: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val categoryConfig = remember(item.category) { CategoryConfig.getByName(item.category) }
    val dateFormatter = remember { SimpleDateFormat("MM-dd", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("todo_item_${item.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isCompleted) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox Container
            IconButton(
                onClick = onToggleComplete,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("todo_item_checkbox_${item.id}")
            ) {
                Icon(
                    imageVector = if (item.isCompleted) {
                        Icons.Default.CheckCircle
                    } else {
                        Icons.Default.RadioButtonUnchecked
                    },
                    contentDescription = if (item.isCompleted) "取消完成" else "标记完成",
                    tint = if (item.isCompleted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    },
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Text Content (Title, Description, Tags)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onEdit() }
            ) {
                Text(
                    text = item.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (item.isCompleted) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (item.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.description,
                        fontSize = 13.sp,
                        color = if (item.isCompleted) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Bottom Metadata tags row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Category Tag
                    Row(
                        modifier = Modifier
                            .background(
                                color = categoryConfig.color.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = categoryConfig.icon,
                            contentDescription = null,
                            tint = categoryConfig.color,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = categoryConfig.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = categoryConfig.color
                        )
                    }

                    // Due Date Tag
                    item.dueDate?.let { dueDateMillis ->
                        val isOverdue = dueDateMillis < System.currentTimeMillis() && !item.isCompleted
                        val dueDateStr = dateFormatter.format(Date(dueDateMillis))
                        Row(
                            modifier = Modifier
                                .background(
                                    color = if (isOverdue) {
                                        MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = if (isOverdue) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = dueDateStr,
                                fontSize = 11.sp,
                                color = if (isOverdue) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }

                    // Calendar Synced Tag
                    if (item.calendarEventId != null) {
                        Row(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Event,
                                contentDescription = "已同步到日历",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "已同步",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Star Toggle
                IconButton(
                    onClick = onToggleImportant,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("todo_item_important_button_${item.id}")
                ) {
                    Icon(
                        imageVector = if (item.isImportant) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = if (item.isImportant) "取消重要" else "标记重要",
                        tint = if (item.isImportant) {
                            Color(0xFFFFB74D)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Delete Button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("todo_item_delete_button_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除待办",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(
    isFiltering: Boolean,
    onClearFilters: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isFiltering) Icons.Default.SearchOff else Icons.Default.TaskAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (isFiltering) "未找到符合条件的事项" else "今天没有任何待办事项哦",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isFiltering) {
                    "请尝试更换分类标签或清除搜索词"
                } else {
                    "一身轻松！点击右下角“+”按钮，开始规划您的日程吧！"
                },
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            if (isFiltering) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onClearFilters,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("clear_filters_button")
                ) {
                    Text("清除所有筛选")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTodoContent(
    editingItem: TodoItem?,
    onSave: (title: String, description: String, category: String, isImportant: Boolean, dueDate: Long?, syncToCalendar: Boolean) -> Unit,
    onCancel: () -> Unit
) {
    var title by remember { mutableStateOf(editingItem?.title ?: "") }
    var description by remember { mutableStateOf(editingItem?.description ?: "") }
    var selectedCategory by remember { mutableStateOf(editingItem?.category ?: "工作") }
    var isImportant by remember { mutableStateOf(editingItem?.isImportant ?: false) }
    var dueDate by remember { mutableStateOf(editingItem?.dueDate) }

    val context = LocalContext.current
    var syncToCalendar by remember { mutableStateOf(editingItem?.calendarEventId != null) }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val writeGranted = permissions[Manifest.permission.WRITE_CALENDAR] ?: false
        val readGranted = permissions[Manifest.permission.READ_CALENDAR] ?: false
        if (writeGranted && readGranted) {
            syncToCalendar = true
            Toast.makeText(context, "日历权限已授予，已开启同步", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "需要日历权限才能使用同步功能", Toast.LENGTH_SHORT).show()
        }
    }

    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Text(
            text = if (editingItem != null) "编辑待办事项" else "新建待办事项",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Title text field
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("待办名称 (必填)") },
            placeholder = { Text("例如：买牛奶、写周报...") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_todo_title"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Description text field
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("备注信息") },
            placeholder = { Text("添加相关的描述、地址或备忘事项") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_todo_description"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Category selection Label
        Text(
            text = "分类标签",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        // Categories selector row (excluding '全部' and '重要')
        val creatorCategories = remember { 
            CategoryConfig.categories.filter { it.name != "全部" && it.name != "重要" } 
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(creatorCategories) { category ->
                val isSelected = selectedCategory == category.name
                val colorAlpha = if (isSelected) 0.2f else 0.05f
                Row(
                    modifier = Modifier
                        .background(
                            color = category.color.copy(alpha = colorAlpha),
                            shape = RoundedCornerShape(18.dp)
                        )
                        .border(
                            width = 1.5.dp,
                            color = if (isSelected) category.color else Color.Transparent,
                            shape = RoundedCornerShape(18.dp)
                        )
                        .clickable { selectedCategory = category.name }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        tint = category.color,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = category.name,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) category.color else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Optional parameters: Important switch & Date selector row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Important switch
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { isImportant = !isImportant }
                    .padding(vertical = 4.dp)
            ) {
                Icon(
                    imageVector = if (isImportant) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = null,
                    tint = if (isImportant) Color(0xFFFFB74D) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "标为重要",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "将在列表中优先显示",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Date selector
            Row(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable {
                        val calendar = Calendar.getInstance()
                        dueDate?.let { calendar.timeInMillis = it }
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val selectedCal = Calendar.getInstance()
                                selectedCal.set(year, month, dayOfMonth, 23, 59, 59)
                                dueDate = selectedCal.timeInMillis
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = "选择日期",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = dueDate?.let { dateFormatter.format(Date(it)) } ?: "选择截止日期",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (dueDate != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "清除日期",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(14.dp)
                            .clickable { dueDate = null }
                    )
                }
            }
        }

        // Calendar sync option, visible only when a due date is specified
        if (dueDate != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable {
                        if (com.example.utils.CalendarSyncHelper.hasCalendarPermission(context)) {
                            syncToCalendar = !syncToCalendar
                        } else {
                            calendarPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_CALENDAR,
                                    Manifest.permission.WRITE_CALENDAR
                                )
                            )
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "同步到手机日历",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "到期时间将自动添加至系统日程",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = syncToCalendar,
                    onCheckedChange = { checked ->
                        if (checked) {
                            if (com.example.utils.CalendarSyncHelper.hasCalendarPermission(context)) {
                                syncToCalendar = true
                            } else {
                                calendarPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_CALENDAR,
                                        Manifest.permission.WRITE_CALENDAR
                                    )
                                )
                            }
                        } else {
                            syncToCalendar = false
                        }
                    },
                    modifier = Modifier.testTag("calendar_sync_switch")
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Confirm / cancel action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("cancel_save_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("取消")
            }

            Button(
                onClick = { onSave(title, description, selectedCategory, isImportant, dueDate, syncToCalendar) },
                enabled = title.isNotBlank(),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("confirm_save_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("保存")
            }
        }
    }
}
