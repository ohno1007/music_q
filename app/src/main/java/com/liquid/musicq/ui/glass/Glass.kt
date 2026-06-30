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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlin.math.cos
import kotlin.math.sin

/**
 * Apple-Music-style background: the current album cover blown up and heavily
 * gaussian-blurred into a soft colour halo, with a slow breathing drift and a
 * dark vignette so foreground text stays readable. Falls back to animated
 * colour blobs when there is no cover yet.
 */
@Composable
fun CoverHaloBackground(coverUrl: String?, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "halo")
    val drift by t.animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(24000, easing = LinearEasing), RepeatMode.Restart),
        label = "drift"
    )
    val pulse by t.animateFloat(
        initialValue = 1.35f, targetValue = 1.55f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Box(modifier.fillMaxSize().background(Color(0xFF07070E))) {
        if (!coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = pulse; scaleY = pulse
                        translationX = 60f * cos(drift)
                        translationY = 50f * sin(drift)
                    }
                    .blur(54.dp)
            )
            // colour wash + vignette so the halo glows but text stays legible
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color(0x3307070E),
                        0.5f to Color(0x66050509),
                        1f to Color(0xE6040409)
                    )
                )
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color(0x99000000)),
                        center = Offset.Unspecified,
                        radius = 1400f
                    )
                )
            )
        } else {
            Box(Modifier.fillMaxSize().blur(80.dp).background(blobBrush(drift)))
        }
    }
}

private fun blobBrush(phase: Float): Brush {
    val cx = 0.25f + 0.15f * cos(phase)
    val cy = 0.2f + 0.1f * sin(phase)
    return Brush.radialGradient(
        colorStops = arrayOf(
            0f to Color(0xFF7C5CFF).copy(alpha = 0.9f),
            0.5f to Color(0xFF2BD9FE).copy(alpha = 0.5f),
            1f to Color(0xFFFF5DA2).copy(alpha = 0.35f)
        ),
        center = Offset(1000f * cx, 2000f * cy),
        radius = 1600f
    )
}

/**
 * Lightweight translucent panel (no backdrop sampling) for dense list rows
 * where running a per-item refraction shader would be wasteful. For floating
 * chrome (nav bar, player, hero buttons) use [LiquidGlass] instead.
 */
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
                    listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.08f))
                ),
                shape = shape
            )
    ) {
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.10f),
                    0.4f to Color.Transparent
                )
            )
        )
        content()
    }
}
