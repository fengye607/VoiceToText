package com.rabbit.voicetotext.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rabbit.voicetotext.data.ConfigStore
import com.rabbit.voicetotext.data.FunctionConfig
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    configStore: ConfigStore,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var llmApiKey by remember { mutableStateOf("") }
    var llmModel by remember { mutableStateOf("") }
    var functions by remember { mutableStateOf(listOf<FunctionConfig>()) }

    LaunchedEffect(Unit) {
        llmApiKey = configStore.getLlmApiKey()
        llmModel = configStore.getLlmModel()
        functions = configStore.getFunctions()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        scope.launch {
                            configStore.setLlmApiKey(llmApiKey)
                            configStore.setLlmModel(llmModel)
                            configStore.saveFunctions(functions)
                            Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text("保存")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                functions = functions + FunctionConfig(
                    id = "func_${System.currentTimeMillis()}",
                    name = "",
                    description = "",
                    url = "",
                    payloadTemplate = """{"content":"{{content}}","timestamp":"{{timestamp}}"}"""
                )
            }) {
                Icon(Icons.Filled.Add, contentDescription = "添加功能")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("豆包 LLM 配置", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = llmApiKey,
                onValueChange = { llmApiKey = it },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = llmModel,
                onValueChange = { llmModel = it },
                label = { Text("模型名称 (如 doubao-seed-2-0-mini-260215)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text("功能配置", style = MaterialTheme.typography.titleMedium)
            Text(
                "模板变量: {{content}} = 语音内容, {{timestamp}} = 时间",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            functions.forEachIndexed { index, func ->
                FunctionConfigCard(
                    config = func,
                    onUpdate = { updated ->
                        functions = functions.toMutableList().apply { set(index, updated) }
                    },
                    onDelete = {
                        functions = functions.toMutableList().apply { removeAt(index) }
                    }
                )
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun FunctionConfigCard(
    config: FunctionConfig,
    onUpdate: (FunctionConfig) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    config.name.ifBlank { "新功能" },
                    style = MaterialTheme.typography.titleSmall
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            OutlinedTextField(
                value = config.id,
                onValueChange = { onUpdate(config.copy(id = it)) },
                label = { Text("功能ID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = config.name,
                onValueChange = { onUpdate(config.copy(name = it)) },
                label = { Text("功能名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = config.description,
                onValueChange = { onUpdate(config.copy(description = it)) },
                label = { Text("功能描述 (用于LLM理解)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = config.url,
                onValueChange = { onUpdate(config.copy(url = it)) },
                label = { Text("POST URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = config.payloadTemplate,
                onValueChange = { onUpdate(config.copy(payloadTemplate = it)) },
                label = { Text("Payload 模板 (JSON)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5
            )
        }
    }
}
