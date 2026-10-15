@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.example.opentv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val Ink = Color(0xFF0B0F14)
val Panel = Color(0xFF151B23)
val PanelHigh = Color(0xFF212A36)
val Teal = Color(0xFF2DD4BF)
val TextPrimary = Color(0xFFE6EDF3)
val TextMuted = Color(0xFF93A1B0)

@Composable
fun OpenTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Teal,
            onPrimary = Color(0xFF00201C),
            secondary = Color(0xFFFFB454),
            background = Ink,
            onBackground = TextPrimary,
            surface = Panel,
            onSurface = TextPrimary,
            surfaceVariant = PanelHigh,
            onSurfaceVariant = TextMuted,
            border = Color(0xFF2B3644),
        ),
        content = content,
    )
}
