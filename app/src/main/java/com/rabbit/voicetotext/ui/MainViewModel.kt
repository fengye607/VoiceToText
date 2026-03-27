package com.rabbit.voicetotext.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rabbit.voicetotext.audio.AudioRecorder
import com.rabbit.voicetotext.data.RecognitionResult
import com.rabbit.voicetotext.network.VolcanoAsrApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class UiState(
    val isRecording: Boolean = false,
    val isProcessing: Boolean = false,
    val history: List<RecognitionResult> = emptyList(),
    val error: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "VoiceToText"
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    private val audioRecorder = AudioRecorder()
    private val asrApi = VolcanoAsrApi()

    fun startRecording() {
        Log.i(TAG, "startRecording() - user tapped record button")
        _uiState.value = _uiState.value.copy(isRecording = true, error = null)
        try {
            audioRecorder.startRecording()
            Log.i(TAG, "Recording started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}", e)
            _uiState.value = _uiState.value.copy(isRecording = false, error = "录音启动失败: ${e.message}")
        }
    }

    fun stopRecording() {
        Log.i(TAG, "stopRecording() - user tapped stop button")
        _uiState.value = _uiState.value.copy(isRecording = false, isProcessing = true)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Stopping audio recorder...")
                val wavData = audioRecorder.stopRecording()
                Log.i(TAG, "Audio recorded, wavData size=${wavData.size} bytes")

                Log.d(TAG, "Sending to ASR API...")
                val text = asrApi.recognize(wavData)
                Log.i(TAG, "ASR returned text='$text'")

                if (text.isNotBlank()) {
                    val result = RecognitionResult(text = text)
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        history = listOf(result) + _uiState.value.history
                    )
                    Log.i(TAG, "Recognition result added to history")
                } else {
                    Log.w(TAG, "ASR returned empty text")
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        error = "未识别到语音内容"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "recognize failed", e)
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    error = "识别失败: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        Log.d(TAG, "clearError()")
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun deleteResult(index: Int) {
        Log.d(TAG, "deleteResult(index=$index)")
        val newHistory = _uiState.value.history.toMutableList()
        if (index in newHistory.indices) {
            newHistory.removeAt(index)
            _uiState.value = _uiState.value.copy(history = newHistory)
            Log.i(TAG, "Deleted result at index=$index, remaining=${newHistory.size}")
        }
    }
}
