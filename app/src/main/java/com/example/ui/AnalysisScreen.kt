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
import java.text.SimpleDateFormat
import java.util.*

data class BarData(val label: String, val value: Int)

@Composable
fun AnalysisScreenContent(
    viewModel: TodoViewModel,
    modifier: Modifier = Modifier
) {
    val todoItems by viewModel.todoItemsState.collectAsStateWithLifecycle()
    var chartTab by remember { mutableStateOf(0) } // 0: Week, 1: Month

    val totalTasks = todoItems.size
    val completedItems = remember(todoItems) { todoItems.filter { it.isCompleted } }
    val completedCount = completedItems.size
    val completionRate = if (totalTasks > 0) (completedCount * 100) / totalTasks else 0

    // 1. Weekly completion stats (last 7 days ending today)
    val last7DaysData = remember(completedItems) {
        val result = mutableListOf<BarData>()
        val sdfLabel = SimpleDateFormat("E", Locale.CHINA) // e.g. "周一", "周二"
        
        // Generate last 7 days ending today in chronological order
        val dates = (0..6).map { offset ->
            val tempCal = Calendar.getInstance()
            tempCal.add(Calendar.DAY_OF_YEAR, -offset)
            tempCal
        }.reversed()
        
        dates.forEach { dateCal ->
            val label = sdfLabel.format(dateCal.time)
            
            val startOfDay = Calendar.getInstance().apply {
                time = dateCal.time
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            
            val endOfDay = startOfDay + 24 * 60 * 60 * 1000 - 1
            
            val count = completedItems.count { item ->
                val itemTime = item.dueDate ?: item.createdAt
                itemTime in startOfDay..endOfDay
            }
            result.add(BarData(label, count))
        }
        result
    }

    // 2. Monthly completion stats (last 6 months ending current month)
    val last6MonthsData = remember(completedItems) {
        val result = mutableListOf<BarData>()
        val sdfLabel = SimpleDateFormat("M月", Locale.CHINA) // e.g. "7月", "6月"
        
        val months = (0..5).map { offset ->
            val tempCal = Calendar.getInstance()
            tempCal.add(Calendar.MONTH, -offset)
            tempCal
        }.reversed()
        
        months.forEach { monthCal ->
            val label = sdfLabel.format(monthCal.time)
            val year = monthCal.get(Calendar.YEAR)
            val month = monthCal.get(Calendar.MONTH)
            
            val startOfMonth = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            
            val endOfMonth = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, monthCal.getActualMaximum(Calendar.DAY_OF_MONTH))
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis
            
            val count = completedItems.count { item ->
                val itemTime = item.dueDate ?: item.createdAt
                itemTime in startOfMonth..endOfMonth
            }
            result.add(BarData(label, count))
        }
        result
    }

    // Identify most productive day/month
    val weekBest = last7DaysData.maxByOrNull { it.value }
    val monthBest = last6MonthsData.maxByOrNull { it.value }

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
                    val totalWork = todoItems.count { it.category == "工作" }
                    val totalStudy = todoItems.count { it.category == "学习" }
                    val totalLife = todoItems.count { it.category == "生活" }
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
