package com.music.bitchord.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import com.music.bitchord.ui.components.LocalPlayingAccent
import com.music.bitchord.ui.components.PlayingAccent
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class DesktopAccentBridgeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `playing accent defaults to Android color and accepts desktop theme overrides`() {
        var default = Color.Unspecified
        var dark = Color.Unspecified
        var light = Color.Unspecified
        compose.setContent {
            default = LocalPlayingAccent.current
            CompositionLocalProvider(LocalPlayingAccent provides DARK_PALETTE.navAccent) {
                dark = LocalPlayingAccent.current
            }
            CompositionLocalProvider(LocalPlayingAccent provides LIGHT_PALETTE.navAccent) {
                light = LocalPlayingAccent.current
            }
        }
        compose.runOnIdle {
            assertEquals(Color(0xFFFB4A62), default)
            assertEquals(PlayingAccent, default)
            assertEquals(DARK_PALETTE.navAccent, dark)
            assertEquals(LIGHT_PALETTE.navAccent, light)
        }
    }

    @Test fun `missing artwork uses desktop fallback colors`() {
        assertEquals(DesktopArtworkPalette.Default, DesktopArtworkPalette.fromBitmap(null))
        assertEquals(Color(0xFFFF6500), DesktopArtworkPalette.Default.primary)
        assertEquals(Color(0xFF1E3E62), DesktopArtworkPalette.Default.secondary)
    }
}
