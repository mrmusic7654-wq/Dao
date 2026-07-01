package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*

@Composable
fun HexagonYinYangBackground(isDark: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val hexRadius = (size.width.coerceAtMost(size.height) * 0.35f)

        val strokeColor = if (isDark) Color(0xFFB5943C).copy(alpha = 0.15f) else Color(0xFFB5943C).copy(alpha = 0.22f)
        val fillYin = if (isDark) Color(0xFF0F0E14).copy(alpha = 0.45f) else Color(0xFFECE9E4).copy(alpha = 0.45f)
        val fillYang = if (isDark) Color(0xFF2E2D38).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.55f)

        // Draw Hexagon outline
        val hexPath = Path().apply {
            for (i in 0..5) {
                val angleRad = Math.toRadians((i * 60 - 30).toDouble())
                val x = centerX + hexRadius * Math.cos(angleRad).toFloat()
                val y = centerY + hexRadius * Math.sin(angleRad).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(path = hexPath, color = strokeColor, style = Stroke(width = 2.dp.toPx()))

        // Yin-Yang inside the hexagon
        val radius = hexRadius * 0.8f

        drawArc(
            color = fillYang,
            startAngle = -90f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(centerX - radius, centerY - radius),
            size = Size(radius * 2, radius * 2)
        )
        drawArc(
            color = fillYin,
            startAngle = 90f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(centerX - radius, centerY - radius),
            size = Size(radius * 2, radius * 2)
        )

        drawCircle(
            color = fillYin,
            radius = radius / 2f,
            center = Offset(centerX, centerY - radius / 2f)
        )
        drawCircle(
            color = fillYang,
            radius = radius / 2f,
            center = Offset(centerX, centerY + radius / 2f)
        )

        drawCircle(
            color = fillYang,
            radius = radius / 8f,
            center = Offset(centerX, centerY - radius / 2f)
        )
        drawCircle(
            color = fillYin,
            radius = radius / 8f,
            center = Offset(centerX, centerY + radius / 2f)
        )

        drawCircle(
            color = strokeColor,
            radius = radius,
            center = Offset(centerX, centerY),
            style = Stroke(width = 1.dp.toPx())
        )
    }
}

@Composable
fun YinYangHexagonLogo(
    modifier: Modifier = Modifier,
    isThinking: Boolean = false,
    sizeDp: Int = 120
) {
    val transition = rememberInfiniteTransition(label = "hexagon_logo")
    val rotationSpeed = if (isThinking) 2500 else 12000
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = rotationSpeed, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    val scaleSpeed = if (isThinking) 700 else 2600
    val pulseScale by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = scaleSpeed, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = modifier
            .scale(pulseScale)
            .size(sizeDp.dp)
            .drawBehind {
                val r = size.minDimension / 2.3f
                val cx = size.width / 2
                val cy = size.height / 2

                rotate(degrees = angle) {
                    // I Ching Trigrams (Bagua) outside the hexagon
                    val trigramRadii = r * 1.25f
                    val trigramAngles = listOf(0f, 60f, 120f, 180f, 240f, 300f)

                    trigramAngles.forEachIndexed { index, tagAngle ->
                        val rad = Math.toRadians(tagAngle.toDouble())
                        val perpAngle = tagAngle + 90f
                        val perpRad = Math.toRadians(perpAngle.toDouble())
                        val dx = Math.cos(perpRad).toFloat() * 6.dp.toPx()
                        val dy = Math.sin(perpRad).toFloat() * 6.dp.toPx()

                        for (level in 0..2) {
                            val levelDist = trigramRadii + (level * 3.dp.toPx())
                            val lx = cx + levelDist * Math.cos(rad).toFloat()
                            val ly = cy + levelDist * Math.sin(rad).toFloat()

                            val isBroken = (index + level) % 2 == 1

                            if (isBroken) {
                                drawLine(
                                    color = ZenGold.copy(alpha = 0.6f),
                                    start = Offset(lx - dx, ly - dy),
                                    end = Offset(lx - dx * 0.25f, ly - dy * 0.25f),
                                    strokeWidth = 1.5.dp.toPx()
                                )
                                drawLine(
                                    color = ZenGold.copy(alpha = 0.6f),
                                    start = Offset(lx + dx * 0.25f, ly + dy * 0.25f),
                                    end = Offset(lx + dx, ly + dy),
                                    strokeWidth = 1.5.dp.toPx()
                                )
                            } else {
                                drawLine(
                                    color = ZenGold.copy(alpha = 0.6f),
                                    start = Offset(lx - dx, ly - dy),
                                    end = Offset(lx + dx, ly + dy),
                                    strokeWidth = 1.5.dp.toPx()
                                )
                            }
                        }
                    }

                    // Regular hexagon Path for clipping
                    val path = Path().apply {
                        for (i in 0..5) {
                            val hexAngle = i * 60f
                            val rad = Math.toRadians(hexAngle.toDouble())
                            val hx = cx + r * Math.cos(rad).toFloat()
                            val hy = cy + r * Math.sin(rad).toFloat()
                            if (i == 0) moveTo(hx, hy) else lineTo(hx, hy)
                        }
                        close()
                    }

                    drawContext.canvas.save()
                    drawContext.canvas.clipPath(path)

                    // Right half Yang
                    drawRect(
                        color = Color(0xFFF1F0EC),
                        topLeft = Offset(cx, cy - r),
                        size = Size(r, r * 2)
                    )
                    // Left half Yin
                    drawRect(
                        color = Color(0xFF141418),
                        topLeft = Offset(cx - r, cy - r),
                        size = Size(r, r * 2)
                    )

                    // Top semicircle (Yang)
                    drawCircle(
                        color = Color(0xFFF1F0EC),
                        radius = r / 2,
                        center = Offset(cx, cy - r / 2)
                    )
                    // Bottom semicircle (Yin)
                    drawCircle(
                        color = Color(0xFF141418),
                        radius = r / 2,
                        center = Offset(cx, cy + r / 2)
                    )
                    // Top dot (Yin)
                    drawCircle(
                        color = Color(0xFF141418),
                        radius = r / 6,
                        center = Offset(cx, cy - r / 2)
                    )
                    // Bottom dot (Yang)
                    drawCircle(
                        color = Color(0xFFF1F0EC),
                        radius = r / 6,
                        center = Offset(cx, cy + r / 2)
                    )

                    drawContext.canvas.restore()

                    // Outer golden hexagon frame
                    for (i in 0..5) {
                        val p1Angle = i * 60f
                        val p2Angle = ((i + 1) % 6) * 60f
                        val r1 = Math.toRadians(p1Angle.toDouble())
                        val r2 = Math.toRadians(p2Angle.toDouble())
                        drawLine(
                            color = ZenGold,
                            start = Offset(cx + r * Math.cos(r1).toFloat(), cy + r * Math.sin(r1).toFloat()),
                            end = Offset(cx + r * Math.cos(r2).toFloat(), cy + r * Math.sin(r2).toFloat()),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }
            }
    )
}

@Composable
fun ThinkingHexagonConstellation() {
    val transition = rememberInfiniteTransition(label = "constellation")

    val orbitAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit"
    )

    Box(
        modifier = Modifier.size(54.dp),
        contentAlignment = Alignment.Center
    ) {
        YinYangHexagonLogo(modifier = Modifier.size(26.dp), isThinking = true, sizeDp = 26)

        val phaseAngles = listOf(0f, 120f, 240f)

        phaseAngles.forEach { phase ->
            val angleRad = Math.toRadians((orbitAngle + phase).toDouble())
            val dx = (15 * Math.cos(angleRad)).toFloat().dp
            val dy = (15 * Math.sin(angleRad)).toFloat().dp

            Box(
                modifier = Modifier
                    .offset(x = dx, y = dy)
                    .size(12.dp)
            ) {
                YinYangHexagonLogo(modifier = Modifier.fillMaxSize(), isThinking = true, sizeDp = 12)
            }
        }
    }
}

@Composable
fun RotatingYinYangSymbol(
    modifier: Modifier = Modifier,
    isThinking: Boolean = false
) {
    val transition = rememberInfiniteTransition(label = "yinyang_rotation")
    val speed = if (isThinking) 1600 else 8000
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = speed, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    val pulseSpeed = if (isThinking) 500 else 2200
    val scale by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = pulseSpeed, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .size(76.dp)
            .drawBehind {
                val r = size.minDimension / 2
                val cx = size.width / 2
                val cy = size.height / 2

                rotate(degrees = angle) {
                    drawArc(
                        color = Color(0xFFF1F0EC),
                        startAngle = -90f,
                        sweepAngle = 180f,
                        useCenter = true
                    )
                    drawArc(
                        color = Color(0xFF141418),
                        startAngle = 90f,
                        sweepAngle = 180f,
                        useCenter = true
                    )
                    drawCircle(
                        color = Color(0xFFF1F0EC),
                        radius = r / 2,
                        center = Offset(cx, cy - r / 2)
                    )
                    drawCircle(
                        color = Color(0xFF141418),
                        radius = r / 2,
                        center = Offset(cx, cy + r / 2)
                    )
                    drawCircle(
                        color = Color(0xFF141418),
                        radius = r / 6,
                        center = Offset(cx, cy - r / 2)
                    )
                    drawCircle(
                        color = Color(0xFFF1F0EC),
                        radius = r / 6,
                        center = Offset(cx, cy + r / 2)
                    )
                }

                drawCircle(
                    color = ZenGold,
                    radius = r,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
    )
}
