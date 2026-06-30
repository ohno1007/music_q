package com.liquid.musicq.ui.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Holds the rendered backdrop (the blurred album-cover halo) so every glass
 * panel can sample the exact pixels sitting behind it and refract them. This is
 * the key to "real" liquid glass rather than a flat translucent overlay.
 */
val LocalGlassBackdrop = compositionLocalOf<GraphicsLayer?> { null }

/**
 * AGSL shader (Android 13+) that takes the backdrop as `content` and bends the
 * sample coordinates near the rounded-rect edges — a lens/refraction effect —
 * plus a bright specular rim. Interior stays clear so the blur shows through.
 */
private const val GLASS_SHADER = """
uniform shader content;
uniform float2 size;
uniform float radius;
uniform float refraction;

float sdRoundRect(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - r;
}

half4 main(float2 coord) {
    float2 b = size * 0.5;
    float2 p = coord - b;
    float d = sdRoundRect(p, b, radius);

    // numeric gradient = outward normal of the rounded rect
    float e = 1.0;
    float gx = sdRoundRect(p + float2(e, 0.0), b, radius) - sdRoundRect(p - float2(e, 0.0), b, radius);
    float gy = sdRoundRect(p + float2(0.0, e), b, radius) - sdRoundRect(p - float2(0.0, e), b, radius);
    float2 n = normalize(float2(gx, gy) + float2(1e-6));

    // displacement is strongest right at the edge and fades over ~36px inward
    float band = 1.0 - smoothstep(0.0, 36.0, -d);
    float2 sampleCoord = coord - n * band * refraction;
    half4 col = content.eval(sampleCoord);

    // glassy specular rim
    float rim = smoothstep(0.0, 4.0, -d) - smoothstep(4.0, 12.0, -d);
    col.rgb += half3(rim * 0.30);

    // gentle inner darkening toward the edge for depth
    col.rgb -= half3(band * 0.04);
    return col;
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun buildGlassEffect(
    widthPx: Float,
    heightPx: Float,
    radiusPx: Float,
    blurPx: Float,
    refractionPx: Float
): RenderEffect {
    val blur = RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
    return try {
        val shader = RuntimeShader(GLASS_SHADER).apply {
            setFloatUniform("size", widthPx, heightPx)
            setFloatUniform("radius", radiusPx)
            setFloatUniform("refraction", refractionPx)
        }
        val refract = RenderEffect.createRuntimeShaderEffect(shader, "content")
        // inner (blur) runs first, then the refraction shader samples the blurred result
        RenderEffect.createChainEffect(refract, blur)
    } catch (t: Throwable) {
        blur // any AGSL/driver issue → graceful blur-only glass
    }
}

/**
 * A frosted, refracting glass panel. On Android 13+ it samples the shared
 * [LocalGlassBackdrop] under its own bounds and runs the refraction shader;
 * on 12 it falls back to a plain blur of the backdrop; below that, a tasteful
 * translucent tint. A bright edge + top sheen sells the glass on every device.
 */
@Composable
fun LiquidGlass(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    blur: Dp = 24.dp,
    refraction: Dp = 22.dp,
    tint: Color = Color.White.copy(alpha = 0.10f),
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val backdrop = LocalGlassBackdrop.current
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    var sizePx by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier
            .clip(shape)
            .onGloballyPositioned {
                origin = it.positionInRoot()
                sizePx = Offset(it.size.width.toFloat(), it.size.height.toFloat())
            }
    ) {
        // --- refracting backdrop layer ---
        if (backdrop != null && sizePx.x > 0f) {
            val radiusPx = with(density) { cornerRadius.toPx() }
            val blurPx = with(density) { blur.toPx() }
            val refrPx = with(density) { refraction.toPx() }
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        clip = true
                        this.shape = shape
                        renderEffect = when {
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                                buildGlassEffect(sizePx.x, sizePx.y, radiusPx, blurPx, refrPx)
                                    .asComposeRenderEffect()
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                                RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
                                    .asComposeRenderEffect()
                            else -> null
                        }
                    }
                    .drawBehind {
                        // draw the captured backdrop, shifted so the slice under
                        // this panel lines up exactly with what's behind it
                        translate(-origin.x, -origin.y) {
                            drawLayer(backdrop)
                        }
                    }
            )
        }

        // --- frost tint + sheen + bright edge ---
        Box(
            Modifier
                .fillMaxSize()
                .background(tint)
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.16f),
                        0.35f to Color.Transparent,
                        1f to Color.White.copy(alpha = 0.05f)
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.65f), Color.White.copy(alpha = 0.10f))
                    ),
                    shape = shape
                )
        )
        content()
    }
}

/**
 * Records [content] (the album-cover halo background) into a shared
 * GraphicsLayer and exposes it via [LocalGlassBackdrop] so glass panels above
 * can refract it. Place this at the root, behind everything else.
 */
@Composable
fun GlassBackdropHost(
    modifier: Modifier = Modifier,
    background: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val layer = rememberGraphicsLayer()
    Box(modifier.fillMaxSize()) {
        // Record the background into the shared layer, then paint it on screen.
        // Glass panels above sample this same layer to refract it.
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                }
        ) { background() }

        CompositionLocalProvider(LocalGlassBackdrop provides layer) {
            content()
        }
    }
}

