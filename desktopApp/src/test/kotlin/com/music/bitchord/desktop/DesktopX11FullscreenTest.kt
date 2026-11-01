package com.music.bitchord.desktop

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The X11 fullscreen's pure parts — the atoms' names, the ClientMessage's `data.l` layout,
 * and the route answering false, without throwing, wherever there is no X toolkit to speak
 * through (CI's headless JVM, any run without the `--add-opens`). The X-touching half of the
 * object only ever runs under a live window manager; a CI box without one cannot reach it,
 * and every path into it sits behind a runCatching that turns a failure into `false`.
 */
class DesktopX11FullscreenTest {

    @Test
    fun testAtomNamesMatchTheEwmhSpec() {
        assertEquals("_NET_WM_STATE", X11_ATOM_NET_WM_STATE)
        assertEquals("_NET_WM_STATE_FULLSCREEN", X11_ATOM_NET_WM_STATE_FULLSCREEN)
        assertEquals("_NET_WM_ALLOWED_ACTIONS", X11_ATOM_NET_WM_ALLOWED_ACTIONS)
        assertEquals("_NET_WM_ACTION_FULLSCREEN", X11_ATOM_NET_WM_ACTION_FULLSCREEN)
    }

    @Test
    fun testClientMessageDataLLayout() {
        // data.l[0] is the action, l[1] the fullscreen atom, l[2] and l[3] the secondary atoms
        // the spec allows (unused for fullscreen) — five longs, the layout the manager reads.
        assertContentEquals(
            longArrayOf(1L, 417L, 0L, 0L, 0L),
            DesktopX11Fullscreen.netWmStateData(X11_NET_WM_STATE_ADD, 417L),
        )
        assertContentEquals(
            longArrayOf(0L, 417L, 0L, 0L, 0L),
            DesktopX11Fullscreen.netWmStateData(X11_NET_WM_STATE_REMOVE, 417L),
        )
    }

    @Test
    fun testSupportedAnswersFalseOnANonX11ToolkitWithoutThrowing() {
        // Headless CI — the toolkit is not the X toolkit, so the route must answer false and
        // never throw. A box with a real X toolkit asserts nothing here; the answer there
        // depends on the JVM's opens, which is the production launcher's business, not a test's.
        val headless = runCatching { java.awt.GraphicsEnvironment.isHeadless() }.getOrDefault(true)
        if (!headless) return
        assertFalse(DesktopX11Fullscreen.supported(null), "headless CI must not find the X11 route")
    }

    @Test
    fun testReadingTheFullscreenStateAnswersFalseWithoutAXServer() {
        // False, never a throw — the runCatching walls around the X calls are what CI exercises.
        assertFalse(DesktopX11Fullscreen.isInFullScreenMode(null))
    }
}
