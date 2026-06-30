package com.liquid.musicq.api

import com.liquid.musicq.model.LrcLine
import com.liquid.musicq.model.Song
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * lrclib.net — a free, open, no-key lyrics database that returns time-synced
 * LRC lyrics. We first try the exact /get match (artist + track + album +
 * duration), then fall back to /search.
 */
class LyricsApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Returns synced lines (preferred) or plain lines with -1 timestamps. */
    fun fetch(song: Song): List<LrcLine> {
        runCatching { exactGet(song) }.getOrNull()?.let { if (it.isNotEmpty()) return it }
        runCatching { search(song) }.getOrNull()?.let { if (it.isNotEmpty()) return it }
        return emptyList()
    }

    private fun exactGet(song: Song): List<LrcLine> {
        val url = buildString {
            append("https://lrclib.net/api/get?")
            append("artist_name=").append(enc(song.artist))
            append("&track_name=").append(enc(song.title))
            if (song.album.isNotBlank()) append("&album_name=").append(enc(song.album))
            if (song.durationSec > 0) append("&duration=").append(song.durationSec)
        }
        val body = get(url) ?: return emptyList()
        return parseLrcObject(JSONObject(body))
    }

    private fun search(song: Song): List<LrcLine> {
        val q = "${song.title} ${song.artist}".trim()
        val url = "https://lrclib.net/api/search?q=${enc(q)}"
        val body = get(url) ?: return emptyList()
        val arr = JSONArray(body)
        if (arr.length() == 0) return emptyList()
        return parseLrcObject(arr.getJSONObject(0))
    }

    private fun parseLrcObject(obj: JSONObject): List<LrcLine> {
        val synced = obj.optString("syncedLyrics")
        if (synced.isNotBlank()) return parseLrc(synced)
        val plain = obj.optString("plainLyrics")
        if (plain.isNotBlank()) return plain.split("\n").map { LrcLine(-1, it) }
        return emptyList()
    }

    /** Parse standard LRC: lines like "[01:23.45] text" (multiple tags allowed). */
    private fun parseLrc(lrc: String): List<LrcLine> {
        val tag = Regex("\\[(\\d{1,2}):(\\d{2})([.:]\\d{1,3})?]")
        val out = ArrayList<LrcLine>()
        for (raw in lrc.split("\n")) {
            val matches = tag.findAll(raw).toList()
            if (matches.isEmpty()) continue
            val text = raw.substring(matches.last().range.last + 1).trim()
            for (m in matches) {
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val fracStr = m.groupValues[3].removePrefix(".").removePrefix(":")
                val frac = if (fracStr.isEmpty()) 0L
                else (fracStr.padEnd(3, '0').take(3)).toLong()
                out += LrcLine(min * 60_000 + sec * 1_000 + frac, text)
            }
        }
        return out.sortedBy { it.timeMs }
    }

    private fun get(url: String): String? {
        val req = Request.Builder().url(url).header("User-Agent", "LiquidMusicQ/1.0 (demo)").build()
        return client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
