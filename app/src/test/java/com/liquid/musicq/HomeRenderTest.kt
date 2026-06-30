package com.liquid.musicq

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.captureRoboImage
import com.liquid.musicq.ui.glass.LiquidGlass
import com.liquid.musicq.ui.theme.LiquidMusicQTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real [LiquidGlass] chrome on the JVM (Robolectric NATIVE graphics)
 * and writes a PNG so the layout can be eyeballed without a device. Proves the
 * bottom nav sits at the bottom and content fills the middle.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h914dp-xxhdpi")
class HomeRenderTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun render_home() {
        compose.setContent {
            LiquidMusicQTheme(darkTheme = true) { DemoHome() }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/home.png")
    }
}

@Composable
private fun DemoHome() {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF3A2E8C), Color(0xFF1B3F6E), Color(0xFF5E2A52)))
        )
    ) {
        Column(Modifier.fillMaxSize().padding(top = 28.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
                Text("Liquid", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text(" MusicQ", color = Color(0xFF2BD9FE), fontSize = 26.sp, fontWeight = FontWeight.Black)
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
                Column {
                    LiquidGlass(Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Search, null, tint = Color.White.copy(0.85f))
                            Spacer(Modifier.width(10.dp))
                            Text("Songs, artists…", color = Color.White.copy(0.55f))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    repeat(4) { i ->
                        LiquidGlass(Modifier.fillMaxWidth().padding(bottom = 10.dp), cornerRadius = 20.dp) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF7C5CFF)),
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Filled.MusicNote, null, tint = Color.White) }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Track ${i + 1}", color = Color.White, fontWeight = FontWeight.SemiBold)
                                    Text("Artist • Album", color = Color.White.copy(0.6f), fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
            LiquidGlass(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), cornerRadius = 28.dp) {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    NavTab("Search", Icons.Filled.Search, true)
                    NavTab("Library", Icons.Filled.LibraryMusic, false)
                    NavTab("Tune", Icons.Filled.Tune, false)
                }
            }
        }
    }
}

@Composable
private fun NavTab(label: String, icon: ImageVector, active: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, tint = if (active) Color(0xFF2BD9FE) else Color.White.copy(0.6f), modifier = Modifier.size(26.dp))
        Text(label, fontSize = 11.sp, color = if (active) Color.White else Color.White.copy(0.6f))
    }
}
