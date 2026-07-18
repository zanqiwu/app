package com.example.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.TodoItem
import com.example.utils.DateUtils
import com.example.utils.PomodoroStore
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BarData(val label: String, val value: Int)
data class FocusBarData(val label: String, val seconds: Int)
private data class AnalysisSnapshot(
    val totalTasks: Int = 0,
    val completedCount: Int = 0,
    val last7DaysData: List<BarData> = emptyList(),
    val last6MonthsData: List<BarData> = emptyList(),
    val todayFocusSeconds: Int = 0,
    val last7DaysFocusData: List<FocusBarData> = emptyList(),
    val last6MonthsFocusData: List<FocusBarData> = emptyList(),
    val categoryCounts: Map<String, Int> = emptyMap()
)

@Composable
fun AnalysisScreenContent(
    viewModel: TodoViewModel,
    modifier: Modifier = Modifier
) {
    val todoItems by viewModel.allTodoItems.collectAsStateWithLifecycle()
    val currentDayKey by viewModel.currentDayKeyState.collectAsStateWithLifecycle()
    val pomodoroRunning by viewModel.pomodoroRunningState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var chartTab by remember { mutableStateOf(0) } // 0: Week, 1: Month
    var focusChartTab by remember { mutableStateOf(0) } // 0: Week, 1: Month

    val snapshot by produceState(
        initialValue = AnalysisSnapshot(),
        todoItems,
        currentDayKey,
        pomodoroRunning
    ) {
        value = withContext(Dispatchers.Default) {
            buildAnalysisSnapshot(context, todoItems, currentDayKey)
        }
    }
    val totalTasks = snapshot.totalTasks
    val completedCount = snapshot.completedCount
    val completionRate = if (totalTasks > 0) (completedCount * 100) / totalTasks else 0
    val last7DaysData = snapshot.last7DaysData
    val last6MonthsData = snapshot.last6MonthsData
    val weekBest = last7DaysData.maxByOrNull { it.value }
    val monthBest = last6MonthsData.maxByOrNull { it.value }
    val todayFocusSeconds = snapshot.todayFocusSeconds
    val last7DaysFocusData = snapshot.last7DaysFocusData
    val last6MonthsFocusData = snapshot.last6MonthsFocusData
    val selectedFocusData = if (focusChartTab == 0) last7DaysFocusData else last6MonthsFocusData
    val selectedFocusTotalSeconds = selectedFocusData.sumOf { it.seconds }
    val selectedFocusBest = selectedFocusData.maxByOrNull { it.seconds }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // Core Statistics Cards Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Total completed card
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "累计完成",
                    value = "$completedCount",
                    unit = "项",
                    icon = Icons.Default.TaskAlt,
                    color = MaterialTheme.colorScheme.primary
                )

                // Completion rate card
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "任务完成率",
                    value = "$completionRate",
                    unit = "%",
                    icon = Icons.Default.TrendingUp,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        // Custom Bar Chart Card Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header with title and tab controller
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "完成趋势分析",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (chartTab == 0) "最近 7 天每日已完成任务量" else "最近 6 个月每月已完成任务量",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }

                        // Tab toggles
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .padding(2.dp)
                        ) {
                            Button(
                                onClick = { chartTab = 0 },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (chartTab == 0) MaterialTheme.colorScheme.surface else Color.Transparent,
                                    contentColor = if (chartTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("周", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { chartTab = 1 },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (chartTab == 1) MaterialTheme.colorScheme.surface else Color.Transparent,
                                    contentColor = if (chartTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("月", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // The Custom Bar Chart Composable
                    CustomBarChart(
                        data = if (chartTab == 0) last7DaysData else last6MonthsData,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                }
            }
        }

        // Pomodoro focus statistics
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "番茄钟专注统计",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (focusChartTab == 0) "最近 7 天每日专注时长" else "最近 6 个月每月专注时长",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .padding(2.dp)
                        ) {
                            Button(
                                onClick = { focusChartTab = 0 },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (focusChartTab == 0) MaterialTheme.colorScheme.surface else Color.Transparent,
                                    contentColor = if (focusChartTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("周", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { focusChartTab = 1 },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (focusChartTab == 1) MaterialTheme.colorScheme.surface else Color.Transparent,
                                    contentColor = if (focusChartTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("月", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetricCard(
                            modifier = Modifier.weight(1f),
                            title = "今日专注",
                            value = formatFocusValue(todayFocusSeconds).first,
                            unit = formatFocusValue(todayFocusSeconds).second,
                            icon = Icons.Default.Timer,
                            color = MaterialTheme.colorScheme.primary
                        )
                        MetricCard(
                            modifier = Modifier.weight(1f),
                            title = if (focusChartTab == 0) "本周累计" else "近六月累计",
                            value = formatFocusValue(selectedFocusTotalSeconds).first,
                            unit = formatFocusValue(selectedFocusTotalSeconds).second,
                            icon = Icons.Default.HourglassTop,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    FocusBarChart(
                        data = selectedFocusData,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )

                    val bestLabel = selectedFocusBest?.takeIf { it.seconds > 0 }?.let {
                        "${it.label} (${formatFocusDuration(it.seconds)})"
                    } ?: "暂无数据"
                    Text(
                        text = "最高专注：$bestLabel",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Insights and Productivity Tips Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Insights,
                            tint = MaterialTheme.colorScheme.primary,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "效率分析报告",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

                    // Insight 1: Most productive period
                    val bestPeriodLabel = if (chartTab == 0) {
                        weekBest?.let { if (it.value > 0) "${it.label} (${it.value}项)" else "暂无数据" } ?: "暂无数据"
                    } else {
                        monthBest?.let { if (it.value > 0) "${it.label} (${it.value}项)" else "暂无数据" } ?: "暂无数据"
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            tint = Color(0xFFFFB74D),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "高效巅峰期：",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = bestPeriodLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Insight 2: Task categorization info
                    val totalWork = snapshot.categoryCounts["工作"] ?: 0
                    val totalStudy = snapshot.categoryCounts["学习"] ?: 0
                    val totalLife = snapshot.categoryCounts["生活"] ?: 0
                    val largestCategory = listOf("工作" to totalWork, "学习" to totalStudy, "生活" to totalLife)
                        .maxByOrNull { it.second }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PieChart,
                            tint = MaterialTheme.colorScheme.secondary,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "核心关注类别：",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (largestCategory != null && largestCategory.second > 0) {
                                "${largestCategory.first} (${largestCategory.second}项)"
                            } else {
                                "暂无核心类别"
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Insight 3: Dynamic productivity suggestion
                    val productivityTip = remember(completionRate) {
                        when {
                            completionRate >= 80 -> "非常棒！你的执行力极其强大，继续保持这一高效状态，注意合理安排休息！"
                            completionRate >= 50 -> "做的不错！你已经掌控了过半的任务，建议使用今日新增的「分段计划」功能，细化每日小目标以实现更高突破。"
                            else -> "没关系，万事开头难。建议开启「紧凑模式」获得高密度清单，并利用「每日计划」每天先解决一到两个最关键的问题。"
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                            .padding(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TipsAndUpdates,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                                contentDescription = null
                            )
                            Text(
                                text = productivityTip,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    unit: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        tint = color,
                        modifier = Modifier.size(14.dp),
                        contentDescription = null
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = value,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = unit,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }
}

@Composable
fun CustomBarChart(
    data: List<BarData>,
    modifier: Modifier = Modifier
) {
    val maxValue = remember(data) { data.map { it.value }.maxOrNull() ?: 1 }
    val displayMaxValue = if (maxValue == 0) 1 else maxValue

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        data.forEach { bar ->
            val animatedFraction by animateFloatAsState(
                targetValue = bar.value.toFloat() / displayMaxValue.toFloat(),
                animationSpec = tween(durationMillis = 800),
                label = "bar_height_${bar.label}"
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
            ) {
                // Value Label above the bar
                Text(
                    text = if (bar.value > 0) "${bar.value}" else "0",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (bar.value > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                // Bar Container Card (Full background track)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .width(16.dp)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    // Actual Animated Filled Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction = animatedFraction.coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // X-Axis Label
                Text(
                    text = bar.label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(36.dp)
                )
            }
        }
    }
}

@Composable
fun FocusBarChart(
    data: List<FocusBarData>,
    modifier: Modifier = Modifier
) {
    val maxSeconds = remember(data) { data.maxOfOrNull { it.seconds } ?: 0 }
    val displayMaxSeconds = if (maxSeconds == 0) 1 else maxSeconds
    val useHours = maxSeconds >= 60 * 60

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        data.forEach { bar ->
            val animatedFraction by animateFloatAsState(
                targetValue = bar.seconds.toFloat() / displayMaxSeconds.toFloat(),
                animationSpec = tween(durationMillis = 800),
                label = "focus_bar_height_${bar.label}"
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
            ) {
                Text(
                    text = formatFocusBarValue(bar.seconds, useHours),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (bar.seconds > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(bottom = 4.dp),
                    maxLines = 1
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .width(16.dp)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction = animatedFraction.coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                )
                            )
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = bar.label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(36.dp)
                )
            }
        }
    }
}

private fun formatFocusValue(seconds: Int): Pair<String, String> {
    if (seconds < 60 * 60) {
        return ((seconds + 59) / 60).toString() to "分钟"
    }
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (minutes == 0) {
        hours.toString() to "小时"
    } else {
        "$hours.${(minutes * 10) / 60}" to "小时"
    }
}

private fun formatFocusDuration(seconds: Int): String {
    if (seconds <= 0) return "0 分钟"
    if (seconds < 60 * 60) return "${(seconds + 59) / 60} 分钟"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (minutes == 0) "${hours} 小时" else "${hours} 小时 ${minutes} 分钟"
}

private fun formatFocusBarValue(seconds: Int, useHours: Boolean): String {
    if (seconds <= 0) return "0"
    return if (useHours) {
        val hours = seconds / 3600.0
        String.format(Locale.CHINA, "%.1fh", hours)
    } else {
        "${(seconds + 59) / 60}"
    }
}

private fun buildAnalysisSnapshot(
    context: android.content.Context,
    todoItems: List<TodoItem>,
    currentDayKey: String
): AnalysisSnapshot {
    val completedItems = todoItems.filter(TodoItem::isCompleted)
    val baseMillis = DateUtils.startOfDayMillis(currentDayKey)
    val baseCalendar = Calendar.getInstance().apply { timeInMillis = baseMillis }
    val dayLabel = SimpleDateFormat("E", Locale.CHINA)
    val monthLabel = SimpleDateFormat("M月", Locale.CHINA)

    val last7Days = (6 downTo 0).map { offset ->
        val day = (baseCalendar.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -offset) }
        val start = day.timeInMillis
        val end = (day.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
        BarData(
            label = dayLabel.format(day.time),
            value = completedItems.count { item ->
                val timestamp = item.completedAt ?: item.dueDate ?: item.createdAt
                timestamp in start until end
            }
        )
    }

    val last6Months = (5 downTo 0).map { offset ->
        val month = (baseCalendar.clone() as Calendar).apply {
            add(Calendar.MONTH, -offset)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = month.timeInMillis
        val end = (month.clone() as Calendar).apply { add(Calendar.MONTH, 1) }.timeInMillis
        BarData(
            label = monthLabel.format(month.time),
            value = completedItems.count { item ->
                val timestamp = item.completedAt ?: item.dueDate ?: item.createdAt
                timestamp in start until end
            }
        )
    }

    val last7DaysFocus = (6 downTo 0).map { offset ->
        val day = (baseCalendar.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -offset) }
        FocusBarData(
            label = dayLabel.format(day.time),
            seconds = PomodoroStore.focusSecondsForDay(context, DateUtils.dayKey(day.timeInMillis))
        )
    }

    val last6MonthsFocus = (5 downTo 0).map { offset ->
        val month = (baseCalendar.clone() as Calendar).apply {
            add(Calendar.MONTH, -offset)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val maxDay = month.getActualMaximum(Calendar.DAY_OF_MONTH)
        var seconds = 0
        repeat(maxDay) {
            seconds += PomodoroStore.focusSecondsForDay(context, DateUtils.dayKey(month.timeInMillis))
            month.add(Calendar.DAY_OF_MONTH, 1)
        }
        FocusBarData(monthLabel.format((baseCalendar.clone() as Calendar).apply {
            add(Calendar.MONTH, -offset)
        }.time), seconds)
    }

    return AnalysisSnapshot(
        totalTasks = todoItems.size,
        completedCount = completedItems.size,
        last7DaysData = last7Days,
        last6MonthsData = last6Months,
        todayFocusSeconds = PomodoroStore.focusSecondsForDay(context, currentDayKey),
        last7DaysFocusData = last7DaysFocus,
        last6MonthsFocusData = last6MonthsFocus,
        categoryCounts = todoItems.groupingBy(TodoItem::category).eachCount()
    )
}
