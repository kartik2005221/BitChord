package com.music.bitchord.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.player.DesktopVideoQueue
import com.music.bitchord.ui.player.PlayerPlatform
import com.music.bitchord.ui.player.QueueSidePanel
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DesktopQueuePanelTest {
    private fun track(title: String, tier: QueueTier = QueueTier.CONTEXT, id: String = title) =
        Song(id, title, "Artist", null, queueEntryId = title, queueTier = tier)

    @Test
    fun sidebarOpensAtUpcomingAndReanchorsAfterReplayingABackedUpOccurrence() =
        runDesktopComposeUiTest(width = 340, height = 220) {
            PlayerPlatform.install(DesktopPlayerHost)
            val past = listOf(track("First copy", id = "duplicate"), track("Second copy", id = "duplicate"))
            val upcoming = List(10) { track("Upcoming ${it + 1}") }
            val queue = mutableStateOf(DesktopQueue(past + track("Current song") + upcoming, index = 2))

            setContent {
                MaterialTheme {
                    QueueSidePanel(
                        queue = queue.value.songs,
                        queueIndex = queue.value.index,
                        autoplayEnabled = false,
                        onJumpTo = {},
                        onRemove = {},
                        onMove = { _, _ -> },
                        onClear = {},
                        onReplayHistory = { queue.value = queue.value.replayFromHistory(it) },
                        modifier = Modifier.fillMaxSize(),
                        showTitle = false,
                    )
                }
            }

            onNodeWithText("Upcoming 1").assertIsDisplayed()
            onNodeWithText("Now playing").assertDoesNotExist()
            onNode(hasScrollToIndexAction()).performScrollToIndex(0)
            onNodeWithText("History").assertIsDisplayed()
            onNodeWithText("Current song").assertDoesNotExist()
            onNodeWithText("Second copy").performClick()

            runOnIdle {
                assertEquals("Second copy", queue.value.current?.queueEntryId)
                assertEquals(upcoming, queue.value.upcoming)
            }
            onNodeWithText("Upcoming 1").assertIsDisplayed()
            onNode(hasScrollToIndexAction()).performScrollToIndex(0)
            onNodeWithText("Second copy").assertDoesNotExist()
            onNodeWithText("Current song").assertIsDisplayed()
        }

    @Test
    fun expandedVideoShowsAutoplayAndEmptyTailsAndLocksHistoryForFollowers() =
        runDesktopComposeUiTest(width = 340, height = 220) {
            PlayerPlatform.install(DesktopPlayerHost)
            val history = listOf(track("History song"), track("Current song"))
            val queue = mutableStateOf(history + List(10) { track("AutoPlay ${it + 1}", QueueTier.AUTOPLAY) })

            setContent {
                MaterialTheme {
                    DesktopVideoQueue(
                        queue = queue.value,
                        currentIndex = 1,
                        autoplayEnabled = false,
                        controlsLocked = true,
                        onJumpTo = {},
                        onRemove = {},
                        onMove = { _, _ -> },
                        onClear = {},
                        onReplayHistory = { error("A locked history row cannot replay") },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            onNodeWithText("AutoPlay 1").assertIsDisplayed()
            onNodeWithText("Current song").assertDoesNotExist()
            onNode(hasScrollToIndexAction()).performScrollToIndex(0)
            onNodeWithText("History song").assertIsDisplayed().assertIsNotEnabled()

            runOnIdle { queue.value = history }
            onNodeWithText("Nothing in Up Next").assertIsDisplayed()
            onNodeWithText("Current song").assertDoesNotExist()
            onNodeWithText("History song").assertIsDisplayed()
        }
}
