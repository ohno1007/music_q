package com.liquid.musicq.download

import android.content.Context
import com.liquid.musicq.dsp.AudioBuffer
import com.liquid.musicq.dsp.AudioEnhancer
import com.liquid.musicq.dsp.AudioIO
import com.liquid.musicq.dsp.EnhanceConfig
import com.liquid.musicq.model.LocalTrack
import com.liquid.musicq.model.Song
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** Pulls songs from a resolved URL to local storage and optionally enhances them. */
class Downloader(private val context: Context) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val musicDir: File by lazy {
        File(context.getExternalFilesDir(null) ?: context.filesDir, "music").apply { mkdirs() }
    }

    sealed class Progress {
        data class Downloading(val fraction: Float) : Progress()
        data class Enhancing(val fraction: Float) : Progress()
        data class Done(val track: LocalTrack) : Progress()
        data class Failed(val reason: String) : Progress()
    }

    /**
     * Download from an already-resolved [url] -> (optionally) enhance. Emits
     * progress via the callback. Returns the final LocalTrack or null on failure.
     */
    fun fetch(
        song: Song,
        url: String,
        enhance: EnhanceConfig?,
        onProgress: (Progress) -> Unit
    ): LocalTrack? {
        val rawExt = when {
            url.contains(".flac") -> "flac"
            url.contains(".m4a") || url.contains("preview") -> "m4a"
            else -> "mp3"
        }
        val safe = "${song.title}-${song.artist}".replace(Regex("[^\\w\\u4e00-\\u9fa5-]"), "_")
        val rawFile = File(musicDir, "$safe.$rawExt")

        try {
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    onProgress(Progress.Failed("HTTP ${resp.code}"))
                    return null
                }
                val total = resp.body?.contentLength() ?: -1
                resp.body!!.byteStream().use { input ->
                    rawFile.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var read: Int
                        var sum = 0L
                        while (input.read(buf).also { read = it } >= 0) {
                            output.write(buf, 0, read)
                            sum += read
                            if (total > 0) onProgress(Progress.Downloading(sum.toFloat() / total))
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            onProgress(Progress.Failed("Download error: ${t.message}"))
            return null
        }

        if (enhance == null) {
            val track = LocalTrack(song, rawFile.absolutePath, rawFile.length(), enhanced = false)
            onProgress(Progress.Done(track))
            return track
        }

        return try {
            onProgress(Progress.Enhancing(0f))
            val decoded = AudioIO.decode(rawFile)
            val buffer = AudioBuffer.fromPcm16(decoded.pcm, decoded.channels, decoded.sampleRate)
            val enhancer = AudioEnhancer(File(context.filesDir, "superres.onnx"))
            val out = enhancer.process(buffer, enhance) { onProgress(Progress.Enhancing(it)) }
            val enhancedFile = File(musicDir, "$safe-enhanced.wav")
            AudioIO.writeWav(enhancedFile, out.toPcm16(), out.channelCount, out.sampleRate)
            val track = LocalTrack(song, enhancedFile.absolutePath, enhancedFile.length(), enhanced = true)
            onProgress(Progress.Done(track))
            track
        } catch (t: Throwable) {
            onProgress(Progress.Failed("Enhance error: ${t.message}"))
            // still return the raw download so the user keeps the file
            LocalTrack(song, rawFile.absolutePath, rawFile.length(), enhanced = false)
        }
    }
}
