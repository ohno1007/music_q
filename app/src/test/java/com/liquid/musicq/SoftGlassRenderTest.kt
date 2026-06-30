package com.liquid.musicq

import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure-JVM reference render of the liquid-glass lens. It runs the SAME signed-
 * distance-field displacement math the on-device AGSL shader uses
 * (LiquidGlass.GLASS_SHADER) over a high-frequency grid, so the refraction can
 * be eyeballed without a GPU. Android's unit-test classpath has no AWT/ImageIO,
 * so a minimal PNG encoder (java.util.zip only) writes the result.
 */
class SoftGlassRenderTest {

    private val W = 700
    private val H = 460
    private val panelW = 460f
    private val panelH = 240f
    private val radius = 56f
    private val strength = 42f

    @Test
    fun render_software_refraction() {
        val bg = buildGrid()
        val out = IntArray(W * H)

        val cx = W / 2f; val cy = H / 2f
        val bx = panelW / 2f; val by = panelH / 2f

        for (y in 0 until H) for (x in 0 until W) {
            val px = x - cx; val py = y - cy
            val d = sdRoundRect(px, py, bx, by, radius)
            if (d >= 0f) { out[y * W + x] = bg[y * W + x]; continue }
            val edge = 1f - smoothstep(0f, radius, -d)
            val e = 1.5f
            val gx = sdRoundRect(px + e, py, bx, by, radius) - sdRoundRect(px - e, py, bx, by, radius)
            val gy = sdRoundRect(px, py + e, bx, by, radius) - sdRoundRect(px, py - e, bx, by, radius)
            val nl = sqrt(gx * gx + gy * gy) + 1e-6f
            val nx = gx / nl; val ny = gy / nl
            val maxc = max(bx, by)
            val sxf = x - nx * edge * edge * strength - (px / maxc) * (1f - edge) * strength * 0.6f
            val syf = y - ny * edge * edge * strength - (py / maxc) * (1f - edge) * strength * 0.6f
            var rgb = sampleBilinear(bg, sxf, syf)
            val rim = smoothstep(0f, 3f, -d) - smoothstep(3f, 12f, -d)
            rgb = add(rgb, rim * 0.45f)
            rgb = mix(rgb, 0xFFFFFF, 0.06f)
            out[y * W + x] = rgb
        }

        val f = File("build/outputs/roborazzi/software_refraction.png")
        f.parentFile.mkdirs()
        f.writeBytes(encodePng(out, W, H))
        assert(f.exists() && f.length() > 0)
    }

    private fun buildGrid(): IntArray {
        val img = IntArray(W * H)
        for (y in 0 until H) for (x in 0 until W) {
            val r = 40 + 180 * x / W
            val g = 30 + 120 * y / H
            val b = 90 + 140 * (W - x) / W
            var c = (r shl 16) or (g shl 8) or b
            if (x % 28 == 0) c = 0x49E0FF
            if (y % 28 == 0) c = 0xFF6FB5
            img[y * W + x] = c
        }
        return img
    }

    private fun sdRoundRect(px: Float, py: Float, bx: Float, by: Float, r: Float): Float {
        val qx = abs(px) - bx + r
        val qy = abs(py) - by + r
        val outside = sqrt(max(qx, 0f) * max(qx, 0f) + max(qy, 0f) * max(qy, 0f))
        return min(max(qx, qy), 0f) + outside - r
    }

    private fun smoothstep(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    private fun sampleBilinear(img: IntArray, fx: Float, fy: Float): Int {
        val x = fx.coerceIn(0f, (W - 1).toFloat()); val y = fy.coerceIn(0f, (H - 1).toFloat())
        val x0 = x.toInt(); val y0 = y.toInt()
        val x1 = min(x0 + 1, W - 1); val y1 = min(y0 + 1, H - 1)
        val tx = x - x0; val ty = y - y0
        val c00 = img[y0 * W + x0]; val c10 = img[y0 * W + x1]
        val c01 = img[y1 * W + x0]; val c11 = img[y1 * W + x1]
        val top = lerpRgb(c00, c10, tx); val bot = lerpRgb(c01, c11, tx)
        return lerpRgb(top, bot, ty)
    }

    private fun lerpRgb(a: Int, b: Int, t: Float): Int {
        val r = (((a shr 16) and 0xFF) + (((b shr 16) and 0xFF) - ((a shr 16) and 0xFF)) * t).toInt()
        val g = (((a shr 8) and 0xFF) + (((b shr 8) and 0xFF) - ((a shr 8) and 0xFF)) * t).toInt()
        val bl = ((a and 0xFF) + ((b and 0xFF) - (a and 0xFF)) * t).toInt()
        return (r shl 16) or (g shl 8) or bl
    }

    private fun add(rgb: Int, amt: Float): Int {
        val a = (amt * 255).toInt()
        val r = min(255, ((rgb shr 16) and 0xFF) + a)
        val g = min(255, ((rgb shr 8) and 0xFF) + a)
        val b = min(255, (rgb and 0xFF) + a)
        return (r shl 16) or (g shl 8) or b
    }

    private fun mix(rgb: Int, other: Int, t: Float): Int {
        val r = (((rgb shr 16) and 0xFF) * (1 - t) + ((other shr 16) and 0xFF) * t).toInt()
        val g = (((rgb shr 8) and 0xFF) * (1 - t) + ((other shr 8) and 0xFF) * t).toInt()
        val b = ((rgb and 0xFF) * (1 - t) + (other and 0xFF) * t).toInt()
        return (r shl 16) or (g shl 8) or b
    }

    // --- minimal PNG (truecolour, 8-bit) using only java.util.zip ---
    private fun encodePng(pixels: IntArray, w: Int, h: Int): ByteArray {
        val raw = ByteArray(h * (1 + w * 3))
        var p = 0
        for (y in 0 until h) {
            raw[p++] = 0 // filter: none
            for (x in 0 until w) {
                val c = pixels[y * w + x]
                raw[p++] = ((c shr 16) and 0xFF).toByte()
                raw[p++] = ((c shr 8) and 0xFF).toByte()
                raw[p++] = (c and 0xFF).toByte()
            }
        }
        val deflater = Deflater(Deflater.BEST_SPEED)
        deflater.setInput(raw); deflater.finish()
        val comp = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (!deflater.finished()) comp.write(buf, 0, deflater.deflate(buf))
        deflater.end()

        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
        val ihdr = ByteArrayOutputStream()
        ihdr.write(intBytes(w)); ihdr.write(intBytes(h))
        ihdr.write(byteArrayOf(8, 2, 0, 0, 0)) // 8-bit, truecolour
        writeChunk(out, "IHDR", ihdr.toByteArray())
        writeChunk(out, "IDAT", comp.toByteArray())
        writeChunk(out, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun writeChunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        out.write(intBytes(data.size))
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        out.write(typeBytes); out.write(data)
        val crc = CRC32(); crc.update(typeBytes); crc.update(data)
        out.write(intBytes(crc.value.toInt()))
    }

    private fun intBytes(v: Int) = byteArrayOf(
        (v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte()
    )
}
