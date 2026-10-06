package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.AgentState
import com.example.data.ChatMessage
import com.example.ui.AgentViewModel
import com.example.ui.components.ActionLogCard
import com.example.ui.components.AgentOrb
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DeepSpace
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceLightDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen(
    viewModel: AgentViewModel,
    onNavigateToCodeStudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val agentState by viewModel.agentState.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val isListening by viewModel.isVoiceListening.collectAsState()
    val ttsEnabled by viewModel.ttsEnabled.collectAsState()

    var inputPrompt by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val listState = rememberLazyListState()

    // Scroll to bottom on new message
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    // Photo picker for visual screen inspection
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        selectedImageUri = uri
        if (uri != null) {
            try {
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                selectedBitmap = bmp
            } catch (_: Exception) {}
        }
    }

    val quickCommands = listOf(
        "⚡ Turn on flashlight",
        "📥 Download Instagram",
        "🎮 Build a neon mini game",
        "💼 Code a modern portfolio",
        "📊 Analyze Bitcoin market",
        "🔋 Battery & specs report",
        "🔊 Max volume"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DeepSpace)
    ) {
        // Futuristic Agent Header with Orb & Status
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(CyberCyan.copy(0.3f), NeonPurple.copy(0.3f))))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (agentState == AgentState.ERROR) Color.Red else AccentGreen)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "MARIA 3.8 CORE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberCyan,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.toggleTts(!ttsEnabled) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                if (ttsEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                contentDescription = "Toggle TTS",
                                tint = if (ttsEnabled) CyberCyan else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // The Holographic AI Orb
                AgentOrb(
                    state = agentState,
                    onClick = {
                        if (isListening) viewModel.stopVoiceListening() else viewModel.startVoiceListening()
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Status text with pulsating indicator
                Text(
                    text = when (agentState) {
                        AgentState.LISTENING -> "🎙️ Listening to your voice command..."
                        AgentState.THINKING -> "🧠 Maria is processing & automating..."
                        AgentState.SPEAKING -> "🔊 Maria speaking..."
                        AgentState.ERROR -> "⚠️ Command interrupted"
                        AgentState.IDLE -> "Tap orb or mic to speak with Maria"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = when (agentState) {
                        AgentState.LISTENING -> Color(0xFFFF5252)
                        AgentState.THINKING -> NeonPurple
                        AgentState.SPEAKING -> CyberCyan
                        else -> TextSecondary
                    }
                )
            }
        }

        // Quick Suggestion Chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(quickCommands) { cmd ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = SurfaceLightDark,
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(CyberCyan.copy(0.2f), Color.Transparent))),
                    modifier = Modifier.clickable {
                        val cleanCmd = cmd.replace(Regex("^[⚡📥🎮💼📊🔋🔊]\\s*"), "")
                        viewModel.sendUserPrompt(cleanCmd)
                    }
                ) {
                    Text(
                        text = cmd,
                        fontSize = 11.sp,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Chat Conversation & Action Execution Stream
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(chatMessages, key = { it.id }) { msg ->
                if (msg.isUser) {
                    UserMessageBubble(msg)
                } else {
                    AssistantMessageBubble(
                        message = msg,
                        onOpenCodeStudio = {
                            viewModel.updateCurrentCode(it)
                            onNavigateToCodeStudio()
                        },
                        onRunActionAgain = {
                            viewModel.sendUserPrompt(it)
                        }
                    )
                }
            }
        }

        // Image Attachment Preview bar if selected
        AnimatedVisibility(visible = selectedImageUri != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .background(SurfaceDark, RoundedCornerShape(12.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = selectedImageUri,
                        contentDescription = "Attachment preview",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Image attached for screen inspection",
                        fontSize = 12.sp,
                        color = CyberCyan
                    )
                }
                IconButton(onClick = {
                    selectedImageUri = null
                    selectedBitmap = null
                }) {
                    Icon(Icons.Default.Close, contentDescription = "Remove image", tint = TextSecondary)
                }
            }
        }

        // Input Action Dock
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = SurfaceDark,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Photo Picker Button for Screen / Vision analysis
                IconButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("attach_screen_btn")
                ) {
                    Icon(
                        Icons.Default.AddPhotoAlternate,
                        contentDescription = "Attach screenshot or photo",
                        tint = if (selectedImageUri != null) CyberCyan else TextSecondary
                    )
                }

                // Text Input Bar
                OutlinedTextField(
                    value = inputPrompt,
                    onValueChange = { inputPrompt = it },
                    placeholder = {
                        Text(
                            text = if (isListening) "Listening..." else "Ask Maria to code or control phone...",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                        .testTag("chat_input_field"),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceLightDark,
                        unfocusedContainerColor = SurfaceLightDark,
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    maxLines = 3
                )

                // Voice Mic Button (hold/tap to speak)
                IconButton(
                    onClick = {
                        if (isListening) {
                            viewModel.stopVoiceListening()
                        } else {
                            viewModel.startVoiceListening()
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isListening) Color(0xFFFF5252) else SurfaceLightDark)
                        .testTag("voice_mic_btn")
                ) {
                    Icon(
                        if (isListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                        contentDescription = "Voice input",
                        tint = if (isListening) Color.White else CyberCyan
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Send Button
                IconButton(
                    onClick = {
                        if (inputPrompt.isNotBlank() || selectedBitmap != null) {
                            viewModel.sendUserPrompt(inputPrompt, selectedBitmap)
                            inputPrompt = ""
                            selectedImageUri = null
                            selectedBitmap = null
                        }
                    },
                    enabled = inputPrompt.isNotBlank() || selectedBitmap != null,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (inputPrompt.isNotBlank() || selectedBitmap != null) CyberCyan else SurfaceLightDark)
                        .testTag("send_prompt_btn")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send prompt",
                        tint = if (inputPrompt.isNotBlank() || selectedBitmap != null) Color.Black else TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun UserMessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(
                    brush = Brush.horizontalGradient(listOf(NeonPurple, Color(0xFF512DA8))),
                    shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
                )
                .padding(12.dp)
        ) {
            Text(
                text = message.text,
                color = Color.White,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun AssistantMessageBubble(
    message: ChatMessage,
    onOpenCodeStudio: (String) -> Unit,
    onRunActionAgain: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(CyberCyan),
                contentAlignment = Alignment.Center
            ) {
                Text("M", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Maria AI",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = CyberCyan
            )
        }

        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(
                    SurfaceDark,
                    shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)
                )
                .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
                .padding(12.dp)
        ) {
            Column {
                Text(
                    text = message.text,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                if (message.actionTag != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ActionLogCard(
                        message = message,
                        onOpenCodeStudio = onOpenCodeStudio,
                        onRunActionAgain = onRunActionAgain
                    )
                }
            }
        }
    }
}
