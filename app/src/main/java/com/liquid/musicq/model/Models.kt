package com.liquid.musicq.model

/** A song returned by a cloud music provider. */
data class Song(
    val id: String,
    val mid: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumMid: String,
    val durationSec: Int,
    val coverUrl: String,
    val source: String = "qq",
    /** Streamable 30s preview (iTunes) or full URL; null when only downloadable. */
    val previewUrl: String? = null
) {
    val subtitle: String get() = if (album.isBlank()) artist else "$artist • $album"
}

/** A single time-stamped lyric line parsed from an LRC file. */
data class LrcLine(val timeMs: Long, val text: String)

/** A locally downloaded track on disk. */
data class LocalTrack(
    val song: Song,
    val filePath: String,
    val sizeBytes: Long,
    val enhanced: Boolean
)

/** Audio quality tiers offered by QQ Music. */
enum class Quality(val label: String, val fileType: String, val bitrate: String) {
    STANDARD("Standard 128k", "M500", "128kbps MP3"),
    HIGH("High 320k", "M800", "320kbps MP3"),
    FLAC("Lossless FLAC", "F000", "FLAC"),
    HIRES("Hi-Res 24bit", "RS01", "Hi-Res FLAC")
}
