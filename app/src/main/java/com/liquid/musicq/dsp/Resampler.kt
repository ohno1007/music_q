package com.liquid.musicq.dsp

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/**
 * High-quality sample-rate conversion using a windowed-sinc (Lanczos) kernel.
 * Used both for the "upsample to 44.1/48k" step and as the front-end of the
 * neural super-resolution path. Anti-aliasing is inherent in the sinc kernel.
 */
object Resampler {
    private const val LOBES = 16   // half-width of the sinc window in source samples

    fun resample(buffer: AudioBuffer, targetRate: Int): AudioBuffer {
        if (buffer.sampleRate == targetRate || buffer.frameCount == 0) {
            buffer.sampleRate = targetRate
            return buffer
        }
        val ratio = targetRate.toDouble() / buffer.sampleRate
        val outFrames = (buffer.frameCount * ratio).toInt()
        val out = Array(buffer.channelCount) { FloatArray(outFrames) }
        // when downsampling, scale the cutoff to avoid aliasing
        val cutoff = if (ratio < 1.0) ratio else 1.0

        for (c in buffer.channels.indices) {
            val src = buffer.channels[c]
            val dst = out[c]
            for (i in 0 until outFrames) {
                val srcPos = i / ratio
                val center = floor(srcPos).toInt()
                var acc = 0.0
                var norm = 0.0
                for (j in -LOBES..LOBES) {
                    val idx = center + j
                    if (idx < 0 || idx >= src.size) continue
                    val x = (srcPos - idx) * cutoff
                    val w = lanczos(x, LOBES)
                    acc += src[idx] * w
                    norm += w
                }
                dst[i] = if (norm != 0.0) (acc / norm).toFloat() else 0f
            }
        }
        return AudioBuffer(out, targetRate)
    }

    private fun sinc(x: Double): Double {
        if (x == 0.0) return 1.0
        val px = PI * x
        return sin(px) / px
    }

    private fun lanczos(x: Double, a: Int): Double {
        if (x <= -a || x >= a) return 0.0
        return sinc(x) * sinc(x / a)
    }
}
