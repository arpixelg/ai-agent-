package com.example.data

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val actionTag: String? = null,
    val actionSummary: String? = null,
    val generatedCode: String? = null,
    val codeLanguage: String? = "html",
    val imageUrl: String? = null
)

enum class AgentState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    ERROR
}

data class DeviceTelemetry(
    val batteryLevel: Int = 100,
    val isCharging: Boolean = false,
    val batteryTempCelsius: Float = 28.5f,
    val totalRamMb: Long = 8192,
    val freeRamMb: Long = 4096,
    val totalStorageGb: Long = 128,
    val freeStorageGb: Long = 64,
    val isTorchOn: Boolean = false,
    val currentVolume: Int = 8,
    val maxVolume: Int = 15,
    val androidVersion: String = "15 (API 35)",
    val deviceModel: String = "Android Device",
    val isNetworkConnected: Boolean = true
)

data class MarketAsset(
    val symbol: String,
    val name: String,
    val priceUsd: Double,
    val change24h: Double,
    val category: String,
    val high24h: Double,
    val low24h: Double
)

data class CodeProject(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val htmlCode: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class AgentExecutionResult(
    val actionName: String,
    val isSuccess: Boolean,
    val message: String,
    val generatedCode: String? = null
)
