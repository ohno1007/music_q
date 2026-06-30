package com.liquid.musicq.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.liquid.musicq.model.LocalTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Thin wrapper around ExoPlayer exposing reactive state for Compose. */
class PlayerController(context: Context) {

    private val exo: ExoPlayer = ExoPlayer.Builder(context).build()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _current = MutableStateFlow<LocalTrack?>(null)
    val current: StateFlow<LocalTrack?> = _current

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs

    init {
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) _durationMs.value = exo.duration.coerceAtLeast(0)
            }
        })
    }

    fun play(track: LocalTrack) {
        _current.value = track
        exo.setMediaItem(MediaItem.fromUri("file://${track.filePath}"))
        exo.prepare()
        exo.playWhenReady = true
    }

    fun toggle() {
        if (exo.isPlaying) exo.pause() else exo.play()
    }

    fun seekTo(ms: Long) = exo.seekTo(ms)

    /** Should be polled from a Compose LaunchedEffect to update the scrubber. */
    fun refreshPosition() {
        _positionMs.value = exo.currentPosition
        if (exo.duration > 0) _durationMs.value = exo.duration
    }

    fun release() = exo.release()
}
