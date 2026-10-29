package com.music.bitchord.desktop

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import java.awt.GraphicsEnvironment
import java.awt.Insets
import java.awt.Toolkit
import java.awt.Window
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

/**
 * The window's geometry, remembered from run to run.
 *
 * GNOME — as are most of the window managers the desktop runs under, unlike KWin
 * with its own window rules — leaves an application's size and place wherever the
 * WM's own defaults put them, so every launch landed away from where the reader
 * had left the window. The desktop keeps its own copies here, in
 * [DesktopPersistence], and hands them back at startup: the size as it was left,
 * the position where the screen it sat on still exists, and the maximized flag,
 * which [DesktopWindowMode]'s starting placement answers to.
 *
 * Nobody owes this a favour at shutdown: the values are written by a background
 * save on every settled change — 400ms after the last one, see [Main.kt] — so a
 * close button, a tray quit or a lost power cut all had the geometry already
 * where it was last seen. [saveTracked] is the belt for a shutdown that outruns
 * a settle, asked from the paths that kill the process outright.
 *
 * Under Wayland a client cannot place its own window, so [loadPosition]'s answer
 * does not arrive there; carrying it anyway costs nothing and keeps X11 sessions —
 * and every other window manager — remembering.
 */
internal object DesktopWindowState {

    private const val KEY_SIZE = "window_size"
    private const val KEY_POSITION = "window_position"
    internal const val KEY_MAXIMIZED = "window_maximized"

    /** The opening window Main.kt has always asked for when nothing remembered says otherwise. */
    private const val DEFAULT_WIDTH_DP = 1_220
    private const val DEFAULT_HEIGHT_DP = 780

    /** [Main.kt] hands the live state over, so a quit that outruns a settle can still [save]. */
    private var tracked: WeakReference<WindowState>? = null

    /** The last run's maximized answer; [DesktopWindowMode]'s starting placement reads it. */
    fun savedPlacement(): WindowPlacement =
        if (DesktopPersistence().boolean(KEY_MAXIMIZED, false)) {
            WindowPlacement.Maximized
        } else {
            WindowPlacement.Floating
        }

    /** The remembered opening size, or the size the app has always opened at when none is saved. */
    fun loadWidth(): Dp =
        parseStoredPositive(DesktopPersistence().string(KEY_SIZE), 'x')?.first?.dp
            ?: DEFAULT_WIDTH_DP.dp

    fun loadHeight(): Dp =
        parseStoredPositive(DesktopPersistence().string(KEY_SIZE), 'x')?.second?.dp
            ?: DEFAULT_HEIGHT_DP.dp

    /** The remembered position, or the platform's own placement when none is saved. */
    fun loadPosition(): WindowPosition {
        val stored = parseStoredAny(DesktopPersistence().string(KEY_POSITION), ',')
            ?: return WindowPosition.PlatformDefault
        return WindowPosition(stored.first.dp, stored.second.dp)
    }

    /** Writes the size and place down. [DesktopWindowMode] keeps the maximized flag as it moves. */
    fun save(state: WindowState) {
        // Only a floating window's geometry is worth writing: a maximized or fullscreen
        // one has its flag kept by [DesktopWindowMode] — that is what the next run starts
        // from — and its size reflects the fill rather than where the reader left their
        // own window.
        if (state.placement != WindowPlacement.Floating) return
        runCatching {
            val persistence = DesktopPersistence()
            persistence.saveString(
                KEY_SIZE,
                "${state.size.width.value.roundToInt()}x${state.size.height.value.roundToInt()}",
            )
            if (state.position is WindowPosition.Absolute) {
                val where = state.position as WindowPosition.Absolute
                persistence.saveString(
                    KEY_POSITION,
                    "${where.x.value.roundToInt()},${where.y.value.roundToInt()}",
                )
            }
        }.onFailure {
            DesktopTrackLog.log("window state not saved: ${it.message ?: it.javaClass.simpleName}")
        }
    }

    /** Saves from anywhere an exit happens without the composition's help — the tray's quit. */
    fun saveTracked() {
        tracked?.get()?.let(::save)
    }

    /** [Main.kt] registers the live state here, for [saveTracked] to find. */
    fun track(state: WindowState) {
        tracked = WeakReference(state)
    }

    /**
     * A remembered monitor can go away. A remembered size can be oversized as well —
     * a smaller display this run, or a size trapped by an older build that multiplied
     * the display's scale into it — so the window is first clamped into the usable area
     * of the screen it has realized on, and only then checked for being on any screen
     * at all: if it still is nowhere, hand it back to the window manager's own
     * placement — AWT re-centres on the primary display — rather than leave it
     * stranded where it cannot be seen or dragged.
     */
    fun ensureOnScreen(window: Window, floats: Boolean = true) {
        window.graphicsConfiguration?.let { config ->
            val screen = config.bounds
            val insets = runCatching {
                Toolkit.getDefaultToolkit().getScreenInsets(config)
            }.getOrNull() ?: Insets(0, 0, 0, 0)
            // The window's bounds and its configuration's speak the same space, so the
            // comparison holds whatever scale the display applies beneath the calls.
            // Only a floating window is clamped: a maximized or fullscreen one has no
            // size of its own to correct, and touches on those fight the placement.
            val widest = screen.width - insets.left - insets.right
            val tallest = screen.height - insets.top - insets.bottom
            if (floats && (window.width > widest || window.height > tallest)) {
                window.setSize(minOf(widest, window.width), minOf(tallest, window.height))
                DesktopTrackLog.log("window size exceeded the screen; clamped to fit")
            }
        }
        val devices = runCatching {
            GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
        }.getOrNull() ?: return
        val onScreen = devices.any { device ->
            device.defaultConfiguration.bounds.intersects(window.bounds)
        }
        if (!onScreen) {
            window.setLocationRelativeTo(null)
            DesktopTrackLog.log("window position not on any screen; replaced with a default one")
        }
    }

    /** "1220x780"-like: two integers joined by [separator], both required positive. */
    private fun parseStoredPositive(raw: String?, separator: Char): Pair<Int, Int>? =
        parseStoredAny(raw, separator)?.takeIf { (a, b) -> a > 0 && b > 0 }

    /** Two integers joined by [separator]; either part failing, or no pair at all, is nothing. */
    private fun parseStoredAny(raw: String?, separator: Char): Pair<Int, Int>? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.trim().split(separator)
        if (parts.size != 2) return null
        val first = parts[0].trim().toIntOrNull() ?: return null
        val second = parts[1].trim().toIntOrNull() ?: return null
        return first to second
    }
}
