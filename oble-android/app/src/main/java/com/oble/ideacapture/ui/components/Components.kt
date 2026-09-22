package com.oble.ideacapture.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.oble.ideacapture.ui.theme.Oble
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/** Pulsing dot used for "● Listening" style indicators. */
@Composable
fun StatusDot(color: Color, pulsing: Boolean, size: Dp = 8.dp) {
    val alpha = if (pulsing) {
        val t = rememberInfiniteTransition(label = "dot")
        val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "dotAlpha")
        a
    } else 1f
    Box(Modifier.size(size).alpha(alpha).clip(CircleShape).background(color))
}

@Composable
fun StatusPill(text: String, color: Color, pulsing: Boolean) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.22f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color, pulsing)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/**
 * Subtle live waveform. Bars follow the recent microphone level history with a
 * gentle idle motion, so it breathes even during quiet moments.
 */
@Composable
fun Waveform(level: Float, active: Boolean, modifier: Modifier = Modifier, bars: Int = 36) {
    val history = remember { mutableStateListOf<Float>().apply { repeat(bars) { add(0f) } } }
    LaunchedEffect(level, active) {
        history.removeAt(0)
        history.add(if (active) level else 0f)
    }
    val t = rememberInfiniteTransition(label = "wave")
    val phase by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "phase")
    val color = if (active) Oble.Green else Oble.TextFaint
    Canvas(modifier.fillMaxWidth().height(56.dp)) {
        val gap = 5.dp.toPx()
        val barW = (size.width - gap * (bars - 1)) / bars
        val mid = size.height / 2
        for (i in 0 until bars) {
            val idle = if (active) 0.06f + 0.04f * sin(phase + i * 0.45f) else 0.03f
            val v = max(idle, history[i])
            val h = max(barW, v * size.height)
            val edge = 1f - kotlin.math.abs(i - bars / 2f) / (bars / 2f)
            drawRoundRect(
                color = color.copy(alpha = 0.25f + 0.75f * edge),
                topLeft = Offset(i * (barW + gap), mid - h / 2),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2, barW / 2),
            )
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Oble.TextFaint,
        modifier = modifier.padding(top = 8.dp, bottom = 10.dp),
    )
}

@Composable
fun Surface(
    modifier: Modifier = Modifier,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(Oble.Charcoal)
            .border(1.dp, Oble.Line.copy(alpha = 0.6f), MaterialTheme.shapes.medium)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        content = content,
    )
}

@Composable
fun Tag(text: String, color: Color = Oble.TextMuted) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
