// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.agents.pokeclaw.ClawApplication
import io.agents.pokeclaw.agent.CloudProvider
import io.agents.pokeclaw.agent.llm.ModelConfigRepository
import io.agents.pokeclaw.utils.KVUtils

class LlmConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF4D64A1),
                    secondary = Color(0xFF6F5578),
                    background = Color(0xFFF9F8FF),
                    surface = Color(0xFFF9F8FF),
                    surfaceVariant = Color(0xFFE9ECFA),
                    onBackground = Color(0xFF20212A),
                    onSurface = Color(0xFF20212A),
                )
            ) {
                CloudModelSettings(onBack = ::finish, onSaved = ::saveAndFinish)
            }
        }
    }

    private fun saveAndFinish(provider: CloudProvider, model: String, baseUrl: String, apiKey: String) {
        if (apiKey.isBlank() || model.isBlank() || baseUrl.isBlank()) {
            Toast.makeText(this, "请完整填写模型、API Key 和请求地址", Toast.LENGTH_SHORT).show()
            return
        }
        ModelConfigRepository.saveCloudDefault(
            providerName = provider.name,
            modelId = model.trim(),
            baseUrl = baseUrl.trim().trimEnd('/'),
            apiKey = apiKey.trim(),
            activateNow = true,
        )
        ClawApplication.appViewModelInstance.updateAgentConfig()
        ClawApplication.appViewModelInstance.initAgent()
        ClawApplication.appViewModelInstance.afterInit()
        Toast.makeText(this, "已启用 ${provider.displayName} · ${model.trim()}", Toast.LENGTH_SHORT).show()
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CloudModelSettings(
    onBack: () -> Unit,
    onSaved: (CloudProvider, String, String, String) -> Unit,
) {
    val initial = remember { ModelConfigRepository.snapshot().activeCloud }
    var provider by remember { mutableStateOf(initial.provider) }
    var model by remember { mutableStateOf(initial.modelName.ifBlank { initial.provider.models.firstOrNull()?.id.orEmpty() }) }
    var baseUrl by remember { mutableStateOf(initial.resolvedBaseUrl.ifBlank { initial.provider.defaultBaseUrl }) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var showApiKey by remember { mutableStateOf(false) }

    fun selectProvider(next: CloudProvider) {
        provider = next
        model = next.models.firstOrNull { it.recommended }?.id ?: next.models.firstOrNull()?.id.orEmpty()
        baseUrl = next.defaultBaseUrl
        apiKey = KVUtils.getApiKeyForProvider(next.name)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("云端模型", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { apiKey = "" }) { Text("清除密钥") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Button(
                onClick = { onSaved(provider, model, baseUrl, apiKey) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).imePadding(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4D64A1)),
            ) {
                Text("保存并启用", modifier = Modifier.padding(vertical = 5.dp))
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text("模型服务", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CloudProvider.entries) { item ->
                        FilterChip(
                            selected = provider == item,
                            onClick = { selectProvider(item) },
                            label = { Text(item.displayName) },
                        )
                    }
                }
            }

            if (provider.models.isNotEmpty()) {
                item { Text("模型", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(provider.models) { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { model = item.id }.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = model == item.id, onClick = { model = item.id })
                        Column(Modifier.padding(start = 6.dp)) {
                            Text(item.displayName, fontWeight = FontWeight.Medium)
                            Text(item.id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                item {
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("模型名称") },
                        placeholder = { Text("例如 deepseek-chat") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showApiKey) "隐藏密钥" else "显示密钥",
                            )
                        }
                    },
                )
            }

            item {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("请求地址 / 中转 URL") },
                    supportingText = {
                        Text(if (provider == CloudProvider.ANTHROPIC) "Claude 中转请选择 Anthropic 协议" else "兼容 OpenAI Responses/Chat Completions 的中转可直接填写")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }

            item {
                Text(
                    "手机控制需在系统设置中启用龙虾无障碍服务。API Key 仅保存在本机。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 84.dp),
                )
            }
        }
    }
}
