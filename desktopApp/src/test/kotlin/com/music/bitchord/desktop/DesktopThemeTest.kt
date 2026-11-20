package com.music.bitchord.desktop

import androidx.compose.ui.graphics.Color
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DesktopThemeTest {
    @Test
    fun `loads known modes and rejects corrupted stored preferences`() {
        assertEquals(DesktopThemeMode.DARK, DesktopThemeMode.fromStored("DARK"))
        assertEquals(DesktopThemeMode.LIGHT, DesktopThemeMode.fromStored("LIGHT"))
        assertEquals(DesktopThemeMode.SYSTEM, DesktopThemeMode.fromStored("SYSTEM"))
        assertEquals(DesktopThemeMode.SYSTEM, DesktopThemeMode.fromStored("not-a-theme"))
        assertEquals(DesktopThemeMode.SYSTEM, DesktopThemeMode.fromStored(""))
    }

    @Test
    fun `system follows operating-system appearance while overrides win`() {
        assertEquals(true, DesktopTheme.resolveDark(DesktopThemeMode.SYSTEM, true))
        assertEquals(false, DesktopTheme.resolveDark(DesktopThemeMode.SYSTEM, false))
        assertEquals(true, DesktopTheme.resolveDark(DesktopThemeMode.DARK, false))
        assertEquals(false, DesktopTheme.resolveDark(DesktopThemeMode.LIGHT, true))
    }

    @Test
    fun `classic dark opt out uses the navy and orange family`() {
        assertEquals(Color(0xFF0B192C), DARK_PALETTE.background)
        assertEquals(Color(0xFF0B192C), DARK_PALETTE.sidebarSurface)
        assertEquals(Color(0xFF0B192C), DARK_PALETTE.surface)
        assertEquals(Color(0xFF0B192C), DARK_PALETTE.chromeSurface)
        assertEquals(Color(0xFFFF6500), DARK_PALETTE.navAccent)
        assertEquals(Color.White, DARK_PALETTE.foreground)
        assertEquals(Color(0xFFFF6500), DARK_PALETTE.accent)
        assertEquals(Color.Black, DARK_PALETTE.onAccent)
    }

    @Test
    fun `selected sidebar uses blue with orange indicators`() {
        assertEquals(Color(0xFFFF6500), DARK_PALETTE.navAccent)
        assertEquals(Color(0xFFA33A00), LIGHT_PALETTE.navAccent)
        assertEquals(Color(0xFF1E3E62), DARK_PALETTE.navSelectedSurface)
        assertEquals(Color(0xFFD6E5F4), LIGHT_PALETTE.navSelectedSurface)
        assertEquals(Color(0xFF0B192C), DARK_PALETTE.sidebarSurface)
        assertEquals(Color(0xFFE5EFF9), LIGHT_PALETTE.sidebarSurface)
        assertNotEquals(DARK_PALETTE.navAccent, DARK_PALETTE.navSelectedSurface)
        assertNotEquals(LIGHT_PALETTE.navAccent, LIGHT_PALETTE.navSelectedSurface)
    }

    @Test
    fun `pitch black preference is available for dark System and Dark modes`() {
        assertTrue(DesktopTheme.supportsPitchBlack(DesktopThemeMode.DARK))
        assertTrue(DesktopTheme.supportsPitchBlack(DesktopThemeMode.SYSTEM))
        assertEquals(false, DesktopTheme.supportsPitchBlack(DesktopThemeMode.LIGHT))
    }

    @Test
    fun `black canvas stays pure while chrome and player get distinct dark surfaces`() {
        assertEquals(DARK_PALETTE, DesktopTheme.resolvePalette(DesktopThemeMode.DARK, false, false))
        assertEquals(PITCH_BLACK_PALETTE, DesktopTheme.resolvePalette(DesktopThemeMode.DARK, false, true))
        assertEquals(LIGHT_PALETTE, DesktopTheme.resolvePalette(DesktopThemeMode.LIGHT, true, true))
        assertEquals(PITCH_BLACK_PALETTE, DesktopTheme.resolvePalette(DesktopThemeMode.SYSTEM, true, true))
        assertEquals(LIGHT_PALETTE, DesktopTheme.resolvePalette(DesktopThemeMode.SYSTEM, false, true))
        assertEquals(Color.Black, PITCH_BLACK_PALETTE.background)
        assertEquals(Color.Black, PITCH_BLACK_PALETTE.surface)
        assertEquals(Color(0xFF142C47), PITCH_BLACK_PALETTE.barGlass)
        assertEquals(Color(0xFF0B192C), PITCH_BLACK_PALETTE.sidebarSurface)
        assertEquals(Color(0xFF0B192C), PITCH_BLACK_PALETTE.chromeSurface)
        assertEquals(Color(0xFF1E3E62), PITCH_BLACK_PALETTE.searchSurface)
        assertEquals(Color(0xFF1A3554), PITCH_BLACK_PALETTE.topPlayerSurface)
        assertEquals(Color(0xFF10243B), PITCH_BLACK_PALETTE.floatingPlayerSurface)
        assertNotEquals(Color.Black, PITCH_BLACK_PALETTE.sidebarSurface)
        assertNotEquals(Color.Black, PITCH_BLACK_PALETTE.chromeSurface)
        assertNotEquals(Color.Black, PITCH_BLACK_PALETTE.floatingPlayerSurface)
        assertEquals(Color.Black, PITCH_BLACK_PALETTE.lyricsSurface)
        assertEquals(Color.Black, PITCH_BLACK_PALETTE.replaySurface)
        assertNotEquals(PITCH_BLACK_PALETTE.background, PITCH_BLACK_PALETTE.surfaceRaised)
        assertNotEquals(PITCH_BLACK_PALETTE.background, PITCH_BLACK_PALETTE.barGlass)
        assertEquals(DARK_PALETTE.navAccent, PITCH_BLACK_PALETTE.navAccent)
        assertEquals(DARK_PALETTE.cardFill, PITCH_BLACK_PALETTE.cardFill)
        assertEquals(DARK_PALETTE.navSelectedSurface, PITCH_BLACK_PALETTE.navSelectedSurface)
        assertTrue(contrastRatio(PITCH_BLACK_PALETTE.secondary, PITCH_BLACK_PALETTE.sidebarSurface) >= 4.5)
    }

    @Test
    fun `appearance migration enables black once without changing theme mode`() {
        val migration = DesktopTheme.appearanceMigration(currentVersion = 0)
        assertEquals(1, migration?.version)
        assertEquals(true, migration?.pitchBlack)
        assertEquals(null, DesktopTheme.appearanceMigration(currentVersion = 1))
        assertEquals(null, DesktopTheme.appearanceMigration(currentVersion = 2))

        assertEquals(PITCH_BLACK_PALETTE, DesktopTheme.resolvePalette(DesktopThemeMode.SYSTEM, true, true))
        assertEquals(LIGHT_PALETTE, DesktopTheme.resolvePalette(DesktopThemeMode.SYSTEM, false, true))
        assertEquals(PITCH_BLACK_PALETTE, DesktopTheme.resolvePalette(DesktopThemeMode.DARK, false, true))
        assertEquals(DARK_PALETTE, DesktopTheme.resolvePalette(DesktopThemeMode.DARK, false, false))
        assertEquals(LIGHT_PALETTE, DesktopTheme.resolvePalette(DesktopThemeMode.LIGHT, true, true))
    }

    @Test
    fun `appearance migration persists once and preserves each existing theme mode`() {
        val root = Preferences.userRoot().node("com/music/bitchord/test/theme-migration-${UUID.randomUUID()}")
        try {
            for (mode in DesktopThemeMode.entries) {
                val persistence = DesktopPersistence(root.node(mode.name))
                persistence.saveString("desktop_theme_mode", mode.name)
                persistence.saveBoolean("desktop_pitch_black", false)

                DesktopTheme.migrateAppearance(persistence)
                persistence.preferences.flush()
                assertEquals(mode.name, persistence.string("desktop_theme_mode"))
                assertEquals(true, persistence.boolean("desktop_pitch_black", false))
                assertEquals(1, persistence.int("desktop_appearance_migration", 0))

                // A user's post-migration opt-out must survive subsequent startup migration.
                persistence.saveBoolean("desktop_pitch_black", false)
                DesktopTheme.migrateAppearance(persistence)
                assertEquals(false, persistence.boolean("desktop_pitch_black", true))
                persistence.preferences.flush()

                // Future migration versions are also left untouched.
                persistence.saveInt("desktop_appearance_migration", 2)
                persistence.saveBoolean("desktop_pitch_black", false)
                DesktopTheme.migrateAppearance(persistence)
                assertEquals(2, persistence.int("desktop_appearance_migration", 0))
                assertEquals(false, persistence.boolean("desktop_pitch_black", true))
                persistence.preferences.flush()
            }
        } finally {
            root.removeNode()
        }
    }

    @Test
    fun `light palette has distinct surfaces and accessible body text`() {
        assertNotEquals(DARK_PALETTE.background, LIGHT_PALETTE.background)
        assertNotEquals(LIGHT_PALETTE.background, LIGHT_PALETTE.surface)
        assertTrue(contrastRatio(LIGHT_PALETTE.foreground, LIGHT_PALETTE.background) >= 7.0)
        assertTrue(contrastRatio(LIGHT_PALETTE.secondary, LIGHT_PALETTE.background) >= 4.5)
        assertTrue(contrastRatio(LIGHT_PALETTE.onAccent, LIGHT_PALETTE.accent) >= 7.0)
        assertEquals(Color(0xFFFF6500), LIGHT_PALETTE.accent)
        assertTrue(contrastRatio(LIGHT_PALETTE.navAccent, LIGHT_PALETTE.sidebarSurface) >= 4.5)
        assertTrue(contrastRatio(LIGHT_PALETTE.navAccent, LIGHT_PALETTE.navSelectedSurface) >= 4.5)
        assertTrue(contrastRatio(DARK_PALETTE.onAccent, DARK_PALETTE.accent) >= 7.0)
        assertTrue(contrastRatio(DARK_PALETTE.navAccent, DARK_PALETTE.navSelectedSurface) >= 3.0)
        // The floating player and chrome must not turn their controls into white-on-white.
        assertTrue(contrastRatio(LIGHT_PALETTE.foreground, LIGHT_PALETTE.floatingPlayerSurface) >= 7.0)
        assertTrue(contrastRatio(LIGHT_PALETTE.secondary, LIGHT_PALETTE.floatingPlayerSurface) >= 4.5)
        assertNotEquals(LIGHT_PALETTE.background, LIGHT_PALETTE.floatingPlayerSurface)
        assertNotEquals(LIGHT_PALETTE.searchSurface, LIGHT_PALETTE.surfaceRaised)
    }

    private fun contrastRatio(foreground: Color, background: Color): Double {
        fun linear(channel: Float): Double {
            val value = channel.toDouble()
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        fun luminance(color: Color): Double =
            0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
        val first = luminance(foreground)
        val second = luminance(background)
        return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
    }
}
