package com.rabbit.voicetotext.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.ByteArrayOutputStream

class AudioRecorder {

    companion object {
        private const val TAG = "VoiceToText"
        const val SAMPLE_RATE = 16000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null
    private var pcmData = ByteArrayOutputStream()

    fun startRecording() {
        Log.i(TAG, "startRecording() called")
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        Log.d(TAG, "AudioRecord minBufferSize=$bufferSize, sampleRate=$SAMPLE_RATE")

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL,
            ENCODING,
            bufferSize
        )
        Log.d(TAG, "AudioRecord created, state=${audioRecord?.state}")

        pcmData.reset()
        isRecording = true
        audioRecord?.startRecording()
        Log.i(TAG, "AudioRecord started recording")

        recordingThread = Thread {
            val buffer = ByteArray(bufferSize)
            var totalRead = 0
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    pcmData.write(buffer, 0, read)
                    totalRead += read
                } else if (read < 0) {
                    Log.w(TAG, "AudioRecord.read() returned error: $read")
                }
            }
            Log.d(TAG, "Recording thread finished, totalBytesRead=$totalRead")
        }.apply { start() }
        Log.d(TAG, "Recording thread started")
    }

    fun stopRecording(): ByteArray {
        Log.i(TAG, "stopRecording() called")
        isRecording = false
        recordingThread?.join(1000)
        Log.d(TAG, "Recording thread joined")

        audioRecord?.stop()
        Log.d(TAG, "AudioRecord stopped")
        audioRecord?.release()
        audioRecord = null
        Log.d(TAG, "AudioRecord released")

        val pcmBytes = pcmData.toByteArray()
        Log.i(TAG, "PCM data size=${pcmBytes.size} bytes, duration=${pcmBytes.size / (SAMPLE_RATE * 2)}s")

        val wavBytes = pcmToWav(pcmBytes)
        Log.i(TAG, "WAV data size=${wavBytes.size} bytes")
        return wavBytes
    }

    private fun pcmToWav(pcmBytes: ByteArray): ByteArray {
        val totalDataLen = pcmBytes.size + 36
        val channels = 1
        val bitsPerSample = 16
        val byteRate = SAMPLE_RATE * channels * bitsPerSample / 8

        val header = ByteArray(44)
        // RIFF header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        writeInt(header, 4, totalDataLen)
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        // fmt chunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        writeInt(header, 16, 16) // chunk size
        writeShort(header, 20, 1) // PCM format
        writeShort(header, 22, channels)
        writeInt(header, 24, SAMPLE_RATE)
        writeInt(header, 28, byteRate)
        writeShort(header, 32, channels * bitsPerSample / 8)
        writeShort(header, 34, bitsPerSample)
        // data chunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        writeInt(header, 40, pcmBytes.size)

        return header + pcmBytes
    }

    private fun writeInt(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value and 0xFF).toByte()
        data[offset + 1] = (value shr 8 and 0xFF).toByte()
        data[offset + 2] = (value shr 16 and 0xFF).toByte()
        data[offset + 3] = (value shr 24 and 0xFF).toByte()
    }

    private fun writeShort(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value and 0xFF).toByte()
        data[offset + 1] = (value shr 8 and 0xFF).toByte()
    }
}
