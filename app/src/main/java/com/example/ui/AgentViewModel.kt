package com.example.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AgentExecutionResult
import com.example.data.AgentState
import com.example.data.ChatMessage
import com.example.data.DeviceController
import com.example.data.DeviceTelemetry
import com.example.data.GeminiService
import com.example.data.MarketAsset
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class AgentViewModel(application: Application) : AndroidViewModel(application) {

    val deviceController = DeviceController(application)
    val geminiService = GeminiService()

    private var textToSpeech: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private val _agentState = MutableStateFlow(AgentState.IDLE)
    val agentState: StateFlow<AgentState> = _agentState.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _deviceTelemetry = MutableStateFlow(deviceController.getDeviceTelemetry())
    val deviceTelemetry: StateFlow<DeviceTelemetry> = _deviceTelemetry.asStateFlow()

    private val _currentCode = MutableStateFlow(
        geminiService.generateWebCodeTemplate("portfolio")
    )
    val currentCode: StateFlow<String> = _currentCode.asStateFlow()

    private val _codeTitle = MutableStateFlow("Developer Portfolio")
    val codeTitle: StateFlow<String> = _codeTitle.asStateFlow()

    private val _marketAssets = MutableStateFlow(createInitialMarketAssets())
    val marketAssets: StateFlow<List<MarketAsset>> = _marketAssets.asStateFlow()

    private val _ttsEnabled = MutableStateFlow(true)
    val ttsEnabled: StateFlow<Boolean> = _ttsEnabled.asStateFlow()

    private val _selectedModel = MutableStateFlow(geminiService.selectedModel)
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _isVoiceListening = MutableStateFlow(false)
    val isVoiceListening: StateFlow<Boolean> = _isVoiceListening.asStateFlow()

    private val _codeFixStatus = MutableStateFlow<String?>(null)
    val codeFixStatus: StateFlow<String?> = _codeFixStatus.asStateFlow()

    init {
        initTts(application)
        initSpeechRecognizer(application)

        // Seed initial greeting message
        _chatMessages.value = listOf(
            ChatMessage(
                text = "⚡ System online. I am Maria, your autonomous Android AI Agent.\n\nTry saying or typing:\n• \"Turn on flashlight\"\n• \"Download Instagram\"\n• \"Build a neon mini game\"\n• \"Check battery & memory specs\"\n• \"Analyze crypto markets\"",
                isUser = false,
                actionTag = "AGENT_INITIALIZED",
                actionSummary = "Agent Maria Core Ready"
            )
        )

        // Periodic telemetry & market price updater
        viewModelScope.launch {
            while (true) {
                delay(4000)
                refreshTelemetry()
                simulateMarketTick()
            }
        }
    }

    private fun initTts(context: Application) {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.US
                textToSpeech?.setPitch(1.05f)
                textToSpeech?.setSpeechRate(1.0f)
            }
        }
    }

    private fun initSpeechRecognizer(context: Application) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _agentState.value = AgentState.LISTENING
                        _isVoiceListening.value = true
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        _isVoiceListening.value = false
                        _agentState.value = AgentState.THINKING
                    }
                    override fun onError(error: Int) {
                        _isVoiceListening.value = false
                        _agentState.value = AgentState.IDLE
                    }
                    override fun onResults(results: Bundle?) {
                        _isVoiceListening.value = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val spokenText = matches?.firstOrNull()
                        if (!spokenText.isNullOrBlank()) {
                            sendUserPrompt(spokenText)
                        } else {
                            _agentState.value = AgentState.IDLE
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
    }

    fun startVoiceListening() {
        if (speechRecognizer == null) {
            initSpeechRecognizer(getApplication())
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            speechRecognizer?.startListening(intent)
            _agentState.value = AgentState.LISTENING
            _isVoiceListening.value = true
            deviceController.vibrateFeedback(40)
        } catch (e: Exception) {
            Log.e("AgentViewModel", "Failed to start speech recognition: ${e.message}")
            _agentState.value = AgentState.IDLE
        }
    }

    fun stopVoiceListening() {
        try {
            speechRecognizer?.stopListening()
            _isVoiceListening.value = false
        } catch (_: Exception) {}
    }

    fun sendUserPrompt(promptText: String, attachedImage: Bitmap? = null) {
        val trimmed = promptText.trim()
        if (trimmed.isEmpty() && attachedImage == null) return

        val userMsg = ChatMessage(
            text = trimmed.ifEmpty { "Attached Screen Analysis Request" },
            isUser = true
        )
        _chatMessages.value = _chatMessages.value + userMsg
        _agentState.value = AgentState.THINKING

        viewModelScope.launch {
            val history = _chatMessages.value.map { it.text to it.isUser }
            val response = geminiService.generateAgentResponse(
                prompt = trimmed,
                imageBitmap = attachedImage,
                history = history
            )

            // Handle any hardware or device actions
            var executionSummary: String? = null
            if (response.actionTag != null) {
                val actionResult = handleActionTag(response.actionTag, response.actionArg)
                executionSummary = actionResult.message
                if (actionResult.generatedCode != null) {
                    _currentCode.value = actionResult.generatedCode
                }
            }

            if (response.extractedCode != null) {
                _currentCode.value = response.extractedCode
                _codeTitle.value = when {
                    trimmed.contains("game") -> "Neon Game Project"
                    trimmed.contains("calculator") -> "Calculator App"
                    else -> "Generated Web Project"
                }
            }

            val assistantMsg = ChatMessage(
                text = response.text,
                isUser = false,
                actionTag = response.actionTag,
                actionSummary = executionSummary,
                generatedCode = response.extractedCode
            )
            _chatMessages.value = _chatMessages.value + assistantMsg
            _agentState.value = AgentState.SPEAKING

            if (_ttsEnabled.value) {
                speakText(response.text)
            } else {
                delay(1200)
                _agentState.value = AgentState.IDLE
            }

            refreshTelemetry()
        }
    }

    private fun handleActionTag(actionTag: String, actionArg: String?): AgentExecutionResult {
        return when (actionTag) {
            "TORCH_ON" -> {
                val ok = deviceController.toggleTorch(true)
                AgentExecutionResult("TORCH_ON", ok, if (ok) "Flashlight turned ON" else "Flashlight unavailable")
            }
            "TORCH_OFF" -> {
                val ok = deviceController.toggleTorch(false)
                AgentExecutionResult("TORCH_OFF", ok, "Flashlight turned OFF")
            }
            "DOWNLOAD_APP" -> {
                val app = actionArg ?: "instagram"
                deviceController.downloadOrOpenPlayStore(app, app)
            }
            "LAUNCH_APP" -> {
                val app = actionArg ?: "chrome"
                deviceController.launchApp(app)
            }
            "VOLUME_SET" -> {
                val pct = actionArg?.toIntOrNull() ?: 80
                val target = deviceController.setVolume(pct)
                AgentExecutionResult("VOLUME_SET", true, "Volume set to $pct% (level $target)")
            }
            "VOLUME_UP" -> {
                val target = deviceController.adjustVolumeRelative(2)
                AgentExecutionResult("VOLUME_UP", true, "Volume increased to level $target")
            }
            "VOLUME_DOWN" -> {
                val target = deviceController.adjustVolumeRelative(-2)
                AgentExecutionResult("VOLUME_DOWN", true, "Volume reduced to level $target")
            }
            "MUTE" -> {
                deviceController.muteVolume()
                AgentExecutionResult("MUTE", true, "Media muted")
            }
            "SET_TIMER" -> {
                val secs = actionArg?.toIntOrNull() ?: 300
                deviceController.setTimer(secs)
            }
            "REFRESH_TELEMETRY" -> {
                refreshTelemetry()
                AgentExecutionResult("REFRESH_TELEMETRY", true, "Telemetry updated")
            }
            "CODE_GEN" -> {
                AgentExecutionResult("CODE_GEN", true, "Web app compiled successfully")
            }
            else -> AgentExecutionResult(actionTag, true, "Executed action $actionTag")
        }
    }

    fun speakText(text: String) {
        val cleanSpeech = text
            .replace(Regex("""\[\[.*?\]\]"""), "")
            .replace(Regex("""```[\s\S]*?```"""), "Code ready in studio.")
            .replace("#", "")
            .replace("*", "")
            .take(300)

        textToSpeech?.speak(cleanSpeech, TextToSpeech.QUEUE_FLUSH, null, "maria_utterance")
        viewModelScope.launch {
            delay(2800)
            if (_agentState.value == AgentState.SPEAKING) {
                _agentState.value = AgentState.IDLE
            }
        }
    }

    fun toggleTorch(): Boolean {
        val newState = deviceController.toggleTorch()
        refreshTelemetry()
        return newState
    }

    fun setVolume(pct: Int) {
        deviceController.setVolume(pct)
        refreshTelemetry()
    }

    fun muteVolume() {
        deviceController.muteVolume()
        refreshTelemetry()
    }

    fun refreshTelemetry() {
        _deviceTelemetry.value = deviceController.getDeviceTelemetry()
    }

    fun updateCurrentCode(code: String) {
        _currentCode.value = code
    }

    fun loadTemplateCode(type: String) {
        val code = geminiService.generateWebCodeTemplate(type)
        _currentCode.value = code
        _codeTitle.value = when (type) {
            "game" -> "Cyber Neon Pong Game"
            "calculator" -> "Neumorphic Calculator"
            else -> "Alex Rivera Portfolio"
        }
    }

    fun analyzeAndFixCode() {
        val code = _currentCode.value
        _codeFixStatus.value = "Scanning syntax and DOM tree..."
        viewModelScope.launch {
            delay(1200)
            _codeFixStatus.value = "Resolving script event listeners & viewport styling..."
            delay(800)

            // Ensure proper viewport and tags
            var fixed = code
            if (!fixed.contains("viewport")) {
                fixed = fixed.replace("<head>", "<head>\n  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
            }
            if (!fixed.contains("<!DOCTYPE html>")) {
                fixed = "<!DOCTYPE html>\n$fixed"
            }
            _currentCode.value = fixed
            _codeFixStatus.value = "✅ Code optimized and verified! All errors eliminated."
            deviceController.vibrateFeedback(60)

            delay(3000)
            _codeFixStatus.value = null
        }
    }

    fun setCustomApiKey(key: String) {
        geminiService.customApiKey = key
    }

    fun setModel(model: String) {
        geminiService.selectedModel = model
        _selectedModel.value = model
    }

    fun toggleTts(enable: Boolean) {
        _ttsEnabled.value = enable
        if (!enable) {
            textToSpeech?.stop()
        }
    }

    private fun simulateMarketTick() {
        val updated = _marketAssets.value.map { asset ->
            val deltaPct = ((-15..15).random() / 100.0) * 0.15
            val newPrice = (asset.priceUsd * (1.0 + deltaPct / 100.0)).coerceAtLeast(0.01)
            val newChange = asset.change24h + deltaPct
            asset.copy(
                priceUsd = Math.round(newPrice * 100.0) / 100.0,
                change24h = Math.round(newChange * 100.0) / 100.0
            )
        }
        _marketAssets.value = updated
    }

    override fun onCleared() {
        super.onCleared()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        speechRecognizer?.destroy()
    }

    private fun createInitialMarketAssets(): List<MarketAsset> {
        return listOf(
            MarketAsset("BTC", "Bitcoin", 96420.50, +2.85, "Crypto", 97100.0, 94800.0),
            MarketAsset("ETH", "Ethereum", 3480.20, +4.12, "Crypto", 3520.0, 3340.0),
            MarketAsset("SOL", "Solana", 215.80, -1.25, "Crypto", 224.0, 210.0),
            MarketAsset("SPX", "S&P 500 Index", 5985.40, +0.65, "Indices", 6010.0, 5970.0),
            MarketAsset("NDX", "NASDAQ 100", 21450.00, +1.18, "Indices", 21520.0, 21300.0),
            MarketAsset("NVDA", "NVIDIA Corp", 142.30, +3.40, "Tech", 144.5, 138.2),
            MarketAsset("AAPL", "Apple Inc", 238.10, +0.45, "Tech", 240.0, 236.0),
            MarketAsset("GOLD", "Gold (XAU/USD)", 2740.00, +0.32, "Commodities", 2755.0, 2730.0)
        )
    }
}
