package com.liquid.musicq.dsp

import kotlin.math.max
import kotlin.math.pow

/**
 * Spectral Band Replication. For tracks that were lossily encoded (AAC/MP3)
 * the top octave is often missing. We synthesize it by translating the band
 * just below the cutoff up into the empty high band, scaled down, and only
 * where little energy already exists. This is the classic SBR idea used by
 * AAC+ / HE-AAC decoders, applied as a post-process.
 */
object SpectralBandReplication {
    fun apply(buffer: AudioBuffer, cutoffHz: Float, gainDb: Float) {
        val fftSize = 2048
        val stft = Stft(fftSize, fftSize / 4)
        val binHz = buffer.sampleRate.toFloat() / fftSize
        val cutoffBin = (cutoffHz / binHz).toInt().coerceIn(2, fftSize / 2 - 2)
        val gain = 10f.pow(gainDb / 20f)
        // width of the source band = everything from cutoffBin/2 to cutoffBin
        val srcStart = cutoffBin / 2

        for (c in buffer.channels.indices) {
            buffer.channels[c] = stft.process(buffer.channels[c]) { mag, phase, bins ->
                var k = cutoffBin
                var src = srcStart
                while (k <= bins) {
                    if (src >= cutoffBin) src = srcStart
                    val candidate = mag[src] * gain
                    // fill: only lift the high band when it is quieter than the
                    // synthesized contribution, so real high content is preserved.
                    if (candidate > mag[k]) {
                        mag[k] = candidate
                        phase[k] = phase[src]
                    }
                    k++; src++
                }
            }
        }
    }
}

/**
 * Single-channel spectral denoise combining minimum-statistics noise tracking
 * with a Wiener-style suppression gain. Good for hiss / broadband noise on old
 * recordings. Over-subtraction is controlled by [strength].
 */
object SpectralDenoise {
    fun apply(buffer: AudioBuffer, strength: Float) {
        val fftSize = 2048
        val stft = Stft(fftSize, fftSize / 4)
        val bins = fftSize / 2
        val over = 1f + 2f * strength.coerceIn(0f, 1f)   // over-subtraction factor
        val floor = 0.05f                                // spectral floor (avoid musical noise)

        for (c in buffer.channels.indices) {
            val noise = FloatArray(bins + 1) { Float.MAX_VALUE }
            var warm = 0
            buffer.channels[c] = stft.process(buffer.channels[c]) { mag, _, b ->
                // track the noise floor per bin via a slow minimum follower
                for (k in 0..b) {
                    if (mag[k] < noise[k]) noise[k] = mag[k]
                    else noise[k] = 0.995f * noise[k] + 0.005f * mag[k]
                }
                warm++
                if (warm > 4) {
                    for (k in 0..b) {
                        val power = mag[k] * mag[k]
                        val noisePow = (over * noise[k]).pow(2)
                        val gain = max(floor, (power - noisePow) / (power + 1e-9f))
                        mag[k] *= gain
                    }
                }
            }
        }
    }
}
