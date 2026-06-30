package com.liquid.musicq.dsp

import android.util.Log
import java.io.File

/**
 * Orchestrates the whole enhancement chain over an [AudioBuffer]. Signal flow
 * follows mastering best practice:
 *
 *   repair (de-click, denoise)
 *     -> bandwidth extension (super-res / SBR / exciter / virtual bass)
 *       -> tone (parametric EQ)
 *         -> dynamics (multiband compression)
 *           -> space (stereo widening / Haas)
 *             -> loudness (R128 normalize + limiter)
 *               -> dither
 *
 * [progress] is reported 0..1 so the UI can show a determinate bar.
 */
class AudioEnhancer(private val superResModel: File? = null) {

    fun process(
        input: AudioBuffer,
        config: EnhanceConfig,
        progress: (Float) -> Unit = {}
    ): AudioBuffer {
        var buf = input
        val steps = mutableListOf<Pair<String, () -> Unit>>()

        // 1. Repair
        if (config.deClick) steps += "de-click" to { DeClick.apply(buf, config.deClickThreshold) }
        if (config.spectralDenoise) steps += "denoise" to { SpectralDenoise.apply(buf, config.denoiseStrength) }

        // 2. Bandwidth extension
        if (config.neuralSuperRes || buf.sampleRate < config.targetSampleRate) {
            steps += "super-res" to {
                val net = NeuralSuperRes(superResModel)
                val ran = config.neuralSuperRes && net.enhance(buf, config.targetSampleRate)
                if (!ran && buf.sampleRate != config.targetSampleRate) {
                    buf = Resampler.resample(buf, config.targetSampleRate)
                }
            }
        }
        if (config.spectralBandReplication) steps += "SBR" to {
            SpectralBandReplication.apply(buf, config.sbrCutoffHz, config.sbrGainDb)
        }
        if (config.harmonicExciter) steps += "exciter" to {
            HarmonicExciter.apply(buf, config.exciterAmount, config.exciterCutoffHz)
        }
        if (config.virtualBass) steps += "virtual-bass" to {
            VirtualBass.apply(buf, config.virtualBassFreqHz)
        }

        // 3. Tone
        if (config.parametricEq) steps += "EQ" to { ParametricEq.apply(buf, config.eqBands) }

        // 4. Dynamics
        if (config.multibandCompressor) steps += "compress" to {
            MultibandCompressor.apply(buf, config.compAmount)
        }

        // 5. Space
        if (config.stereoWiden || config.haasEffect) steps += "stereo" to {
            StereoWidener.apply(buf, config.widenAmount, config.haasEffect, config.haasMs)
        }

        // 6. Loudness
        if (config.loudnessNormalize) steps += "loudness" to {
            LoudnessNormalizer.normalize(buf, config.targetLufs)
        }
        if (config.limiter) steps += "limiter" to { Limiter.apply(buf, config.limiterCeilingDb) }

        // 7. Dither
        if (config.dither) steps += "dither" to { Dither.apply(buf, config.ditherBits) }

        val total = steps.size.coerceAtLeast(1)
        steps.forEachIndexed { i, (name, op) ->
            val t = System.currentTimeMillis()
            op()
            Log.i(TAG, "stage '$name' took ${System.currentTimeMillis() - t} ms")
            progress((i + 1f) / total)
        }
        return buf
    }

    companion object { private const val TAG = "AudioEnhancer" }
}
