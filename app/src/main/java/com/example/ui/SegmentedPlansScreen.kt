package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.example.data.SegmentedPlan
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SegmentedPlansScreenContent(
    viewModel: TodoViewModel,
    modifier: Modifier = Modifier
) {
    val plans by viewModel.segmentedPlansState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(0) } // 0: Current Plans, 1: History Archive

    // Current Date strings
    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    val currentWeekStr = remember {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val week = cal.get(Calendar.WEEK_OF_YEAR)
        val formattedWeek = String.format("%02d", week)
        "$year-W$formattedWeek"
    }
    val currentMonthStr = remember { SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date()) }

    // Date displays (Friendly text)
    val todayFriendly = remember {
        val cal = Calendar.getInstance()
        val dayOfWeek = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> "周日"
            Calendar.MONDAY -> "周一"
            Calendar.TUESDAY -> "周二"
            Calendar.WEDNESDAY -> "周三"
            Calendar.THURSDAY -> "周四"
            Calendar.FRIDAY -> "周五"
            Calendar.SATURDAY -> "周六"
            else -> ""
        }
        val sdf = SimpleDateFormat("M月d日", Locale.getDefault())
        "${sdf.format(cal.time)} · $dayOfWeek"
    }
    val currentWeekFriendly = remember {
        val cal = Calendar.getInstance()
        val weekNum = cal.get(Calendar.WEEK_OF_YEAR)
        "第 ${weekNum} 周计划"
    }
    val currentMonthFriendly = remember {
        SimpleDateFormat("yyyy年 M月计划", Locale.getDefault()).format(Date())
    }

    // Filter plans
    val todayPlans = remember(plans, todayStr) {
        plans.filter { it.planType == "DAILY" && it.targetDate == todayStr }
    }
    val weeklyPlans = remember(plans, currentWeekStr) {
        plans.filter { it.planType == "WEEKLY" && it.targetDate == currentWeekStr }
    }
    val monthlyPlans = remember(plans, currentMonthStr) {
        plans.filter { it.planType == "MONTHLY" && it.targetDate == currentMonthStr }
    }
    val historyPlansGrouped = remember(plans, todayStr) {
        plans.filter { it.planType == "DAILY" && it.targetDate != todayStr }
            .groupBy { it.targetDate }
            .toSortedMap(compareByDescending { it })
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Tab Row
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("当前计划", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                icon = { Icon(Icons.Default.Today, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("历史归档", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                icon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        if (selectedTab == 0) {
            // Current Plans View
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // DAILY PLANS SECTION
                item {
                    PlanSectionCard(
                        title = "今日计划",
                        subtitle = todayFriendly,
                        icon = Icons.Default.Event,
                        plans = todayPlans,
                        planType = "DAILY",
                        targetDate = todayStr,
                        onAddPlan = { viewModel.insertSegmentedPlan(it, "DAILY", todayStr) },
                        onTogglePlan = { viewModel.toggleSegmentedPlan(it) },
                        onDeletePlan = { viewModel.deleteSegmentedPlan(it) }
                    )
                }

                // WEEKLY PLANS SECTION
                item {
                    PlanSectionCard(
                        title = "本周计划",
                        subtitle = currentWeekFriendly,
                        icon = Icons.Default.DateRange,
                        plans = weeklyPlans,
                        planType = "WEEKLY",
                        targetDate = currentWeekStr,
                        onAddPlan = { viewModel.insertSegmentedPlan(it, "WEEKLY", currentWeekStr) },
                        onTogglePlan = { viewModel.toggleSegmentedPlan(it) },
                        onDeletePlan = { viewModel.deleteSegmentedPlan(it) }
                    )
                }

                // MONTHLY PLANS SECTION
                item {
                    PlanSectionCard(
                        title = "本月计划",
                        subtitle = currentMonthFriendly,
                        icon = Icons.Default.CalendarMonth,
                        plans = monthlyPlans,
                        planType = "MONTHLY",
                        targetDate = currentMonthStr,
                        onAddPlan = { viewModel.insertSegmentedPlan(it, "MONTHLY", currentMonthStr) },
                        onTogglePlan = { viewModel.toggleSegmentedPlan(it) },
                        onDeletePlan = { viewModel.deleteSegmentedPlan(it) }
                    )
                }
            }
        } else {
            // History Archive View
            if (historyPlansGrouped.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.HistoryToggleOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "暂无历史计划",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "每日过去的计划会自动存档到这里哦",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    historyPlansGrouped.forEach { (date, plansList) ->
                        item(key = date) {
                            var isExpanded by remember { mutableStateOf(false) }
                            val completedCount = plansList.count { it.isCompleted }
                            val totalCount = plansList.size
                            val completionRate = if (totalCount > 0) (completedCount * 100) / totalCount else 0

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItemPlacement(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { isExpanded = !isExpanded }
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (completionRate == 100) Icons.Default.CheckCircle else Icons.Default.CalendarToday,
                                                tint = if (completionRate == 100) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp),
                                                contentDescription = null
                                            )
                                            Column {
                                                Text(
                                                    text = formatDateFriendly(date),
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 15.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "完成 $completedCount / $totalCount  (${completionRate}%)",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                )
                                            }
                                        }

                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = if (isExpanded) "折叠" else "展开",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    AnimatedVisibility(visible = isExpanded) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                                            plansList.forEach { plan ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (plan.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                            tint = if (plan.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                            modifier = Modifier.size(16.dp),
                                                            contentDescription = null
                                                        )
                                                        Text(
                                                            text = plan.title,
                                                            fontSize = 13.sp,
                                                            textDecoration = if (plan.isCompleted) TextDecoration.LineThrough else null,
                                                            color = if (plan.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }

                                                    // One-click restore to today button
                                                    IconButton(
                                                        onClick = {
                                                            viewModel.insertSegmentedPlan(plan.title, "DAILY", todayStr)
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.ContentCopy,
                                                            contentDescription = "复制到今日",
                                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlanSectionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    plans: List<SegmentedPlan>,
    planType: String,
    targetDate: String,
    onAddPlan: (String) -> Unit,
    onTogglePlan: (SegmentedPlan) -> Unit,
    onDeletePlan: (SegmentedPlan) -> Unit
) {
    var newPlanText by remember { mutableStateOf("") }
    val completedCount = plans.count { it.isCompleted }
    val totalCount = plans.size
    val progress = if (totalCount > 0) completedCount.toFloat() / totalCount.toFloat() else 0f
    val progressAnimated by animateFloatAsState(targetValue = progress, label = "plan_progress")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                            contentDescription = null
                        )
                    }
                    Column {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = subtitle,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                // Progress Indicator
                if (totalCount > 0) {
                    Text(
                        text = "$completedCount/$totalCount",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress Bar
            if (totalCount > 0) {
                LinearProgressIndicator(
                    progress = progressAnimated,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Checklist of plans
            if (plans.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无计划，在下方输入添加吧！",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    plans.forEach { plan ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(
                                    checked = plan.isCompleted,
                                    onCheckedChange = { onTogglePlan(plan) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary,
                                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = plan.title,
                                    fontSize = 14.sp,
                                    textDecoration = if (plan.isCompleted) TextDecoration.LineThrough else null,
                                    color = if (plan.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(
                                onClick = { onDeletePlan(plan) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "删除计划",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newPlanText,
                    onValueChange = { newPlanText = it },
                    placeholder = { Text("添加新计划...", fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (newPlanText.isNotBlank()) {
                            onAddPlan(newPlanText)
                            newPlanText = ""
                        }
                    }),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                )

                IconButton(
                    onClick = {
                        if (newPlanText.isNotBlank()) {
                            onAddPlan(newPlanText)
                            newPlanText = ""
                        }
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "添加",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// Helper to format date cleanly
private fun formatDateFriendly(dateStr: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = parser.parse(dateStr) ?: return dateStr
        val cal = Calendar.getInstance()
        cal.time = date
        
        val todayCal = Calendar.getInstance()
        val yesterdayCal = Calendar.getInstance()
        yesterdayCal.add(Calendar.DAY_OF_YEAR, -1)
        
        if (cal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)) {
            "今天"
        } else if (cal.get(Calendar.YEAR) == yesterdayCal.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == yesterdayCal.get(Calendar.DAY_OF_YEAR)) {
            "昨天"
        } else {
            val dayOfWeek = when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.SUNDAY -> "周日"
                Calendar.MONDAY -> "周一"
                Calendar.TUESDAY -> "周二"
                Calendar.WEDNESDAY -> "周三"
                Calendar.THURSDAY -> "周四"
                Calendar.FRIDAY -> "周五"
                Calendar.SATURDAY -> "周六"
                else -> ""
            }
            val formatter = SimpleDateFormat("M月d日", Locale.getDefault())
            "${formatter.format(date)} ($dayOfWeek)"
        }
    } catch (e: Exception) {
        dateStr
    }
}
