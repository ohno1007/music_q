package com.liquid.musicq.api

import com.liquid.musicq.model.Quality
import com.liquid.musicq.model.Song
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Lightweight QQ Music cloud client.
 *
 * QQ Music has no public/official API, so this talks to the same web endpoints
 * the browser player uses (c.y.qq.com / u.y.qq.com). A logged-in **cookie**
 * (paste it from a browser where you are signed in) is required for higher
 * quality tiers and for play/download URLs of many tracks. Endpoints and the
 * required signing change over time; if results stop coming back, refresh the
 * cookie or update the request shape here.
 */
class QQMusicApi(private val cookieProvider: () -> String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val guid = "1234567890"

    private fun newRequest(url: String): Request.Builder {
        val b = Request.Builder().url(url)
            .header("Referer", "https://y.qq.com/")
            .header("Origin", "https://y.qq.com")
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0 Safari/537.36"
            )
        val cookie = cookieProvider().trim()
        if (cookie.isNotEmpty()) b.header("Cookie", cookie)
        return b
    }

    /** Full-text search for songs. */
    fun search(keyword: String, page: Int = 1, num: Int = 30): List<Song> {
        val url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cgi" +
            "?format=json&p=$page&n=$num&w=${enc(keyword)}&cr=1&new_json=1&platform=yqq.json"
        val body = client.newCall(newRequest(url).build()).execute().use { it.body?.string() } ?: return emptyList()
        val root = JSONObject(body)
        val list = root.optJSONObject("data")?.optJSONObject("song")?.optJSONArray("list")
            ?: return emptyList()
        val out = ArrayList<Song>(list.length())
        for (i in 0 until list.length()) {
            val s = list.getJSONObject(i)
            out += parseSong(s) ?: continue
        }
        return out
    }

    private fun parseSong(s: JSONObject): Song? {
        val mid = s.optString("mid").ifEmpty { s.optString("songmid") }
        if (mid.isEmpty()) return null
        val singers = s.optJSONArray("singer") ?: JSONArray()
        val artist = (0 until singers.length())
            .joinToString("/") { singers.getJSONObject(it).optString("name") }
            .ifEmpty { "Unknown" }
        val album = s.optJSONObject("album")
        val interval = s.optInt("interval", s.optInt("songtime"))
        return Song(
            id = s.optLong("id", s.optLong("songid")).toString(),
            mid = mid,
            title = s.optString("title").ifEmpty { s.optString("songname") },
            artist = artist,
            album = album?.optString("name") ?: "",
            albumMid = album?.optString("mid") ?: "",
            durationSec = interval,
            coverUrl = albumCover(album?.optString("mid") ?: "")
        )
    }

    fun albumCover(albumMid: String): String =
        if (albumMid.isEmpty()) ""
        else "https://y.qq.com/music/photo_new/T002R300x300M000$albumMid.jpg"

    /**
     * Resolve a playable/downloadable URL for a song at the requested quality.
     * Falls back through lower tiers when the requested one isn't licensed for
     * the current account.
     */
    fun songUrl(mid: String, quality: Quality): String? {
        val order = Quality.values().filter { it.ordinal <= quality.ordinal }.reversed()
        for (q in order) {
            val url = vkeyUrl(mid, q)
            if (url != null) return url
        }
        return null
    }

    private fun vkeyUrl(mid: String, quality: Quality): String? {
        val ext = when (quality) {
            Quality.FLAC, Quality.HIRES -> ".flac"
            else -> ".mp3"
        }
        val fileName = "${quality.fileType}$mid$mid$ext"
        val payload = JSONObject().apply {
            put("req_1", JSONObject().apply {
                put("module", "vkey.GetVkeyServer")
                put("method", "CgiGetVkey")
                put("param", JSONObject().apply {
                    put("guid", guid)
                    put("songmid", JSONArray().put(mid))
                    put("songtype", JSONArray().put(0))
                    put("uin", uinFromCookie())
                    put("loginflag", 1)
                    put("platform", "20")
                    put("filename", JSONArray().put(fileName))
                })
            })
            put("loginUin", uinFromCookie())
            put("comm", JSONObject().apply {
                put("uin", uinFromCookie())
                put("format", "json")
                put("ct", 24); put("cv", 0)
            })
        }
        val url = "https://u.y.qq.com/cgi-bin/musicu.fcg?format=json&data=${enc(payload.toString())}"
        val body = client.newCall(newRequest(url).build()).execute().use { it.body?.string() } ?: return null
        val data = JSONObject(body).optJSONObject("req_1")?.optJSONObject("data") ?: return null
        val purlArr = data.optJSONArray("midurlinfo") ?: return null
        if (purlArr.length() == 0) return null
        val purl = purlArr.getJSONObject(0).optString("purl")
        if (purl.isEmpty()) return null
        val host = data.optJSONArray("sip")?.optString(0) ?: "https://ws.stream.qqmusic.qq.com/"
        return host + purl
    }

    private fun uinFromCookie(): String {
        val cookie = cookieProvider()
        val m = Regex("uin=O?0*(\\d+)").find(cookie) ?: Regex("wxuin=(\\d+)").find(cookie)
        return m?.groupValues?.get(1) ?: "0"
    }

    private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")
}
