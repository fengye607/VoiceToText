package com.rabbit.voicetotext.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.rabbit.voicetotext.audio.AudioRecorder
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class VolcanoAsrApi {

    companion object {
        private const val TAG = "VoiceToText"
        private const val WS_URL = "wss://openspeech.bytedance.com/api/v2/asr"
        private const val APP_ID = "8388733341"
        private const val TOKEN = "7Yn2IIs8R2dvFX_4RVEoe_1qkVlr9414"
        private const val CLUSTER = "volcengine_input_common"
        private const val CHUNK_MS = 200
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    fun recognize(wavBytes: ByteArray): String {
        Log.i(TAG, "recognize() called, wavBytes size=${wavBytes.size}")

        // Strip WAV header (44 bytes) to get raw PCM
        val pcmData = if (wavBytes.size > 44) wavBytes.copyOfRange(44, wavBytes.size) else wavBytes
        Log.i(TAG, "PCM data size=${pcmData.size}, ~${pcmData.size / (AudioRecorder.SAMPLE_RATE * 2)}s")

        val reqId = UUID.randomUUID().toString()
        val latch = CountDownLatch(1)
        var resultText = ""
        var errorMsg: String? = null

        val request = Request.Builder()
            .url(WS_URL)
            .addHeader("Authorization", "Bearer;$TOKEN")
            .build()

        val ws = client.newWebSocket(request, object : WebSocketListener() {
            private var configAcked = false

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected")

                // Send config (full client request)
                val config = buildConfig(reqId)
                val configJson = gson.toJson(config).toByteArray(Charsets.UTF_8)
                val configMsg = buildFullClientRequest(configJson)
                webSocket.send(configMsg.toByteString(0, configMsg.size))
                Log.d(TAG, "Sent config request, reqId=$reqId")
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val parsed = parseResponse(bytes.toByteArray())
                val code = parsed.code
                val text = parsed.result?.firstOrNull()?.text ?: ""

                if (!configAcked) {
                    configAcked = true
                    Log.i(TAG, "Config ack: code=$code, msg=${parsed.message}")

                    if (code != 1000) {
                        errorMsg = "ASR config error $code: ${parsed.message}"
                        latch.countDown()
                        webSocket.close(1000, "config error")
                        return
                    }

                    // Start sending audio chunks
                    Thread {
                        sendAudioChunks(webSocket, pcmData)
                    }.start()
                    return
                }

                if (text.isNotEmpty()) {
                    resultText = text
                    Log.d(TAG, "Partial result: '$text'")
                }

                // Check if this is the final response (sequence < 0)
                if (parsed.sequence < 0) {
                    Log.i(TAG, "Final result: '$resultText', code=$code")
                    latch.countDown()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.w(TAG, "Unexpected text message: $text")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                errorMsg = "WebSocket error: ${t.message}"
                latch.countDown()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                latch.countDown()
            }
        })

        // Wait for result with timeout
        if (!latch.await(60, TimeUnit.SECONDS)) {
            ws.close(1000, "timeout")
            throw IOException("ASR recognition timed out")
        }

        if (errorMsg != null) {
            throw IOException(errorMsg)
        }

        Log.i(TAG, "ASR recognize success, text='$resultText'")
        return resultText
    }

    private fun sendAudioChunks(ws: WebSocket, pcmData: ByteArray) {
        val chunkSize = AudioRecorder.SAMPLE_RATE * 2 * CHUNK_MS / 1000  // bytes per chunk
        var offset = 0
        var seq = 2

        while (offset < pcmData.size) {
            val end = minOf(offset + chunkSize, pcmData.size)
            val chunk = pcmData.copyOfRange(offset, end)
            val isLast = (end >= pcmData.size)

            val msg = buildAudioRequest(chunk, isLast)
            ws.send(msg.toByteString(0, msg.size))

            Log.d(TAG, "Sent audio chunk seq=$seq, size=${chunk.size}, last=$isLast")
            offset = end
            seq++
        }
    }

    private fun buildConfig(reqId: String): Map<String, Any> {
        return mapOf(
            "app" to mapOf(
                "appid" to APP_ID,
                "token" to TOKEN,
                "cluster" to CLUSTER
            ),
            "user" to mapOf(
                "uid" to "android_user"
            ),
            "audio" to mapOf(
                "format" to "raw",
                "rate" to AudioRecorder.SAMPLE_RATE,
                "bits" to 16,
                "channel" to 1,
                "language" to "zh-CN",
                "codec" to "raw"
            ),
            "request" to mapOf(
                "reqid" to reqId,
                "workflow" to "audio_in,resample,partition,vad,fe,decode,itn,nlu_punctuate",
                "sequence" to 1,
                "result_type" to "full"
            )
        )
    }

    /**
     * Build binary full client request:
     * Header(4 bytes) + PayloadSize(4 bytes, big-endian) + GzipPayload
     *
     * Byte 0: version=1 (bits 7-4), header_size=1 (bits 3-0) -> 0x11
     * Byte 1: msg_type=full_client_request=0b0001 (bits 7-4), flags=0b0000 -> 0x10
     * Byte 2: serialization=json=0b0001 (bits 7-4), compression=gzip=0b0001 -> 0x11
     * Byte 3: reserved -> 0x00
     */
    private fun buildFullClientRequest(jsonPayload: ByteArray): ByteArray {
        val compressed = gzipCompress(jsonPayload)
        val buf = ByteBuffer.allocate(4 + 4 + compressed.size)
        buf.order(ByteOrder.BIG_ENDIAN)
        buf.put(0x11.toByte())
        buf.put(0x10.toByte())
        buf.put(0x11.toByte())
        buf.put(0x00.toByte())
        buf.putInt(compressed.size)
        buf.put(compressed)
        return buf.array()
    }

    /**
     * Build binary audio-only request:
     * Header(4 bytes) + PayloadSize(4 bytes, big-endian) + GzipAudio
     *
     * Byte 0: 0x11
     * Byte 1: msg_type=audio_only=0b0010 (bits 7-4), flags: 0b0010 if last else 0b0000
     * Byte 2: serialization=none=0b0000, compression=gzip=0b0001 -> 0x01
     * Byte 3: 0x00
     */
    private fun buildAudioRequest(audioBytes: ByteArray, isLast: Boolean): ByteArray {
        val flags = if (isLast) 0b0010 else 0b0000
        val msgTypeByte = ((0b0010 shl 4) or flags).toByte()
        val compressed = gzipCompress(audioBytes)
        val buf = ByteBuffer.allocate(4 + 4 + compressed.size)
        buf.order(ByteOrder.BIG_ENDIAN)
        buf.put(0x11.toByte())
        buf.put(msgTypeByte)
        buf.put(0x01.toByte())
        buf.put(0x00.toByte())
        buf.putInt(compressed.size)
        buf.put(compressed)
        return buf.array()
    }

    private fun parseResponse(data: ByteArray): AsrWsResponse {
        if (data.size < 8) return AsrWsResponse(code = -1, message = "response too short")

        val headerSize = (data[0].toInt() and 0x0F) * 4
        val compression = data[2].toInt() and 0x0F

        if (data.size < headerSize + 4) return AsrWsResponse(code = -1, message = "incomplete response")

        val payloadSize = ByteBuffer.wrap(data, headerSize, 4).order(ByteOrder.BIG_ENDIAN).int
        var payload = data.copyOfRange(headerSize + 4, headerSize + 4 + payloadSize)

        if (compression == 1) {
            payload = gzipDecompress(payload)
        }

        return try {
            gson.fromJson(String(payload, Charsets.UTF_8), AsrWsResponse::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse response: ${String(payload, Charsets.UTF_8)}", e)
            AsrWsResponse(code = -1, message = "parse error: ${e.message}")
        }
    }

    private fun gzipCompress(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    private fun gzipDecompress(data: ByteArray): ByteArray {
        return GZIPInputStream(data.inputStream()).use { it.readBytes() }
    }
}

data class AsrWsResult(
    val text: String = "",
    val confidence: Int = 0
)

data class AsrWsResponse(
    val code: Int = -1,
    val message: String = "",
    val result: List<AsrWsResult>? = null,
    val reqid: String = "",
    val sequence: Int = 0
)
