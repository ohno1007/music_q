package com.liquid.musicq.api

import com.liquid.musicq.model.Song
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Apple iTunes Search API — a fully public, **no-key** endpoint. We use it as
 * the default "sample" provider so the UI works out of the box: it returns
 * high-res cover art and a 30-second streamable preview for every track, which
 * is perfect for demoing the Apple-Music-style player without any login.
 *
 *   https://itunes.apple.com/search?term=...&media=music
 */
class ItunesApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun search(keyword: String, limit: Int = 30): List<Song> {
        val term = URLEncoder.encode(keyword, "UTF-8")
        val url = "https://itunes.apple.com/search?term=$term&media=music&entity=song&limit=$limit"
        val req = Request.Builder().url(url)
            .header("User-Agent", "LiquidMusicQ/1.0")
            .build()
        val body = client.newCall(req).execute().use { it.body?.string() } ?: return emptyList()
        val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
        val out = ArrayList<Song>(results.length())
        for (i in 0 until results.length()) {
            val r = results.getJSONObject(i)
            val art100 = r.optString("artworkUrl100")
            // upscale the artwork: iTunes lets you swap the size segment
            val cover = art100.replace("100x100bb", "600x600bb")
            out += Song(
                id = r.optLong("trackId").toString(),
                mid = r.optLong("trackId").toString(),
                title = r.optString("trackName"),
                artist = r.optString("artistName"),
                album = r.optString("collectionName"),
                albumMid = "",
                durationSec = (r.optLong("trackTimeMillis") / 1000).toInt(),
                coverUrl = cover,
                source = "itunes",
                previewUrl = r.optString("previewUrl").ifEmpty { null }
            )
        }
        return out
    }
}
