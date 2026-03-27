package com.rabbit.voicetotext.ui

import android.app.Application
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

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    private val audioRecorder = AudioRecorder()
    private val asrApi = VolcanoAsrApi()

    fun startRecording() {
        _uiState.value = _uiState.value.copy(isRecording = true, error = null)
        audioRecorder.startRecording()
    }

    fun stopRecording() {
        _uiState.value = _uiState.value.copy(isRecording = false, isProcessing = true)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val wavData = audioRecorder.stopRecording()
                val text = asrApi.recognize(wavData)

                if (text.isNotBlank()) {
                    val result = RecognitionResult(text = text)
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        history = listOf(result) + _uiState.value.history
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        error = "未识别到语音内容"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    error = "识别失败: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun deleteResult(index: Int) {
        val newHistory = _uiState.value.history.toMutableList()
        if (index in newHistory.indices) {
            newHistory.removeAt(index)
            _uiState.value = _uiState.value.copy(history = newHistory)
        }
    }
}
