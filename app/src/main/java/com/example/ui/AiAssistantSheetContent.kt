package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.draw.rotate as drawRotate
import androidx.compose.animation.core.*
import com.example.utils.AudioSynthPlayer
import com.example.utils.GeneratedTodoItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantSheetContent(
    viewModel: TodoViewModel,
    onDismiss: () -> Unit,
    onCenterMap: (latitude: Double, longitude: Double) -> Unit = { _, _ -> }
) {
    val aiLoading by viewModel.aiLoading.collectAsState()
    val aiGeneratedItems by viewModel.aiGeneratedItems.collectAsState()
    val aiError by viewModel.aiError.collectAsState()

    var promptInput by remember { mutableStateOf("") }
    var currentTab by remember { mutableStateOf(0) } // 0: Planner, 1: Creative Studio
    var creativeTab by remember { mutableStateOf(0) } // 0: Image, 1: Song
    
    var artPrompt by remember { mutableStateOf("") }
    var songPrompt by remember { mutableStateOf("") }
    
    val aiGeneratedArt by viewModel.aiGeneratedArt.collectAsState()
    val aiGeneratedSong by viewModel.aiGeneratedSong.collectAsState()
    
    var activeNoteIndex by remember { mutableStateOf(-1) }
    var isSongPlaying by remember { mutableStateOf(false) }

    DisposableEffect(currentTab, creativeTab) {
        onDispose {
            AudioSynthPlayer.stopPlaying()
            isSongPlaying = false
            activeNoteIndex = -1
        }
    }

    val aiApiKey by viewModel.aiApiKey.collectAsState()
    var isSettingsExpanded by remember { mutableStateOf(false) }
    var tempApiKeyInput by remember { mutableStateOf(aiApiKey) }

    LaunchedEffect(aiApiKey) {
        tempApiKeyInput = aiApiKey
    }
    
    // Tracks checked status of each generated item
    val selectedItemIndices = remember { mutableStateMapOf<Int, Boolean>() }

    // Initialize/Sync selection states when new items are generated
    LaunchedEffect(aiGeneratedItems) {
        selectedItemIndices.clear()
        aiGeneratedItems.forEachIndexed { index, _ ->
            selectedItemIndices[index] = true // checked by default
        }
    }

    val quickPrompts = listOf(
        "🏕️ 筹备周末露营准备",
        "💻 新学期软件学习计划",
        "🧹 周末全屋大扫除清单",
        "🍳 准备家常双人晚餐"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        // Sheet Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "AI 智能待办规划",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "关闭")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Custom Styled Tab Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (currentTab == 0) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { currentTab = 0 }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🎯 智能计划拆解",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (currentTab == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (currentTab == 1) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { 
                        currentTab = 1 
                        // clear other generation errors or results on tab switch to keep UI neat
                        viewModel.clearAiMultimedia()
                    }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🎨 创意多媒体中心",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (currentTab == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // API Key settings section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                .animateContentSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isSettingsExpanded = !isSettingsExpanded }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (aiApiKey.isEmpty()) "✨ 点击配置您的 Gemini API Key" else "🔒 已启用您自定义的 Pro 密钥",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = if (isSettingsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }

            if (isSettingsExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .padding(bottom = 14.dp)
                ) {
                    Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), modifier = Modifier.padding(bottom = 10.dp))
                    Text(
                        text = "关于 Google AI Pro 授权模式：",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "根据政策，消费级 App 无法直接通过 Google 账号登录来直接划扣或调用您的 API 额度。您可以在 Google AI Studio 免费申请您个人的 API Key 并填入下方。该 Key 仅保存在您手机本地，不经过任何中转服务器，安全且免受公共 API 额度限制！",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = tempApiKeyInput,
                        onValueChange = { tempApiKeyInput = it },
                        placeholder = { Text("输入 AI Studio API 密钥 (AIzaSy...)") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (tempApiKeyInput.isNotEmpty()) {
                                IconButton(onClick = { tempApiKeyInput = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "清除")
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                tempApiKeyInput = aiApiKey
                                isSettingsExpanded = false
                            }
                        ) {
                            Text("取消", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.saveAiApiKey(tempApiKeyInput.trim())
                                isSettingsExpanded = false
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("保存并应用", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (currentTab == 0) {
            if (aiGeneratedItems.isEmpty() && !aiLoading) {
            // STEP 1: INPUT AND QUICK PROMPTS
            Text(
                text = "告诉 AI 您的目标或任务（例如：筹备周末出行、搬家规划等），AI 将为您智能拆解为步骤详尽的清单！",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = promptInput,
                onValueChange = { promptInput = it },
                label = { Text("请输入您的规划目标") },
                placeholder = { Text("例如：下周去北京旅游三天的计划...") },
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_prompt_input")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Recommendations
            Text(
                text = "热门规划灵感：",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickPrompts.forEach { item ->
                    SuggestionChip(
                        onClick = { promptInput = item.substring(3) }, // strip emoji prefix
                        label = { Text(item, fontSize = 12.sp) },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Button
            Button(
                onClick = { viewModel.generateTodoWithAi(promptInput) },
                enabled = promptInput.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("ai_generate_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("AI 智能拆解规划", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        } else if (aiLoading) {
            // STEP 2: LOADING STATE
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4.dp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "✨ Gemini 正在为您定制专属计划...",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "正在努力将复杂的目标拆解为可行步骤",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (aiError != null) {
            // STEP 3: ERROR STATE
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "规划拆解遇到问题",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = aiError ?: "未知错误，请重试",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.clearAiGeneratedItems() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("返回修改")
                    }
                    Button(
                        onClick = { viewModel.generateTodoWithAi(promptInput) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("重试一次")
                    }
                }
            }
        } else {
            // STEP 4: DISPLAY AND SELECTION LIST
            val actions by viewModel.aiGeneratedActions.collectAsState()
            if (actions.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Handyman,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "🤖 AI 已自动调用以下系统工具：",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        actions.forEach { action ->
                            Row(
                                modifier = Modifier.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                when (action.type) {
                                    "center_map" -> {
                                        Icon(
                                            imageVector = Icons.Default.MyLocation,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "地图聚焦工具：已自动聚焦于 ${action.address ?: "选中位置"} (${action.latitude}, ${action.longitude})",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    "generate_image" -> {
                                        Icon(
                                            imageVector = Icons.Default.Palette,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Nanobanana 绘图仪：已为「${action.targetTaskTitle}」设计专属配图",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    else -> {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "自定义工具：${action.type}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Text(
                text = "✨ 规划方案已就绪！您可以勾选需要的任务导入您的清单：",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 350.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(aiGeneratedItems) { index, item ->
                    val isChecked = selectedItemIndices[index] ?: false
                    val categoryConfig = remember(item.category) { CategoryConfig.getByName(item.category) }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .clickable { selectedItemIndices[index] = !isChecked }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { selectedItemIndices[index] = it }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = item.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                // Category Tag
                                Row(
                                    modifier = Modifier
                                        .background(categoryConfig.color.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = categoryConfig.icon,
                                        contentDescription = null,
                                        tint = categoryConfig.color,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = item.category,
                                        fontSize = 9.sp,
                                        color = categoryConfig.color,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (item.isImportant) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "重要",
                                        tint = Color(0xFFFFB74D),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            if (item.description.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.description,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Selection calculation
            val checkedCount = selectedItemIndices.values.count { it }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.clearAiGeneratedItems() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("重新输入")
                }
                Button(
                    onClick = {
                        val itemsToImport = aiGeneratedItems.filterIndexed { index, _ ->
                            selectedItemIndices[index] == true
                        }
                        viewModel.importAiTodoItems(itemsToImport)

                        // Trigger tool actions on import
                        val centerAction = viewModel.aiGeneratedActions.value.firstOrNull { it.type == "center_map" }
                        if (centerAction != null && centerAction.latitude != null && centerAction.longitude != null) {
                            onCenterMap(centerAction.latitude, centerAction.longitude)
                        }

                        onDismiss()
                    },
                    enabled = checkedCount > 0,
                    modifier = Modifier.weight(1.5f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("一键导入清单 ($checkedCount)", fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
            // Creative Studio Section
            // 1. Sub-tab Selection
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = creativeTab == 0,
                    onClick = { 
                        creativeTab = 0 
                        viewModel.clearAiMultimedia()
                    },
                    label = { Text("AI 绘图仪", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Palette, null, modifier = Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = creativeTab == 1,
                    onClick = { 
                        creativeTab = 1 
                        viewModel.clearAiMultimedia()
                    },
                    label = { Text("AI 音乐合成", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(16.dp)) }
                )
            }

            if (creativeTab == 0) {
                // AI IMAGE GENERATOR (GENERATIVE ART CANVAS)
                if (aiGeneratedArt == null && !aiLoading) {
                    Text(
                        text = "输入您的艺术灵感关键词，AI 将为您智能生成一幅动态抽象的数码艺术画作！",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = artPrompt,
                        onValueChange = { artPrompt = it },
                        label = { Text("输入绘图灵感") },
                        placeholder = { Text("例如：璀璨的星空、宁静的森林深处、未来的霓虹都市...") },
                        minLines = 2,
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Quick Recommendation chips
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("🌌 赛博朋克星河市集", "🍃 极简日系禅意枯山水", "🎠 梦幻童话旋转木马", "🏔️ 极光掠过雪山之巅").forEach { item ->
                            SuggestionChip(
                                onClick = { artPrompt = item.substring(2) },
                                label = { Text(item, fontSize = 11.sp) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.generateArtWithAi(artPrompt) },
                        enabled = artPrompt.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("开始 AI 创作绘图", fontWeight = FontWeight.Bold)
                    }
                } else if (aiLoading) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("🎨 Gemini 正在为您绘制专属抽象艺术画作...", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    }
                } else if (aiError != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(aiError ?: "生成出错", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.clearAiMultimedia() }) {
                            Text("返回重新输入")
                        }
                    }
                } else if (aiGeneratedArt != null) {
                    val art = aiGeneratedArt!!
                    // Render canvas with shapes!
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "🎨 艺术画作: ${art.style}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text(text = "灵感: \"${art.prompt}\"", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { viewModel.clearAiMultimedia() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "重试")
                            }
                        }

                        val infiniteTransition = rememberInfiniteTransition()
                        val pulseFactor by infiniteTransition.animateFloat(
                            initialValue = 0.92f,
                            targetValue = 1.08f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(2500, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            )
                        )

                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(RoundedCornerShape(16.dp))
                        ) {
                            val bgColor = try { Color(android.graphics.Color.parseColor(art.primaryColor)) } catch(e: Exception) { Color(0xFF1E1E2E) }
                            drawRect(bgColor)

                            art.shapes.forEach { shape ->
                                val color = try { Color(android.graphics.Color.parseColor(shape.color)) } catch(e: Exception) { Color.Cyan }
                                val sizePx = (shape.size / 100f * size.minDimension) * pulseFactor
                                val xPx = shape.x / 100f * size.width
                                val yPx = shape.y / 100f * size.height

                                rotate(shape.rotation * pulseFactor, pivot = androidx.compose.ui.geometry.Offset(xPx, yPx)) {
                                    when (shape.type) {
                                        "circle" -> drawCircle(color.copy(alpha = shape.alpha), radius = sizePx / 2, center = androidx.compose.ui.geometry.Offset(xPx, yPx))
                                        "rect" -> drawRect(color.copy(alpha = shape.alpha), topLeft = androidx.compose.ui.geometry.Offset(xPx - sizePx/2, yPx - sizePx/2), size = androidx.compose.ui.geometry.Size(sizePx, sizePx))
                                        "triangle" -> {
                                            val path = androidx.compose.ui.graphics.Path().apply {
                                                moveTo(xPx, yPx - sizePx/2)
                                                lineTo(xPx - sizePx/2, yPx + sizePx/2)
                                                lineTo(xPx + sizePx/2, yPx + sizePx/2)
                                                close()
                                            }
                                            drawPath(path, color.copy(alpha = shape.alpha))
                                        }
                                        else -> { // star or circle
                                            val path = androidx.compose.ui.graphics.Path().apply {
                                                for (i in 0..4) {
                                                    val angle = i * 2 * Math.PI / 5 - Math.PI / 2
                                                    val xOuter = xPx + sizePx/2 * Math.cos(angle).toFloat()
                                                    val yOuter = yPx + sizePx/2 * Math.sin(angle).toFloat()
                                                    if (i == 0) moveTo(xOuter, yOuter) else lineTo(xOuter, yOuter)
                                                    
                                                    val innerAngle = angle + Math.PI / 5
                                                    val xInner = xPx + sizePx/4 * Math.cos(innerAngle).toFloat()
                                                    val yInner = yPx + sizePx/4 * Math.sin(innerAngle).toFloat()
                                                    lineTo(xInner, yInner)
                                                }
                                                close()
                                            }
                                            drawPath(path, color.copy(alpha = shape.alpha))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // AI SONG COMPOSITOR & PLAYBACK
                if (aiGeneratedSong == null && !aiLoading) {
                    Text(
                        text = "输入音乐灵感或主题词，AI 将智能创作歌词、设计和弦、编配主旋律，并现场合成声音播放！",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = songPrompt,
                        onValueChange = { songPrompt = it },
                        label = { Text("输入音乐灵感") },
                        placeholder = { Text("例如：夜晚下的静谧思绪、奔向未来的旅程、夏日午后的清风...") },
                        minLines = 2,
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("🌌 星空下的舒缓民谣", "⚡ 动感的电子流行", "🎹 雨打窗台的极简古典", "🧘 晨雾森林的冥想环境音").forEach { item ->
                            SuggestionChip(
                                onClick = { songPrompt = item.substring(2) },
                                label = { Text(item, fontSize = 11.sp) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.generateSongWithAi(songPrompt) },
                        enabled = songPrompt.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("开始 AI 创作歌曲", fontWeight = FontWeight.Bold)
                    }
                } else if (aiLoading) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("🎵 Gemini 正在为您谱写原创词曲与编曲...", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    }
                } else if (aiError != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(aiError ?: "生成出错", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.clearAiMultimedia() }) {
                            Text("返回重新输入")
                        }
                    }
                } else if (aiGeneratedSong != null) {
                    val song = aiGeneratedSong!!
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "🎵 ${song.title}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text(text = "曲风: ${song.style} | 速度: ${song.tempoBpm} BPM", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(
                                onClick = { 
                                    AudioSynthPlayer.stopPlaying()
                                    isSongPlaying = false
                                    activeNoteIndex = -1
                                    viewModel.clearAiMultimedia() 
                                }
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "重新制作")
                            }
                        }

                        // Retro cassette or Record Visualizer
                        val infiniteTransition = rememberInfiniteTransition()
                        val rotationAngle by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = if (isSongPlaying) 3000 else 100000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            )
                        )

                        // Visual Disc Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Animated Disc
                                Box(
                                    modifier = Modifier
                                        .size(90.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1E1E2E))
                                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        .drawRotate(if (isSongPlaying) rotationAngle else 0f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Vinyl Grooves
                                    Box(modifier = Modifier.size(60.dp).border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape))
                                    Box(modifier = Modifier.size(40.dp).border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape))
                                    
                                    // Center Sticker
                                    Box(
                                        modifier = Modifier.size(24.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(modifier = Modifier.size(6.dp).background(Color.White, CircleShape))
                                    }
                                }

                                // Interactive scrollable or highlighted lyrics
                                val activeLineIndex = if (activeNoteIndex == -1 || song.notes.isEmpty()) -1 else {
                                    (activeNoteIndex * song.lyrics.size / song.notes.size).coerceIn(0, song.lyrics.size - 1)
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    song.lyrics.forEachIndexed { lineIdx, lyric ->
                                        val isActive = lineIdx == activeLineIndex
                                        Text(
                                            text = lyric,
                                            fontSize = if (isActive) 14.sp else 12.sp,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Controls Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (!isSongPlaying) {
                                Button(
                                    onClick = {
                                        AudioSynthPlayer.startPlaying(
                                            notes = song.notes,
                                            onNotePlayed = { idx -> activeNoteIndex = idx },
                                            onFinished = { 
                                                isSongPlaying = false
                                                activeNoteIndex = -1 
                                            }
                                        )
                                        isSongPlaying = true
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("播放原创合成音乐", fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        AudioSynthPlayer.stopPlaying()
                                        isSongPlaying = false
                                        activeNoteIndex = -1
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Stop, null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("停止播放", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        content = { content() }
    )
}
