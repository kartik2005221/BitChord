package com.music.bitchord.desktop

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.music.bitchord.data.model.Song
import java.util.prefs.Preferences
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DesktopQuickAddTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun targetSurvivesRestartWithinItsProfile() {
        val preferences = Preferences.userRoot().node("quick-add-test-${System.nanoTime()}")
        try {
            val first = DesktopPlaylistPinScope("account", "first")
            val second = first.copy(profileId = "second")
            val destination = DesktopPlaylistDestination("local", account = false)
            DesktopPersistence(preferences).saveQuickAddTarget(first, destination)
            assertEquals(destination, DesktopPersistence(preferences).quickAddTarget(first))
            assertNull(DesktopPersistence(preferences).quickAddTarget(second))
            DesktopPersistence(preferences).saveQuickAddTarget(first, null)
            assertNull(DesktopPersistence(preferences).quickAddTarget(first))
        } finally {
            preferences.removeNode()
        }
    }

    @Test
    fun localAddRejectsDuplicatesAndUndoRemovesOnlyTheAddedOccurrence() {
        val song = Song("abcdefghijk", "Current song", "Artist", null)
        val playlist = DesktopPlaylist(title = "Test target")
        val (added, entry) = assertNotNull(playlist.quickAdd(song))
        assertNull(added.quickAdd(song))
        val separatelyAdded = song.copy(setVideoId = "some-other-entry")
        val editedLater = added.copy(songs = listOf(separatelyAdded) + added.songs)
        assertEquals(listOf(separatelyAdded), editedLater.undoQuickAdd(entry, song.videoId)?.songs)
        assertNull(editedLater.undoQuickAdd("wrong-entry", song.videoId))
    }

    @Test
    fun plusAndPickerAreIndependentAndCheckBlocksAnotherAdd() {
        val added = mutableStateOf(false)
        var pickerOpens = 0
        compose.setContent {
            DesktopQuickAddButtons("Road Trip", added.value, false, { added.value = true }, { pickerOpens++ })
        }
        compose.onNodeWithContentDescription("Choose quick-add playlist").performClick()
        assertEquals(1, pickerOpens)
        compose.onNodeWithContentDescription("Add to Road Trip").performClick()
        compose.onNodeWithContentDescription("Already in Road Trip").assertIsNotEnabled()
        assertEquals(1, pickerOpens)
    }
}
