package com.liquid.musicq.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liquid.musicq.model.LocalTrack
import com.liquid.musicq.model.Song
import com.liquid.musicq.ui.glass.GlassSurface

@Composable
fun SearchScreen(vm: MusicViewModel) {
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val searching by vm.searching.collectAsState()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        GlassSurface(Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Search, null, tint = Color.White.copy(0.8f),
                    modifier = Modifier.padding(start = 8.dp))
                TextField(
                    value = query,
                    onValueChange = vm::setQuery,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search QQ Music…", color = Color.White.copy(0.5f)) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = MaterialTheme.colorScheme.secondary
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.search() })
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (searching) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.secondary
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(results) { song -> SongRow(song) { vm.download(song) } }
        }
    }
}

@Composable
private fun SongRow(song: Song, onDownload: () -> Unit) {
    GlassSurface(Modifier.fillMaxWidth(), cornerRadius = 20.dp, tintAlpha = 0.08f) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(46.dp).background(
                    MaterialTheme.colorScheme.primary.copy(0.4f), RoundedCornerShape(12.dp)
                ),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.MusicNote, null, tint = Color.White) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, color = Color.White, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.subtitle, color = Color.White.copy(0.6f), fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(
                Icons.Filled.Download, "download", tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(26.dp).clickable { onDownload() }
            )
        }
    }
}

@Composable
fun LibraryScreen(vm: MusicViewModel) {
    val library by vm.library.collectAsState()
    val current by vm.player.current.collectAsState()
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Library", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 12.dp))
        if (library.isEmpty()) {
            GlassSurface(Modifier.fillMaxWidth()) {
                Text("No downloads yet. Search and tap ⬇ to pull tracks here.",
                    color = Color.White.copy(0.7f), modifier = Modifier.padding(20.dp))
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(library) { track ->
                LibraryRow(track, playing = current?.filePath == track.filePath) {
                    vm.player.play(track)
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(track: LocalTrack, playing: Boolean, onPlay: () -> Unit) {
    GlassSurface(Modifier.fillMaxWidth(), cornerRadius = 20.dp, tintAlpha = 0.08f) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp).clickable { onPlay() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(46.dp).background(
                    MaterialTheme.colorScheme.secondary.copy(0.35f), CircleShape
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.song.title, color = Color.White, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${track.sizeBytes / 1024 / 1024} MB", color = Color.White.copy(0.55f), fontSize = 12.sp)
                    if (track.enhanced) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Filled.GraphicEq, null, tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp))
                        Text(" enhanced", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
