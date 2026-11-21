package com.music.bitchord.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DesktopSidebarActionsTest {
    @Test
    fun `Back skips trailing entries for the current destination`() {
        val history = listOf("home", "library", "artist", "artist")
        assertEquals(1, desktopBackIndex(history, "artist") { it })
        assertEquals(-1, desktopBackIndex(listOf("home", "home"), "home") { it })
        assertEquals(-1, desktopBackIndex(emptyList<String>(), "home") { it })
    }

    @Test
    fun `both sidebar presentations display commands with their current availability`() {
        for (collapsed in listOf(true, false)) {
            runDesktopComposeUiTest(width = if (collapsed) 48 else 196, height = 85) {
                var backClicks = 0
                var refreshClicks = 0
                val ready = mutableStateOf(false)
                val refresh = mutableStateOf<DesktopPageRefresh?>(null)
                setContent {
                    MaterialTheme {
                        DesktopSidebarActions(
                            collapsed = collapsed,
                            backLabel = "Back to Settings",
                            canGoBack = ready.value,
                            refresh = refresh.value,
                            onBack = { backClicks++ },
                            onRefresh = {
                                refreshClicks++
                                refresh.value = DesktopPageRefresh(loading = true) {}
                            },
                        )
                    }
                }
                val back = onNodeWithContentDescription("Back to Settings")
                val refreshButton = onNodeWithContentDescription("Refresh")
                back.assertIsDisplayed().assertIsNotEnabled()
                refreshButton.assertIsDisplayed().assertIsNotEnabled()

                runOnIdle {
                    ready.value = true
                    refresh.value = DesktopPageRefresh(loading = false) {}
                }
                back.assertIsEnabled().performClick()
                refreshButton.assertIsEnabled().performClick()
                refreshButton.assertIsDisplayed().assertIsNotEnabled()
                runOnIdle {
                    assertEquals(1, backClicks)
                    assertEquals(1, refreshClicks)
                }
            }
        }
    }
}
