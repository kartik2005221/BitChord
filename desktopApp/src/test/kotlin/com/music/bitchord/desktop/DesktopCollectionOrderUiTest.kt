package com.music.bitchord.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.Song
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class DesktopCollectionOrderUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `sort feeds header play and filtered Spotify menu uses the displayed occurrence`() {
        val pending = Song("sp:same", "Acoustic D", "Artist", null)
        val songs = listOf(
            pending,
            Song("other", "Drums", "Artist", null, localDateAddedSeconds = 10),
            pending,
            Song("a", "Acoustic A", "Artist", null),
        )
        val collection = DesktopCollection("spotify:playlist:test", "Mix", "", null, BrowseType.PLAYLIST, songs)
        val played = mutableListOf<Pair<List<Int>, Int>>()
        showCollection(collection, onPlay = { order, position -> played += order to position })
        compose.onNodeWithContentDescription("Sort songs").performClick()
        compose.onNodeWithText("Date added (newest first)").assertDoesNotExist()
        compose.onNodeWithText("Date added (oldest first)").assertDoesNotExist()
        compose.onNodeWithText("Alphabetical (Z to A)").performClick()
        compose.onNodeWithContentDescription("Play").performClick()
        compose.runOnIdle { assertEquals(listOf(1, 0, 2, 3) to 0, played.single()) }

        compose.onNodeWithContentDescription("Search this list").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Acoustic")
        compose.onNodeWithText("Acoustic A").performClick()
        compose.runOnIdle { assertEquals(listOf(0, 2, 3) to 2, played.last()) }

        // Both identical pending rows have menus; the second is the later source occurrence.
        compose.onAllNodes(hasContentDescription("More options"))[1].performClick()
        compose.onNodeWithText("Find & play on YouTube Music").performClick()
        compose.runOnIdle { assertEquals(listOf(0, 2, 3) to 1, played.last()) }
    }

    @Test fun `remove from a sorted searched playlist uses the original occurrence`() {
        val first = Song("same", "Acoustic D", "Artist", null, setVideoId = "first")
        val second = first.copy(setVideoId = "second")
        val songs = listOf(
            first,
            Song("other", "Drums", "Artist", null, setVideoId = "other"),
            second,
            Song("a", "Acoustic A", "Artist", null, setVideoId = "a"),
        )
        val collection = DesktopCollection("VLowned", "Mix", "", null, BrowseType.PLAYLIST, songs, owned = true)
        val removed = mutableListOf<Pair<Int, String?>>()
        showCollection(collection, onRemove = { index, song -> removed += index to song.setVideoId })

        compose.onNodeWithContentDescription("Sort songs").performClick()
        compose.onNodeWithText("Alphabetical (A to Z)").performClick()
        compose.onNodeWithContentDescription("Search this list").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Acoustic")
        compose.onAllNodes(hasContentDescription("Remove from playlist"))[2].performClick()
        compose.runOnIdle { assertEquals(listOf<Pair<Int, String?>>(2 to "second"), removed) }
    }

    @Test fun `playlist menu reports only current download membership`() {
        val catalog = (1..311).map { Song("track-$it", "Track $it", "Artist", null) }
        val record = DesktopPlaylistDownload(
            "spotify:playlist:fixture", "daylist", "", null, true, catalog, 1L, 2L,
        )
        val live = DesktopCollection(record.id, record.title, "", null, BrowseType.PLAYLIST, catalog.take(50))
        showCollection(
            live,
            playlistDownload = record,
            downloadedIds = catalog.map(Song::videoId).toSet() - setOf("track-49", "track-50"),
            activeIds = setOf("track-49", "track-50", "track-311"),
        )
        compose.onNodeWithContentDescription("More", substring = false).performClick()
        compose.onNodeWithText("48 of 50 downloaded · 2 in progress").assertExists()
        compose.onNodeWithText("311", substring = true).assertDoesNotExist()
    }

    private fun showCollection(
        collection: DesktopCollection,
        onPlay: (List<Int>, Int) -> Unit = { _, _ -> },
        onRemove: ((Int, Song) -> Unit)? = null,
        playlistDownload: DesktopPlaylistDownload? = null,
        downloadedIds: Set<String> = emptySet(),
        activeIds: Set<String> = emptySet(),
    ) {
        compose.setContent {
            val sort = remember { mutableStateOf(DesktopCollectionSort.DEFAULT) }
            Box(Modifier.size(1000.dp, 720.dp)) {
                DesktopCollectionPage(
                    collection = collection,
                    pinned = false,
                    onTogglePin = null,
                    loadingMore = false,
                    onLoadMore = {},
                    sort = sort.value,
                    onSort = { _, selection -> sort.value = selection },
                    pageError = null,
                    localPlaylist = false,
                    onReorder = null,
                    onRename = null,
                    onDelete = null,
                    onShare = null,
                    onRemoveFromPlaylist = onRemove,
                    likedIds = emptySet(),
                    onPlaySongs = { _, rows, position -> onPlay(rows.map { it.index }, position) },
                    onDownloadAll = {},
                    signedIn = false,
                    onLibraryChanged = {},
                    onStartMix = {},
                    onQueueSongs = { _, _ -> },
                    songMenu = { song, play ->
                        if (song.unresolvedSpotifyTrack() != null) DesktopSpotifyPendingSongMenu(play)
                    },
                    animatedCanvas = false,
                    onToggleLike = {},
                    onDownload = {},
                    onAddToPlaylist = {},
                    downloadedIds = downloadedIds,
                    downloadInProgress = activeIds,
                    playlistDownload = playlistDownload,
                    contentPadding = PaddingValues(),
                )
            }
        }
    }
}
