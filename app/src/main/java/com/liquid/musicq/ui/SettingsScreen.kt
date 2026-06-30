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
        Text("云端与 Cookie", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp))

        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                toggleRow("使用 QQ 音乐(需要 Cookie)", vm.prefs.useQQ) { vm.prefs.useQQ = it }
                Text("关闭 = 使用公开 iTunes 接口(封面 + 30 秒试听,无需登录);" +
                    "开启 = 用下方 Cookie 搜索并播放 QQ 音乐。",
                    color = Color.White.copy(0.6f), fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Text("QQ 音乐 Cookie", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("从已登录 y.qq.com 的浏览器中复制 Cookie 粘贴到这里。" +
                    "高音质和多数下载都需要它。",
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

        Text("音频增强", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)

        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                toggleRow("下载时自动增强", vm.prefs.autoEnhance) { vm.prefs.autoEnhance = it }

                section("频带扩展")
                toggleRow("谐波激励", cfg.harmonicExciter) { update(vm) { harmonicExciter = it } }
                sliderRow("  激励强度", cfg.exciterAmount) { update(vm) { exciterAmount = it } }
                toggleRow("SBR(频谱带宽复制)", cfg.spectralBandReplication) {
                    update(vm) { spectralBandReplication = it }
                }
                toggleRow("神经网络超分(需模型)", cfg.neuralSuperRes) { update(vm) { neuralSuperRes = it } }

                section("修复")
                toggleRow("频谱降噪", cfg.spectralDenoise) { update(vm) { spectralDenoise = it } }
                sliderRow("  降噪强度", cfg.denoiseStrength) { update(vm) { denoiseStrength = it } }
                toggleRow("去咔哒 / 去爆音", cfg.deClick) { update(vm) { deClick = it } }

                section("音色")
                toggleRow("参数均衡器", cfg.parametricEq) { update(vm) { parametricEq = it } }
                toggleRow("虚拟低音(心理声学)", cfg.virtualBass) { update(vm) { virtualBass = it } }

                section("动态与响度")
                toggleRow("多段压缩", cfg.multibandCompressor) { update(vm) { multibandCompressor = it } }
                sliderRow("  压缩强度", cfg.compAmount) { update(vm) { compAmount = it } }
                toggleRow("响度归一化(R128 / LUFS)", cfg.loudnessNormalize) {
                    update(vm) { loudnessNormalize = it }
                }
                toggleRow("砖墙限幅器", cfg.limiter) { update(vm) { limiter = it } }

                section("立体声与输出")
                toggleRow("立体声扩展", cfg.stereoWiden) { update(vm) { stereoWiden = it } }
                toggleRow("哈斯效应", cfg.haasEffect) { update(vm) { haasEffect = it } }
                toggleRow("TPDF 抖动", cfg.dither) { update(vm) { dither = it } }
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
            Text("下载音质", color = Color.White, fontWeight = FontWeight.SemiBold)
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
