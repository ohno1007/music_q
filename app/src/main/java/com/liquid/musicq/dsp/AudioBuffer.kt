package com.liquid.musicq.dsp

/**
 * De-interleaved floating-point audio. Samples are normalized to roughly
 * [-1, 1]. channels[c][n] is sample n of channel c.
 */
class AudioBuffer(
    val channels: Array<FloatArray>,
    var sampleRate: Int
) {
    val channelCount: Int get() = channels.size
    val frameCount: Int get() = if (channels.isEmpty()) 0 else channels[0].size

    fun copy(): AudioBuffer =
        AudioBuffer(Array(channels.size) { channels[it].copyOf() }, sampleRate)

    companion object {
        /** Decode interleaved 16-bit PCM into a float AudioBuffer. */
        fun fromPcm16(pcm: ShortArray, channelCount: Int, sampleRate: Int): AudioBuffer {
            val frames = pcm.size / channelCount
            val ch = Array(channelCount) { FloatArray(frames) }
            var i = 0
            for (n in 0 until frames) {
                for (c in 0 until channelCount) {
                    ch[c][n] = pcm[i++] / 32768f
                }
            }
            return AudioBuffer(ch, sampleRate)
        }
    }

    /** Re-interleave to 16-bit PCM with TPDF dithering already assumed applied. */
    fun toPcm16(): ShortArray {
        val frames = frameCount
        val out = ShortArray(frames * channelCount)
        var i = 0
        for (n in 0 until frames) {
            for (c in 0 until channelCount) {
                var v = channels[c][n]
                if (v > 1f) v = 1f else if (v < -1f) v = -1f
                out[i++] = (v * 32767f).toInt().toShort()
            }
        }
        return out
    }
}
