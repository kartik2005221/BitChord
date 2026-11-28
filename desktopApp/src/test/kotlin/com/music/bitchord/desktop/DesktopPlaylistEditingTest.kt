package com.music.bitchord.desktop

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
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

    private val first = Song(videoId = "aaaaaaaaaaa", title = "First occurrence", artist = "Artist", thumbnailUrl = null, setVideoId = "set1")
    private val second = first.copy(title = "Second occurrence", setVideoId = "set2")

    @Test
    fun desktopReorderSubmitsExplicitOccurrences() {
        val listing = DesktopPlaylistOrder(listOf(first, second))
        var saved: List<String>? = null
        compose.setContent {
            DesktopPlaylistReorderPage("Long playlist", listing, false, null, { saved = it }, {})
        }
        compose.onNodeWithContentDescription("Move Second occurrence up").performClick()
        compose.onNodeWithText("Done").performClick()
        assertEquals(listOf("set2", "set1"), saved)
        assertEquals(listOf("set1", "set2"), listing.validate(listOf(first, second), assertNotNull(saved)))
        assertFailsWith<IllegalStateException> { listing.validate(listOf(second, first), assertNotNull(saved)) }
        assertFailsWith<IllegalStateException> { listing.validate(listOf(first, second), listOf("set2", "other")) }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun longPlaylistReorderSupportsDragPositionJumpAndCancel() {
        val entries = listOf(first, second) + (3..501).map { index ->
            first.copy(title = "Track $index", setVideoId = "set$index")
        }
        val order = DesktopPlaylistOrder(entries)
        val editing = mutableStateOf(true)
        var saved: List<String>? = null
        compose.setContent {
            if (editing.value) {
                DesktopPlaylistReorderPage("Long playlist", order, false, null, { saved = it }, { editing.value = false })
            }
        }
        compose.onNodeWithContentDescription("Drag First occurrence", useUnmergedTree = true).performMouseInput {
            moveTo(center)
            press()
            repeat(6) { moveBy(Offset(0f, 16f)) }
            release()
        }
        compose.onNodeWithText("Done").assertIsEnabled()
        compose.onNodeWithText("Cancel").performClick()
        assertNull(saved)

        compose.runOnIdle { editing.value = true }
        compose.onNodeWithText("1. First occurrence · Artist").performClick()
        compose.onNodeWithText("Position").performTextReplacement("501")
        compose.onNodeWithText("Move", substring = false).performClick()
        compose.onNodeWithText("Done").performClick()
        assertEquals(entries.drop(1).map { requireNotNull(it.setVideoId) } + "set1", saved)
    }

    @Test
    fun localReorderingPersistsOccurrencesAndRejectsAStaleListing() {
        val prefs = Preferences.userRoot().node("bitchord-test-reorder-${System.nanoTime()}")
        try {
            val persistence = DesktopPersistence(prefs)
            val pins = DesktopPlaylistPins(persistence)
            val imported = DesktopPlaylist(title = "Imported", songs = listOf(first, second).map { it.copy(setVideoId = null) })
            persistence.savePlaylists(listOf(imported))
            val listing = persistence.playlists().single().toCollection()
            val edit = DesktopPlaylistEdit(listing, 0, PlaylistEditAction.REORDER, null)
            assertNull(persistLocalPlaylistEdit(persistence, pins, persistence.playlists(), edit, order = listOf("1", "1")))
            assertNotNull(persistLocalPlaylistEdit(persistence, pins, persistence.playlists(), edit, order = listOf("1", "0")))
            val restored = DesktopPersistence(prefs).playlists()
            assertEquals(listing.songs.reversed(), restored.single().songs)
            assertNull(persistLocalPlaylistEdit(persistence, pins, restored, edit, order = listOf("1", "0")))
        } finally {
            prefs.removeNode()
        }
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
                        val edited = assertNotNull(persistLocalPlaylistEdit(
                            persistence, pins, persistence.playlists(),
                            DesktopPlaylistEdit(listing, 0, PlaylistEditAction.REMOVE, 1),
                        ))
                        persistLocalPlaylistEdit(
                            persistence, pins, edited,
                            DesktopPlaylistEdit(edited.first().toCollection(), 0, PlaylistEditAction.RENAME, null),
                            "Renamed",
                        )
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

            assertNotNull(persistLocalPlaylistEdit(
                persistence, pins, reopened.playlists(),
                DesktopPlaylistEdit(edited.toCollection(), 0, PlaylistEditAction.DELETE, null),
            ))
            assertEquals(listOf(sameTitle.id), reopened.playlists().map(DesktopPlaylist::id))
            assertEquals(emptyList(), DesktopPlaylistPins(reopened).pins.value)
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
