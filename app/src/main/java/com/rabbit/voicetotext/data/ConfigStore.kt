package com.rabbit.voicetotext.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_config")

data class FunctionConfig(
    val id: String,
    val name: String,
    val description: String,
    val url: String,
    val payloadTemplate: String  // JSON template, use {{content}} as placeholder
)

class ConfigStore(private val context: Context) {

    companion object {
        private val LLM_API_KEY = stringPreferencesKey("llm_api_key")
        private val LLM_MODEL = stringPreferencesKey("llm_model")
        private val FUNCTIONS_JSON = stringPreferencesKey("functions_json")
    }

    private val gson = Gson()

    suspend fun getLlmApiKey(): String {
        return context.dataStore.data.map { it[LLM_API_KEY] ?: "" }.first()
    }

    suspend fun setLlmApiKey(key: String) {
        context.dataStore.edit { it[LLM_API_KEY] = key }
    }

    suspend fun getLlmModel(): String {
        return context.dataStore.data.map { it[LLM_MODEL] ?: "doubao-seed-1-6-lite-251015" }.first()
    }

    suspend fun setLlmModel(model: String) {
        context.dataStore.edit { it[LLM_MODEL] = model }
    }

    suspend fun getFunctions(): List<FunctionConfig> {
        val json = context.dataStore.data.map { it[FUNCTIONS_JSON] ?: "" }.first()
        if (json.isBlank()) return getDefaultFunctions()
        return try {
            gson.fromJson(json, Array<FunctionConfig>::class.java).toList()
        } catch (e: Exception) {
            getDefaultFunctions()
        }
    }

    suspend fun saveFunctions(functions: List<FunctionConfig>) {
        context.dataStore.edit { it[FUNCTIONS_JSON] = gson.toJson(functions) }
    }

    private fun getDefaultFunctions(): List<FunctionConfig> {
        return listOf(
            FunctionConfig(
                id = "feishu_todo",
                name = "记录待办",
                description = "发送到飞书创建待办事项",
                url = "",
                payloadTemplate = """{"msg_type":"text","content":{"text":"待办: {{content}}"}}"""
            ),
            FunctionConfig(
                id = "flash_note",
                name = "闪记",
                description = "快速记录，发送到指定URL",
                url = "",
                payloadTemplate = """{"content":"{{content}}","timestamp":"{{timestamp}}"}"""
            )
        )
    }
}
