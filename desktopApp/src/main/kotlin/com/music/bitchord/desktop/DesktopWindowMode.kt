package com.music.bitchord.desktop

import androidx.compose.ui.window.WindowPlacement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** How the window is sitting on the screen: floating, maximized, or filling it. */
internal object DesktopWindowMode {

    // The maximized flag the last run left behind answers for the placement the window starts in
    // — see [DesktopWindowState]. Main.kt seeds its own window state with this same flow's value,
    // so both layers agree before any effect runs and restoring the flag survives the
    // placement-driven effect below rather than being caught between it and the compose set.
    private val bootPlacement = DesktopWindowState.savedPlacement()
    private val _placement = MutableStateFlow(bootPlacement)
    private val _fullScreen = MutableStateFlow(false)
    private val _maximized = MutableStateFlow(bootPlacement == WindowPlacement.Maximized)

    /**
     * A placement the app itself asked for and has not yet seen the window confirm.
     *
     * The window manager applies a fullscreen request asynchronously and its mid-transition
     * reports lie: an ordinary bounds event in the gap reads `Floating` while the screen is
     * visibly filling. Believing one desynced the two layers for good — the app thought
     * "floating" (opening the player's swipe gate) while the window stayed fullscreen, and
     * nothing ever told it otherwise, because the re-apply guard in Compose saw both layers
     * agreeing on the lie. A report that matches the pending value confirms it; one that
     * disagrees is ignored until the pending value expires, and then whatever the window
     * says wins (fail-open). Seeded with the boot value, so a slow WM maximising on launch
     * cannot have its work undone by an early report either.
     */
    private var pendingPlacement: WindowPlacement? = bootPlacement
    private var pendingAtMs = System.currentTimeMillis()

    /** When the window last left fullscreen — stores that must not record a transition read this. */
    private var lastFullScreenExitAtMs = 0L
    private var lastToggleAtMs = 0L

    /** What the window's own `placement` is driven from. */
    val placement: StateFlow<WindowPlacement> = _placement

    /** Whether the player is filling the screen, for the controls that say so. */
    val fullScreen: StateFlow<Boolean> = _fullScreen

    /** Whether the window is maximized, for the caption button's own glyph. */
    val maximized: StateFlow<Boolean> = _maximized

    /**
     * The placement the player's full-screen button asks for.
     *
     * Maximized on Windows, not Fullscreen: Compose routes Fullscreen through Skiko's layer, which
     * swaps the rendering surface and there leaves the whole screen white.
     */
    private val fillsScreen: WindowPlacement =
        if (DesktopPlatform.drawsOwnWindowFrame) WindowPlacement.Maximized else WindowPlacement.Fullscreen

    /**
     * F11. A press while a transition is in flight — or one just asked for — did nothing
     * useful and everything harmful: each enter captured and each exit restored a
     * mid-transition geometry, and five rapid presses left the window shrunken and stuck to
     * the bottom of the screen (the two restorers — AWT's remembered bounds and the window
     * manager's — racing on every cycle). The settled state wins a spam.
     */
    fun toggle() {
        val now = System.currentTimeMillis()
        if (isTransitionPending(now) || now - lastToggleAtMs < TOGGLE_LOCK_MS) return
        lastToggleAtMs = now
        setFullScreen(!_fullScreen.value)
    }

    /**
     * Maximize or restore, which is what the caption button in the corner does and what
     * double-clicking the title bar has always done.
     */
    fun toggleMaximized() {
        if (DesktopPlatform.isWindows && _fullScreen.value && DesktopWindowsFrame.setFullscreen(false)) {
            DesktopWindowBackdrop.apply()
        }
        _fullScreen.value = false
        set(if (_placement.value == WindowPlacement.Maximized) WindowPlacement.Floating else WindowPlacement.Maximized)
    }

    /**
     * Leaves full screen, for the paths that can only mean "get me out" — closing the player, or
     * the window being put away to the tray.
     */
    fun exit() {
        if (_fullScreen.value) setFullScreen(false)
    }

    /**
     * Takes the window's word for how it is sitting — but only once the window has had its say
     * for good. Returns whether the report was adopted; a rejected one (it disagrees with a
     * transition still in flight) should be re-asserted by the caller writing this object's
     * placement back over the window, and Compose's own guard makes that re-apply idempotent.
     */
    fun adopt(placement: WindowPlacement): Boolean {
        pendingPlacement?.let { pending ->
            val now = System.currentTimeMillis()
            if (now - pendingAtMs >= PENDING_EXPIRY_MS) {
                pendingPlacement = null
            } else if (placement == pending) {
                pendingPlacement = null
            } else {
                return false
            }
        }
        if (placement == _placement.value) return true
        if (placement == WindowPlacement.Floating) _fullScreen.value = false
        set(placement)
        return true
    }

    /** Whether a placement the app asked for is still awaiting the window's confirmation. */
    fun isTransitionPending(nowMs: Long = System.currentTimeMillis()): Boolean =
        pendingPlacement != null && nowMs - pendingAtMs < PENDING_EXPIRY_MS

    /** When the window last left fullscreen — stores that must not record a transition read this. */
    fun lastFullScreenExitAtMs(): Long = lastFullScreenExitAtMs

    private fun setFullScreen(on: Boolean) {
        _fullScreen.value = on
        if (!on) lastFullScreenExitAtMs = System.currentTimeMillis()
        // On Windows the only thing that covers a taskbar told to stay on top is a native borderless
        // window raised above it; no Compose placement does, and true Fullscreen paints the screen
        // white. With that in place the window's placement is left as it was — it is the Win32 style
        // that changes, not the placement.
        if (DesktopPlatform.isWindows && DesktopWindowsFrame.setFullscreen(on)) {
            // The native window resets DWM's frame margin on the way out; the chosen material goes
            // back over it so the window is exactly as it was before fullscreen.
            if (!on) DesktopWindowBackdrop.apply()
            return
        }
        set(if (on) fillsScreen else WindowPlacement.Floating)
    }

    private fun set(placement: WindowPlacement) {
        val maximized = placement == WindowPlacement.Maximized
        pendingPlacement = placement
        pendingAtMs = System.currentTimeMillis()
        _placement.value = placement
        _maximized.value = maximized
        // The flag is what the next run starts from — see [DesktopWindowState.savedPlacement].
        DesktopPersistence().saveBoolean(DesktopWindowState.KEY_MAXIMIZED, maximized)
    }

    /** How long the window has to confirm a placement the app asked for, before its word wins. */
    private const val PENDING_EXPIRY_MS = 1_500L

    /** How soon after a toggle another one is heard: the presses in between had only ever harmed. */
    private const val TOGGLE_LOCK_MS = 300L
}
