package com.liquid.musicq.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.liquid.musicq.api.QQMusicApi
import com.liquid.musicq.data.Prefs
import com.liquid.musicq.download.Downloader
import com.liquid.musicq.dsp.EnhanceConfig
import com.liquid.musicq.model.LocalTrack
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
    private val api = QQMusicApi { prefs.cookie }
    val downloader = Downloader(app, api)
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

    fun setQuery(q: String) { _query.value = q }

    fun search() {
        val q = _query.value.trim()
        if (q.isEmpty()) return
        _searching.value = true
        _status.value = null
        viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.IO) { api.search(q) }
                _results.value = r
                if (r.isEmpty()) _status.value = "No results (cookie may be required)"
            } catch (t: Throwable) {
                _status.value = "Search failed: ${t.message}"
            } finally {
                _searching.value = false
            }
        }
    }

    fun download(song: Song) {
        val quality = Quality.values()[prefs.qualityOrdinal.coerceIn(0, Quality.values().lastIndex)]
        val config = if (prefs.autoEnhance) enhanceConfig.value else null
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                downloader.fetch(song, quality, config) { p ->
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
