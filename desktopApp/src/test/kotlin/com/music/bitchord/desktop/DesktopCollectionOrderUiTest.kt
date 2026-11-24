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
            Song("other", "Drums", "Artist", null),
            pending,
            Song("a", "Acoustic A", "Artist", null),
        )
        val collection = DesktopCollection("spotify:playlist:test", "Mix", "", null, BrowseType.PLAYLIST, songs)
        val played = mutableListOf<Pair<List<Int>, Int>>()
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
                    onRename = null,
                    onDelete = null,
                    onShare = null,
                    onRemoveFromPlaylist = null,
                    likedIds = emptySet(),
                    onPlaySongs = { _, rows, position -> played += rows.map { it.index } to position },
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
                    downloadedIds = emptySet(),
                    downloadInProgress = emptySet(),
                    playlistDownload = null,
                    contentPadding = PaddingValues(),
                )
            }
        }
        compose.onNodeWithContentDescription("Sort songs").performClick()
        compose.onNodeWithText("Date added (newest first)").performClick()
        compose.onNodeWithContentDescription("Play").performClick()
        compose.runOnIdle { assertEquals(listOf(3, 2, 1, 0) to 0, played.single()) }

        compose.onNodeWithContentDescription("Search this list").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Acoustic")
        compose.onNodeWithText("Acoustic A").performClick()
        compose.runOnIdle { assertEquals(listOf(3, 2, 0) to 0, played.last()) }

        // Both identical pending rows have menus; the first is the later source occurrence.
        compose.onAllNodes(hasContentDescription("More options"))[0].performClick()
        compose.onNodeWithText("Find & play on YouTube Music").performClick()
        compose.runOnIdle { assertEquals(listOf(3, 2, 0) to 1, played.last()) }
    }
}
