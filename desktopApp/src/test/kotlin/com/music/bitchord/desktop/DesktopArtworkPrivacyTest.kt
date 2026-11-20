package com.music.bitchord.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import java.util.UUID
import java.util.prefs.Preferences

class DesktopArtworkPrivacyTest {
    @Test
    fun externalArtworkIsOmittedInPrivacyMode() {
        val url = "https://example.test/cover.jpg"
        assertNull(externalArtworkUrl(url, true))
        assertEquals(url, externalArtworkUrl(url, false))
        assertNull(externalArtworkUrl(null, false))
        assertNull(externalArtworkUrl(" ", false))
    }

    @Test
    fun masterScopeAndTimedRevealAreIndependent() {
        assertFalse(artworkPrivacyActive(false, true, Long.MIN_VALUE, 100))
        assertFalse(artworkPrivacyActive(true, false, Long.MIN_VALUE, 100))
        assertTrue(artworkPrivacyActive(true, true, Long.MIN_VALUE, 100))
        assertFalse(artworkPrivacyActive(true, true, 10_100, 10_099))
        assertTrue(artworkPrivacyActive(true, true, 10_100, 10_100))
        assertTrue(artworkPrivacyActive(true, true, 10_100, 100, external = true))
        assertFalse(artworkPrivacyActive(true, false, 10_100, 100, external = true))
    }

    @Test
    fun scopeAndClampedStrengthPersistWithSafeDefaults() {
        val node = Preferences.userRoot().node("com.music.bitchord.desktop.test.privacy.${UUID.randomUUID()}")
        try {
            val first = ArtworkPrivacyPreferences(DesktopPersistence(node))
            ArtworkPrivacyScope.entries.forEach { assertTrue(first.scope(it)) }
            assertEquals(2, first.strength())
            first.setScope(ArtworkPrivacyScope.PLAYER, false)
            first.setStrength(99)
            val restored = ArtworkPrivacyPreferences(DesktopPersistence(node))
            assertFalse(restored.scope(ArtworkPrivacyScope.PLAYER))
            assertTrue(restored.scope(ArtworkPrivacyScope.BROWSING))
            assertEquals(3, restored.strength())
            restored.setStrength(-2)
            assertEquals(1, first.strength())
            node.putInt(ArtworkPrivacyPreferences.STRENGTH_KEY, 99)
            assertEquals(3, first.strength())
            assertTrue(node.keys().none { it.contains("reveal") })
        } finally {
            node.removeNode()
        }
    }
}
