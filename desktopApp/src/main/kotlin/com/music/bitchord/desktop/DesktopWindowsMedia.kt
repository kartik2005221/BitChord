package com.music.bitchord.desktop

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

/**
 * Windows' System Media Transport Controls — the volume flyout's media card,
 * the lock screen, and the media keys.
 *
 * The Windows counterpart of [DesktopMprisController], and the same contract:
 * the player hands it state, it hands back button presses. What it talks to is
 * WinRT, which the JVM cannot reach, so the work happens in `bitchord_smtc.dll`
 * beside the analyser.
 *
 * Absent, everything here is a no-op. The library is only built by MSVC, so a
 * Linux cross-build of the Windows archive has no SMTC in it — the installers,
 * which CI builds on Windows, do.
 */
internal object DesktopWindowsMedia {

    private val started = AtomicBoolean(false)
    private val publicationGate = DesktopPublicationGate()
    private val lifecycleLock = Any()

    private val shutdownHookAdded = AtomicBoolean(false)
    private var privacyScope: CoroutineScope? = null
    @Volatile private var lastState: DesktopPlaybackState? = null

    @Volatile
    private var controller: Controller? = null

    /** What a media key does, filled in by the player. */
    internal class Controller(
        val onPlay: () -> Unit,
        val onPause: () -> Unit,
        val onNext: () -> Unit,
        val onPrevious: () -> Unit,
        val onStop: () -> Unit,
    )

    private val available: Boolean by lazy {
        if (!DesktopPlatform.isWindows) return@lazy false
        runCatching { DesktopAnalysisRuntime.loadNative(LIBRARY) }
            .onFailure { DesktopTrackLog.log("Windows media controls unavailable: ${it.message}") }
            .isSuccess
    }

    fun start(controller: Controller): Unit = synchronized(lifecycleLock) { publicationGate.run {
        // Held whether or not the library is there, so what a button does is
        // decided in one place and can be checked without Windows.
        this.controller = controller
        if (!available || !started.compareAndSet(false, true)) return@run
        if (!nativeStart()) {
            started.set(false)
            DesktopTrackLog.log("Windows media controls could not be created")
            return@run
        }
        privacyScope = CoroutineScope(Dispatchers.IO + Job()).also { scope ->
            scope.launch { DesktopAppearanceSettings.externalArtworkPrivacy.collect { republish() } }
        }
        // Quitting from the tray is exitProcess, which never reaches the window's onDispose. The
        // library's pump thread was then still running when the DLL unloaded, and destroying a
        // joinable std::thread aborts the process — every quit ended in a native crash.
        if (shutdownHookAdded.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(Thread(::stop, "bitchord-smtc-stop"))
        }
    } }

    fun stop() = synchronized(lifecycleLock) {
        val wasStarted = publicationGate.run {
            if (!started.compareAndSet(true, false)) return@run false
            controller = null
            privacyScope?.cancel()
            privacyScope = null
            lastState = null
            true
        }
        // A native callback may invoke player code. Never join its pump while holding the gate.
        if (wasStarted) runCatching { nativeStop() }
        Unit
    }

    /** Publishes the current track. Called on every change the shell would show. */
    fun publish(state: DesktopPlaybackState) {
        publicationGate.run {
            lastState = state
            publishLocked()
        }
    }

    private fun republish() = publicationGate.run { publishLocked() }

    private fun publishLocked() {
        if (!started.get()) return
        val state = lastState ?: return
        val song = state.song
        runCatching {
            nativeUpdate(
                song?.title.orEmpty(),
                song?.artist.orEmpty(),
                song?.albumName.orEmpty(),
                externalArtworkUrl(song?.thumbnailUrl, DesktopAppearanceSettings.externalArtworkPrivacy.value).orEmpty(),
                state.isPlaying,
                state.positionMs,
                state.durationMs,
            )
        }
    }

    /** Called from the native side when a media key or the shell's card is used. */
    @JvmStatic
    fun onButton(button: Int) {
        val target = controller ?: return
        when (button) {
            BUTTON_PLAY -> target.onPlay()
            BUTTON_PAUSE -> target.onPause()
            BUTTON_NEXT -> target.onNext()
            BUTTON_PREVIOUS -> target.onPrevious()
            BUTTON_STOP -> target.onStop()
        }
    }

    private external fun nativeStart(): Boolean
    private external fun nativeStop()
    private external fun nativeUpdate(
        title: String,
        artist: String,
        album: String,
        artUrl: String,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
    )

    private const val LIBRARY = "bitchord_smtc"

    private const val BUTTON_PLAY = 0
    private const val BUTTON_PAUSE = 1
    private const val BUTTON_NEXT = 2
    private const val BUTTON_PREVIOUS = 3
    private const val BUTTON_STOP = 4
}
