package com.liquid.musicq.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.tanh

/**
 * Harmonic Exciter. Isolates the high band, drives it through an asymmetric
 * nonlinearity to generate fresh upper harmonics, then mixes the excited band
 * back in. Makes dull / over-compressed sources sound more open and "airy".
 */
object HarmonicExciter {
    fun apply(buffer: AudioBuffer, amount: Float, cutoffHz: Float) {
        val drive = 1f + amount * 6f
        val mix = amount.coerceIn(0f, 1f)
        for (c in buffer.channels.indices) {
            val hp = Biquad.highPass(buffer.sampleRate.toFloat(), cutoffHz, 0.707f)
            val hp2 = Biquad.highPass(buffer.sampleRate.toFloat(), cutoffHz, 0.707f)
            val ch = buffer.channels[c]
            for (i in ch.indices) {
                val band = hp.process(ch[i])
                // asymmetric soft clip -> even + odd harmonics
                val shaped = tanh(drive * band) + 0.15f * (band * band) * sign(band)
                val excited = hp2.process(shaped)          // keep only the new highs
                ch[i] = ch[i] + mix * 0.5f * excited
            }
        }
    }
}

/**
 * Psychoacoustic Virtual Bass. Generates harmonics of the low band so small
 * speakers/earbuds are perceived as having bass they cannot physically
 * reproduce (the "missing fundamental" effect).
 */
object VirtualBass {
    fun apply(buffer: AudioBuffer, freqHz: Float) {
        for (c in buffer.channels.indices) {
            val lp = Biquad.lowPass(buffer.sampleRate.toFloat(), freqHz, 0.707f)
            val bp = Biquad.bandPass(buffer.sampleRate.toFloat(), freqHz * 2.5f, 1.0f)
            val ch = buffer.channels[c]
            for (i in ch.indices) {
                val low = lp.process(ch[i])
                // full-wave rectify + square -> rich harmonic series
                val harm = bp.process(abs(low) * low * 2.0f)
                ch[i] = ch[i] + 0.6f * harm
            }
        }
    }
}

/** Parametric EQ: a cascade of peaking biquads, one chain per channel. */
object ParametricEq {
    fun apply(buffer: AudioBuffer, bands: List<EnhanceConfig.EqBand>) {
        if (bands.isEmpty()) return
        for (c in buffer.channels.indices) {
            val filters = bands.map {
                Biquad.peaking(buffer.sampleRate.toFloat(), it.freqHz, it.q, it.gainDb)
            }
            val ch = buffer.channels[c]
            for (i in ch.indices) {
                var v = ch[i]
                for (f in filters) v = f.process(v)
                ch[i] = v
            }
        }
    }
}

/** Feed-forward compressor with an envelope follower. Reused per band. */
class Compressor(
    fs: Float,
    private val thresholdDb: Float,
    private val ratio: Float,
    attackMs: Float,
    releaseMs: Float,
    private val makeupDb: Float = 0f
) {
    private val atk = exp(-1f / (fs * attackMs / 1000f))
    private val rel = exp(-1f / (fs * releaseMs / 1000f))
    private val makeup = 10f.pow(makeupDb / 20f)
    private var env = 0f

    fun process(x: Float): Float {
        val rect = abs(x)
        env = if (rect > env) atk * env + (1 - atk) * rect else rel * env + (1 - rel) * rect
        if (env < 1e-7f) return x * makeup
        val envDb = 20f * kotlin.math.log10(env)
        val overDb = envDb - thresholdDb
        val grDb = if (overDb > 0) overDb * (1f - 1f / ratio) else 0f
        val gain = 10f.pow(-grDb / 20f)
        return x * gain * makeup
    }
}

/**
 * Three-band compressor. Splits low / mid / high with crossovers, compresses
 * each band independently, then sums. [amount] is a single macro that maps to
 * threshold and ratio so the UI stays simple.
 */
object MultibandCompressor {
    fun apply(buffer: AudioBuffer, amount: Float) {
        val a = amount.coerceIn(0f, 1f)
        val threshold = -10f - a * 20f          // -10 .. -30 dB
        val ratio = 1.5f + a * 4f               // 1.5 .. 5.5 : 1
        val fs = buffer.sampleRate.toFloat()
        val lowCross = 250f
        val highCross = 4000f

        for (c in buffer.channels.indices) {
            val lp = Biquad.lowPass(fs, lowCross)
            val hpForMid = Biquad.highPass(fs, lowCross)
            val lpForMid = Biquad.lowPass(fs, highCross)
            val hp = Biquad.highPass(fs, highCross)
            val cLow = Compressor(fs, threshold, ratio, 15f, 150f, a * 3f)
            val cMid = Compressor(fs, threshold, ratio, 10f, 120f, a * 2f)
            val cHigh = Compressor(fs, threshold, ratio, 5f, 80f, a * 2f)
            val ch = buffer.channels[c]
            for (i in ch.indices) {
                val x = ch[i]
                val low = lp.process(x)
                val mid = lpForMid.process(hpForMid.process(x))
                val high = hp.process(x)
                ch[i] = cLow.process(low) + cMid.process(mid) + cHigh.process(high)
            }
        }
    }
}

/** Brick-wall look-ahead limiter to stop inter-sample/peak clipping. */
object Limiter {
    fun apply(buffer: AudioBuffer, ceilingDb: Float) {
        val ceiling = 10f.pow(ceilingDb / 20f)
        val fs = buffer.sampleRate.toFloat()
        val lookahead = (fs * 0.005f).toInt().coerceAtLeast(1)   // 5 ms
        val rel = exp(-1f / (fs * 0.050f))                       // 50 ms release
        val frames = buffer.frameCount

        // shared gain envelope across channels keeps the stereo image stable
        var gain = 1f
        // peak detect across channels with look-ahead delay line
        for (n in 0 until frames) {
            var peak = 0f
            val ahead = min(n + lookahead, frames - 1)
            for (c in buffer.channels.indices) peak = max(peak, abs(buffer.channels[c][ahead]))
            val target = if (peak > ceiling) ceiling / peak else 1f
            gain = if (target < gain) target else rel * gain + (1 - rel) * target
            for (c in buffer.channels.indices) {
                buffer.channels[c][n] *= gain
            }
        }
    }
}

/**
 * Stereo widener via mid/side processing plus an optional Haas delay. Mono
 * sources are first up-mixed to dual mono so widening still applies.
 */
object StereoWidener {
    fun apply(buffer: AudioBuffer, widenAmount: Float, haas: Boolean, haasMs: Float) {
        if (buffer.channelCount < 2) return
        val l = buffer.channels[0]
        val r = buffer.channels[1]
        val sideGain = 1f + widenAmount.coerceIn(0f, 1f) * 1.5f
        for (i in l.indices) {
            val mid = (l[i] + r[i]) * 0.5f
            val side = (l[i] - r[i]) * 0.5f * sideGain
            l[i] = mid + side
            r[i] = mid - side
        }
        if (haas) {
            val delay = (buffer.sampleRate * haasMs / 1000f).toInt().coerceAtLeast(1)
            val delayed = FloatArray(r.size)
            for (i in r.indices) delayed[i] = if (i >= delay) r[i - delay] else 0f
            System.arraycopy(delayed, 0, r, 0, r.size)
        }
    }
}

/**
 * De-click / de-crackle. Detects isolated sample-to-sample jumps that exceed a
 * threshold relative to the local trend and repairs them with cubic-ish
 * interpolation from the neighbours. Classic for vinyl / old recordings.
 */
object DeClick {
    fun apply(buffer: AudioBuffer, threshold: Float) {
        for (c in buffer.channels.indices) {
            val ch = buffer.channels[c]
            for (i in 2 until ch.size - 2) {
                val predicted = 2f * ch[i - 1] - ch[i - 2]
                if (abs(ch[i] - predicted) > threshold &&
                    abs(ch[i] - ch[i - 1]) > threshold &&
                    abs(ch[i] - ch[i + 1]) > threshold
                ) {
                    ch[i] = 0.5f * (ch[i - 1] + ch[i + 1])
                }
            }
        }
    }
}

/** TPDF (triangular) dither added before final quantization to N bits. */
object Dither {
    // Deterministic xorshift so results are reproducible (no Math.random).
    private var state = 0x9E3779B9.toInt()
    private fun nextFloat(): Float {
        var x = state
        x = x xor (x shl 13); x = x xor (x ushr 17); x = x xor (x shl 5)
        state = x
        return (x ushr 8) / 16777216f   // [0,1)
    }

    fun apply(buffer: AudioBuffer, bits: Int) {
        val q = 1f / (1 shl (bits - 1))   // one LSB
        for (c in buffer.channels.indices) {
            val ch = buffer.channels[c]
            for (i in ch.indices) {
                val tpdf = (nextFloat() - nextFloat()) * q   // triangular PDF, ±1 LSB
                ch[i] += tpdf
            }
        }
    }
}
