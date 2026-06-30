package com.liquid.musicq.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liquid.musicq.dsp.EnhanceConfig
import com.liquid.musicq.model.Quality
import com.liquid.musicq.ui.glass.GlassSurface

@Composable
fun SettingsScreen(vm: MusicViewModel) {
    val cfg by vm.enhanceConfig.collectAsState()
    var cookie by remember { mutableStateOf(vm.prefs.cookie) }
    val scroll = rememberScrollState()

    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Cloud & Cookie", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp))

        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                toggleRow("Use QQ Music (needs cookie)", vm.prefs.useQQ) { vm.prefs.useQQ = it }
                Text("Off = search the public iTunes API (covers + 30s previews, no login). " +
                    "On = search/stream QQ Music using the cookie below.",
                    color = Color.White.copy(0.6f), fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Text("QQ Music cookie", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("Paste the cookie from a logged-in y.qq.com browser session. " +
                    "Required for high-quality streams and many downloads.",
                    color = Color.White.copy(0.6f), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = cookie,
                    onValueChange = {
                        cookie = it
                        vm.prefs.cookie = it
                    },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    placeholder = { Text("uin=...; qqmusic_key=...; ...", color = Color.White.copy(0.4f)) },
                    colors = glassFieldColors()
                )
            }
        }

        QualityPicker(vm)

        Text("Audio Enhancement", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)

        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                toggleRow("Auto-enhance on download", vm.prefs.autoEnhance) { vm.prefs.autoEnhance = it }

                section("Bandwidth extension")
                toggleRow("Harmonic exciter", cfg.harmonicExciter) { update(vm) { harmonicExciter = it } }
                sliderRow("  Exciter amount", cfg.exciterAmount) { update(vm) { exciterAmount = it } }
                toggleRow("SBR (spectral band replication)", cfg.spectralBandReplication) {
                    update(vm) { spectralBandReplication = it }
                }
                toggleRow("Neural super-res (needs model)", cfg.neuralSuperRes) { update(vm) { neuralSuperRes = it } }

                section("Repair")
                toggleRow("Spectral denoise", cfg.spectralDenoise) { update(vm) { spectralDenoise = it } }
                sliderRow("  Denoise strength", cfg.denoiseStrength) { update(vm) { denoiseStrength = it } }
                toggleRow("De-click / de-crackle", cfg.deClick) { update(vm) { deClick = it } }

                section("Tone")
                toggleRow("Parametric EQ", cfg.parametricEq) { update(vm) { parametricEq = it } }
                toggleRow("Virtual bass (psychoacoustic)", cfg.virtualBass) { update(vm) { virtualBass = it } }

                section("Dynamics & loudness")
                toggleRow("Multiband compressor", cfg.multibandCompressor) { update(vm) { multibandCompressor = it } }
                sliderRow("  Compression amount", cfg.compAmount) { update(vm) { compAmount = it } }
                toggleRow("Loudness normalize (R128 / LUFS)", cfg.loudnessNormalize) {
                    update(vm) { loudnessNormalize = it }
                }
                toggleRow("Brick-wall limiter", cfg.limiter) { update(vm) { limiter = it } }

                section("Stereo & output")
                toggleRow("Stereo widening", cfg.stereoWiden) { update(vm) { stereoWiden = it } }
                toggleRow("Haas effect", cfg.haasEffect) { update(vm) { haasEffect = it } }
                toggleRow("TPDF dithering", cfg.dither) { update(vm) { dither = it } }
            }
        }
        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun QualityPicker(vm: MusicViewModel) {
    var sel by remember { mutableStateOf(vm.prefs.qualityOrdinal) }
    GlassSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Download quality", color = Color.White, fontWeight = FontWeight.SemiBold)
            Quality.values().forEachIndexed { i, q ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(q.label, color = Color.White, modifier = Modifier.weight(1f))
                    Switch(
                        checked = sel == i,
                        onCheckedChange = { if (it) { sel = i; vm.prefs.qualityOrdinal = i } },
                        colors = switchColors()
                    )
                }
            }
        }
    }
}

@Composable
private fun section(title: String) {
    Spacer(Modifier.height(6.dp))
    Text(title, color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun toggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f), fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onChange, colors = switchColors())
    }
}

@Composable
private fun sliderRow(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White.copy(0.75f), modifier = Modifier.weight(1f), fontSize = 13.sp)
        Slider(
            value = value, onValueChange = onChange, valueRange = 0f..1f,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = MaterialTheme.colorScheme.secondary,
    uncheckedThumbColor = Color.White.copy(0.8f),
    uncheckedTrackColor = Color.White.copy(0.15f)
)

@Composable
private fun glassFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.White.copy(0.06f),
    unfocusedContainerColor = Color.White.copy(0.06f),
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = MaterialTheme.colorScheme.secondary
)

/** Mutate the config flow in place and republish. */
private fun update(vm: MusicViewModel, block: EnhanceConfig.() -> Unit) {
    vm.enhanceConfig.value = vm.enhanceConfig.value.copy().apply(block)
}
