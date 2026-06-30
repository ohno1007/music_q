package com.liquid.musicq.dsp

/**
 * Toggles and parameters for the whole enhancement chain. Each block maps to
 * one of the algorithm families the user asked for. Order in [AudioEnhancer]
 * matches signal-flow best practice: repair -> bandwidth -> tone -> dynamics
 * -> space -> loudness -> dither.
 */
data class EnhanceConfig(
    // --- Bandwidth extension ---
    var harmonicExciter: Boolean = true,
    var exciterAmount: Float = 0.35f,        // 0..1 drive into the nonlinearity
    var exciterCutoffHz: Float = 6000f,      // only frequencies above this are excited

    var spectralBandReplication: Boolean = true,
    var sbrCutoffHz: Float = 14000f,         // band above this is synthesized from below
    var sbrGainDb: Float = -6f,              // level of replicated band

    var neuralSuperRes: Boolean = false,     // pluggable ONNX model (off unless model present)
    var targetSampleRate: Int = 48000,

    // --- Denoise / repair ---
    var spectralDenoise: Boolean = false,
    var denoiseStrength: Float = 0.6f,       // 0..1 over-subtraction factor
    var deClick: Boolean = false,
    var deClickThreshold: Float = 0.18f,     // sample-to-sample jump that counts as a click

    // --- Tone / EQ ---
    var parametricEq: Boolean = false,
    var eqBands: List<EqBand> = defaultEq(),
    var virtualBass: Boolean = false,        // psychoacoustic low-frequency harmonics
    var virtualBassFreqHz: Float = 90f,

    // --- Dynamics / loudness ---
    var multibandCompressor: Boolean = false,
    var compAmount: Float = 0.4f,            // 0..1 macro that maps to ratio/threshold
    var loudnessNormalize: Boolean = true,
    var targetLufs: Float = -14f,           // streaming reference (EBU R128 / ReplayGain 2)
    var limiter: Boolean = true,
    var limiterCeilingDb: Float = -1f,

    // --- Stereo / space ---
    var stereoWiden: Boolean = false,
    var widenAmount: Float = 0.3f,          // 0..1 mid/side ratio boost
    var haasEffect: Boolean = false,
    var haasMs: Float = 12f,                // inter-channel delay

    // --- Output ---
    var dither: Boolean = true,             // TPDF dither on the final 16-bit truncation
    var ditherBits: Int = 16
) {
    data class EqBand(val freqHz: Float, val gainDb: Float, val q: Float = 1.0f)

    companion object {
        fun defaultEq() = listOf(
            EqBand(60f, 2f, 0.8f),
            EqBand(250f, -1f, 1.0f),
            EqBand(1000f, 0f, 1.0f),
            EqBand(4000f, 2f, 1.2f),
            EqBand(12000f, 3f, 0.9f)
        )

        /** A musical "make it brighter & fuller" starting point. */
        fun warmAndBright() = EnhanceConfig(
            harmonicExciter = true, exciterAmount = 0.4f,
            spectralBandReplication = true,
            parametricEq = true, virtualBass = true,
            loudnessNormalize = true, limiter = true
        )
    }
}
