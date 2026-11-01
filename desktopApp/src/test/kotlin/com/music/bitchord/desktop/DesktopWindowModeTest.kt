package com.music.bitchord.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The confirm/correct half of the window mode: the flag the mode sets when asking for
 * fullscreen is only ever a claim, and the window manager's own answer — read off the X
 * server by the correction poller — is what the app finally believes, the swipe gate with it.
 */
class DesktopWindowModeTest {

    @Test
    fun testARefusedFullscreenRequestIsCorrectedDown() {
        val was = DesktopWindowMode.fullScreen.value
        try {
            DesktopWindowMode.confirmFullScreen(true)
            assertTrue(DesktopWindowMode.fullScreen.value, "the optimistic claim must stand until corrected")
            DesktopWindowMode.confirmFullScreen(false)
            assertFalse(DesktopWindowMode.fullScreen.value, "the manager's refusal must correct the claim down")
        } finally {
            DesktopWindowMode.confirmFullScreen(was)
        }
    }

    @Test
    fun testAConfirmationThatAgreesChangesNothing() {
        val was = DesktopWindowMode.fullScreen.value
        try {
            DesktopWindowMode.confirmFullScreen(was)
            assertEquals(was, DesktopWindowMode.fullScreen.value, "an agreeing confirmation must be a no-op")
        } finally {
            DesktopWindowMode.confirmFullScreen(was)
        }
    }
}
