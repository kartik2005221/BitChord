package com.music.bitchord.desktop

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.music.bitchord.data.model.Song
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DesktopDownloadsViewTest {
    @Test
    fun `playlist sorting applies to rows and tiles from the downloads toolbar`() {
        val prefs = Preferences.userRoot().node("com.music.bitchord.tests/downloads-view-${UUID.randomUUID()}")
        try {
            val persistence = DesktopPersistence(prefs)
            persistence.saveString("downloaded_view", "LIST")
            persistence.saveString("downloaded_sort", "DATE_ADDED")
            val song = Song("track", "Track", "Artist", null)
            val first = DesktopPlaylistDownload("VLfirst", "alpha", "", null, true, listOf(song), 1, 5)
            val second = first.copy(id = "VLsecond", title = "Bravo", createdAt = 3, updatedAt = 3)
            val third = first.copy(id = "VLthird", title = "Charlie", createdAt = 2, updatedAt = 4)
            var opened: DesktopPlaylistDownload? = null

            runDesktopComposeUiTest(width = 800, height = 600) {
                setContent {
                    MaterialTheme {
                        DesktopDownloadsPage(
                            persistence = persistence,
                            downloads = emptyList(),
                            playlistRecords = listOf(first, second, third),
                            downloadedIds = setOf(song.videoId),
                            downloadInProgress = emptySet(),
                            playlistStaleIds = emptySet(),
                            onSongClick = {},
                            onOpenPlaylist = { opened = it },
                            onUpdatePlaylist = {},
                            onRemovePlaylist = {},
                            contentPadding = PaddingValues(),
                        )
                    }
                }

                onNodeWithText("Playlists").assertIsSelected()
                fun assertOrder(vararg titles: String, grid: Boolean = false) {
                    val displayed = titles.map { onNodeWithText(it).fetchSemanticsNode().boundsInRoot }
                    displayed.zipWithNext().forEach { (before, after) ->
                        assertTrue(if (grid) before.right < after.left else before.bottom < after.top)
                    }
                }

                assertOrder(second.title, third.title, first.title)
                onNodeWithText("Added").performClick()
                assertOrder(first.title, third.title, second.title)
                onNodeWithText("Modified").performClick()
                assertOrder(first.title, second.title, third.title)
                onNodeWithText("A–Z").performClick()
                assertOrder(third.title, second.title, first.title)
                onNodeWithText("Z–A").performClick()
                assertOrder(second.title, third.title, first.title)

                val changeView = onNodeWithContentDescription("Change view")
                changeView.performClick()
                assertOrder(second.title, third.title, first.title, grid = true)
                onNodeWithText("Added").performClick()
                assertOrder(first.title, third.title, second.title, grid = true)
                onNodeWithText("Modified").performClick()
                assertOrder(first.title, second.title, third.title, grid = true)
                onNodeWithText("A–Z").performClick()
                assertOrder(third.title, second.title, first.title, grid = true)
                assertEquals("TITLE_DESC", persistence.string("downloaded_sort", ""))
                assertEquals("GRID", persistence.string("downloaded_view", ""))

                changeView.performClick()
                assertOrder(third.title, second.title, first.title)
                onNode(hasSetTextAction()).performTextInput("brav")
                onNodeWithText(first.title).assertDoesNotExist()
                onNodeWithText(second.title).performClick()
                runOnIdle { assertEquals(second, opened) }
                assertEquals("LIST", persistence.string("downloaded_view", ""))
            }
        } finally {
            prefs.removeNode()
        }
    }

    @Test
    fun `artist drilldown responds to changes in the sort and search controls`() {
        val prefs = Preferences.userRoot().node("com.music.bitchord.tests/downloads-view-${UUID.randomUUID()}")
        try {
            val persistence = DesktopPersistence(prefs)
            persistence.saveString("downloaded_view", "LIST")
            val first = Song("first", "alpha", "The Band", null)
            val second = Song("second", "Bravo", "The Band", null)
            runDesktopComposeUiTest(width = 800, height = 600) {
                setContent {
                    var drillDown by remember { mutableStateOf<DesktopLibraryDrillDown?>(null) }
                    MaterialTheme {
                        DesktopDownloadsPage(
                            persistence = persistence,
                            downloads = listOf(first, second),
                            playlistRecords = emptyList(),
                            downloadedIds = setOf(first.videoId, second.videoId),
                            downloadInProgress = emptySet(),
                            playlistStaleIds = emptySet(),
                            onSongClick = {},
                            onOpenPlaylist = {},
                            onUpdatePlaylist = {},
                            onRemovePlaylist = {},
                            contentPadding = PaddingValues(),
                            drillDown = drillDown,
                            onDrillDownChange = { drillDown = it },
                        )
                    }
                }

                onNodeWithText("Artists").performClick()
                onNodeWithText("The Band").performClick()
                val alpha = onNodeWithText(first.title)
                val bravo = onNodeWithText(second.title)
                assertTrue(alpha.fetchSemanticsNode().boundsInRoot.bottom < bravo.fetchSemanticsNode().boundsInRoot.top)
                onNodeWithText("A–Z").performClick()
                assertTrue(bravo.fetchSemanticsNode().boundsInRoot.bottom < alpha.fetchSemanticsNode().boundsInRoot.top)
                onNode(hasSetTextAction()).performTextInput("brav")
                alpha.assertDoesNotExist()
                bravo.assertExists()
            }
        } finally {
            prefs.removeNode()
        }
    }
}
