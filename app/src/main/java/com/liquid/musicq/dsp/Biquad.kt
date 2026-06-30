package com.liquid.musicq.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.ln
import kotlin.math.pow

/**
 * Direct-Form-I biquad filter. Coefficients follow the RBJ Audio EQ Cookbook.
 * Each instance keeps its own delay line, so use one per channel.
 */
class Biquad private constructor(
    private val b0: Float, private val b1: Float, private val b2: Float,
    private val a1: Float, private val a2: Float
) {
    private var x1 = 0f; private var x2 = 0f
    private var y1 = 0f; private var y2 = 0f

    fun process(x: Float): Float {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x
        y2 = y1; y1 = y
        return y
    }

    fun reset() { x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f }

    companion object {
        fun peaking(fs: Float, freq: Float, q: Float, gainDb: Float): Biquad {
            val a = 10f.pow(gainDb / 40f)
            val w0 = 2f * PI.toFloat() * freq / fs
            val cw = cos(w0); val sw = sin(w0)
            val alpha = sw / (2f * q)
            val b0 = 1 + alpha * a
            val b1 = -2 * cw
            val b2 = 1 - alpha * a
            val a0 = 1 + alpha / a
            val a1 = -2 * cw
            val a2 = 1 - alpha / a
            return norm(b0, b1, b2, a0, a1, a2)
        }

        fun lowShelf(fs: Float, freq: Float, gainDb: Float, s: Float = 1f): Biquad {
            val a = 10f.pow(gainDb / 40f)
            val w0 = 2f * PI.toFloat() * freq / fs
            val cw = cos(w0); val sw = sin(w0)
            val alpha = sw / 2f * sqrt((a + 1 / a) * (1 / s - 1) + 2)
            val twoSqrtAAlpha = 2 * sqrt(a) * alpha
            val b0 = a * ((a + 1) - (a - 1) * cw + twoSqrtAAlpha)
            val b1 = 2 * a * ((a - 1) - (a + 1) * cw)
            val b2 = a * ((a + 1) - (a - 1) * cw - twoSqrtAAlpha)
            val a0 = (a + 1) + (a - 1) * cw + twoSqrtAAlpha
            val a1 = -2 * ((a - 1) + (a + 1) * cw)
            val a2 = (a + 1) + (a - 1) * cw - twoSqrtAAlpha
            return norm(b0, b1, b2, a0, a1, a2)
        }

        fun highShelf(fs: Float, freq: Float, gainDb: Float, s: Float = 1f): Biquad {
            val a = 10f.pow(gainDb / 40f)
            val w0 = 2f * PI.toFloat() * freq / fs
            val cw = cos(w0); val sw = sin(w0)
            val alpha = sw / 2f * sqrt((a + 1 / a) * (1 / s - 1) + 2)
            val twoSqrtAAlpha = 2 * sqrt(a) * alpha
            val b0 = a * ((a + 1) + (a - 1) * cw + twoSqrtAAlpha)
            val b1 = -2 * a * ((a - 1) + (a + 1) * cw)
            val b2 = a * ((a + 1) + (a - 1) * cw - twoSqrtAAlpha)
            val a0 = (a + 1) - (a - 1) * cw + twoSqrtAAlpha
            val a1 = 2 * ((a - 1) - (a + 1) * cw)
            val a2 = (a + 1) - (a - 1) * cw - twoSqrtAAlpha
            return norm(b0, b1, b2, a0, a1, a2)
        }

        fun highPass(fs: Float, freq: Float, q: Float = 0.707f): Biquad {
            val w0 = 2f * PI.toFloat() * freq / fs
            val cw = cos(w0); val sw = sin(w0)
            val alpha = sw / (2f * q)
            val b0 = (1 + cw) / 2
            val b1 = -(1 + cw)
            val b2 = (1 + cw) / 2
            val a0 = 1 + alpha
            val a1 = -2 * cw
            val a2 = 1 - alpha
            return norm(b0, b1, b2, a0, a1, a2)
        }

        fun lowPass(fs: Float, freq: Float, q: Float = 0.707f): Biquad {
            val w0 = 2f * PI.toFloat() * freq / fs
            val cw = cos(w0); val sw = sin(w0)
            val alpha = sw / (2f * q)
            val b0 = (1 - cw) / 2
            val b1 = 1 - cw
            val b2 = (1 - cw) / 2
            val a0 = 1 + alpha
            val a1 = -2 * cw
            val a2 = 1 - alpha
            return norm(b0, b1, b2, a0, a1, a2)
        }

        /** Band-pass (constant 0 dB peak gain), used as crossover band for multiband comp. */
        fun bandPass(fs: Float, freq: Float, q: Float): Biquad {
            val w0 = 2f * PI.toFloat() * freq / fs
            val cw = cos(w0); val sw = sin(w0)
            val alpha = sw / (2f * q)
            val b0 = alpha
            val b1 = 0f
            val b2 = -alpha
            val a0 = 1 + alpha
            val a1 = -2 * cw
            val a2 = 1 - alpha
            return norm(b0, b1, b2, a0, a1, a2)
        }

        private fun norm(b0: Float, b1: Float, b2: Float, a0: Float, a1: Float, a2: Float) =
            Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)

        // touch ln/sinh so they are kept available for future filter types
        @Suppress("unused")
        private val keep = ln(1.0) + sinh(0.0)
    }
}
