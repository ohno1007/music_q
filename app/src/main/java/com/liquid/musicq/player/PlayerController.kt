package com.liquid.musicq.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.liquid.musicq.model.LocalTrack
import com.liquid.musicq.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Thin wrapper around ExoPlayer exposing reactive state for Compose. */
class PlayerController(context: Context) {

    private val exo: ExoPlayer = ExoPlayer.Builder(context).build()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    /** Metadata of whatever is loaded (cover/title/artist) for the now-playing UI. */
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong

    /** Local file path when playing a download (used to highlight the library). */
    private val _currentPath = MutableStateFlow<String?>(null)
    val currentPath: StateFlow<String?> = _currentPath

    private val _isPreview = MutableStateFlow(false)
    val isPreview: StateFlow<Boolean> = _isPreview

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs

    init {
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { _isPlaying.value = isPlaying }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) _durationMs.value = exo.duration.coerceAtLeast(0)
            }
        })
    }

    /** Stream a remote URL (e.g. an iTunes 30-second preview). */
    fun playRemote(song: Song) {
        val url = song.previewUrl ?: return
        _currentSong.value = song
        _currentPath.value = null
        _isPreview.value = true
        load(url)
    }

    /** Play a downloaded local file. */
    fun playLocal(track: LocalTrack) {
        _currentSong.value = track.song
        _currentPath.value = track.filePath
        _isPreview.value = false
        load("file://${track.filePath}")
    }

    private fun load(uri: String) {
        exo.setMediaItem(MediaItem.fromUri(uri))
        exo.prepare()
        exo.playWhenReady = true
    }

    fun toggle() { if (exo.isPlaying) exo.pause() else exo.play() }

    fun seekTo(ms: Long) = exo.seekTo(ms)

    /** Polled from a Compose LaunchedEffect to advance the scrubber & lyrics. */
    fun refreshPosition() {
        _positionMs.value = exo.currentPosition
        if (exo.duration > 0) _durationMs.value = exo.duration
    }

    fun release() = exo.release()
}
