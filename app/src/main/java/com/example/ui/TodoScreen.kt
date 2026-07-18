package com.example.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.TodoItem
import com.example.utils.PomodoroNotifier
import com.example.utils.XiaomiSuperIsland
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlin.math.abs

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
    val rolloverCandidates by viewModel.pendingRolloverState.collectAsStateWithLifecycle()

    var showAddSheet by remember { mutableStateOf(false) }
    var showAiAssistant by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<TodoItem?>(null) }
    var isMapMode by remember { mutableStateOf(false) }
    var mapSelectedItemId by remember { mutableStateOf<Int?>(null) }
    var mapCenterLat by remember { mutableStateOf(30.25) }
    var mapCenterLng by remember { mutableStateOf(120.15) }

    val context = LocalContext.current
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    // Dialog & sheet state
    var showDeleteConfirmDialog by remember { mutableStateOf<TodoItem?>(null) }
    var showMapSelectorForTodo by remember { mutableStateOf<TodoItem?>(null) }

    // Navigation and Background variables
    var currentScreen by remember { mutableStateOf("todos") } // "todos", "settings", "extensions"
    val backgroundStyle by viewModel.backgroundStyle.collectAsStateWithLifecycle()
    val isCompactMode by viewModel.isCompactMode.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val defaultSyncPrefs = remember { context.getSharedPreferences("todo_settings", android.content.Context.MODE_PRIVATE) }
    var defaultSyncToCalendar by remember { mutableStateOf(defaultSyncPrefs.getBoolean("default_sync_to_calendar", false)) }

    val backgroundBrush = remember(backgroundStyle) {
        when (backgroundStyle) {
            "cosmic" -> Brush.verticalGradient(
                colors = listOf(Color(0xFF0F0C20), Color(0xFF16102D), Color(0xFF08060D))
            )
            "forest" -> Brush.verticalGradient(
                colors = listOf(Color(0xFF091F15), Color(0xFF113222), Color(0xFF050E0A))
            )
            "sakura" -> Brush.verticalGradient(
                colors = listOf(Color(0xFF2E151B), Color(0xFF3F1D25), Color(0xFF1B090E))
            )
            "aurora" -> Brush.verticalGradient(
                colors = listOf(Color(0xFF051D21), Color(0xFF0A3138), Color(0xFF020F12))
            )
            "sunset" -> Brush.verticalGradient(
                colors = listOf(Color(0xFF270E17), Color(0xFF3A1320), Color(0xFF14030A))
            )
            else -> null
        }
    }

    val rootModifier = if (backgroundBrush != null) {
        modifier.fillMaxSize().background(backgroundBrush)
    } else {
        modifier.fillMaxSize()
    }

    Box(modifier = rootModifier) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = currentScreen != "todos" || !isMapMode,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = if (backgroundBrush != null) Color(0xFF110F20).copy(alpha = 0.95f) else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.width(300.dp)
                ) {
                    DrawerContent(
                        currentScreen = currentScreen,
                        stats = stats,
                        onScreenSelected = { screen ->
                            currentScreen = screen
                            scope.launch { drawerState.close() }
                        }
                    )
                }
            }
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = if (backgroundBrush != null) Color.Transparent else MaterialTheme.colorScheme.background,
                topBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (backgroundBrush != null) Color.Transparent else MaterialTheme.colorScheme.surface)
                            .statusBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        if (currentScreen == "todos") {
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
                                        IconButton(
                                            onClick = { scope.launch { drawerState.open() } },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Menu,
                                                contentDescription = "菜单",
                                                tint = if (backgroundBrush != null) Color.White else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Text(
                                            text = "待办清单",
                                            fontSize = 28.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (backgroundBrush != null) Color.White else MaterialTheme.colorScheme.onSurface
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

                                        Spacer(modifier = Modifier.width(4.dp))

                                        // 地图模式 Toggle Button
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(
                                                    if (isMapMode) MaterialTheme.colorScheme.primaryContainer
                                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                )
                                                .clickable { isMapMode = !isMapMode }
                                                .padding(horizontal = 10.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isMapMode) Icons.Default.List else Icons.Default.Map,
                                                contentDescription = "地图模式",
                                                tint = if (isMapMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = if (isMapMode) "列表" else "地图",
                                                color = if (isMapMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(4.dp))

                                        // 紧凑模式 Toggle Button
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(
                                                    if (isCompactMode) MaterialTheme.colorScheme.primaryContainer
                                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                )
                                                .clickable { viewModel.setCompactMode(!isCompactMode) }
                                                .padding(horizontal = 10.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isCompactMode) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                                                contentDescription = "紧凑模式",
                                                tint = if (isCompactMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = if (isCompactMode) "紧凑" else "标准",
                                                color = if (isCompactMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
                                        color = if (backgroundBrush != null) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 38.dp)
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

                            // Search Bar with local state & debounce to prevent heavy recompositions while typing
                            var localSearchQuery by remember { mutableStateOf(searchQuery) }
                            LaunchedEffect(searchQuery) {
                                if (searchQuery != localSearchQuery) {
                                    localSearchQuery = searchQuery
                                }
                            }
                            LaunchedEffect(localSearchQuery) {
                                if (localSearchQuery != searchQuery) {
                                    kotlinx.coroutines.delay(300)
                                    viewModel.setSearchQuery(localSearchQuery)
                                }
                            }

                            OutlinedTextField(
                                value = localSearchQuery,
                                onValueChange = { localSearchQuery = it },
                                placeholder = { Text("搜索待办事项...", fontSize = 14.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "搜索",
                                        tint = if (backgroundBrush != null) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingIcon = {
                                    if (localSearchQuery.isNotEmpty()) {
                                        IconButton(onClick = { 
                                            localSearchQuery = ""
                                            viewModel.setSearchQuery("") 
                                        }) {
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
                                    unfocusedBorderColor = if (backgroundBrush != null) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                    focusedContainerColor = if (backgroundBrush != null) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    unfocusedContainerColor = if (backgroundBrush != null) Color.White.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    focusedTextColor = if (backgroundBrush != null) Color.White else MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = if (backgroundBrush != null) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurface,
                                    focusedPlaceholderColor = if (backgroundBrush != null) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    unfocusedPlaceholderColor = if (backgroundBrush != null) Color.White.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("search_bar")
                            )
                        } else {
                            // Settings or Extensions TopBar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    IconButton(
                                        onClick = { scope.launch { drawerState.open() } },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Menu,
                                            contentDescription = "菜单",
                                            tint = if (backgroundBrush != null) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        text = when (currentScreen) {
                                            "settings" -> "设置中心"
                                            "extensions" -> "效率拓展"
                                            "plans" -> "分段计划"
                                            "analysis" -> "数据分析"
                                            "openclaw" -> "龙虾"
                                            else -> "待办清单"
                                        },
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (backgroundBrush != null) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                },
        floatingActionButton = {
            if (currentScreen == "todos") {
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
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                "settings" -> {
                    SettingsScreenContent(
                        currentStyle = backgroundStyle,
                        onStyleSelected = { viewModel.setBackgroundStyle(it) },
                        syncToCalendar = defaultSyncToCalendar,
                        onSyncToCalendarChanged = {
                            defaultSyncToCalendar = it
                            defaultSyncPrefs.edit().putBoolean("default_sync_to_calendar", it).apply()
                        },
                        context = context
                    )
                }
                "extensions" -> {
                    ExtensionsScreenContent(
                        viewModel = viewModel,
                        stats = stats,
                        context = context
                    )
                }
                "plans" -> {
                    SegmentedPlansScreenContent(viewModel = viewModel)
                }
                "weekly_report" -> {
                    WeeklyReportScreenContent(viewModel = viewModel)
                }
                "analysis" -> {
                    AnalysisScreenContent(viewModel = viewModel)
                }
                "openclaw" -> {
                    OpenDroidLauncherScreen()
                }
                else -> {
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

            // Todo List Core or Map View
            if (isMapMode) {
                val itemsWithLocation = remember(items) { items.filter { it.latitude != null && it.longitude != null } }
                if (itemsWithLocation.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "暂无位置标记的待办",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "在地图模式下，所有带有定位的任务将显示在地图上。编辑任务添加一个物理地址，或者直接在 AI 规划中让 AI 助理自动生成带有物理坐标的位置吧！",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        editingItem = null
                                        showAddSheet = true
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("添加带有位置的任务", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                } else {
                    val mapCarouselState = rememberLazyListState()
                    val mapCarouselFlingBehavior = rememberSnapFlingBehavior(mapCarouselState)
                    var selectionFromMap by remember { mutableStateOf(false) }

                    LaunchedEffect(itemsWithLocation) {
                        val selectedStillExists = itemsWithLocation.any { it.id == mapSelectedItemId }
                        if (!selectedStillExists) {
                            itemsWithLocation.firstOrNull()?.let { first ->
                                mapSelectedItemId = first.id
                                mapCenterLat = first.latitude!!
                                mapCenterLng = first.longitude!!
                            }
                        }
                    }

                    // Split Screen: Map on top, Task Carousel at the bottom
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    ) {
                        // Interactive Map Section
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1.3f)
                        ) {
                            BaiduTodoMapView(
                                todoItems = itemsWithLocation,
                                selectedItemId = mapSelectedItemId,
                                modifier = Modifier.fillMaxSize(),
                                onMarkerClicked = { id ->
                                    selectionFromMap = true
                                    mapSelectedItemId = id
                                    val match = itemsWithLocation.find { it.id == id }
                                    if (match != null) {
                                        mapCenterLat = match.latitude!!
                                        mapCenterLng = match.longitude!!
                                    }
                                }
                            )
                        }

                        // Task Horizontal Carousel
                        // A marker click moves the carousel to the matching task.
                        LaunchedEffect(mapSelectedItemId, selectionFromMap) {
                            if (selectionFromMap) {
                                mapSelectedItemId?.let { selectedId ->
                                val index = itemsWithLocation.indexOfFirst { it.id == selectedId }
                                if (index != -1) {
                                        mapCarouselState.animateScrollToItem(index)
                                    }
                                }
                                selectionFromMap = false
                            }
                        }

                        // A horizontal swipe updates the map only after scrolling
                        // settles, preventing camera jitter during a fling.
                        LaunchedEffect(mapCarouselState, itemsWithLocation) {
                            snapshotFlow {
                                if (mapCarouselState.isScrollInProgress) {
                                    null
                                } else {
                                    val layoutInfo = mapCarouselState.layoutInfo
                                    val viewportCenter =
                                        (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                                    layoutInfo.visibleItemsInfo
                                        .minByOrNull { info ->
                                            abs((info.offset + info.size / 2) - viewportCenter)
                                        }
                                        ?.key as? Int
                                }
                            }
                                .filterNotNull()
                                .distinctUntilChanged()
                                .collect { selectedId ->
                                    val selected = itemsWithLocation.firstOrNull { it.id == selectedId }
                                        ?: return@collect
                                    mapSelectedItemId = selected.id
                                    mapCenterLat = selected.latitude!!
                                    mapCenterLng = selected.longitude!!
                                }
                        }

                        androidx.compose.foundation.lazy.LazyRow(
                            state = mapCarouselState,
                            flingBehavior = mapCarouselFlingBehavior,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = itemsWithLocation,
                                key = { it.id },
                                contentType = { "map_todo" }
                            ) { item ->
                                val isSelected = mapSelectedItemId == item.id
                                val categoryConfig = remember(item.category) { CategoryConfig.getByName(item.category) }
                                
                                Card(
                                    modifier = Modifier
                                        .width(280.dp)
                                        .height(110.dp)
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            selectionFromMap = false
                                            mapSelectedItemId = item.id
                                            mapCenterLat = item.latitude!!
                                            mapCenterLng = item.longitude!!
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                                        else MaterialTheme.colorScheme.surface
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Complete Checkbox
                                        IconButton(
                                            onClick = { viewModel.toggleComplete(item) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (item.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                contentDescription = null,
                                                tint = if (item.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.title,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
                                                color = if (item.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                                            )
                                            
                                            Spacer(modifier = Modifier.height(2.dp))
                                            
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.LocationOn,
                                                    contentDescription = null,
                                                    tint = categoryConfig.color,
                                                    modifier = Modifier.size(10.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = item.locationName ?: "未知位置",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = item.category,
                                                    fontSize = 9.sp,
                                                    color = categoryConfig.color,
                                                    modifier = Modifier
                                                        .background(categoryConfig.color.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                                
                                                if (item.isImportant) {
                                                    Icon(
                                                        imageVector = Icons.Default.Star,
                                                        contentDescription = null,
                                                        tint = Color(0xFFFFB74D),
                                                        modifier = Modifier.size(10.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // Navigate Button
                                        IconButton(
                                            onClick = { showMapSelectorForTodo = item },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Directions,
                                                contentDescription = "导航",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
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
                    val todoListState = rememberLazyListState()
                    var orderedItems by remember { mutableStateOf(items) }
                    var draggingItemId by remember { mutableStateOf<Int?>(null) }
                    var draggedOffsetY by remember { mutableStateOf(0f) }
                    val latestOrderedItems by rememberUpdatedState(orderedItems)
                    val latestSourceItems by rememberUpdatedState(items)
                    LaunchedEffect(items) {
                        if (draggingItemId == null) {
                            orderedItems = items
                        }
                    }

                    LazyColumn(
                        state = todoListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .testTag("todo_list"),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isCompactMode) 6.dp else 10.dp)
                    ) {
                        items(
                            items = orderedItems,
                            key = { it.id },
                            contentType = { "todo_item" }
                        ) { item ->
                            val isDragging = draggingItemId == item.id
                            val dragModifier = Modifier
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer {
                                    translationY = if (isDragging) draggedOffsetY else 0f
                                    scaleX = if (isDragging) 1.02f else 1f
                                    scaleY = if (isDragging) 1.02f else 1f
                                }
                                .pointerInput(item.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingItemId = item.id
                                            draggedOffsetY = 0f
                                        },
                                        onDragEnd = {
                                            viewModel.updateTodoOrder(latestOrderedItems)
                                            draggingItemId = null
                                            draggedOffsetY = 0f
                                        },
                                        onDragCancel = {
                                            orderedItems = latestSourceItems
                                            draggingItemId = null
                                            draggedOffsetY = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val currentItems = latestOrderedItems
                                            val currentIndex = currentItems.indexOfFirst { it.id == item.id }
                                            if (currentIndex == -1) return@detectDragGesturesAfterLongPress

                                            draggedOffsetY += dragAmount.y
                                            val visibleInfo = todoListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == item.id }
                                            val threshold = ((visibleInfo?.size ?: 96) * 0.55f).coerceAtLeast(32f)
                                            val targetIndex = when {
                                                draggedOffsetY > threshold && currentIndex < currentItems.lastIndex -> currentIndex + 1
                                                draggedOffsetY < -threshold && currentIndex > 0 -> currentIndex - 1
                                                else -> currentIndex
                                            }

                                            if (targetIndex != currentIndex) {
                                                orderedItems = currentItems.toMutableList().apply {
                                                    add(targetIndex, removeAt(currentIndex))
                                                }
                                                draggedOffsetY = 0f
                                            }
                                        }
                                    )
                                }

                            TodoItemRow(
                                item = item,
                                isCompact = isCompactMode,
                                modifier = dragModifier,
                                onToggleComplete = { viewModel.toggleComplete(item) },
                                onToggleImportant = { viewModel.toggleImportant(item) },
                                onEdit = {
                                    editingItem = item
                                    showAddSheet = true
                                },
                                onDelete = { showDeleteConfirmDialog = item },
                                onNavigate = { showMapSelectorForTodo = item }
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

    // Delete confirmation Dialog
    if (rolloverCandidates.isNotEmpty()) {
        DailyRolloverDialog(
            candidates = rolloverCandidates,
            onConfirm = viewModel::confirmDailyRollover
        )
    }

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

    // Map selection Dialog
    showMapSelectorForTodo?.let { item ->
        AlertDialog(
            onDismissRequest = { showMapSelectorForTodo = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("选择地图导航应用", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val mapApps = listOf(
                        Triple("default", "系统默认地图 (推荐)", Icons.Default.Explore),
                        Triple("amap", "高德地图", Icons.Default.Navigation),
                        Triple("baidu", "百度地图", Icons.Default.PinDrop),
                        Triple("tencent", "腾讯地图", Icons.Default.LocationOn),
                        Triple("google", "Google 地图", Icons.Default.Language)
                    )
                    
                    mapApps.forEach { (type, name, icon) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    openExternalMap(
                                        context = context,
                                        latitude = item.latitude ?: 0.0,
                                        longitude = item.longitude ?: 0.0,
                                        label = item.locationName ?: item.title,
                                        mapType = type
                                    )
                                    showMapSelectorForTodo = null
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = name,
                                tint = when (type) {
                                    "default" -> MaterialTheme.colorScheme.primary
                                    "amap" -> Color(0xFF1B82D2)
                                    "baidu" -> Color(0xFFE60012)
                                    "tencent" -> Color(0xFF2FA87B)
                                    "google" -> Color(0xFF4285F4)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMapSelectorForTodo = null }) {
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
                onSave = { title, description, category, isImportant, dueDate, locationName, latitude, longitude, imageUrl, syncToCalendar, alarmTime, hasAlarm ->
                    if (editingItem != null) {
                        viewModel.updateTodo(
                            editingItem!!.copy(
                                title = title,
                                description = description,
                                category = category,
                                isImportant = isImportant,
                                dueDate = dueDate,
                                locationName = locationName,
                                latitude = latitude,
                                longitude = longitude,
                                imageUrl = imageUrl,
                                alarmTime = alarmTime,
                                hasAlarm = hasAlarm
                            ),
                            syncToCalendar = syncToCalendar
                        )
                    } else {
                        viewModel.insertTodo(
                            title = title,
                            description = description,
                            category = category,
                            isImportant = isImportant,
                            dueDate = dueDate,
                            locationName = locationName,
                            latitude = latitude,
                            longitude = longitude,
                            imageUrl = imageUrl,
                            syncToCalendar = syncToCalendar,
                            alarmTime = alarmTime,
                            hasAlarm = hasAlarm
                        )
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
                },
                onCenterMap = { lat, lng ->
                    mapCenterLat = lat
                    mapCenterLng = lng
                    isMapMode = true
                }
            )
        }
    }
}

@Composable
private fun DailyRolloverDialog(
    candidates: List<RolloverCandidate>,
    onConfirm: (Set<Int>) -> Unit
) {
    var selectedIds by remember(candidates) {
        mutableStateOf(candidates.mapTo(mutableSetOf()) { it.todo.id })
    }

    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Default.History, contentDescription = null) },
        title = { Text("处理昨日/过期任务") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "昨日记录已保存到历史归档。请选择仍需在今天继续的任务，未选择的任务会以“未完成”状态留在历史中。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${candidates.size} 项待处理", fontWeight = FontWeight.SemiBold)
                    TextButton(
                        onClick = {
                            selectedIds = if (selectedIds.size == candidates.size) {
                                mutableSetOf()
                            } else {
                                candidates.mapTo(mutableSetOf()) { it.todo.id }
                            }
                        }
                    ) {
                        Text(if (selectedIds.size == candidates.size) "全部取消" else "全部选择")
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(candidates, key = { it.todo.id }) { candidate ->
                        val selected = candidate.todo.id in selectedIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    selectedIds = selectedIds.toMutableSet().apply {
                                        if (selected) remove(candidate.todo.id) else add(candidate.todo.id)
                                    }
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selected,
                                onCheckedChange = {
                                    selectedIds = selectedIds.toMutableSet().apply {
                                        if (selected) remove(candidate.todo.id) else add(candidate.todo.id)
                                    }
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    candidate.todo.title,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${candidate.archiveDay} · ${candidate.todo.category}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedIds) }) {
                Text(if (selectedIds.isEmpty()) "仅保存历史" else "继续 ${selectedIds.size} 项")
            }
        }
    )
}

@Composable
fun StatsCard(stats: TaskStats) {
    val completionRatio = if (stats.todayTotal > 0) {
        stats.todayCompleted.toFloat() / stats.todayTotal.toFloat()
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
                        stats.todayTotal == 0 -> "没有找到待办事项，开始新的一天吧！"
                        stats.yesterdayUnfinished > 0 -> "昨日还有 ${stats.yesterdayUnfinished} 项未完成，今天优先处理它们。"
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
                        text = "${stats.todayCompleted}/${stats.todayTotal}",
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
    isCompact: Boolean = false,
    modifier: Modifier = Modifier,
    onToggleComplete: () -> Unit,
    onToggleImportant: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onNavigate: () -> Unit = {}
) {
    val categoryConfig = remember(item.category) { CategoryConfig.getByName(item.category) }
    val dateFormatter = remember { SimpleDateFormat("MM-dd", Locale.getDefault()) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("todo_item_${item.id}"),
        shape = RoundedCornerShape(if (isCompact) 8.dp else 12.dp),
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
                .padding(
                    vertical = if (isCompact) 6.dp else 12.dp,
                    horizontal = if (isCompact) 10.dp else 14.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox Container
            IconButton(
                onClick = onToggleComplete,
                modifier = Modifier
                    .size(if (isCompact) 28.dp else 36.dp)
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
                    modifier = Modifier.size(if (isCompact) 18.dp else 24.dp)
                )
            }

            Spacer(modifier = Modifier.width(if (isCompact) 4.dp else 6.dp))

            // Text Content (Title, Description, Tags)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onEdit() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.title,
                        fontSize = if (isCompact) 14.sp else 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (item.isCompleted) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (isCompact) {
                        // Small category dot indicator in compact mode
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(categoryConfig.color, CircleShape)
                        )
                    }
                }

                if (item.description.isNotEmpty() && !isCompact) {
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

                if (!isCompact) {
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

                        // Location Tag
                        if (item.latitude != null && item.longitude != null) {
                            Row(
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clickable {
                                        onNavigate()
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = "查看位置与导航",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = item.locationName ?: "查看位置",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 100.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Micro indicators for compact mode
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        // Due date micro indicator
                        item.dueDate?.let { dueDateMillis ->
                            val isOverdue = dueDateMillis < System.currentTimeMillis() && !item.isCompleted
                            val dueDateStr = dateFormatter.format(Date(dueDateMillis))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = dueDateStr,
                                    fontSize = 10.sp,
                                    color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }

                        if (item.calendarEventId != null) {
                            Icon(
                                imageVector = Icons.Default.Event,
                                contentDescription = "已同步",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.size(10.dp)
                            )
                        }

                        if (item.latitude != null && item.longitude != null) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { onNavigate() }
                                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = "有位置与导航",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = item.locationName ?: "查看位置",
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 60.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (!item.imageUrl.isNullOrEmpty() && !isCompact) {
                Spacer(modifier = Modifier.width(8.dp))
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.imageUrl)
                        .crossfade(false)
                        .build(),
                    contentDescription = "任务配图",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                )
            }

            Spacer(modifier = Modifier.width(if (isCompact) 4.dp else 8.dp))

            // Action Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Star Toggle
                IconButton(
                    onClick = onToggleImportant,
                    modifier = Modifier
                        .size(if (isCompact) 28.dp else 36.dp)
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
                        modifier = Modifier.size(if (isCompact) 18.dp else 22.dp)
                    )
                }

                // Delete Button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(if (isCompact) 28.dp else 36.dp)
                        .testTag("todo_item_delete_button_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除待办",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(if (isCompact) 18.dp else 20.dp)
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
    onSave: (title: String, description: String, category: String, isImportant: Boolean, dueDate: Long?, locationName: String?, latitude: Double?, longitude: Double?, imageUrl: String?, syncToCalendar: Boolean, alarmTime: Long?, hasAlarm: Boolean) -> Unit,
    onCancel: () -> Unit
) {
    val titleState = remember { mutableStateOf(editingItem?.title ?: "") }
    val descriptionState = remember { mutableStateOf(editingItem?.description ?: "") }
    var selectedCategory by remember { mutableStateOf(editingItem?.category ?: "工作") }
    val imageUrlState = remember { mutableStateOf(editingItem?.imageUrl ?: "") }
    var isImportant by remember { mutableStateOf(editingItem?.isImportant ?: false) }
    var dueDate by remember { mutableStateOf(editingItem?.dueDate) }
    val locationNameState = remember { mutableStateOf(editingItem?.locationName ?: "") }
    var latitude by remember { mutableStateOf(editingItem?.latitude) }
    var longitude by remember { mutableStateOf(editingItem?.longitude) }
    var showMapPicker by remember { mutableStateOf(false) }
    val mapSearchInputState = remember { mutableStateOf(editingItem?.locationName ?: "") }
    var mapSearchTrigger by remember { mutableStateOf(0) }
    var mapSearchLoading by remember { mutableStateOf(false) }
    var mapSearchMessage by remember { mutableStateOf<String?>(null) }
    var mapPoiResults by remember { mutableStateOf<List<BaiduPoiSuggestion>>(emptyList()) }
    var selectedPoiSuggestion by remember { mutableStateOf<BaiduPoiSuggestion?>(null) }
    var selectedPoiTrigger by remember { mutableStateOf(0) }

    var hasAlarm by remember { mutableStateOf(editingItem?.hasAlarm ?: false) }
    var alarmTime by remember { mutableStateOf(editingItem?.alarmTime) }
    var setSystemAlarmToo by remember { mutableStateOf(false) }

    // Auto-fill alarmTime if hasAlarm is enabled and alarmTime is null, using dueDate as default
    LaunchedEffect(hasAlarm) {
        if (hasAlarm && alarmTime == null) {
            alarmTime = dueDate ?: (System.currentTimeMillis() + 3600000) // 1 hour from now as default if no due date
        }
    }

    val context = LocalContext.current
    val sharedPrefsForSync = remember { context.getSharedPreferences("todo_settings", android.content.Context.MODE_PRIVATE) }
    val defaultSyncVal = remember { sharedPrefsForSync.getBoolean("default_sync_to_calendar", false) }
    var syncToCalendar by remember { mutableStateOf(editingItem?.calendarEventId != null || (editingItem == null && defaultSyncVal)) }

    val coroutineScope = rememberCoroutineScope()
    var isGeneratingImage by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val file = java.io.File(context.cacheDir, "upload_img_${System.currentTimeMillis()}.jpg")
                java.io.FileOutputStream(file).use { out ->
                    inputStream?.copyTo(out)
                }
                imageUrlState.value = file.absolutePath
                Toast.makeText(context, "图片上传成功！", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "图片加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
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
        FastOutlinedTextField(
            state = titleState,
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
        FastOutlinedTextField(
            state = descriptionState,
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

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable {
                    hasAlarm = !hasAlarm
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
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "设置日程闹钟",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (hasAlarm && alarmTime != null) {
                            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                            "⏰ 响铃于: ${sdf.format(Date(alarmTime!!))}"
                        } else {
                            "截止时间或指定时间响铃提醒"
                        },
                        fontSize = 11.sp,
                        color = if (hasAlarm) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = hasAlarm,
                onCheckedChange = { hasAlarm = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.tertiary,
                    checkedTrackColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            )
        }

        // Expanded alarm settings
        if (hasAlarm) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Change alarm time button
                Button(
                    onClick = {
                        val calendar = Calendar.getInstance()
                        alarmTime?.let { calendar.timeInMillis = it }
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                TimePickerDialog(
                                    context,
                                    { _, hourOfDay, minute ->
                                        val selectedCal = Calendar.getInstance().apply {
                                            set(Calendar.YEAR, year)
                                            set(Calendar.MONTH, month)
                                            set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                            set(Calendar.HOUR_OF_DAY, hourOfDay)
                                            set(Calendar.MINUTE, minute)
                                            set(Calendar.SECOND, 0)
                                        }
                                        alarmTime = selectedCal.timeInMillis
                                    },
                                    calendar.get(Calendar.HOUR_OF_DAY),
                                    calendar.get(Calendar.MINUTE),
                                    true
                                ).show()
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(1f).height(36.dp)
                ) {
                    Icon(Icons.Default.AccessTime, null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("修改闹钟响铃时间", fontSize = 11.sp)
                }

                // System alarm integration checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { setSystemAlarmToo = !setSystemAlarmToo }
                        .padding(horizontal = 4.dp)
                ) {
                    Checkbox(
                        checked = setSystemAlarmToo,
                        onCheckedChange = { setSystemAlarmToo = it },
                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.tertiary)
                    )
                    Text("同步至系统闹钟", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Task Image Cover section
        Text(
            text = "配图封面 (本地/AI 生成)",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (imageUrlState.value.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrlState.value)
                        .crossfade(true)
                        .build(),
                    contentDescription = "预览图",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                FastOutlinedTextField(
                    state = imageUrlState,
                    label = { Text("配图 URL/本地路径") },
                    placeholder = { Text("输入图片 URL/路径或生成") },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    ),
                    trailingIcon = {
                        if (imageUrlState.value.isNotEmpty()) {
                            IconButton(onClick = { imageUrlState.value = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "清除")
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Upload button
                    Button(
                        onClick = {
                            imagePickerLauncher.launch("image/*")
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.weight(1f).height(36.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Image, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("本地上传", fontSize = 11.sp)
                    }

                    // Direct Gemini Imagen Button (Optimized to isolate recompositions)
                    ImagenButton(
                        titleState = titleState,
                        imageUrlState = imageUrlState,
                        isGeneratingImage = isGeneratingImage,
                        onImageGenerated = { path -> imageUrlState.value = path },
                        modifier = Modifier.weight(1.2f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Map Location section
        Text(
            text = "位置标记 (地图与位置感知)",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FastOutlinedTextField(
                state = locationNameState,
                label = { Text("地点/名称") },
                placeholder = { Text("输入或在地图上选择地点") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                ),
                trailingIcon = {
                    if (locationNameState.value.isNotEmpty()) {
                        IconButton(onClick = { 
                            locationNameState.value = "" 
                            latitude = null
                            longitude = null
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "清除")
                        }
                    }
                }
            )

            // Select on Map Button
            IconButton(
                onClick = {
                    mapSearchInputState.value = locationNameState.value
                    mapPoiResults = emptyList()
                    selectedPoiSuggestion = null
                    mapSearchMessage = if (locationNameState.value.isNotBlank()) "正在按地点名称搜索..." else null
                    mapSearchTrigger += 1
                    showMapPicker = true
                },
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = "在地图上选择定位",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (latitude != null && longitude != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "定位经纬度: ${String.format("%.4f", latitude)}, ${String.format("%.4f", longitude)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = {
                        openExternalMap(
                            context = context,
                            latitude = latitude ?: return@OutlinedButton,
                            longitude = longitude ?: return@OutlinedButton,
                            label = locationNameState.value.ifBlank { "待办位置" },
                            mapType = "baidu"
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("导航", fontSize = 11.sp)
                }
            }
        }

        if (showMapPicker) {
            Dialog(
                onDismissRequest = { showMapPicker = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        tonalElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.88f)
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp)
                ) {
                            BottomSheetDefaults.DragHandle(modifier = Modifier.align(Alignment.CenterHorizontally))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("搜索或点击地图选择位置", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                text = "拖动地图时请从地图内部开始滑动，避开屏幕边缘返回手势。",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    openExternalMap(
                                        context = context,
                                        latitude = latitude ?: return@OutlinedButton,
                                        longitude = longitude ?: return@OutlinedButton,
                                        label = locationNameState.value.ifBlank { "待办位置" },
                                        mapType = "baidu"
                                    )
                                },
                                enabled = latitude != null && longitude != null,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier.height(38.dp)
                            ) {
                                Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("导航", fontSize = 12.sp)
                            }
                            Button(
                                onClick = { showMapPicker = false },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(38.dp)
                            ) {
                                Text("完成", fontSize = 12.sp)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = mapSearchInputState.value,
                            onValueChange = { mapSearchInputState.value = it },
                            placeholder = { Text("搜索地点或地址") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    val keyword = mapSearchInputState.value.trim()
                                    if (keyword.isNotBlank()) {
                                        mapSearchInputState.value = keyword
                                        mapSearchTrigger += 1
                                    }
                                }
                            ),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            ),
                            trailingIcon = {
                                if (mapSearchInputState.value.isNotBlank()) {
                                    IconButton(onClick = { mapSearchInputState.value = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "清除")
                                    }
                                }
                            }
                        )
                        Button(
                            onClick = {
                                val keyword = mapSearchInputState.value.trim()
                                if (keyword.isBlank()) {
                                    Toast.makeText(context, "请输入要搜索的地点", Toast.LENGTH_SHORT).show()
                                } else {
                                    mapSearchInputState.value = keyword
                                    mapSearchTrigger += 1
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            modifier = Modifier.height(56.dp)
                        ) {
                            if (mapSearchLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    if (!mapSearchMessage.isNullOrBlank() || mapPoiResults.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .padding(bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            mapSearchMessage?.let { message ->
                                Text(
                                    text = message,
                                    fontSize = 11.sp,
                                    color = if (mapSearchLoading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (mapPoiResults.isNotEmpty()) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(end = 4.dp)
                                ) {
                                    items(mapPoiResults) { suggestion ->
                                        Column(
                                            modifier = Modifier
                                                .widthIn(min = 150.dp, max = 240.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f))
                                                .clickable {
                                                    selectedPoiSuggestion = suggestion
                                                    selectedPoiTrigger += 1
                                                    locationNameState.value = suggestion.displayName
                                                    latitude = suggestion.latitude
                                                    longitude = suggestion.longitude
                                                }
                                                .padding(horizontal = 12.dp, vertical = 9.dp)
                                        ) {
                                            Text(
                                                text = suggestion.name,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (suggestion.displayAddress.isNotBlank()) {
                                                Text(
                                                    text = suggestion.displayAddress,
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 14.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                                RoundedCornerShape(14.dp)
                            )
                    ) {
                        BaiduMapPickerView(
                            initialLat = latitude ?: 30.25,
                            initialLng = longitude ?: 120.15,
                            initialZoom = 15f,
                            searchKeyword = mapSearchInputState.value,
                            searchTrigger = mapSearchTrigger,
                            selectedSuggestion = selectedPoiSuggestion,
                            selectedSuggestionTrigger = selectedPoiTrigger,
                            modifier = Modifier.fillMaxSize(),
                            onLocationPicked = { lat, lng, address ->
                                latitude = lat
                                longitude = lng
                                if (!address.isNullOrBlank()) {
                                    locationNameState.value = address
                                    mapSearchInputState.value = address
                                } else if (locationNameState.value.isBlank()) {
                                    locationNameState.value = "百度地图选中位置"
                                }
                            },
                            onSearchStateChanged = { searching, message ->
                                mapSearchLoading = searching
                                mapSearchMessage = message
                            },
                            onSearchResultsChanged = { results ->
                                mapPoiResults = results
                            }
                        )
                    }
                }
            }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

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

            SaveButton(
                titleState = titleState,
                onClick = { 
                    if (hasAlarm && alarmTime != null && setSystemAlarmToo) {
                        val cal = Calendar.getInstance().apply { timeInMillis = alarmTime!! }
                        com.example.utils.AlarmScheduler.createSystemAlarm(
                            context = context,
                            title = titleState.value,
                            hour = cal.get(Calendar.HOUR_OF_DAY),
                            minute = cal.get(Calendar.MINUTE)
                        )
                    }
                    onSave(
                        titleState.value,
                        descriptionState.value,
                        selectedCategory,
                        isImportant,
                        dueDate,
                        locationNameState.value.ifBlank { null },
                        latitude,
                        longitude,
                        imageUrlState.value.ifBlank { null },
                        syncToCalendar,
                        alarmTime,
                        hasAlarm
                    ) 
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("confirm_save_button")
            )
        }
    }
}

@Composable
fun DrawerContent(
    currentScreen: String,
    stats: TaskStats,
    onScreenSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        // Drawer Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TaskAlt,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "待办随行",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "高效、专注、有温度的待办",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Live statistics display inside drawer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${stats.completed}/${stats.total}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "已完成任务",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    
                    val progressPercent = if (stats.total > 0) (stats.completed * 100) / stats.total else 100
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "进度 ${progressPercent}%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Navigation Group Label
        Text(
            text = "核心功能",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
        )
        
        // Navigation Drawer Items
        val items = listOf(
            Triple("todos", "待办清单", Icons.Default.Checklist),
            Triple("plans", "分段计划", Icons.Default.EventNote),
            Triple("weekly_report", "智能周报", Icons.Default.Article),
            Triple("analysis", "数据统计", Icons.Default.BarChart),
            Triple("openclaw", "龙虾", Icons.Default.PhoneAndroid),
            Triple("settings", "软件设置", Icons.Default.Settings),
            Triple("extensions", "效率拓展", Icons.Default.Extension)
        )
        
        items.forEach { (screenKey, label, icon) ->
            val isSelected = currentScreen == screenKey
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent
                    )
                    .clickable { onScreenSelected(screenKey) }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                if (screenKey == "openclaw") {
                    Box(
                        modifier = Modifier.size(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🦞",
                            fontSize = 18.sp
                        )
                    }
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Footer inside drawer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "💡 用专注重塑日常",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun SettingsScreenContent(
    currentStyle: String,
    onStyleSelected: (String) -> Unit,
    syncToCalendar: Boolean,
    onSyncToCalendarChanged: (Boolean) -> Unit,
    context: android.content.Context
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
    ) {
        // Section: Background Style Settings
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "软件背景与主题风格",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Style list
                    val styles = listOf(
                        Triple("default", "系统默认风格", "Material 3 经典光暗色系"),
                        Triple("cosmic", "星辰深邃 (Cosmic)", "梦幻迷人的紫红黑渐变夜空"),
                        Triple("forest", "森林绿意 (Healing)", "舒缓健康的墨绿原野呼吸感"),
                        Triple("sakura", "樱落粉黛 (Romance)", "温柔细腻的初春樱粉花海"),
                        Triple("aurora", "极光之境 (Teal)", "北欧冰原的炫彩青荧极光"),
                        Triple("sunset", "日落晚霞 (Sunset)", "绚丽浪漫的橘紫黄昏色彩")
                    )
                    
                    styles.forEach { (styleKey, title, desc) ->
                        val isSelected = currentStyle == styleKey
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                    else Color.Transparent
                                )
                                .clickable { onStyleSelected(styleKey) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Mini circle preview
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            brush = when (styleKey) {
                                                "cosmic" -> Brush.verticalGradient(listOf(Color(0xFF0F0C20), Color(0xFF16102D)))
                                                "forest" -> Brush.verticalGradient(listOf(Color(0xFF091F15), Color(0xFF113222)))
                                                "sakura" -> Brush.verticalGradient(listOf(Color(0xFF2E151B), Color(0xFF3F1D25)))
                                                "aurora" -> Brush.verticalGradient(listOf(Color(0xFF051D21), Color(0xFF0A3138)))
                                                "sunset" -> Brush.verticalGradient(listOf(Color(0xFF270E17), Color(0xFF3A1320)))
                                                else -> Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))
                                            }
                                        )
                                )
                                
                                Column {
                                    Text(
                                        text = title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = desc,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "已选择",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
        
        // Section: System Permissions & Sync
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "日程与系统同步设置",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Calendar sync switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "新建日程默认同步至系统日历",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "开启后，新建设置截止日期的待办将自动添加系统日程事件。",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = syncToCalendar,
                            onCheckedChange = onSyncToCalendarChanged
                        )
                    }
                    
                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    
                    // Notification Permission advisory
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "本地闹钟与通知状态",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "待办事项自带高精度闹钟引擎。如无法响铃，请确保在系统“设置 -> 应用通知”中允许本软件发送通知及使用精确闹钟权限。",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        
        // Section: About developer / brand
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Task,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "待办随行 Companion",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "版本 v1.4.2 · 极速编译版",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "为您的生活和工作提供最极致的高效守护。支持日历同步、高精度截止时间闹钟提醒、AI 智能语音与图像辅助创意规划，随时随地与您随行。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ExtensionsScreenContent(
    viewModel: TodoViewModel,
    stats: TaskStats,
    context: android.content.Context
) {
    val pomodoroState by viewModel.pomodoroState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val statusScope = rememberCoroutineScope()
    var notificationChannelEnabled by remember {
        mutableStateOf(PomodoroNotifier.isRunningChannelEnabled(context))
    }
    var os3IslandSupported by remember { mutableStateOf(false) }
    var os3FocusPermission by remember { mutableStateOf(false) }
    val refreshXiaomiStatus = {
        statusScope.launch {
            val status = withContext(Dispatchers.IO) {
                XiaomiSuperIsland.isOs3Supported(context) to XiaomiSuperIsland.hasFocusPermission(context)
            }
            os3IslandSupported = status.first
            os3FocusPermission = status.second
        }
    }
    LaunchedEffect(Unit) { refreshXiaomiStatus() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationChannelEnabled = PomodoroNotifier.isRunningChannelEnabled(context)
                refreshXiaomiStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            notificationChannelEnabled = PomodoroNotifier.isRunningChannelEnabled(context)
            viewModel.togglePomodoro()
        } else {
            Toast.makeText(context, "未开启通知权限，锁屏倒计时和完成提醒可能无法显示。", Toast.LENGTH_LONG).show()
        }
    }
    val startOrPausePomodoro = {
        if (!pomodoroState.isRunning &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (!pomodoroState.isRunning && !PomodoroNotifier.isRunningChannelEnabled(context)) {
            PomodoroNotifier.openRunningChannelSettings(context)
            Toast.makeText(context, "请允许“番茄钟锁屏倒计时”渠道显示通知和锁屏通知。", Toast.LENGTH_LONG).show()
        } else {
            viewModel.togglePomodoro()
        }
    }
    
    // States for AI quotes
    var currentQuote by remember { mutableStateOf("专注于当下，这是塑造美好未来的唯一方式。✨") }
    var quoteAuthor by remember { mutableStateOf("待办随行金句") }
    var isGeneratingQuote by remember { mutableStateOf(false) }

    val presetMinutes = listOf(15, 25, 45, 60)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
    ) {
        // Section: Pomodoro Focus Timer
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Icon(
                            imageVector = Icons.Default.HourglassEmpty,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "极简高效番茄钟 (Pomodoro)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "锁屏通知会显示剩余时间；结束时会响铃或震动提醒。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (notificationChannelEnabled) "番茄钟通知渠道：可发送" else "番茄钟通知渠道：被关闭",
                            fontSize = 11.sp,
                            color = if (notificationChannelEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = { PomodoroNotifier.openRunningChannelSettings(context) }) {
                            Text("通知设置", fontSize = 11.sp)
                        }
                    }
                    if (os3IslandSupported) {
                        Text(
                            text = if (os3FocusPermission) {
                                "小米超级岛：系统与焦点通知权限已就绪"
                            } else {
                                "小米超级岛：系统支持，应用尚未获得焦点通知授权"
                            },
                            fontSize = 11.sp,
                            color = if (os3FocusPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(160.dp)
                    ) {
                        val progress = if (pomodoroState.totalSeconds > 0) {
                            pomodoroState.remainingSeconds.toFloat() / pomodoroState.totalSeconds
                        } else {
                            1f
                        }
                        CircularProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 8.dp,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        )
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val mins = pomodoroState.remainingSeconds / 60
                            val secs = pomodoroState.remainingSeconds % 60
                            Text(
                                text = String.format("%02d:%02d", mins, secs),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (pomodoroState.isRunning) "专注中..." else "已准备就绪",
                                fontSize = 11.sp,
                                color = if (pomodoroState.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    if (!pomodoroState.isRunning) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            presetMinutes.forEach { mins ->
                                val selected = pomodoroState.totalSeconds == mins * 60
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                        )
                                        .clickable {
                                            viewModel.setPomodoroPreset(mins)
                                        }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${mins}分钟",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = startOrPausePomodoro,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (pomodoroState.isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            Icon(
                                imageVector = if (pomodoroState.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (pomodoroState.isRunning) "暂停专注" else "开始专注", fontSize = 13.sp)
                        }
                        
                        OutlinedButton(
                            onClick = {
                                viewModel.resetPomodoro()
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("重置", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        
        // Section: AI Quotes of Mindfulness and Inspiration
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFFFFB74D)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AI 效率能量站",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        IconButton(
                            onClick = {
                                isGeneratingQuote = true
                                val quoteList = listOf(
                                    "不积跬步，无以至千里；不积小流，无以成江海。每一条待办都是迈向不凡的一步！🚀" to "荀子",
                                    "真正的专注不是做百件小事，而是把一件重要的事做到极致。💼" to "史蒂夫·乔布斯",
                                    "每一个今天，都是你余生中最年轻的一天。把握当下，马上行动！🔥" to "随行金句",
                                    "拖延是最高昂的智商税。给未来的自己一个拥抱，从勾掉下一个复选框开始。☀️" to "效率语录",
                                    "山不在高，有仙则名；事不在多，专注则灵。愿你今天轻装上阵，效率倍增！⛰️" to "伴读客"
                                )
                                val randomPair = quoteList.random()
                                currentQuote = randomPair.first
                                quoteAuthor = randomPair.second
                                isGeneratingQuote = false
                            },
                            enabled = !isGeneratingQuote,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "刷新金句",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Column {
                            Text(
                                text = "“",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                modifier = Modifier.offset(y = (-8).dp)
                            )
                            Text(
                                text = currentQuote,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                modifier = Modifier.offset(y = (-8).dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = "—— $quoteAuthor",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


// --- Optimized High-Performance UI Components & Helpers to avoid lags ---

@Composable
fun FastOutlinedTextField(
    state: MutableState<String>,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    textStyle: androidx.compose.ui.text.TextStyle = LocalTextStyle.current,
    modifier: Modifier = Modifier,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = state.value,
        onValueChange = { state.value = it },
        label = label,
        placeholder = placeholder,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        textStyle = textStyle,
        modifier = modifier,
        colors = colors,
        trailingIcon = trailingIcon
    )
}

@Composable
fun SaveButton(
    titleState: MutableState<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = titleState.value.isNotBlank(),
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Text("保存")
    }
}

@Composable
fun ImagenButton(
    titleState: MutableState<String>,
    imageUrlState: MutableState<String>,
    isGeneratingImage: Boolean,
    onImageGenerated: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var localGenerating by remember { mutableStateOf(false) }

    val promptToUse = remember(titleState.value, imageUrlState.value) {
        val title = titleState.value
        val imgUrl = imageUrlState.value
        if (imgUrl.isNotBlank() && !imgUrl.startsWith("http") && !imgUrl.startsWith("/")) {
            imgUrl
        } else if (title.isNotBlank()) {
            title
        } else {
            ""
        }
    }

    Button(
        onClick = {
            if (promptToUse.isNotBlank()) {
                coroutineScope.launch {
                    localGenerating = true
                    try {
                        val path = com.example.utils.GeminiManager.generateImageWithGemini(
                            context,
                            promptToUse + " flat vector illustration digital art"
                        )
                        onImageGenerated(path)
                        Toast.makeText(context, "Imagen 4 绘图生成成功！", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(context, "生成失败: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        localGenerating = false
                    }
                }
            }
        },
        enabled = promptToUse.isNotBlank() && !isGeneratingImage && !localGenerating,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = modifier.height(36.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
    ) {
        if (isGeneratingImage || localGenerating) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        } else {
            Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(14.dp))
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(if (isGeneratingImage || localGenerating) "正在绘制..." else "Imagen 4 绘制", fontSize = 11.sp)
    }
}

fun openExternalMap(
    context: android.content.Context,
    latitude: Double,
    longitude: Double,
    label: String,
    mapType: String // "default", "amap", "baidu", "tencent", "google"
) {
    if ((latitude == 0.0 && longitude == 0.0) || latitude.isNaN() || longitude.isNaN()) {
        Toast.makeText(context, "【错误】位置经纬度无效: ($latitude, $longitude)", Toast.LENGTH_LONG).show()
        return
    }

    val labelEncoded = Uri.encode(label)
    val mapName = when (mapType) {
        "amap" -> "高德地图"
        "baidu" -> "百度地图"
        "tencent" -> "腾讯地图"
        "google" -> "Google 地图"
        else -> "系统默认地图"
    }

    val baiduDirectionUri = "bdapp://map/direction?destination=latlng:$latitude,$longitude|name:$labelEncoded&mode=driving&coord_type=gcj02&src=andr.todo"
    val baiduLegacyDirectionUri = "baidumap://map/direction?destination=latlng:$latitude,$longitude|name:$labelEncoded&mode=driving&coord_type=gcj02&src=andr.todo"

    // Define route/navigation App Intents
    val appIntent = when (mapType) {
        "amap" -> {
            Intent(Intent.ACTION_VIEW, Uri.parse("androidamap://route/plan/?sourceApplication=TodoApp&dlat=$latitude&dlon=$longitude&dname=$labelEncoded&dev=0&t=0")).apply {
                setPackage("com.autonavi.minimap")
            }
        }
        "baidu" -> {
            Intent(Intent.ACTION_VIEW, Uri.parse(baiduDirectionUri)).apply {
                setPackage("com.baidu.BaiduMap")
            }
        }
        "tencent" -> {
            Intent(Intent.ACTION_VIEW, Uri.parse("qqmap://map/routeplan?type=drive&to=$labelEncoded&tocoord=$latitude,$longitude&referer=TodoApp")).apply {
                setPackage("com.tencent.map")
            }
        }
        "google" -> {
            Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$latitude,$longitude")).apply {
                setPackage("com.google.android.apps.maps")
            }
        }
        else -> { // "default"
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$latitude,$longitude($labelEncoded)"))
        }
    }
    appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    // Define Web Fallbacks
    val webFallbackUrl = when (mapType) {
        "amap" -> "https://uri.amap.com/navigation?to=$longitude,$latitude,$labelEncoded&mode=car&src=TodoApp&coordinate=gaode"
        "baidu" -> "https://api.map.baidu.com/direction?destination=latlng:$latitude,$longitude|name:$labelEncoded&mode=driving&coord_type=gcj02&output=html&src=andr.todo"
        "tencent" -> "https://apis.map.qq.com/uri/v1/routeplan?type=drive&tocoord=$latitude,$longitude&to=$labelEncoded&referer=TodoApp"
        "google" -> "https://www.google.com/maps/dir/?api=1&destination=$latitude,$longitude"
        else -> "https://uri.amap.com/navigation?to=$longitude,$latitude,$labelEncoded&mode=car&src=TodoApp&coordinate=gaode"
    }

    val fallbackSchemeIntents = when (mapType) {
        "amap" -> listOf(Intent(Intent.ACTION_VIEW, Uri.parse("androidamap://route/plan/?sourceApplication=TodoApp&dlat=$latitude&dlon=$longitude&dname=$labelEncoded&dev=0&t=0")))
        "baidu" -> listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse(baiduDirectionUri)),
            Intent(Intent.ACTION_VIEW, Uri.parse(baiduLegacyDirectionUri))
        )
        "tencent" -> listOf(Intent(Intent.ACTION_VIEW, Uri.parse("qqmap://map/routeplan?type=drive&to=$labelEncoded&tocoord=$latitude,$longitude&referer=TodoApp")))
        "google" -> listOf(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$latitude,$longitude")))
        else -> emptyList()
    }

    try {
        context.startActivity(appIntent)
        Toast.makeText(context, "正在打开${mapName}导航", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        android.util.Log.e("TodoAppMap", "Failed to launch main map intent: ${e.message}", e)
        try {
            var launched = false
            for (fallbackSchemeIntent in fallbackSchemeIntents) {
                try {
                    fallbackSchemeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(fallbackSchemeIntent)
                    launched = true
                    break
                } catch (_: Exception) {
                    // Try the next compatible URI scheme before falling back to web.
                }
            }
            if (!launched) throw e
            Toast.makeText(context, "正在打开${mapName}导航", Toast.LENGTH_SHORT).show()
        } catch (ex2: Exception) {
            android.util.Log.e("TodoAppMap", "Failed to launch fallback scheme: ${ex2.message}", ex2)
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webFallbackUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                Toast.makeText(context, "已切换到网页版${mapName}", Toast.LENGTH_SHORT).show()
            } catch (exWeb: Exception) {
                android.util.Log.e("TodoAppMap", "Failed to launch web browser fallback: ${exWeb.message}", exWeb)
                Toast.makeText(context, "【严重错误】所有拉起方式和网页浏览器均失败:\n${exWeb.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WeeklyReportScreenContent(
    viewModel: TodoViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val completedTasks by viewModel.completedTodoItems.collectAsStateWithLifecycle()
    val reportContent by viewModel.weeklyReportContent.collectAsStateWithLifecycle()
    val reportLoading by viewModel.weeklyReportLoading.collectAsStateWithLifecycle()
    val reportError by viewModel.weeklyReportError.collectAsStateWithLifecycle()

    // Active Category Filter for completed tasks list
    var selectedCategory by remember { mutableStateOf("全部") }
    val categories = remember {
        listOf("全部") + CategoryConfig.categories.filter { it.name != "全部" && it.name != "重要" }.map { it.name }
    }

    // Filtered tasks shown to the user
    val displayedTasks = remember(completedTasks, selectedCategory) {
        if (selectedCategory == "全部") {
            completedTasks
        } else {
            completedTasks.filter { it.category == selectedCategory }
        }
    }
    var visibleTaskCount by remember { mutableIntStateOf(20) }
    LaunchedEffect(selectedCategory) { visibleTaskCount = 20 }
    val visibleTasks = remember(displayedTasks, visibleTaskCount) {
        displayedTasks.take(visibleTaskCount)
    }

    // Map of selected tasks to summarize
    val selectedTaskIds = remember { mutableStateMapOf<Int, Boolean>() }

    // Initialize/sync selections when displayedTasks changes
    LaunchedEffect(displayedTasks) {
        displayedTasks.forEach { task ->
            if (!selectedTaskIds.containsKey(task.id)) {
                selectedTaskIds[task.id] = true // select by default
            }
        }
    }

    var customPrompt by remember { mutableStateOf("") }
    var summaryStyle by remember { mutableStateOf("standard") } // "standard", "concise", "detailed", "work"

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // Title banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Assessment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "AI 智能周报生成",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "选择完成的工作，AI 自动为你汇总精美的职场周报",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // Selection & Tag filtering block
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "1. 挑选待总结任务",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    // Select All / Deselect All
                    if (displayedTasks.isNotEmpty()) {
                        val allSelected = displayedTasks.all { selectedTaskIds[it.id] == true }
                        TextButton(
                            onClick = {
                                displayedTasks.forEach { task ->
                                    selectedTaskIds[task.id] = !allSelected
                                }
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = if (allSelected) "取消全选" else "全选本组",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tags/Categories row filter
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { categoryName ->
                        val isSelected = selectedCategory == categoryName
                        val categoryConfig = CategoryConfig.categories.find { it.name == categoryName }
                        val chipColor = categoryConfig?.color ?: MaterialTheme.colorScheme.primary
                        
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = categoryName },
                            label = { Text(categoryName, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = chipColor.copy(alpha = 0.2f),
                                selectedLabelColor = chipColor,
                                selectedLeadingIconColor = chipColor
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (completedTasks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "暂无已完成的任务，快去完成一些任务吧！",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else if (displayedTasks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "该分类下暂无已完成的任务",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        visibleTasks.forEach { task ->
                            val isChecked = selectedTaskIds[task.id] ?: false
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                    .clickable { selectedTaskIds[task.id] = !isChecked }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { selectedTaskIds[task.id] = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = task.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (task.description.isNotBlank()) {
                                        Text(
                                            text = task.description,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = task.category,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        if (visibleTasks.size < displayedTasks.size) {
                            TextButton(
                                onClick = { visibleTaskCount += 20 },
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text(
                                    "显示更多（剩余 ${displayedTasks.size - visibleTasks.size} 项）",
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Style preferences and instructions
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "2. 设定周报风格与附加要求",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Summary Style Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val styles = listOf(
                        "standard" to "标准",
                        "concise" to "精炼",
                        "detailed" to "详细",
                        "work" to "职场"
                    )
                    styles.forEach { (styleKey, label) ->
                        val isSelected = summaryStyle == styleKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                                )
                                .clickable { summaryStyle = styleKey }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Custom Prompt Textbox
                OutlinedTextField(
                    value = customPrompt,
                    onValueChange = { customPrompt = it },
                    placeholder = { Text("（可选）输入您希望补充的信息或周报撰写的特定指令（如：重点强调项目A进度、使用幽默风格、说明下周休假等）", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 4,
                    minLines = 2,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    )
                )

                // Generate Button
                val selectedTasks = completedTasks.filter { selectedTaskIds[it.id] == true }
                Button(
                    onClick = {
                        viewModel.generateWeeklyReport(context, selectedTasks, customPrompt, summaryStyle)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("generate_weekly_report_button"),
                    shape = RoundedCornerShape(12.dp),
                    enabled = selectedTasks.isNotEmpty() && !reportLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (reportLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("正在拼命撰写中...", fontSize = 14.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (selectedTasks.isEmpty()) "请先勾选完成的任务" else "一键生成智能周报 (${selectedTasks.size}项)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Output Result Card
        if (reportLoading || reportContent != null || reportError != null) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "3. 生成的周报内容",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        // Clear option
                        if (reportContent != null) {
                            TextButton(
                                onClick = { viewModel.clearWeeklyReport() },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("清空内容", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    if (reportLoading) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .height(6.dp)
                                    .clip(CircleShape)
                            )
                            Text(
                                text = "Gemini AI 正在梳理任务、整合逻辑并撰写专业措辞，这可能需要10-20秒时间...",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 24.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }

                    reportError?.let { err ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Column {
                                    Text(
                                        text = "周报生成遇到阻碍",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = err,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "💡 提示: 请确保在设置中配置了有效的 Gemini API Key。如果网络连接失败，请检查并重试。",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }

                    reportContent?.let { content ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                androidx.compose.foundation.text.selection.SelectionContainer {
                                    Text(
                                        text = content,
                                        fontSize = 13.sp,
                                        lineHeight = 20.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                Spacer(modifier = Modifier.height(8.dp))

                                // Quick Actions
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Copy Button
                                    OutlinedButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("AI 周报", content)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "周报已复制到剪贴板！", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("一键复制", fontSize = 12.sp)
                                    }

                                    // Share Button
                                    Button(
                                        onClick = {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_SUBJECT, "智能周报汇总")
                                                putExtra(Intent.EXTRA_TEXT, content)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "分享周报到..."))
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("系统分享", fontSize = 12.sp)
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
