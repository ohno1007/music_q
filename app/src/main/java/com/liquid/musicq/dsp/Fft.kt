package com.liquid.musicq.dsp

import kotlin.math.cos
import kotlin.math.sin

/**
 * In-place radix-2 Cooley-Tukey FFT operating on separate real/imag arrays.
 * Length must be a power of two. Used by the spectral processors (SBR,
 * spectral subtraction / Wiener denoise) for STFT analysis.
 */
object Fft {

    fun isPowerOfTwo(n: Int): Boolean = n > 0 && (n and (n - 1)) == 0

    /** Forward FFT (no normalization). re/im are modified in place. */
    fun forward(re: FloatArray, im: FloatArray) = transform(re, im, false)

    /** Inverse FFT. Normalizes by 1/N. re/im are modified in place. */
    fun inverse(re: FloatArray, im: FloatArray) {
        transform(re, im, true)
        val n = re.size
        val inv = 1f / n
        for (i in 0 until n) {
            re[i] *= inv
            im[i] *= inv
        }
    }

    private fun transform(re: FloatArray, im: FloatArray, inverse: Boolean) {
        val n = re.size
        require(isPowerOfTwo(n)) { "FFT length must be a power of two, was $n" }

        // Bit-reversal permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val ang = (if (inverse) 2.0 else -2.0) * Math.PI / len
            val wpr = cos(ang).toFloat()
            val wpi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var wr = 1f
                var wi = 0f
                val half = len shr 1
                for (k in 0 until half) {
                    val a = i + k
                    val b = a + half
                    val xr = re[b] * wr - im[b] * wi
                    val xi = re[b] * wi + im[b] * wr
                    re[b] = re[a] - xr
                    im[b] = im[a] - xi
                    re[a] += xr
                    im[a] += xi
                    val tmp = wr
                    wr = wr * wpr - wi * wpi
                    wi = tmp * wpi + wi * wpr
                }
                i += len
            }
            len = len shl 1
        }
    }
}
