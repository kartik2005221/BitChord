package com.music.bitchord.desktop

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.music.bitchord.ui.AppUi
import com.music.bitchord.ui.player.PlayerPlatform
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import org.junit.Assume.assumeTrue

/** Opt-in release evidence: the actual application, not a palette or component fixture. */
@OptIn(ExperimentalTestApi::class)
class DesktopReleaseScreenshotTest {
    @Test
    fun captureSettings() {
        assumeTrue(System.getenv("BITCHORD_RELEASE_SCREENSHOT") == "1")
        // Require the caller to opt into an isolated process, not just a different preferences node:
        // the app also uses user.home and XDG directories for caches and other persistent state.
        val home = System.getenv("BITCHORD_SCREENSHOT_HOME")
        require(!home.isNullOrBlank() && File(home).canonicalFile == File(System.getProperty("user.home")).canonicalFile)
        PlayerPlatform.install(DesktopPlayerHost)
        AppUi.install(DesktopAppUiHost)
        DesktopTheme.selectMode(DesktopThemeMode.DARK)
        DesktopTheme.updatePitchBlack(true)
        runDesktopComposeUiTest(width = 1440, height = 900) {
            setContent {
                BitChordDesktopApp(DesktopWelcomeOnboarding.Launch(false, "screenshot"), DesktopPipHost())
            }
            waitUntil(timeoutMillis = 30_000) {
                onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithContentDescription("Settings").performClick()
            waitUntil(timeoutMillis = 30_000) {
                onAllNodesWithText("Search settings").fetchSemanticsNodes().isNotEmpty()
            }
            waitForIdle()
            val image = onRoot().captureToImage()
            val pixels = image.toPixelMap()
            val output = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
            val data = IntArray(image.width * image.height) { pixels[it % image.width, it / image.width].toArgb() }
            output.setRGB(0, 0, image.width, image.height, data, 0, image.width)
            val file = File("build/reports/release-screenshot/v1.12.0-desktop.png")
            file.parentFile.mkdirs()
            check(ImageIO.write(output, "png", file))
        }
    }
}
