package com.rabbit.voicetotext.function

import android.util.Log
import com.rabbit.voicetotext.data.FunctionConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ExecutionResult(
    val success: Boolean,
    val message: String
)

class FunctionExecutor {

    companion object {
        private const val TAG = "VoiceToText"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun execute(functionConfig: FunctionConfig, content: String): ExecutionResult {
        Log.i(TAG, "execute() function=${functionConfig.id}, content='$content'")

        if (functionConfig.url.isBlank()) {
            return ExecutionResult(false, "未配置URL，请在设置中配置")
        }

        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val body = functionConfig.payloadTemplate
            .replace("{{content}}", content)
            .replace("{{timestamp}}", now)

        Log.d(TAG, "POST ${functionConfig.url}, body=$body")

        return try {
            val request = Request.Builder()
                .url(functionConfig.url)
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                Log.i(TAG, "Execute success: ${response.code}")
                ExecutionResult(true, "执行成功")
            } else {
                Log.w(TAG, "Execute failed: ${response.code} $responseBody")
                ExecutionResult(false, "执行失败: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Execute error", e)
            ExecutionResult(false, "执行出错: ${e.message}")
        }
    }
}
