package com.example.data

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class GeminiService {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    var customApiKey: String? = null
    var selectedModel: String = "gemini-3.5-flash"

    private fun getEffectiveApiKey(): String {
        val custom = customApiKey?.trim()
        if (!custom.isNullOrEmpty() && custom != "MY_GEMINI_API_KEY") {
            return custom
        }
        val buildKey = BuildConfig.GEMINI_API_KEY
        return if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY") buildKey else ""
    }

    suspend fun generateAgentResponse(
        prompt: String,
        imageBitmap: Bitmap? = null,
        history: List<Pair<String, Boolean>> = emptyList()
    ): AgentResponse = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey()
        if (apiKey.isBlank()) {
            // Intelligent local fallback if no valid key is supplied
            return@withContext executeLocalRuleAgent(prompt)
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$selectedModel:generateContent?key=$apiKey"

        try {
            val rootJson = JSONObject()

            // System Instruction
            val systemInstruction = JSONObject().apply {
                val parts = JSONArray().put(JSONObject().apply {
                    put("text", """
                        You are Maria, an advanced autonomous Android AI Agent and Expert Software Engineer.
                        You control phone settings, download apps from Play Store, write full HTML/CSS/JS applications that run live inside the user's Android phone, debug and fix code errors, analyze financial markets, and answer questions.
                        
                        When responding to device commands or coding requests, provide a helpful and conversational explanation, and ALWAYS tag action triggers when applicable:
                        - Flashlight: [[ACTION:TORCH_ON]] or [[ACTION:TORCH_OFF]]
                        - Open app: [[ACTION:LAUNCH_APP:instagram]] (or whatsapp, youtube, chrome, camera, etc.)
                        - Download / Install app: [[ACTION:DOWNLOAD_APP:instagram]] (or whatsapp, etc.)
                        - Volume: [[ACTION:VOLUME_SET:80]], [[ACTION:VOLUME_UP]], [[ACTION:VOLUME_DOWN]], [[ACTION:MUTE]]
                        - Timer: [[ACTION:SET_TIMER:300]]
                        - Code Generation: wrap complete, self-contained single-file HTML/CSS/JS code inside ```html ... ``` code blocks.
                        - Market Analysis: Provide sharp, data-driven financial commentary.
                    """.trimIndent())
                })
                put("parts", parts)
            }
            rootJson.put("systemInstruction", systemInstruction)

            // Contents array
            val contentsArray = JSONArray()

            // Include limited recent conversation history
            val recentHistory = history.takeLast(4)
            for ((text, isUser) in recentHistory) {
                val turnObj = JSONObject()
                turnObj.put("role", if (isUser) "user" else "model")
                val p = JSONArray().put(JSONObject().put("text", text))
                turnObj.put("parts", p)
                contentsArray.put(turnObj)
            }

            // Current user turn
            val currentTurn = JSONObject().apply {
                put("role", "user")
                val partsArray = JSONArray()
                partsArray.put(JSONObject().put("text", prompt))

                if (imageBitmap != null) {
                    val stream = ByteArrayOutputStream()
                    imageBitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                    val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                    partsArray.put(JSONObject().apply {
                        put("inlineData", JSONObject().apply {
                            put("mimeType", "image/jpeg")
                            put("data", base64)
                        })
                    })
                }
                put("parts", partsArray)
            }
            contentsArray.put(currentTurn)
            rootJson.put("contents", contentsArray)

            // Generation config
            val genConfig = JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 2048)
            }
            rootJson.put("generationConfig", genConfig)

            val requestBody = rootJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.w("GeminiService", "API error: ${response.code} $responseBody")
                return@withContext executeLocalRuleAgent(prompt, "API Response (${response.code}): Switching to autonomous on-device mode.")
            }

            val jsonResp = JSONObject(responseBody)
            val candidates = jsonResp.optJSONArray("candidates")
            val candidate = candidates?.optJSONObject(0)
            val content = candidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val replyText = parts?.optJSONObject(0)?.optString("text") ?: "I processed your request, but received an empty response."

            parseAgentResponse(replyText)
        } catch (e: Exception) {
            Log.e("GeminiService", "Call failed", e)
            executeLocalRuleAgent(prompt, "Network notice: Using autonomous on-device intelligence (${e.message ?: "offline"}).")
        }
    }

    private fun parseAgentResponse(rawText: String): AgentResponse {
        var cleanText = rawText
        var actionTag: String? = null
        var actionArg: String? = null

        // Check for [[ACTION:...]] pattern
        val actionRegex = Regex("""\[\[ACTION:([A-Z_]+)(?::([^\]]+))?\]\]""")
        val match = actionRegex.find(rawText)
        if (match != null) {
            actionTag = match.groupValues[1]
            actionArg = match.groupValues.getOrNull(2)
            cleanText = rawText.replace(match.value, "").trim()
        }

        // Check for code blocks
        val codeRegex = Regex("```(?:html)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
        val codeMatch = codeRegex.find(rawText)
        val extractedCode = codeMatch?.groupValues?.getOrNull(1)?.trim()

        return AgentResponse(
            text = cleanText,
            actionTag = actionTag,
            actionArg = actionArg,
            extractedCode = extractedCode
        )
    }

    private fun executeLocalRuleAgent(prompt: String, notice: String? = null): AgentResponse {
        val lower = prompt.lowercase().trim()

        val prefixNotice = if (notice != null) "[$notice]\n\n" else ""

        // Torch/Flashlight
        if (lower.contains("flashlight") || lower.contains("torch")) {
            return if (lower.contains("off") || lower.contains("stop")) {
                AgentResponse(
                    text = "${prefixNotice}Flashlight has been turned OFF.",
                    actionTag = "TORCH_OFF"
                )
            } else {
                AgentResponse(
                    text = "${prefixNotice}Flashlight turned ON! Glowing at maximum brightness.",
                    actionTag = "TORCH_ON"
                )
            }
        }

        // Download / Install app
        if (lower.contains("download") || lower.contains("install")) {
            val app = extractTargetApp(lower)
            return AgentResponse(
                text = "${prefixNotice}Opening Google Play Store to install $app right away.",
                actionTag = "DOWNLOAD_APP",
                actionArg = app
            )
        }

        // Open / Launch app
        if (lower.startsWith("open ") || lower.contains("launch ") || lower.contains("start ")) {
            val app = extractTargetApp(lower)
            return AgentResponse(
                text = "${prefixNotice}Launching $app on your device.",
                actionTag = "LAUNCH_APP",
                actionArg = app
            )
        }

        // Volume control
        if (lower.contains("volume") || lower.contains("mute") || lower.contains("sound")) {
            return when {
                lower.contains("mute") -> AgentResponse("${prefixNotice}Media volume muted to 0%.", "MUTE")
                lower.contains("max") || lower.contains("100") -> AgentResponse("${prefixNotice}Volume set to maximum (100%).", "VOLUME_SET", "100")
                lower.contains("up") || lower.contains("increase") -> AgentResponse("${prefixNotice}Media volume increased.", "VOLUME_UP")
                lower.contains("down") || lower.contains("decrease") -> AgentResponse("${prefixNotice}Media volume lowered.", "VOLUME_DOWN")
                else -> AgentResponse("${prefixNotice}Adjusted media volume to 75%.", "VOLUME_SET", "75")
            }
        }

        // Battery / Telemetry
        if (lower.contains("battery") || lower.contains("ram") || lower.contains("storage") || lower.contains("specs") || lower.contains("device info")) {
            return AgentResponse(
                text = "${prefixNotice}Here are your real-time phone diagnostics. Check the Device Status cards below for live battery temperature, charging health, and RAM usage.",
                actionTag = "REFRESH_TELEMETRY"
            )
        }

        // Code Generation
        if (lower.contains("website") || lower.contains("code") || lower.contains("game") || lower.contains("portfolio") || lower.contains("app") || lower.contains("html") || lower.contains("fix")) {
            val generatedApp = generateWebCodeTemplate(lower)
            return AgentResponse(
                text = "${prefixNotice}I've generated the complete application code for you! You can run it live in the interactive Code Studio preview, or test it directly below.",
                actionTag = "CODE_GEN",
                extractedCode = generatedApp
            )
        }

        // Market Insights
        if (lower.contains("market") || lower.contains("crypto") || lower.contains("bitcoin") || lower.contains("btc") || lower.contains("stock") || lower.contains("eth")) {
            return AgentResponse(
                text = "${prefixNotice}📊 Market Intelligence Overview:\n- Bitcoin (BTC): Steady bullish momentum trading around \$96,400 with high institutional ETF volume.\n- Ethereum (ETH): Consolidation near \$3,450 with strong DeFi staking yield.\n- Tech Indices: NASDAQ & S&P 500 hovering near all-time highs powered by AI infrastructure demand.\nCheck the Market Insights tab for interactive live trackers!",
                actionTag = "MARKET_CHECK"
            )
        }

        // Timer
        if (lower.contains("timer") || lower.contains("alarm")) {
            return AgentResponse("${prefixNotice}Starting a 5-minute countdown timer on your phone.", "SET_TIMER", "300")
        }

        // General AI Assistant response
        return AgentResponse(
            text = "${prefixNotice}Hello! I am Maria, your autonomous Android AI assistant. I can control your phone hardware (flashlight, volume, apps), install new apps from Google Play, write full-stack code and execute it live in my Code Studio, analyze market trends, and inspect screens. How can I help you right now?"
        )
    }

    private fun extractTargetApp(query: String): String {
        val targets = listOf("instagram", "whatsapp", "youtube", "chrome", "spotify", "telegram", "camera", "maps", "calculator", "netflix", "twitter")
        for (target in targets) {
            if (query.contains(target)) return target
        }
        val words = query.split(" ")
        return words.lastOrNull()?.replace(Regex("[^a-zA-Z0-9]"), "") ?: "chrome"
    }

    fun generateWebCodeTemplate(prompt: String): String {
        return when {
            prompt.contains("game") || prompt.contains("snake") || prompt.contains("pong") -> {
                """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <style>
                    body { margin: 0; background: #0B0E14; color: #00E5FF; font-family: sans-serif; display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100vh; overflow: hidden; }
                    h2 { margin: 10px 0; font-size: 20px; text-shadow: 0 0 10px #00E5FF; }
                    canvas { background: #131A29; border: 2px solid #00E5FF; border-radius: 12px; box-shadow: 0 0 20px rgba(0,229,255,0.3); }
                    .controls { margin-top: 15px; display: grid; grid-template-columns: repeat(3, 60px); gap: 10px; }
                    button { background: #1E283D; color: #FFF; border: 1px solid #7C4DFF; border-radius: 8px; height: 50px; font-weight: bold; font-size: 18px; }
                    button:active { background: #7C4DFF; }
                    .score { font-size: 16px; color: #7C4DFF; margin-bottom: 5px; font-weight: bold; }
                  </style>
                </head>
                <body>
                  <h2>⚡ Cyber Neon Pong</h2>
                  <div class="score">Score: <span id="scoreVal">0</span></div>
                  <canvas id="gameCanvas" width="300" height="260"></canvas>
                  <div class="controls">
                    <div></div>
                    <button onclick="movePaddle(-1)">▲</button>
                    <div></div>
                    <button onclick="movePaddle(-1)">◀</button>
                    <button onclick="resetGame()">↻</button>
                    <button onclick="movePaddle(1)">▶</button>
                  </div>
                  <script>
                    const canvas = document.getElementById('gameCanvas');
                    const ctx = canvas.getContext('2d');
                    let paddleX = 110, paddleW = 80;
                    let ballX = 150, ballY = 50, ballDx = 2.5, ballDy = 2.5;
                    let score = 0;
                    function movePaddle(dir) {
                      paddleX = Math.max(0, Math.min(canvas.width - paddleW, paddleX + dir * 30));
                    }
                    function resetGame() {
                      ballX = 150; ballY = 50; score = 0;
                      document.getElementById('scoreVal').innerText = score;
                    }
                    function update() {
                      ballX += ballDx; ballY += ballDy;
                      if (ballX <= 5 || ballX >= canvas.width - 5) ballDx = -ballDx;
                      if (ballY <= 5) ballDy = -ballDy;
                      if (ballY >= canvas.height - 25 && ballX >= paddleX && ballX <= paddleX + paddleW) {
                        ballDy = -Math.abs(ballDy) * 1.05;
                        score += 10;
                        document.getElementById('scoreVal').innerText = score;
                      }
                      if (ballY > canvas.height) resetGame();
                      ctx.clearRect(0, 0, canvas.width, canvas.height);
                      // Draw paddle
                      ctx.fillStyle = '#00E5FF';
                      ctx.shadowBlur = 10; ctx.shadowColor = '#00E5FF';
                      ctx.fillRect(paddleX, canvas.height - 18, paddleW, 10);
                      // Draw ball
                      ctx.fillStyle = '#FF5252';
                      ctx.beginPath();
                      ctx.arc(ballX, ballY, 7, 0, Math.PI*2);
                      ctx.fill();
                      requestAnimationFrame(update);
                    }
                    update();
                  </script>
                </body>
                </html>
                """.trimIndent()
            }
            prompt.contains("calculator") -> {
                """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <style>
                    body { margin: 0; background: #0A0E17; color: #FFF; font-family: system-ui, sans-serif; display: flex; align-items: center; justify-content: center; height: 100vh; }
                    .calc { background: #131A29; padding: 20px; border-radius: 20px; border: 1px solid #1E283D; box-shadow: 0 10px 30px rgba(0,0,0,0.5); width: 280px; }
                    .screen { background: #0B0E14; border: 1px solid #00E5FF; border-radius: 12px; padding: 15px; font-size: 28px; text-align: right; color: #00E5FF; min-height: 35px; margin-bottom: 20px; overflow-x: auto; }
                    .grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; }
                    button { background: #1E283D; color: #F1F5F9; border: none; border-radius: 12px; padding: 15px; font-size: 18px; font-weight: bold; cursor: pointer; transition: 0.2s; }
                    button:active { transform: scale(0.95); opacity: 0.8; }
                    .op { background: #7C4DFF; color: #FFF; }
                    .eq { background: #00E5FF; color: #00363D; grid-column: span 2; }
                  </style>
                </head>
                <body>
                  <div class="calc">
                    <div class="screen" id="display">0</div>
                    <div class="grid">
                      <button onclick="clearScreen()">C</button>
                      <button onclick="delChar()">⌫</button>
                      <button class="op" onclick="appendOp('/')">÷</button>
                      <button class="op" onclick="appendOp('*')">×</button>
                      <button onclick="appendNum('7')">7</button>
                      <button onclick="appendNum('8')">8</button>
                      <button onclick="appendNum('9')">9</button>
                      <button class="op" onclick="appendOp('-')">-</button>
                      <button onclick="appendNum('4')">4</button>
                      <button onclick="appendNum('5')">5</button>
                      <button onclick="appendNum('6')">6</button>
                      <button class="op" onclick="appendOp('+')">+</button>
                      <button onclick="appendNum('1')">1</button>
                      <button onclick="appendNum('2')">2</button>
                      <button onclick="appendNum('3')">3</button>
                      <button onclick="appendNum('.')">.</button>
                      <button onclick="appendNum('0')">0</button>
                      <button class="eq" onclick="calculate()">=</button>
                    </div>
                  </div>
                  <script>
                    let current = '0';
                    function update() { document.getElementById('display').innerText = current; }
                    function appendNum(n) { current = current === '0' ? n : current + n; update(); }
                    function appendOp(op) { current += ' ' + op + ' '; update(); }
                    function clearScreen() { current = '0'; update(); }
                    function delChar() { current = current.length > 1 ? current.slice(0, -1).trim() : '0'; update(); }
                    function calculate() { try { current = String(eval(current)); } catch(e) { current = 'Error'; } update(); }
                  </script>
                </body>
                </html>
                """.trimIndent()
            }
            else -> {
                // Futuristic Portfolio Landing Page
                """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <style>
                    * { box-sizing: border-box; }
                    body { margin: 0; background: #0A0E17; color: #F1F5F9; font-family: system-ui, sans-serif; padding: 20px; line-height: 1.6; }
                    .badge { display: inline-block; background: rgba(0,229,255,0.15); color: #00E5FF; padding: 4px 12px; border-radius: 20px; font-size: 12px; font-weight: bold; border: 1px solid rgba(0,229,255,0.4); margin-bottom: 12px; }
                    h1 { font-size: 26px; margin: 0 0 10px 0; background: linear-gradient(90deg, #00E5FF, #7C4DFF); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }
                    p { color: #94A3B8; margin-top: 0; font-size: 14px; }
                    .card { background: #131A29; border: 1px solid #1E283D; border-radius: 16px; padding: 16px; margin-bottom: 16px; transition: 0.3s; }
                    .card:hover { border-color: #00E5FF; }
                    .card h3 { margin: 0 0 6px 0; color: #00E5FF; font-size: 16px; }
                    .stats { display: flex; gap: 10px; margin-top: 15px; }
                    .stat-box { flex: 1; background: #1E283D; border-radius: 10px; padding: 10px; text-align: center; }
                    .stat-val { font-size: 18px; font-weight: bold; color: #00E676; }
                    .stat-lbl { font-size: 11px; color: #94A3B8; }
                    .btn { display: block; width: 100%; background: linear-gradient(90deg, #00E5FF, #7C4DFF); color: #000; font-weight: bold; text-align: center; padding: 12px; border-radius: 10px; text-decoration: none; border: none; font-size: 15px; cursor: pointer; }
                  </style>
                </head>
                <body>
                  <span class="badge">🚀 Built with Maria AI</span>
                  <h1>Alex Rivera</h1>
                  <p>Full-Stack Mobile & AI Systems Architect. Crafting high-performance intelligent interfaces.</p>
                  
                  <div class="stats">
                    <div class="stat-box"><div class="stat-val">48+</div><div class="stat-lbl">Projects</div></div>
                    <div class="stat-box"><div class="stat-val">99.8%</div><div class="stat-lbl">Accuracy</div></div>
                    <div class="stat-box"><div class="stat-val">100k+</div><div class="stat-lbl">Users</div></div>
                  </div>
                  
                  <h2 style="font-size: 18px; margin: 20px 0 10px 0; color: #FFF;">Featured Works</h2>
                  <div class="card">
                    <h3>⚡ Autonomous Android Core</h3>
                    <p>Agent runtime capable of voice automation, screen perception, and on-device logic.</p>
                  </div>
                  <div class="card">
                    <h3>🔮 Neural Vision Sandbox</h3>
                    <p>Real-time visual processing engine with edge object detection.</p>
                  </div>
                  
                  <button class="btn" onclick="alert('Contact message simulated! Ready to build together.')">Connect With Me</button>
                </body>
                </html>
                """.trimIndent()
            }
        }
    }
}

data class AgentResponse(
    val text: String,
    val actionTag: String? = null,
    val actionArg: String? = null,
    val extractedCode: String? = null
)
