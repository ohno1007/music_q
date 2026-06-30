package com.liquid.musicq.ui.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Full-screen animated "liquid" backdrop: several soft colour blobs that drift
 * and breathe. The frosted glass panels float on top of this, so the blur and
 * translucency read as Apple-style liquid glass.
 */
@Composable
fun LiquidBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val t = rememberInfiniteTransition(label = "bg")
    val phase by t.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Box(modifier.fillMaxSize().background(Color(0xFF07070E))) {
        Box(
            Modifier
                .fillMaxSize()
                .blur(80.dp)
                .background(blobBrush(phase))
        )
        content()
    }
}

private fun blobBrush(phase: Float): Brush {
    // three drifting radial gradients combined via a sweep-ish layered brush
    fun p(cx: Float, cy: Float) = Offset(cx, cy)
    val c1 = p(0.25f + 0.15f * cos(phase), 0.2f + 0.1f * sin(phase))
    return Brush.radialGradient(
        colorStops = arrayOf(
            0f to Color(0xFF7C5CFF).copy(alpha = 0.9f),
            0.5f to Color(0xFF2BD9FE).copy(alpha = 0.5f),
            1f to Color(0xFFFF5DA2).copy(alpha = 0.35f)
        ),
        center = Offset(1000f * c1.x, 2000f * c1.y),
        radius = 1600f
    )
}

/** A frosted glass surface: translucent fill, soft inner blur sheen, bright edge. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    tintAlpha: Float = 0.12f,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = tintAlpha + 0.06f),
                        Color.White.copy(alpha = tintAlpha)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.55f),
                        Color.White.copy(alpha = 0.08f)
                    )
                ),
                shape = shape
            )
    ) {
        // top sheen highlight
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.10f),
                        0.4f to Color.Transparent
                    )
                )
        )
        Box(Modifier.padding(0.dp)) { content() }
    }
}
