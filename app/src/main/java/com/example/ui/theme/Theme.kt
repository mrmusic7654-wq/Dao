package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val YinDarkColorScheme = darkColorScheme(
    primary = ZenGold,
    secondary = ZenBlue,
    tertiary = ZenSienna,
    background = YinBlack,
    surface = YinCardBg,
    onPrimary = YinBlack,
    onSecondary = YinBlack,
    onTertiary = YinBlack,
    onBackground = YinText,
    onSurface = YinText,
    surfaceVariant = YinCardBg,
    onSurfaceVariant = YinTextSecondary
)

private val YangLightColorScheme = lightColorScheme(
    primary = ZenGold,
    secondary = ZenBlue,
    tertiary = ZenSienna,
    background = YangWhite,
    surface = YangCardBg,
    onPrimary = YangWhite,
    onSecondary = YangWhite,
    onTertiary = YangWhite,
    onBackground = YangText,
    onSurface = YangText,
    surfaceVariant = YangCardBg,
    onSurfaceVariant = YangTextSecondary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) YinDarkColorScheme else YangLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
