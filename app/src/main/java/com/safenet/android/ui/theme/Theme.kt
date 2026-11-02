package com.safenet.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SafeNetDarkColorScheme = darkColorScheme(
    primary = CyberGreen,
    onPrimary = CyberBackground,
    secondary = CyberBlue,
    onSecondary = CyberBackground,
    background = CyberBackground,
    onBackground = TextPrimary,
    surface = CyberSurface,
    onSurface = TextPrimary,
    surfaceVariant = CyberCard,
    onSurfaceVariant = TextSecondary,
    outline = CyberCardBorder,
    error = CyberRed,
    onError = TextPrimary
)

@Composable
fun SafeNetTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SafeNetDarkColorScheme,
        content = content
    )
}
