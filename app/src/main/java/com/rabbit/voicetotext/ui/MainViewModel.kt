package com.rabbit.voicetotext.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rabbit.voicetotext.audio.AudioRecorder
import com.rabbit.voicetotext.data.ConfigStore
import com.rabbit.voicetotext.data.FunctionConfig
import com.rabbit.voicetotext.data.db.AppDatabase
import com.rabbit.voicetotext.data.db.ExecutionRecord
import com.rabbit.voicetotext.function.FunctionExecutor
import com.rabbit.voicetotext.network.DouBaoApi
import com.rabbit.voicetotext.network.FunctionMatch
import com.rabbit.voicetotext.network.VolcanoAsrApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class MatchedFunction(
    val config: FunctionConfig,
    val match: FunctionMatch
)

data class UiState(
    val isRecording: Boolean = false,
    val isProcessing: Boolean = false,
    val isMatching: Boolean = false,
    val isExecuting: Boolean = false,
    val recognizedText: String = "",
    val functionMatches: List<MatchedFunction> = emptyList(),
    val history: List<ExecutionRecord> = emptyList(),
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
    private val douBaoApi = DouBaoApi()
    private val functionExecutor = FunctionExecutor()
    val configStore = ConfigStore(application)
    private val db = AppDatabase.getInstance(application)

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val records = db.executionRecordDao().getAll()
            _uiState.value = _uiState.value.copy(history = records)
        }
    }

    fun startRecording() {
        Log.i(TAG, "startRecording()")
        _uiState.value = _uiState.value.copy(
            isRecording = true,
            error = null,
            functionMatches = emptyList(),
            recognizedText = ""
        )
        try {
            audioRecorder.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            _uiState.value = _uiState.value.copy(
                isRecording = false,
                error = "录音启动失败: ${e.message}"
            )
        }
    }

    fun stopRecording() {
        Log.i(TAG, "stopRecording()")
        _uiState.value = _uiState.value.copy(isRecording = false, isProcessing = true)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val wavData = audioRecorder.stopRecording()
                Log.i(TAG, "Audio recorded, size=${wavData.size}")

                val text = asrApi.recognize(wavData)
                Log.i(TAG, "ASR result: '$text'")

                if (text.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        error = "未识别到语音内容"
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    isMatching = true,
                    recognizedText = text
                )

                // Call LLM to match functions
                val apiKey = configStore.getLlmApiKey()
                val model = configStore.getLlmModel()

                if (apiKey.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        isMatching = false,
                        error = "请先在设置中配置豆包 API Key"
                    )
                    return@launch
                }

                val functions = configStore.getFunctions()
                val matches = douBaoApi.matchFunctions(text, functions, apiKey, model)

                val matchedFunctions = matches.mapNotNull { match ->
                    val config = functions.find { it.id == match.functionId }
                    config?.let { MatchedFunction(it, match) }
                }

                _uiState.value = _uiState.value.copy(
                    isMatching = false,
                    functionMatches = matchedFunctions
                )

                if (matchedFunctions.isEmpty()) {
                    _uiState.value = _uiState.value.copy(error = "未匹配到功能")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Processing failed", e)
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    isMatching = false,
                    error = "处理失败: ${e.message}"
                )
            }
        }
    }

    fun executeFunction(matchedFunction: MatchedFunction) {
        Log.i(TAG, "executeFunction: ${matchedFunction.config.id}")
        _uiState.value = _uiState.value.copy(isExecuting = true, functionMatches = emptyList())

        viewModelScope.launch(Dispatchers.IO) {
            val result = functionExecutor.execute(
                matchedFunction.config,
                matchedFunction.match.parsedContent
            )

            val record = ExecutionRecord(
                functionId = matchedFunction.config.id,
                functionName = matchedFunction.config.name,
                inputText = _uiState.value.recognizedText,
                parsedContent = matchedFunction.match.parsedContent,
                result = result.message,
                success = result.success
            )
            db.executionRecordDao().insert(record)

            val records = db.executionRecordDao().getAll()
            _uiState.value = _uiState.value.copy(
                isExecuting = false,
                history = records,
                error = if (!result.success) result.message else null
            )
        }
    }

    fun dismissMatches() {
        _uiState.value = _uiState.value.copy(functionMatches = emptyList())
    }

    fun deleteRecord(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            db.executionRecordDao().deleteById(id)
            val records = db.executionRecordDao().getAll()
            _uiState.value = _uiState.value.copy(history = records)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
