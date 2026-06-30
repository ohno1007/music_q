package com.liquid.musicq.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.liquid.musicq.api.ItunesApi
import com.liquid.musicq.api.LyricsApi
import com.liquid.musicq.api.QQMusicApi
import com.liquid.musicq.data.Prefs
import com.liquid.musicq.download.Downloader
import com.liquid.musicq.dsp.EnhanceConfig
import com.liquid.musicq.model.LocalTrack
import com.liquid.musicq.model.LrcLine
import com.liquid.musicq.model.Quality
import com.liquid.musicq.model.Song
import com.liquid.musicq.player.PlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicViewModel(app: Application) : AndroidViewModel(app) {

    val prefs = Prefs(app)
    private val qq = QQMusicApi { prefs.cookie }
    private val itunes = ItunesApi()
    private val lyricsApi = LyricsApi()
    val downloader = Downloader(app)
    val player = PlayerController(app)

    val enhanceConfig = MutableStateFlow(EnhanceConfig.warmAndBright())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _results = MutableStateFlow<List<Song>>(emptyList())
    val results: StateFlow<List<Song>> = _results

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching

    private val _library = MutableStateFlow<List<LocalTrack>>(emptyList())
    val library: StateFlow<List<LocalTrack>> = _library

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    private val _downloadProgress = MutableStateFlow<Pair<String, Float>?>(null)
    val downloadProgress: StateFlow<Pair<String, Float>?> = _downloadProgress

    private val _lyrics = MutableStateFlow<List<LrcLine>>(emptyList())
    val lyrics: StateFlow<List<LrcLine>> = _lyrics

    private val _nowPlayingOpen = MutableStateFlow(false)
    val nowPlayingOpen: StateFlow<Boolean> = _nowPlayingOpen

    fun setQuery(q: String) { _query.value = q }
    fun openNowPlaying() { _nowPlayingOpen.value = true }
    fun closeNowPlaying() { _nowPlayingOpen.value = false }

    fun search() {
        val q = _query.value.trim()
        if (q.isEmpty()) return
        _searching.value = true
        _status.value = null
        viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.IO) {
                    if (prefs.useQQ && prefs.cookie.isNotBlank()) qq.search(q) else itunes.search(q)
                }
                _results.value = r
                if (r.isEmpty()) _status.value = "No results"
            } catch (t: Throwable) {
                _status.value = "Search failed: ${t.message}"
            } finally {
                _searching.value = false
            }
        }
    }

    /** Tapping a result: stream the preview (iTunes) or resolve a QQ stream. */
    fun playSong(song: Song) {
        viewModelScope.launch {
            val playable = withContext(Dispatchers.IO) { resolvePlayUrl(song) }
            if (playable == null) {
                _status.value = "Can't stream (preview unavailable / cookie required)"
                return@launch
            }
            player.playRemote(song.copy(previewUrl = playable))
            loadLyrics(song)
        }
    }

    fun playLocal(track: LocalTrack) {
        player.playLocal(track)
        loadLyrics(track.song)
    }

    private fun loadLyrics(song: Song) {
        _lyrics.value = emptyList()
        viewModelScope.launch {
            val l = withContext(Dispatchers.IO) { runCatching { lyricsApi.fetch(song) }.getOrDefault(emptyList()) }
            _lyrics.value = l
        }
    }

    private fun resolvePlayUrl(song: Song): String? =
        song.previewUrl ?: if (song.source == "qq") qq.songUrl(song.mid, currentQuality()) else null

    private fun currentQuality() =
        Quality.values()[prefs.qualityOrdinal.coerceIn(0, Quality.values().lastIndex)]

    fun download(song: Song) {
        val config = if (prefs.autoEnhance) enhanceConfig.value else null
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val url = resolvePlayUrl(song)
                if (url == null) {
                    _status.value = "No downloadable URL"
                    return@withContext
                }
                downloader.fetch(song, url, config) { p ->
                    when (p) {
                        is Downloader.Progress.Downloading ->
                            _downloadProgress.value = "Downloading ${song.title}" to p.fraction
                        is Downloader.Progress.Enhancing ->
                            _downloadProgress.value = "Enhancing ${song.title}" to p.fraction
                        is Downloader.Progress.Done -> {
                            _downloadProgress.value = null
                            _status.value = "Saved: ${p.track.song.title}"
                            refreshLibrary()
                        }
                        is Downloader.Progress.Failed -> {
                            _downloadProgress.value = null
                            _status.value = p.reason
                        }
                    }
                }
            }
        }
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            val files = withContext(Dispatchers.IO) {
                downloader.musicDir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }
                    ?: emptyList()
            }
            _library.value = files.map { f ->
                LocalTrack(
                    song = Song(
                        id = f.name, mid = f.name,
                        title = f.nameWithoutExtension.substringBeforeLast("-enhanced"),
                        artist = "", album = "", albumMid = "",
                        durationSec = 0, coverUrl = ""
                    ),
                    filePath = f.absolutePath,
                    sizeBytes = f.length(),
                    enhanced = f.name.contains("enhanced")
                )
            }
        }
    }

    fun clearStatus() { _status.value = null }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }
}
