package com.liquid.musicq

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.liquid.musicq.ui.LibraryScreen
import com.liquid.musicq.ui.MusicViewModel
import com.liquid.musicq.ui.NowPlayingOverlay
import com.liquid.musicq.ui.SearchScreen
import com.liquid.musicq.ui.SettingsScreen
import com.liquid.musicq.ui.clickableNoRipple
import com.liquid.musicq.ui.glass.CoverHaloBackground
import com.liquid.musicq.ui.glass.GlassBackdropHost
import com.liquid.musicq.ui.glass.LiquidGlass
import com.liquid.musicq.ui.theme.LiquidMusicQTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val vm: MusicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        vm.refreshLibrary()
        setContent {
            LiquidMusicQTheme(darkTheme = true) { AppRoot(vm) }
        }
    }
}

private enum class Tab(val title: String, val icon: ImageVector) {
    Search("Search", Icons.Filled.Search),
    Library("Library", Icons.Filled.LibraryMusic),
    Settings("Tune", Icons.Filled.Tune)
}

@Composable
private fun AppRoot(vm: MusicViewModel) {
    var tab by remember { mutableStateOf(Tab.Search) }
    val progress by vm.downloadProgress.collectAsState()
    val current by vm.player.currentSong.collectAsState()

    // the whole UI sits on a cover-halo backdrop captured for glass refraction
    GlassBackdropHost(
        background = { CoverHaloBackground(current?.coverUrl) }
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Header()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    Tab.Search -> SearchScreen(vm)
                    Tab.Library -> LibraryScreen(vm)
                    Tab.Settings -> SettingsScreen(vm)
                }
            }
            progress?.let { (label, frac) -> DownloadBanner(label, frac) }
            if (current != null) MiniPlayer(vm)
            GlassBottomBar(tab) { tab = it }
        }
    }

    // full-screen now-playing slides over everything
    NowPlayingOverlay(vm)
}

@Composable
private fun Header() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Liquid", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
        Text(" MusicQ", color = MaterialTheme.colorScheme.secondary, fontSize = 26.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun DownloadBanner(label: String, frac: Float) {
    LiquidGlass(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), cornerRadius = 18.dp) {
        Column(Modifier.padding(12.dp)) {
            Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { frac.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = Color.White.copy(0.15f)
            )
        }
    }
}

@Composable
private fun MiniPlayer(vm: MusicViewModel) {
    val current by vm.player.currentSong.collectAsState()
    val isPlaying by vm.player.isPlaying.collectAsState()
    val pos by vm.player.positionMs.collectAsState()
    val dur by vm.player.durationMs.collectAsState()

    LaunchedEffect(current, isPlaying) {
        while (true) { vm.player.refreshPosition(); delay(500) }
    }

    LiquidGlass(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable { vm.openNowPlaying() },
        cornerRadius = 22.dp
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!current?.coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = current?.coverUrl, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(current?.title ?: "", color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(current?.artist ?: "", color = Color.White.copy(0.6f), fontSize = 12.sp, maxLines = 1)
                }
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "toggle",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp).clickableNoRipple { vm.player.toggle() }
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { if (dur > 0) pos.toFloat() / dur else 0f },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = Color.White.copy(0.15f)
            )
        }
    }
}

@Composable
private fun GlassBottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    LiquidGlass(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        cornerRadius = 28.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Tab.values().forEach { t ->
                val active = t == selected
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickableNoRipple { onSelect(t) }.padding(horizontal = 8.dp)
                ) {
                    Icon(
                        t.icon, t.title,
                        tint = if (active) MaterialTheme.colorScheme.secondary else Color.White.copy(0.6f),
                        modifier = Modifier.size(26.dp)
                    )
                    Text(
                        t.title, fontSize = 11.sp,
                        color = if (active) Color.White else Color.White.copy(0.6f),
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
