package com.rabbit.voicetotext.network

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.rabbit.voicetotext.audio.AudioRecorder
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

class VolcanoAsrApi {

    companion object {
        private const val API_URL = "https://openspeech.bytedance.com/api/v1/asr"
        private const val APP_ID = "8388733341"
        private const val TOKEN = "7Yn2IIs8R2dvFX_4RVEoe_1qkVlr9414"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    fun recognize(wavBytes: ByteArray): String {
        val audioBase64 = Base64.encodeToString(wavBytes, Base64.NO_WRAP)

        val requestBody = AsrRequest(
            app = AppInfo(appid = APP_ID, token = TOKEN, cluster = "volcengine_input_common"),
            user = UserInfo(uid = UUID.randomUUID().toString()),
            audio = AudioInfo(
                format = "wav",
                codec = "raw",
                rate = AudioRecorder.SAMPLE_RATE,
                bits = 16,
                channel = 1
            ),
            request = RequestInfo(
                reqid = UUID.randomUUID().toString(),
                sequence = 1
            ),
            additions = mapOf("with_frontend_process" to "true")
        )

        val jsonBody = gson.toJson(requestBody)

        val mapType = object : com.google.gson.reflect.TypeToken<MutableMap<String, Any>>() {}.type
        val fullBody: MutableMap<String, Any> = gson.fromJson(jsonBody, mapType)
        fullBody["data"] = audioBase64

        val request = Request.Builder()
            .url(API_URL)
            .post(gson.toJson(fullBody).toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer; $TOKEN")
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty response")

        val result = gson.fromJson(responseBody, AsrResponse::class.java)
        if (result.code != 0) {
            throw Exception("ASR error ${result.code}: ${result.message}")
        }

        return result.result?.firstOrNull() ?: ""
    }
}

data class AppInfo(
    val appid: String,
    val token: String,
    val cluster: String
)

data class UserInfo(
    val uid: String
)

data class AudioInfo(
    val format: String,
    val codec: String,
    val rate: Int,
    val bits: Int,
    val channel: Int
)

data class RequestInfo(
    val reqid: String,
    val sequence: Int
)

data class AsrRequest(
    val app: AppInfo,
    val user: UserInfo,
    val audio: AudioInfo,
    val request: RequestInfo,
    val additions: Map<String, String>? = null
)

data class AsrResponse(
    val code: Int = -1,
    val message: String = "",
    val result: List<String>? = null,
    val reqid: String = ""
)
