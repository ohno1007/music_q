# Liquid MusicQ

An Android music player with an Apple-style **Liquid Glass** UI that pulls
tracks from a cloud provider (QQ Music) via a pasted cookie, downloads them
locally, and runs a full **audio-enhancement / restoration DSP chain** on each
track.

> ⚠️ For personal/educational use. QQ Music has no public API — the cloud
> client talks to the same web endpoints the browser player uses and requires
> *your own* logged-in cookie. Endpoints and signing change over time; if
> search/download stops returning results, refresh the cookie.

## Features

### Liquid Glass UI (Jetpack Compose)
- Animated drifting colour-blob backdrop with heavy blur
- Frosted translucent panels with bright edge highlights & top sheen
- Glass bottom navigation, mini-player and download banner

### Cloud + local
- Paste QQ Music cookie in **Settings**
- Search, pick quality (128k → Hi-Res FLAC), download to app storage
- Plays local files with ExoPlayer (Media3)

### Audio enhancement DSP (pure Kotlin, `com.liquid.musicq.dsp`)
Signal flow: **repair → bandwidth → tone → dynamics → space → loudness → dither**

| Family | Implementation |
|--------|----------------|
| Bandwidth extension | `HarmonicExciter` (nonlinear high-band waveshaping), `SpectralBandReplication` (STFT translate-up, AAC+ style), `Resampler` (windowed-sinc / Lanczos upsample) |
| Neural super-res | `NeuralSuperRes` — pluggable NU-Wave/AudioSR ONNX hook (inert until a model is dropped in; falls back to sinc + SBR) |
| Repair | `SpectralDenoise` (minimum-statistics noise tracking + Wiener gain), `DeClick` (de-crackle via predictive interpolation) |
| Tone | `ParametricEq` (RBJ peaking biquads), `VirtualBass` (psychoacoustic missing-fundamental harmonics) |
| Dynamics | `MultibandCompressor` (3-band crossover + feed-forward comp), `Limiter` (look-ahead brick-wall) |
| Loudness | `LoudnessNormalizer` (ITU-R BS.1770 / EBU R128 K-weighting, gated LUFS, ReplayGain-style gain) |
| Stereo | `StereoWidener` (mid/side widening + Haas delay) |
| Output | `Dither` (TPDF), high-quality resample |

All DSP is implemented from scratch (FFT, biquads, STFT overlap-add) so the
build needs no native libraries.

## Build

Requirements: JDK 17+, Android SDK (platform 34, build-tools 34).

```bash
# point the build at your SDK
echo "sdk.dir=/path/to/Android/sdk" > local.properties

./gradlew :app:assembleDebug      # debug APK
./gradlew :app:assembleRelease    # unsigned release APK
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

## Notes / honest caveats
- **QQ Music signing** changes frequently; HQ/lossless tiers need a VIP cookie
  and may be region-locked. The client falls back through lower tiers.
- **Neural super-resolution** models (NU-Wave, AudioSR) are 100s of MB and must
  be converted/quantized for mobile, so none is bundled. The integration point
  is wired (`NeuralSuperRes.runModel`) — drop `superres.onnx` into the app's
  files dir and add `onnxruntime-android` to enable it.
- Enhanced output is written as 16-bit WAV next to the original download.
