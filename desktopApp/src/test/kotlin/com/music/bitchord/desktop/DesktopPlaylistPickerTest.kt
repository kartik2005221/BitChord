package com.music.bitchord.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UserPlaylist
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DesktopPlaylistPickerTest {
    @Test
    fun albumSearchPreservesSelectionsAndRetryOmitsCompletedDestinations() {
        val remote = DesktopPlaylistDestination("same", account = true)
        val local = DesktopPlaylistDestination("same", account = false)
        val picked = mutableListOf<List<DesktopPlaylistDestination>>()
        var completed by mutableStateOf(emptySet<DesktopPlaylistDestination>())
        val album = DesktopPlaylistTarget(
            "Album to add",
            listOf(Song("AAAAAAAAAAA", "Track", "Artist", null)),
            album = true,
        )
        runDesktopComposeUiTest(width = 800, height = 720) {
            setContent {
                MaterialTheme {
                    DesktopPlaylistDialog(
                        target = album,
                        accountPlaylists = listOf(UserPlaylist(remote.id, "Morning Beyoncé Mix", "", null)),
                        localPlaylists = listOf(DesktopPlaylist(local.id, "Jazz at home")),
                        signedIn = true,
                        canUseAccount = true,
                        busy = false,
                        error = null,
                        completed = completed,
                        onPick = { picked += it },
                        onCreate = { _, _ -> },
                        onDismiss = {},
                    )
                }
            }

            onNode(hasSetTextAction()).performTextInput("MIX beyonce")
            onNodeWithText("Jazz at home").assertDoesNotExist()
            onNodeWithText("Morning Beyoncé Mix").assertIsOff().performClick().assertIsOn()
            onNodeWithContentDescription("Clear search").performClick()
            onNode(hasSetTextAction()).performTextInput("jazz")
            onNodeWithText("Morning Beyoncé Mix").assertDoesNotExist()
            onNodeWithText("Jazz at home").performClick().assertIsOn()
            onNodeWithText("Add to 2").performClick()
            runOnIdle {
                assertEquals(listOf(remote, local), picked.single())
                completed = setOf(remote)
            }
            onNodeWithText("Add to 1").performClick()
            runOnIdle { assertEquals(listOf(local), picked.last()) }

            onNodeWithContentDescription("Clear search").performClick()
            onNode(hasSetTextAction()).performTextInput("no results")
            onNodeWithText("No matching playlists").assertExists()
            onNodeWithText("New playlist").performClick()
            onNodeWithText("Create playlist").assertExists()
        }
    }
}
