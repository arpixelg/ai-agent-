package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.data.AgentState
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.NeonPurple
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AgentOrb(
    state: AgentState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_anim")

    // Pulsing scale
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = when (state) {
            AgentState.LISTENING -> 1.35f
            AgentState.SPEAKING -> 1.25f
            AgentState.THINKING -> 1.15f
            else -> 1.0f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    AgentState.LISTENING -> 600
                    AgentState.SPEAKING -> 800
                    AgentState.THINKING -> 1000
                    else -> 2400
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Rotation angle
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == AgentState.THINKING) 2500 else 8000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_rotation"
    )

    // Secondary ring wave
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val coreColor = when (state) {
        AgentState.LISTENING -> Color(0xFFFF5252)
        AgentState.THINKING -> NeonPurple
        AgentState.SPEAKING -> CyberCyan
        AgentState.ERROR -> Color(0xFFFF1744)
        AgentState.IDLE -> CyberCyan
    }

    Box(
        modifier = modifier
            .size(160.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(160.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = (size.minDimension / 2f) * 0.45f

            // Outer expanding acoustic wave when speaking or listening
            if (state == AgentState.LISTENING || state == AgentState.SPEAKING) {
                val waveRadius = baseRadius + (wavePhase * 36.dp.toPx())
                val waveAlpha = (1f - wavePhase).coerceIn(0f, 1f) * 0.6f
                drawCircle(
                    color = coreColor.copy(alpha = waveAlpha),
                    radius = waveRadius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Outer holographic orbits
            val orbitRadius = baseRadius * 1.35f * pulseScale
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        CyberCyan.copy(alpha = 0.8f),
                        NeonPurple.copy(alpha = 0.3f),
                        Color.Transparent,
                        CyberCyan.copy(alpha = 0.8f)
                    )
                ),
                radius = orbitRadius,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Orbiting satellite dots
            val radAngle = Math.toRadians(rotation.toDouble())
            val dotX = center.x + (orbitRadius * cos(radAngle)).toFloat()
            val dotY = center.y + (orbitRadius * sin(radAngle)).toFloat()
            drawCircle(
                color = coreColor,
                radius = 4.dp.toPx(),
                center = Offset(dotX, dotY)
            )

            val oppositeX = center.x - (orbitRadius * cos(radAngle)).toFloat()
            val oppositeY = center.y - (orbitRadius * sin(radAngle)).toFloat()
            drawCircle(
                color = NeonPurple,
                radius = 3.dp.toPx(),
                center = Offset(oppositeX, oppositeY)
            )

            // Glowing Outer Aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        coreColor.copy(alpha = 0.45f),
                        NeonPurple.copy(alpha = 0.2f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 1.5f * pulseScale
                ),
                radius = baseRadius * 1.5f * pulseScale,
                center = center
            )

            // Inner Core Spherical Gradient
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        coreColor,
                        Color(0xFF0A0E17)
                    ),
                    center = Offset(center.x - baseRadius * 0.2f, center.y - baseRadius * 0.2f),
                    radius = baseRadius * pulseScale
                ),
                radius = baseRadius * pulseScale,
                center = center
            )
        }
    }
}
