package com.shizzi.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.shizzi.app.ui.theme.ShizziPink
import com.shizzi.app.ui.theme.ShizziViolet

/** The big animated status ring on the home screen - glows and rotates while connected. */
@Composable
fun ConnectionRing(
    connected: Boolean,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 260.dp,
) {
    val infinite = rememberInfiniteTransition(label = "ring")
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation",
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .size(size)
                .rotate(if (connected) rotation else 0f),
        ) {
            val outerStroke = Stroke(width = 26f, cap = StrokeCap.Round)
            val innerStroke = Stroke(width = 26f, cap = StrokeCap.Round)
            val outerBrush = Brush.sweepGradient(listOf(ShizziViolet, ShizziPink, ShizziViolet))
            val innerBrush = Brush.sweepGradient(listOf(ShizziPink, ShizziViolet, ShizziPink))

            val outerInset = 20f
            drawArc(
                brush = outerBrush,
                startAngle = -200f,
                sweepAngle = 220f,
                useCenter = false,
                style = outerStroke,
                topLeft = Offset(outerInset, outerInset),
                size = this.size.copy(width = this.size.width - outerInset * 2, height = this.size.height - outerInset * 2),
                alpha = if (connected) pulse else 0.35f,
            )

            val innerInset = 70f
            drawArc(
                brush = innerBrush,
                startAngle = -160f,
                sweepAngle = 220f,
                useCenter = false,
                style = innerStroke,
                topLeft = Offset(innerInset, innerInset),
                size = this.size.copy(width = this.size.width - innerInset * 2, height = this.size.height - innerInset * 2),
                alpha = if (connected) pulse else 0.35f,
            )

            drawCircle(
                color = if (connected) ShizziPink else Color(0xFF4A4A55),
                radius = 46f,
            )
        }

        Icon(
            imageVector = Icons.Filled.SettingsInputAntenna,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(40.dp),
        )
    }
}
