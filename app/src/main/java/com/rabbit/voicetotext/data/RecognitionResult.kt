package com.rabbit.voicetotext.data

data class RecognitionResult(
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)
