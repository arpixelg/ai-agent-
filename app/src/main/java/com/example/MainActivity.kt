package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Badge
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AgentState
import com.example.ui.AgentViewModel
import com.example.ui.screens.AgentScreen
import com.example.ui.screens.CodeStudioScreen
import com.example.ui.screens.MarketInsightsScreen
import com.example.ui.screens.PhoneControlScreen
import com.example.ui.screens.SettingsDialog
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DeepSpace
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

enum class AppScreen {
    AGENT,
    CODE_STUDIO,
    PHONE_CONTROLS,
    MARKET_INSIGHTS
}

class MainActivity : ComponentActivity() {

    private val viewModel: AgentViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppContent(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(viewModel: AgentViewModel) {
    var currentScreen by remember { mutableStateOf(AppScreen.AGENT) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val agentState by viewModel.agentState.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()

    // Back handling
    if (currentScreen != AppScreen.AGENT) {
        BackHandler {
            currentScreen = AppScreen.AGENT
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (agentState == AgentState.ERROR) Color.Red else AccentGreen)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "MARIA AI",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            letterSpacing = 2.sp,
                            color = CyberCyan,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(NeonPurple.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "3.8 FLASH",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonPurple,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.testTag("settings_btn")
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = CyberCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = SurfaceDark
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceDark,
                contentColor = CyberCyan
            ) {
                NavigationBarItem(
                    selected = currentScreen == AppScreen.AGENT,
                    onClick = { currentScreen = AppScreen.AGENT },
                    icon = { Icon(Icons.Default.Psychology, contentDescription = "Maria Agent") },
                    label = { Text("Agent", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = CyberCyan,
                        indicatorColor = CyberCyan,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("nav_agent")
                )

                NavigationBarItem(
                    selected = currentScreen == AppScreen.CODE_STUDIO,
                    onClick = { currentScreen = AppScreen.CODE_STUDIO },
                    icon = { Icon(Icons.Default.Code, contentDescription = "Code Studio") },
                    label = { Text("Code Studio", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = CyberCyan,
                        indicatorColor = CyberCyan,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("nav_code_studio")
                )

                NavigationBarItem(
                    selected = currentScreen == AppScreen.PHONE_CONTROLS,
                    onClick = { currentScreen = AppScreen.PHONE_CONTROLS },
                    icon = { Icon(Icons.Default.Smartphone, contentDescription = "Phone Controls") },
                    label = { Text("Controls", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = CyberCyan,
                        indicatorColor = CyberCyan,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("nav_phone_controls")
                )

                NavigationBarItem(
                    selected = currentScreen == AppScreen.MARKET_INSIGHTS,
                    onClick = { currentScreen = AppScreen.MARKET_INSIGHTS },
                    icon = { Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = "Market Insights") },
                    label = { Text("Markets", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = CyberCyan,
                        indicatorColor = CyberCyan,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("nav_markets")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DeepSpace)
        ) {
            when (currentScreen) {
                AppScreen.AGENT -> AgentScreen(
                    viewModel = viewModel,
                    onNavigateToCodeStudio = { currentScreen = AppScreen.CODE_STUDIO }
                )
                AppScreen.CODE_STUDIO -> CodeStudioScreen(
                    viewModel = viewModel
                )
                AppScreen.PHONE_CONTROLS -> PhoneControlScreen(
                    viewModel = viewModel
                )
                AppScreen.MARKET_INSIGHTS -> MarketInsightsScreen(
                    viewModel = viewModel,
                    onNavigateToChat = { currentScreen = AppScreen.AGENT }
                )
            }
        }
    }
}
