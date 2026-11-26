package com.music.bitchord.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopPlaylistFilterTest {
    private val titles = listOf("Beyoncé — Morning Mix", "Morning Jazz", "Mix for Beyoncé")

    @Test
    fun matchesUnorderedWordsIgnoringCaseAndAccents() {
        assertEquals(listOf(titles[0], titles[2]), filterDesktopPlaylists(titles, "MIX\u00a0beyonce") { it })
    }

    @Test
    fun blankAndMissingQueries() {
        assertEquals(titles, filterDesktopPlaylists(titles, "  ") { it })
        assertEquals(emptyList(), filterDesktopPlaylists(titles, "evening jazz") { it })
    }
}
