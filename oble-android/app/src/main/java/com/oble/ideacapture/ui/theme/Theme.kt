package com.oble.ideacapture.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Oble {
    val Black = Color(0xFF0A0B0A)
    val Charcoal = Color(0xFF121412)
    val Raised = Color(0xFF181B18)
    val Line = Color(0xFF232723)
    val Text = Color(0xFFF2F2EE)
    val TextMuted = Color(0xFF9AA19A)
    val TextFaint = Color(0xFF5E655E)
    val Green = Color(0xFF3DDC84)
    val GreenDeep = Color(0xFF0F2A1B)
    val GreenLine = Color(0xFF1E4D33)
    val Amber = Color(0xFFE8B04A)
    val Red = Color(0xFFEF6A5B)
}

private val scheme = darkColorScheme(
    primary = Oble.Green,
    onPrimary = Oble.Black,
    primaryContainer = Oble.GreenDeep,
    onPrimaryContainer = Oble.Green,
    secondary = Oble.TextMuted,
    onSecondary = Oble.Black,
    background = Oble.Black,
    onBackground = Oble.Text,
    surface = Oble.Black,
    onSurface = Oble.Text,
    surfaceVariant = Oble.Charcoal,
    onSurfaceVariant = Oble.TextMuted,
    surfaceContainer = Oble.Charcoal,
    surfaceContainerHigh = Oble.Raised,
    surfaceContainerHighest = Oble.Raised,
    surfaceContainerLow = Oble.Charcoal,
    outline = Oble.Line,
    outlineVariant = Oble.Line,
    error = Oble.Red,
)

private val Sans = FontFamily.SansSerif

private val typography = Typography(
    displayLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Black, fontSize = 44.sp, letterSpacing = 10.sp),
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 1.2.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 1.4.sp),
)

private val shapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
)

@Composable
fun ObleTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
}
