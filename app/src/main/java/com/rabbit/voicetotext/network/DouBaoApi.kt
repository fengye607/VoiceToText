package com.rabbit.voicetotext.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.rabbit.voicetotext.data.FunctionConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class FunctionMatch(
    val functionId: String,
    val confidence: Double,
    val parsedContent: String
)

class DouBaoApi {

    companion object {
        private const val TAG = "VoiceToText"
        private const val API_URL = "https://ark.cn-beijing.volces.com/api/v3/chat/completions"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    fun matchFunctions(
        inputText: String,
        functions: List<FunctionConfig>,
        apiKey: String,
        model: String
    ): List<FunctionMatch> {
        Log.i(TAG, "matchFunctions() input='$inputText', functions=${functions.size}")

        val functionList = functions.mapIndexed { i, f ->
            "${i + 1}. ${f.id} - ${f.name}: ${f.description}"
        }.joinToString("\n")

        val systemPrompt = """你是一个语音指令分类助手。用户会说一句话，你需要判断用户的意图最可能对应哪些功能。

可用功能列表:
$functionList

请返回最匹配的功能（最多3个），按匹配度从高到低排列。严格按以下JSON格式返回，不要有其他内容:
[
  {"function_id": "功能id", "confidence": 0.95, "parsed_content": "提取的核心内容"},
  {"function_id": "功能id", "confidence": 0.7, "parsed_content": "提取的核心内容"}
]

注意:
- confidence 是 0-1 之间的浮点数
- parsed_content 是从用户话语中提取出的与该功能相关的核心内容
- 如果没有任何功能匹配，返回空数组 []"""

        val requestBody = mapOf(
            "model" to model,
            "messages" to listOf(
                mapOf("role" to "system", "content" to systemPrompt),
                mapOf("role" to "user", "content" to inputText)
            ),
            "max_completion_tokens" to 256
        )

        val json = gson.toJson(requestBody)
        Log.d(TAG, "LLM request body: $json")

        val request = Request.Builder()
            .url(API_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("LLM API returned empty body")
        Log.d(TAG, "LLM response: $responseBody")

        if (!response.isSuccessful) {
            throw Exception("LLM API error ${response.code}: $responseBody")
        }

        // Parse chat completions response
        val chatResponse = gson.fromJson(responseBody, ChatCompletionResponse::class.java)
        val content = chatResponse.choices.firstOrNull()?.message?.content
            ?: throw Exception("LLM returned no content")

        Log.i(TAG, "LLM content: $content")

        // Extract JSON array from content (may have markdown fences)
        val jsonContent = content
            .replace("```json", "")
            .replace("```", "")
            .trim()

        return try {
            val matches = gson.fromJson(jsonContent, Array<LlmFunctionMatch>::class.java)
            matches.map { m ->
                FunctionMatch(
                    functionId = m.functionId,
                    confidence = m.confidence,
                    parsedContent = m.parsedContent
                )
            }.take(3)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse LLM response as matches: $jsonContent", e)
            emptyList()
        }
    }
}

private data class LlmFunctionMatch(
    @SerializedName("function_id") val functionId: String = "",
    val confidence: Double = 0.0,
    @SerializedName("parsed_content") val parsedContent: String = ""
)

private data class ChatCompletionResponse(
    val choices: List<Choice> = emptyList()
)

private data class Choice(
    val message: Message? = null
)

private data class Message(
    val content: String? = null
)
