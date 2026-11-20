package com.music.bitchord.desktop

import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class DesktopDownloadsSettingsTest {
    private fun withSettings(test: (DesktopDownloadsSettings, Preferences) -> Unit) {
        val prefs = Preferences.userRoot().node("com.music.bitchord.tests/downloads-${UUID.randomUUID()}")
        try { test(DesktopDownloadsSettings(prefs), prefs) } finally { prefs.removeNode() }
    }

    @Test fun defaultsAndCorruptValues() = withSettings { settings, prefs ->
        assertEquals(DownloadsTab.PLAYLISTS, settings.initialTab())
        assertFalse(settings.autoUpdate())
        prefs.put("downloads_opening_tab", "invalid")
        assertEquals(DownloadsTab.PLAYLISTS, settings.initialTab())
        settings.setOpeningTab(DownloadsOpeningTab.LAST_USED)
        prefs.put("downloads_last_tab", "invalid")
        assertEquals(DownloadsTab.PLAYLISTS, settings.initialTab())
    }

    @Test fun selectedAndLastUsedTabsPersist() = withSettings { settings, prefs ->
        settings.setLastTab(DownloadsTab.ARTISTS)
        settings.setOpeningTab(DownloadsOpeningTab.LAST_USED)
        assertEquals(DownloadsTab.ARTISTS, DesktopDownloadsSettings(prefs).initialTab())
        settings.setOpeningTab(DownloadsOpeningTab.SONGS)
        assertEquals(DownloadsTab.SONGS, DesktopDownloadsSettings(prefs).initialTab())
        settings.setAutoUpdate(true)
        assertTrue(DesktopDownloadsSettings(prefs).autoUpdate())
    }

    @Test fun updatePolicyKeepsRemovedTracksWithoutRepeating() {
        assertFalse(shouldAutoUpdatePlaylist(false, listOf("a"), listOf("a", "b")))
        assertTrue(shouldAutoUpdatePlaylist(true, listOf("a"), listOf("a", "b")))
        assertFalse(shouldAutoUpdatePlaylist(true, listOf("a", "b"), listOf("a")))
        assertTrue(shouldAutoUpdatePlaylist(true, listOf("a", "b"), listOf("b", "a")))
        assertFalse(shouldAutoUpdatePlaylist(true, listOf("b", "a"), listOf("b", "a")))
    }

    @Test fun paginationRequiresEveryPageAndKeepsOrder() = runBlocking {
        val pages = mapOf("one" to (listOf("b", "a") to "two"), "two" to (listOf("c", "b") to null))
        val complete = collectDownloadListing(listOf("a"), "one", { it }) { token ->
            Result.success(pages.getValue(token))
        }
        assertEquals(listOf("a", "b", "c"), complete.getOrThrow())
        val failed = collectDownloadListing(listOf("a"), "one", { it }) { token ->
            if (token == "two") Result.failure(IllegalStateException("offline"))
            else Result.success(pages.getValue(token))
        }
        assertTrue(failed.isFailure)
        val cyclic = collectDownloadListing(listOf("a"), "one", { it }) {
            Result.success(listOf("b") to "one")
        }
        assertTrue(cyclic.isFailure)
    }
}
