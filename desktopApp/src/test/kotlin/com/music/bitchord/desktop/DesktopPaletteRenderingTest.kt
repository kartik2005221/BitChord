package com.music.bitchord.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Pixel evidence for representative palette surfaces, including the real desktop chrome modifiers. */
@OptIn(ExperimentalTestApi::class)
class DesktopPaletteRenderingTest {
    @Test
    fun pitchBlackPaletteRendersItsCanvasSidebarChromeAndAccent() =
        renderPalette(DesktopThemeMode.DARK, pitchBlack = true, "dark.png") { palette ->
            assertPixel("canvas", palette.background)
            assertPixel("sidebar", palette.sidebarSurface)
            assertPixel("chrome", palette.chromeSurface)
            assertPixel("bar", palette.barGlass)
            assertPixel("raised", palette.surfaceRaised)
            assertPixel("selected", palette.navSelectedSurface)
            assertPixel("accent", palette.navAccent)
            assertEquals(Color.Black.toArgb(), pixel("canvas"), "Pitch-black canvas must render exact #000000")
            assertNotEquals(pixel("canvas"), pixel("sidebar"), "Sidebar must remain distinct from the black canvas")
            assertNotEquals(pixel("canvas"), pixel("player"), "Player surface must remain distinct from the black canvas")
            assertPixel("player", palette.topPlayerSurface)
        }

    @Test
    fun lightPaletteRendersRepresentativeSurfacesAndChrome() =
        renderPalette(DesktopThemeMode.LIGHT, pitchBlack = false, "light.png") { palette ->
            assertPixel("canvas", palette.background)
            assertPixel("sidebar", palette.sidebarSurface)
            assertPixel("raised", palette.surfaceRaised)
            assertPixel("selected", palette.navSelectedSurface)
            assertPixel("accent", palette.navAccent)
            assertPixel("player", palette.topPlayerSurface)
        }

    private fun renderPalette(
        mode: DesktopThemeMode,
        pitchBlack: Boolean,
        imageName: String,
        verify: PaletteProbe.(DesktopThemePalette) -> Unit,
    ) = runDesktopComposeUiTest(width = 720, height = 360) {
        val oldMode = DesktopTheme.mode
        val oldPitchBlack = DesktopTheme.pitchBlack
        var applied = false
        try {
            DesktopTheme.selectMode(mode)
            DesktopTheme.updatePitchBlack(pitchBlack)
            setContent {
                MaterialTheme {
                    SideEffect { applied = DesktopTheme.palette == expectedPalette(mode, pitchBlack) }
                    PaletteShell()
                }
            }
            waitUntil(timeoutMillis = 10_000) { applied }
            waitForIdle()
            val probe = PaletteProbe(this)
            save(onRoot().captureToImage(), imageName)
            probe.verify(DesktopTheme.palette)
        } finally {
            DesktopTheme.selectMode(oldMode)
            DesktopTheme.updatePitchBlack(oldPitchBlack)
        }
    }

    private fun expectedPalette(mode: DesktopThemeMode, pitchBlack: Boolean): DesktopThemePalette =
        DesktopTheme.resolvePalette(mode, osDark = false, pitchBlack = pitchBlack)

    @Composable
    private fun PaletteShell() {
        val palette = DesktopTheme.palette
        Row(Modifier.fillMaxSize().background(palette.background)) {
            Column(
                Modifier.size(width = 190.dp, height = 360.dp)
                    .background(palette.sidebarSurface)
                    .testTag("sidebar"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Sidebar", color = palette.foreground)
                Swatch("selected", "Selected", palette.navSelectedSurface, palette.foreground)
                Box(
                    Modifier.size(width = 150.dp, height = 48.dp)
                        .desktopChromeGlass()
                        .testTag("chrome"),
                    contentAlignment = Alignment.CenterStart,
                ) { Text("Chrome", color = palette.foreground) }
            }
            Column(
                Modifier.weight(1f).fillMaxSize().background(palette.background),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Representative desktop palette", color = palette.foreground)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Swatch("raised", "Raised", palette.surfaceRaised, palette.foreground)
                    Swatch("accent", "Accent", palette.navAccent, palette.onAccent)
                    Swatch("player", "Player", palette.topPlayerSurface, palette.foreground)
                }
                Box(
                    Modifier.size(width = 480.dp, height = 72.dp)
                        .desktopBarGlass(RectangleShape)
                        .testTag("bar"),
                    contentAlignment = Alignment.CenterStart,
                    ) { Text("Desktop bar glass", color = palette.foreground) }
            }
        }
        // A tagged canvas probe verifies the underlying page color independently of its children.
        Box(Modifier.size(2.dp).background(palette.background).testTag("canvas"))
    }

    @Composable
    private fun Swatch(tag: String, label: String, color: Color, textColor: Color) {
        Box(
            Modifier.size(width = 112.dp, height = 56.dp).background(color).testTag(tag),
            contentAlignment = Alignment.Center,
        ) { Text(label, color = textColor) }
    }

    private class PaletteProbe(private val scope: androidx.compose.ui.test.ComposeUiTest) {
        fun pixel(tag: String): Int {
            val image = scope.onNodeWithTag(tag).captureToImage()
            val map = image.toPixelMap()
            return map[(image.width - 8).coerceAtLeast(0), (image.height - 8).coerceAtLeast(0)].toArgb()
        }

        fun assertPixel(tag: String, expected: Color) =
            assertEquals(expected.toArgb(), pixel(tag), "Rendered $tag surface must match its palette token")
    }

    private fun save(image: ImageBitmap, name: String) {
        val target = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        val map = image.toPixelMap()
        val pixels = IntArray(image.width * image.height) { map[it % image.width, it / image.width].toArgb() }
        target.setRGB(0, 0, image.width, image.height, pixels, 0, image.width)
        val file = File("build/reports/desktop-palette/$name")
        file.parentFile.mkdirs()
        ImageIO.write(target, "png", file)
    }
}
