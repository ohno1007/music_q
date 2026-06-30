package com.liquid.musicq.dsp

import kotlin.math.log10
import kotlin.math.pow

/**
 * Loudness measurement & normalization following ITU-R BS.1770 / EBU R128.
 * The signal is K-weighted (a high-shelf "head" filter + a ~38 Hz high-pass),
 * the mean square is gated in 400 ms blocks at -10 LU relative gating, and the
 * integrated loudness is reported in LUFS. We then apply a constant gain to
 * hit [targetLufs] without touching dynamics (ReplayGain-style).
 */
object LoudnessNormalizer {

    fun measureLufs(buffer: AudioBuffer): Float {
        val fs = buffer.sampleRate.toFloat()
        // per-channel K-weighting filters
        val shelf = Array(buffer.channelCount) { Biquad.highShelf(fs, 1681.97f, 3.99f) }
        val hp = Array(buffer.channelCount) { Biquad.highPass(fs, 38.13f, 0.5f) }

        val frames = buffer.frameCount
        val blockLen = (fs * 0.4f).toInt().coerceAtLeast(1)   // 400 ms
        val step = blockLen / 4                               // 75% overlap
        if (step <= 0) return -70f

        // accumulate K-weighted power per block
        val blockPowers = ArrayList<Float>()
        val weighted = Array(buffer.channelCount) { FloatArray(frames) }
        for (c in 0 until buffer.channelCount) {
            val ch = buffer.channels[c]
            for (i in 0 until frames) {
                weighted[c][i] = hp[c].process(shelf[c].process(ch[i]))
            }
        }

        var start = 0
        while (start + blockLen <= frames) {
            var sum = 0.0
            for (c in 0 until buffer.channelCount) {
                val w = weighted[c]
                var s = 0.0
                for (i in start until start + blockLen) s += (w[i] * w[i]).toDouble()
                sum += s / blockLen
            }
            blockPowers.add(sum.toFloat())
            start += step
        }
        if (blockPowers.isEmpty()) return -70f

        fun loud(power: Double) = -0.691 + 10.0 * log10(power + 1e-12)

        // absolute gate at -70 LUFS
        val gatedAbs = blockPowers.filter { loud(it.toDouble()) > -70.0 }
        if (gatedAbs.isEmpty()) return -70f
        val meanAbs = gatedAbs.map { it.toDouble() }.average()
        // relative gate at -10 LU below the ungated mean
        val relThresh = loud(meanAbs) - 10.0
        val gatedRel = gatedAbs.filter { loud(it.toDouble()) > relThresh }
        val finalMean = (if (gatedRel.isEmpty()) gatedAbs else gatedRel).map { it.toDouble() }.average()
        return loud(finalMean).toFloat()
    }

    fun normalize(buffer: AudioBuffer, targetLufs: Float) {
        val measured = measureLufs(buffer)
        if (measured <= -70f) return
        val gainDb = targetLufs - measured
        val gain = 10f.pow(gainDb / 20f)
        for (c in buffer.channels.indices) {
            val ch = buffer.channels[c]
            for (i in ch.indices) ch[i] *= gain
        }
    }
}
