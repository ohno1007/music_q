package com.liquid.musicq.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.liquid.musicq.ui.glass.CoverHaloBackground
import com.liquid.musicq.ui.glass.GlassBackdropHost
import com.liquid.musicq.ui.glass.LiquidGlass

@Composable
fun NowPlayingOverlay(vm: MusicViewModel) {
    val open by vm.nowPlayingOpen.collectAsState()
    AnimatedVisibility(
        visible = open,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        NowPlayingScreen(vm)
    }
}

@Composable
private fun NowPlayingScreen(vm: MusicViewModel) {
    val song by vm.player.currentSong.collectAsState()
    val isPlaying by vm.player.isPlaying.collectAsState()
    val pos by vm.player.positionMs.collectAsState()
    val dur by vm.player.durationMs.collectAsState()
    val lyrics by vm.lyrics.collectAsState()
    val isPreview by vm.player.isPreview.collectAsState()

    LaunchedEffect(song, isPlaying) {
        while (true) { vm.player.refreshPosition(); kotlinx.coroutines.delay(250) }
    }

    GlassBackdropHost(background = { CoverHaloBackground(song?.coverUrl) }) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // top bar
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                LiquidGlass(Modifier.size(40.dp), cornerRadius = 20.dp) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown, "close", tint = Color.White,
                            modifier = Modifier.size(26.dp)
                                .clickableNoRipple { vm.closeNowPlaying() }
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (isPreview) {
                    LiquidGlass(cornerRadius = 14.dp) {
                        Text("试听 30 秒", color = Color.White, fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            // cover art
            AsyncImage(
                model = song?.coverUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(0.82f).aspectRatio(1f).clip(RoundedCornerShape(24.dp))
            )

            Spacer(Modifier.height(20.dp))
            Text(song?.title ?: "", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song?.artist ?: "", color = Color.White.copy(0.7f), fontSize = 15.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)

            Spacer(Modifier.height(16.dp))
            // scrubber
            Slider(
                value = if (dur > 0) pos.toFloat() / dur else 0f,
                onValueChange = { if (dur > 0) vm.player.seekTo((it * dur).toLong()) },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = MaterialTheme.colorScheme.secondary,
                    inactiveTrackColor = Color.White.copy(0.18f)
                )
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt(pos), color = Color.White.copy(0.6f), fontSize = 12.sp)
                Text(fmt(dur), color = Color.White.copy(0.6f), fontSize = 12.sp)
            }

            Spacer(Modifier.height(8.dp))
            // transport
            LiquidGlass(Modifier.size(76.dp), cornerRadius = 38.dp) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "toggle",
                        tint = Color.White, modifier = Modifier.size(40.dp)
                            .clickableNoRipple { vm.player.toggle() }
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            // synced lyrics
            LyricsView(lyrics, pos, Modifier.fillMaxWidth().weight(1f))
        }
    }
}

@Composable
private fun LyricsView(
    lyrics: List<com.liquid.musicq.model.LrcLine>,
    positionMs: Long,
    modifier: Modifier = Modifier
) {
    if (lyrics.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("歌词将显示在这里", color = Color.White.copy(0.4f), fontSize = 14.sp)
        }
        return
    }
    val synced = lyrics.any { it.timeMs >= 0 }
    val current = remember(positionMs, lyrics) {
        if (!synced) -1 else lyrics.indexOfLast { it.timeMs in 0..positionMs }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState()
    LaunchedEffect(current) {
        if (current >= 0) listState.animateScrollToItem(current.coerceAtLeast(0), -120)
    }

    LiquidGlass(modifier, cornerRadius = 26.dp) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(lyrics) { i, line ->
                val active = i == current
                Text(
                    line.text.ifBlank { "♪" },
                    color = if (active) Color.White else Color.White.copy(0.45f),
                    fontSize = if (active) 19.sp else 16.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
}
