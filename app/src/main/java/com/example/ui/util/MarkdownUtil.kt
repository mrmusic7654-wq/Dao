package com.example.ui.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.example.ui.theme.ZenGold

fun buildFormattedAnnotatedString(text: String, isDark: Boolean) = buildAnnotatedString {
    val boldParts = text.split("**")
    boldParts.forEachIndexed { bIdx, bPart ->
        val isBold = bIdx % 2 == 1
        val inlineCodeParts = bPart.split("`")
        inlineCodeParts.forEachIndexed { cIdx, cPart ->
            val isInlineCode = cIdx % 2 == 1

            val style = when {
                isBold && isInlineCode -> SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE5C07B),
                    background = if (isDark) Color(0xFF1E1E24) else Color(0xFFEAE5DA)
                )
                isBold -> SpanStyle(
                    fontWeight = FontWeight.Bold,
                    color = ZenGold
                )
                isInlineCode -> SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    color = if (isDark) Color(0xFFE5C07B) else Color(0xFFB53D3D),
                    background = if (isDark) Color(0xFF1E1E24) else Color(0xFFEAE5DA)
                )
                else -> SpanStyle()
            }

            withStyle(style = style) {
                append(cPart)
            }
        }
    }
}
