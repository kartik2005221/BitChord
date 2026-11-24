package com.music.bitchord.desktop

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.Song
import java.util.prefs.Preferences
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DesktopPlaylistEditingTest {
    @get:Rule val compose = createComposeRule()

    private val first = Song(videoId = "aaaaaaaaaaa", title = "First occurrence", artist = "Artist", setVideoId = "set1")
    private val second = first.copy(title = "Second occurrence", setVideoId = "set2")

    @Test
    fun desktopReorderSubmitsExplicitOccurrences() {
        val listing = DesktopPlaylistOrder(listOf(first, second))
        var saved: List<String>? = null
        compose.setContent {
            DesktopReorderPlaylistDialog("Long playlist", listing, false, null, { saved = it }, {})
        }
        compose.onNodeWithContentDescription("Move Second occurrence up").performClick()
        compose.onNodeWithText("Save order").performClick()
        assertEquals(listOf("set2", "set1"), saved)
        assertEquals(listOf("set1", "set2"), listing.validate(listOf(first, second), assertNotNull(saved)))
        assertFailsWith<IllegalStateException> { listing.validate(listOf(second, first), assertNotNull(saved)) }
        assertFailsWith<IllegalStateException> { listing.validate(listOf(first, second), listOf("set2", "other")) }
    }

    @Test
    fun signedOutLocalRemovalKeepsOtherOccurrencesAndPersistedPins() {
        val prefs = Preferences.userRoot().node("bitchord-test-playlist-edit-${System.nanoTime()}")
        try {
            val persistence = DesktopPersistence(prefs)
            val imported = DesktopPlaylist(title = "Imported", songs = listOf(first, second).map { it.copy(setVideoId = null) })
            val sameTitle = DesktopPlaylist(title = imported.title, songs = listOf(first))
            persistence.savePlaylists(listOf(imported, sameTitle))
            persistence.saveDownloads(listOf(first))
            val restored = persistence.playlists().first()
            val listing = restored.toCollection()
            val pin = DesktopPlaylistPin(restored.id, restored.title, null, null)
            val pins = DesktopPlaylistPins(persistence)
            pins.toggle(pin)
            compose.setContent {
                DesktopRemovePlaylistOccurrenceDialog(
                    listing.title, listing.songs[1], false, null,
                    onRemove = {
                        val edited = assertNotNull(restored.withoutOccurrence(listing, 1)).copy(title = "Renamed")
                        persistence.savePlaylists(listOf(edited, sameTitle))
                        pins.refresh(listOf(pin.copy(title = edited.title)))
                    },
                    onDismiss = {},
                )
            }
            compose.onNodeWithText("Remove").performClick()
            val reopened = DesktopPersistence(prefs)
            val edited = reopened.playlists().first()
            assertEquals(listOf(restored.songs[0]), edited.songs)
            assertNull(edited.withoutOccurrence(listing, 0))
            assertEquals("Renamed", DesktopPlaylistPins(reopened).pins.value.single().title)
            assertEquals(imported.title, reopened.playlists().last().title)
            assertEquals(listOf(first.videoId), reopened.downloads().map(Song::videoId))
        } finally {
            prefs.removeNode()
        }
    }

    @Test
    fun displayPagingKeepsDuplicatesAcrossPagesAndRejectsCycles() {
        val playlist = DesktopCollection("VLPL1", "Songs", "", null, BrowseType.PLAYLIST, listOf(first), "page2")
        val appended = playlist.appendPage("page2", listOf(second), "page3")
        assertEquals(listOf(first, second), appended.songs)
        assertEquals("page3", appended.continuation)
        assertFailsWith<IllegalStateException> { appended.appendPage("page3", emptyList(), "page2") }
        assertFailsWith<IllegalStateException> { appended.appendPage("page3", listOf(second), null) }
        assertEquals(listOf(first, second), mergedSnapshot(listOf(first), appended.songs))
        assertEquals(listOf(first), tracksMissingOffline(appended.songs, emptySet(), emptySet()))
    }
}
