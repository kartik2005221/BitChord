package com.music.bitchord.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopSettingsNavigationTest {
    @Test fun everySettingsChildReturnsToMainBeforeWindowHistory() {
        for (page in DesktopSettingsPage.entries - DesktopSettingsPage.MAIN) {
            // Both the visible button and Alt+Left call goBack, which checks this parent first.
            assertEquals(DesktopSettingsPage.MAIN, settingsParentForBack(page, listOf(DesktopSettingsPage.MAIN)), page.name)
            assertEquals("Back to Settings", settingsBackLabel(DesktopSettingsPage.MAIN))
        }
    }

    @Test fun integrationSpotifyBackUnwindsWithoutRecordingReverseVisits() {
        val parents = mutableListOf(DesktopSettingsPage.MAIN, DesktopSettingsPage.INTEGRATIONS)
        assertEquals(DesktopSettingsPage.INTEGRATIONS, settingsParentForBack(DesktopSettingsPage.SPOTIFY_CANVAS, parents))
        assertEquals("Back to Integrations", settingsBackLabel(parents.removeLast()))
        assertEquals(DesktopSettingsPage.MAIN, settingsParentForBack(DesktopSettingsPage.INTEGRATIONS, parents))
        parents.removeLast()
        assertNull(settingsParentForBack(DesktopSettingsPage.MAIN, parents))
        assertEquals(DesktopHistoryTransition.SETTINGS_CHILD, desktopHistoryTransition(false, false, DesktopSettingsPage.SPOTIFY_CANVAS, DesktopSettingsPage.INTEGRATIONS))
        assertEquals(DesktopHistoryTransition.SETTINGS_CHILD, desktopHistoryTransition(false, false, DesktopSettingsPage.INTEGRATIONS, DesktopSettingsPage.MAIN))
    }

    @Test fun directSpotifyPagesUseOrdinaryBack() {
        assertNull(settingsParentForBack(DesktopSettingsPage.SPOTIFY_CANVAS, emptyList()))
        assertNull(settingsParentForBack(DesktopSettingsPage.INTEGRATIONS, emptyList()))
        assertEquals("Back", settingsBackLabel(null))
        assertEquals(DesktopHistoryTransition.RECORD, desktopHistoryTransition(false, false, null, DesktopSettingsPage.INTEGRATIONS))
        assertEquals(DesktopHistoryTransition.RECORD, desktopHistoryTransition(false, false, DesktopSettingsPage.INTEGRATIONS, null))
    }

    private data class Place(val name: String, val settings: DesktopSettingsPage?, val parents: List<DesktopSettingsPage> = emptyList())

    /** Mirrors the collector and goBack: nested parents win over the window history. */
    private class Navigation(initial: Place) {
        var current = initial
        val history = mutableListOf<Place>()

        fun move(next: Place, restoring: Boolean = false) {
            when (desktopHistoryTransition(current.name == next.name, restoring, current.settings, next.settings)) {
                DesktopHistoryTransition.RECORD -> history.add(current)
                else -> Unit
            }
            current = next
        }

        fun back() {
            val parent = settingsParentForBack(current.settings, current.parents)
            if (parent != null) {
                move(Place(parent.name, parent, current.parents.dropLast(1)))
            } else {
                val target = history.removeLastOrNull() ?: return
                move(target, restoring = true)
            }
        }
    }

    @Test fun fullNestedRoundTripDoesNotBounce() {
        val home = Place("Home", null)
        val main = Place(DesktopSettingsPage.MAIN.name, DesktopSettingsPage.MAIN)
        val integrations = Place(DesktopSettingsPage.INTEGRATIONS.name, DesktopSettingsPage.INTEGRATIONS, listOf(DesktopSettingsPage.MAIN))
        val spotify = Place(DesktopSettingsPage.SPOTIFY_CANVAS.name, DesktopSettingsPage.SPOTIFY_CANVAS, listOf(DesktopSettingsPage.MAIN, DesktopSettingsPage.INTEGRATIONS))
        val nav = Navigation(home)
        nav.move(main)
        nav.move(integrations)
        nav.move(spotify)
        assertEquals(listOf(home), nav.history)
        nav.back() // The same parent-first path is used by Alt+Left.
        assertEquals(integrations, nav.current)
        nav.back()
        assertEquals(main, nav.current)
        nav.back()
        assertEquals(home, nav.current)
        nav.back()
        assertEquals(home, nav.current)
        assertEquals(emptyList(), nav.history)
    }

    @Test fun directSidebarEntryReturnsToSidebarWithoutTrap() {
        val sidebar = Place("Spotify sidebar", null)
        val page = Place(DesktopSettingsPage.SPOTIFY_CANVAS.name, DesktopSettingsPage.SPOTIFY_CANVAS)
        val nav = Navigation(sidebar)
        nav.move(page)
        nav.back()
        assertEquals(sidebar, nav.current)
        nav.back()
        assertEquals(sidebar, nav.current)
        assertEquals(emptyList(), nav.history)
    }

    @Test fun restorationDoesNotRecordReverseVisit() {
        assertEquals(DesktopHistoryTransition.RESTORED, desktopHistoryTransition(false, true, DesktopSettingsPage.MAIN, null))
        assertEquals(DesktopHistoryTransition.UNCHANGED, desktopHistoryTransition(true, true, null, null))
    }
}
