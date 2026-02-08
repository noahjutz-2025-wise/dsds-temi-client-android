package com.noahjutz.kinetiquery.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun infiniteRotation(): State<Float> {
    val infiniteTransition = rememberInfiniteTransition(label = "CircleRotation")
    return infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 2000, // 2 seconds for a full rotation
                easing = LinearEasing
            )
        ),
        label = "RotationAngle"
    )
}

val sappGradient
    @Composable get() = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
    )

val bwGradient = listOf(
    Color.White.copy(alpha = 0.5f),
    Color.Transparent
)

val pulseWhite: State<Color>
    @Composable get() {
        val infiniteTransition = rememberInfiniteTransition(label = "pulseWhite")
        val animatedAlpha by infiniteTransition.animateFloat(
            initialValue = 0.5f,
            targetValue = 0.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )
        val color = MaterialTheme.colorScheme.onBackground

        return remember(animatedAlpha) {
            mutableStateOf(color.copy(alpha = animatedAlpha))
        }
    }

@Composable
fun SappCircle(
    angle: Float = 0f,
    fill: Brush = Brush.linearGradient(sappGradient)
) {
    val radius = 24.dp
    val thickness = 8.dp
    Canvas(modifier = Modifier.size(radius * 2)) {
        val center = Offset(size.width / 2f, size.height / 2f)

        rotate(degrees = angle, pivot = center) {
            drawCircle(
                radius = (radius - thickness / 2).toPx(),
                brush = fill,
                style = Stroke(width = thickness.toPx())
            )
        }
    }
}

@Preview
@Composable
fun SappCirclePreview() {
    SappCircle()
}