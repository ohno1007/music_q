package com.liquid.musicq.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Short-time Fourier transform with a Hann window and overlap-add resynthesis.
 * Used by SBR and the spectral denoiser. Processes a single channel.
 *
 * The frame callback receives the per-bin magnitude and phase for the first
 * half-spectrum (0..fftSize/2) and may modify them in place before resynthesis.
 */
class Stft(
    private val fftSize: Int = 2048,
    private val hop: Int = 512
) {
    private val window = FloatArray(fftSize) {
        (0.5f - 0.5f * cos(2.0 * PI * it / (fftSize - 1)).toFloat())
    }
    // Normalization so overlap-add of squared windows reconstructs unity gain.
    private val winNorm: Float = run {
        var acc = 0f
        var i = 0
        while (i < fftSize) { acc += window[i] * window[i]; i += hop }
        // acc approximates the constant overlap-add of w^2; guard against zero.
        if (acc <= 1e-6f) 1f else 1f / acc
    }

    fun process(signal: FloatArray, frame: (mag: FloatArray, phase: FloatArray, bins: Int) -> Unit): FloatArray {
        val n = signal.size
        val out = FloatArray(n + fftSize)
        val re = FloatArray(fftSize)
        val im = FloatArray(fftSize)
        val bins = fftSize / 2
        val mag = FloatArray(bins + 1)
        val phase = FloatArray(bins + 1)

        var pos = 0
        while (pos < n) {
            // window the frame
            for (i in 0 until fftSize) {
                val s = pos + i
                re[i] = if (s < n) signal[s] * window[i] else 0f
                im[i] = 0f
            }
            Fft.forward(re, im)
            for (k in 0..bins) {
                val rr = re[k]; val ii = im[k]
                mag[k] = sqrt(rr * rr + ii * ii)
                phase[k] = Math.atan2(ii.toDouble(), rr.toDouble()).toFloat()
            }

            frame(mag, phase, bins)

            // rebuild full spectrum (Hermitian symmetry)
            for (k in 0..bins) {
                val m = mag[k]; val p = phase[k]
                re[k] = m * cos(p)
                im[k] = m * kotlin.math.sin(p)
            }
            for (k in 1 until bins) {
                re[fftSize - k] = re[k]
                im[fftSize - k] = -im[k]
            }
            Fft.inverse(re, im)

            for (i in 0 until fftSize) {
                out[pos + i] += re[i] * window[i] * winNorm
            }
            pos += hop
        }
        return out.copyOf(n)
    }
}
