package com.music.bitchord.desktop

import java.awt.Toolkit
import java.awt.Window
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Fullscreen on X11, spoken straight to the window manager in the extended window manager
 * hints rather than through AWT's own `GraphicsDevice.setFullScreenWindow`.
 *
 * AWT's fullscreen does its own bounds save and restore, and that races the window manager's:
 * rapid F11 presses left the window shrunken and stuck to the bottom of the screen, with the
 * junk remembered for the next boot. And AWT's answer to "am I fullscreen" is a write-only
 * cached field that says whatever the app last wrote, not what the screen shows — the app's
 * own state desynced from the window manager's and stayed desynced. So on Linux the app never
 * asks AWT at all: the fullscreen request is a `_NET_WM_STATE` ClientMessage to the root
 * window, and the truth is `_NET_WM_STATE` read back off the X server — the same route
 * IntelliJ's platform code has shipped for years, whose X11UiUtil this mirrors down to the
 * reflection wrappers over `sun.awt.X11.XlibWrapper`.
 *
 * Every piece of the plumbing is reached by reflection — no compile-time `sun.*`, no JNA —
 * so the file is inert wherever AWT is not the X toolkit: [DesktopX11Fullscreen.supported]
 * answers false, the callers fall back to Compose's own placement handling, and nothing here
 * can throw out. The reflection needs the packages opened for it, which the packaged launcher
 * carries as `--add-opens` arguments (see build.gradle.kts); without them it answers false too.
 */

/** The EWMH atoms this speaks; the object's internal ones are held to these by tests. */
internal const val X11_ATOM_NET_WM_STATE = "_NET_WM_STATE"
internal const val X11_ATOM_NET_WM_STATE_FULLSCREEN = "_NET_WM_STATE_FULLSCREEN"
internal const val X11_ATOM_NET_WM_ALLOWED_ACTIONS = "_NET_WM_ALLOWED_ACTIONS"
internal const val X11_ATOM_NET_WM_ACTION_FULLSCREEN = "_NET_WM_ACTION_FULLSCREEN"
private const val X11_ATOM_NET_SUPPORTING_WM_CHECK = "_NET_SUPPORTING_WM_CHECK"

/** `_NET_WM_STATE` ClientMessage actions — `data.l[0]` says add or remove. */
internal const val X11_NET_WM_STATE_REMOVE = 0L
internal const val X11_NET_WM_STATE_ADD = 1L

// The X constants the original carries alongside: the ClientMessage event code, the
// 32-bit-property format, the atom type tags, and SubstructureNotify|SubstructureRedirect —
// the mask a hint to the root window must carry so the window manager forwards it.
private const val X11_TRUE = 1
private const val X11_FALSE = 0
private const val X11_XA_ATOM = 4L
private const val X11_XA_WINDOW = 33L
private const val X11_CLIENT_MESSAGE = 33
private const val X11_FORMAT_LONG = 32
private const val X11_SUBSTRUCTURE_NOTIFY_OR_REDIRECT = 3L shl 19

private const val X11_LOG_TAG = "x11 fullscreen"

internal object DesktopX11Fullscreen {

    /** The plumbing, resolved once on first use; null wherever any piece of it could not be reached. */
    private val x11: Xlib? by lazy { Xlib.create() }

    /**
     * Whether the raw EWMH route is available at all — the toolkit is AWT's X toolkit and the
     * Xlib wrappers resolved with a display to speak through. Cheap after the first call, and
     * false without ever throwing wherever any of that fails: headless CI, Windows, macOS, or
     * a JVM run without the `--add-opens`.
     */
    fun supported(window: Window?): Boolean =
        runCatching { Toolkit.getDefaultToolkit().javaClass.name }.getOrNull()
            ?.contains("XToolkit") == true && x11 != null

    /**
     * What the window manager itself says the window is doing — `_NET_WM_STATE` read off the
     * X server, not anything the app wrote. This is the single source of truth; the mode's
     * optimistic flag is corrected against it.
     */
    fun isInFullScreenMode(window: Window?): Boolean {
        val x = x11 ?: return false
        return runCatching {
            hasWindowProperty(x, window, x.netWmState, x.netWmStateFullscreen)
        }.getOrElse { t ->
            fail("cannot read _NET_WM_STATE", t)
            false
        }
    }

    /**
     * Whether the window manager grants this window fullscreen at all —
     * `_NET_WM_ACTION_FULLSCREEN` inside `_NET_WM_ALLOWED_ACTIONS`. A manager that does not
     * list it gets the legacy Compose placement path instead of a hint it would ignore.
     */
    fun isFullscreenAllowed(window: Window?): Boolean {
        val x = x11 ?: return false
        return runCatching {
            hasWindowProperty(x, window, x.netWmAllowedActions, x.netWmActionFullscreen)
        }.getOrElse { t ->
            fail("cannot read _NET_WM_ALLOWED_ACTIONS", t)
            false
        }
    }

    /**
     * Asks the window manager for fullscreen — a `_NET_WM_STATE` ClientMessage to the root
     * window (ADD or REMOVE, the fullscreen atom, zero — the layout the EWMH spec fixes),
     * sent with the substructure masks so the manager forwards it, then flushed. A failure is
     * logged and swallowed: the flag in [DesktopWindowMode] is corrected by the poller either
     * way.
     */
    fun setFullScreenMode(window: Window?, fullscreen: Boolean) {
        val x = x11 ?: return
        runCatching {
            val peer = peerOf(x, window) ?: error("window has no AWT peer")
            val windowId = x.getWindow.invoke(peer) as Long
            if (windowId == 0L) error("peer has no X window handle")
            val screen = x.getScreenNumber.invoke(peer) as Long
            val root = x.rootWindow(screen)
            val action = if (fullscreen) X11_NET_WM_STATE_ADD else X11_NET_WM_STATE_REMOVE
            x.sendClientMessage(root, windowId, x.netWmState, netWmStateData(action, x.netWmStateFullscreen))
        }.onFailure { t -> fail("cannot set fullscreen=$fullscreen", t) }
    }

    /**
     * The `data.l` of a `_NET_WM_STATE` ClientMessage: the action, the fullscreen atom, then
     * the two secondary atoms the spec allows (unused here) and the terminator — five longs,
     * laid out exactly as the manager reads them.
     */
    internal fun netWmStateData(action: Long, stateAtom: Long): LongArray =
        longArrayOf(action, stateAtom, 0L, 0L, 0L)

    /** The window's AWT peer, or null — the one thing standing between the app and its X window. */
    private fun peerOf(x: Xlib, window: Window?): Any? {
        window ?: return null
        return runCatching { x.getPeer.invoke(x.componentAccessor, window) }.getOrNull()
    }

    /** Whether [expected] is among the atoms [name] holds on the window, read off the X server. */
    private fun hasWindowProperty(x: Xlib, window: Window?, name: Long, expected: Long): Boolean {
        val peer = peerOf(x, window) ?: return false
        val windowId = runCatching { x.getWindow.invoke(peer) as Long }.getOrDefault(0L)
        if (windowId == 0L) return false
        val atoms = runCatching { x.getWindowProperty(windowId, name, X11_XA_ATOM) }
            .getOrElse { t ->
                fail("cannot read window property", t)
                return false
            }
        return atoms != null && atoms.contains(expected)
    }

    private fun fail(step: String, t: Throwable) {
        DesktopTrackLog.log("$X11_LOG_TAG: $step: ${t.message ?: t.javaClass.simpleName}")
    }

    /**
     * The X plumbing the original reaches for, mirrored member for member and resolved in the
     * same order it resolves them — an order that only ever runs on a live X toolkit, and that
     * throws (the caller catches) the moment anything is missing.
     */
    private class Xlib private constructor() {

        /** The native memory the X calls' out-parameters and the ClientMessage event live in. */
        val memory: NativeMemory = NativeMemory.create()
            ?: throw IllegalStateException("sun.misc.Unsafe is unavailable")

        private val xlibWrapper: Class<*> = Class.forName("sun.awt.X11.XlibWrapper")
        val xGetWindowProperty: Method = method(xlibWrapper, "XGetWindowProperty", 12)
        val xFree: Method = method(xlibWrapper, "XFree", 1)
        val rootWindowMethod: Method = method(xlibWrapper, "RootWindow", 2)
        val xSendEvent: Method = method(xlibWrapper, "XSendEvent", 5)
        val xFlush: Method = method(xlibWrapper, "XFlush", 1)

        // The peer's shell window and its screen — the X window the window manager manages
        // (the EWMH client, which is what the hints name) and the screen whose root they go to.
        private val xBaseWindow: Class<*> = Class.forName("sun.awt.X11.XBaseWindow")
        val getWindow: Method = method(xBaseWindow, "getWindow")
        val getScreenNumber: Method = method(xBaseWindow, "getScreenNumber")

        /** The toolkit's own display handle — XToolkit.getDisplay(), as the original reads it. */
        private val toolkitClass: Class<*> = Toolkit.getDefaultToolkit().javaClass
        val display: Long = method(toolkitClass, "getDisplay").invoke(null) as Long

        // The AWT lock every X round trip holds — SunToolkit's reentrant lock, inherited by
        // the X toolkit, so the original's walking `method()` finds it on the toolkit class.
        val awtLock: Method = method(toolkitClass, "awtLock")
        val awtUnlock: Method = method(toolkitClass, "awtUnlock")

        // AWTAccessor is how both AWT itself and the original reach the peer.
        val componentAccessor: Any = Class.forName("sun.awt.AWTAccessor")
            .getDeclaredMethod("getComponentAccessor").apply { isAccessible = true }
            .invoke(null) ?: throw IllegalStateException("AWTAccessor.getComponentAccessor() was null")
        val getPeer: Method = method(
            Class.forName("sun.awt.AWTAccessor\$ComponentAccessor"),
            "getPeer",
            java.awt.Component::class.java,
        )

        // The atoms, interned through XAtom the way the original does.
        private val xAtomClass: Class<*> = Class.forName("sun.awt.X11.XAtom")
        private val atomGet: Method = method(xAtomClass, "get", String::class.java)
        private val atomField: Field = field(xAtomClass, "atom")

        val netSupportingWmCheck: Long = intern(X11_ATOM_NET_SUPPORTING_WM_CHECK)
        val netWmAllowedActions: Long = intern(X11_ATOM_NET_WM_ALLOWED_ACTIONS)
        val netWmState: Long = intern(X11_ATOM_NET_WM_STATE)
        val netWmActionFullscreen: Long = intern(X11_ATOM_NET_WM_ACTION_FULLSCREEN)
        val netWmStateFullscreen: Long = intern(X11_ATOM_NET_WM_STATE_FULLSCREEN)

        /** The root window of [screen], under the AWT lock like every X round trip here. */
        fun rootWindow(screen: Long): Long {
            awtLock.invoke(null)
            try {
                return rootWindowMethod.invoke(null, display, screen) as Long
            } finally {
                awtUnlock.invoke(null)
            }
        }

        /**
         * The atoms [name] holds on [window] — `XGetWindowProperty` into a 64-byte block of
         * out-pointers (type, format, count, bytes-after, data), the returned array read long
         * by long, the X server's buffer freed. Null when the property is absent or the call
         * failed; the exceptions are the caller's to catch.
         */
        fun getWindowProperty(window: Long, name: Long, type: Long): LongArray? {
            awtLock.invoke(null)
            try {
                val buf = memory.allocate(64)
                try {
                    memory.zero(buf, 64)
                    val result = xGetWindowProperty.invoke(
                        null,
                        display, window, name,
                        0L, 65535L, X11_FALSE.toLong(), type,
                        buf, buf + 8, buf + 16, buf + 24, buf + 32,
                    ) as Int
                    if (result != 0) return null
                    val format = memory.readInt(buf + 8)
                    val nitems = memory.readLong(buf + 16)
                    val data = memory.readLong(buf + 32)
                    if (data == 0L) return LongArray(0)
                    try {
                        // The X library hands a 32-bit property back as an array of native
                        // longs — 8 bytes each here, which is what the original's FORMAT_LONG
                        // branch reads them as too.
                        if (format != X11_FORMAT_LONG) {
                            DesktopTrackLog.log("$X11_LOG_TAG: unexpected property format $format")
                            return null
                        }
                        return LongArray(nitems.toInt().coerceAtMost(65535)) { i ->
                            memory.readLong(data + 8L * i)
                        }
                    } finally {
                        xFree.invoke(null, data)
                    }
                } finally {
                    memory.free(buf)
                }
            } finally {
                awtUnlock.invoke(null)
            }
        }

        /**
         * The ClientMessage itself — an `XClientMessageEvent` laid out byte for byte the way
         * the original writes it (type at 0, send_event at 16, window at 32, message_type at
         * 40, format at 48, `data.l` from 56), posted to [target] with the substructure masks
         * and flushed so the hint leaves the process at once. True when the manager took it.
         */
        fun sendClientMessage(target: Long, windowId: Long, type: Long, data: LongArray): Boolean =
            try {
                awtLock.invoke(null)
                try {
                    val event = memory.allocate(128)
                    try {
                        memory.zero(event, 128)
                        memory.writeInt(event, X11_CLIENT_MESSAGE)
                        memory.writeInt(event + 16, X11_TRUE)
                        memory.writeLong(event + 32, windowId)
                        memory.writeLong(event + 40, type)
                        memory.writeInt(event + 48, X11_FORMAT_LONG)
                        for (i in data.indices) {
                            memory.writeLong(event + 56 + 8L * i, data[i])
                        }
                        xSendEvent.invoke(null, display, target, false, X11_SUBSTRUCTURE_NOTIFY_OR_REDIRECT, event)
                        xFlush.invoke(null, display)
                    } finally {
                        memory.free(event)
                    }
                } finally {
                    awtUnlock.invoke(null)
                }
                true
            } catch (t: Throwable) {
                DesktopTrackLog.log("$X11_LOG_TAG: cannot send client message: ${t.message ?: t.javaClass.simpleName}")
                false
            }

        private fun intern(name: String): Long {
            val atom = atomGet.invoke(null, name)
                ?: throw IllegalStateException("XAtom.get($name) returned null")
            val id = atomField.get(atom) as Long
            if (id == 0L) throw IllegalStateException("atom $name did not intern")
            return id
        }

        companion object {

            /**
             * Everything resolved or null — one failed reflection, a non-X toolkit, or a
             * manager that does not speak `_NET` (checked the way the original does, through
             * `_NET_SUPPORTING_WM_CHECK` on the root window) leaves the whole route closed.
             */
            fun create(): Xlib? = runCatching {
                val toolkit = Toolkit.getDefaultToolkit().javaClass.name
                if (!toolkit.contains("XToolkit")) {
                    DesktopTrackLog.log("$X11_LOG_TAG: toolkit is $toolkit; the X11 route stays closed")
                    return@runCatching null
                }
                val x = Xlib()
                val check = x.getWindowProperty(x.rootWindow(0), x.netSupportingWmCheck, X11_XA_WINDOW)
                if (check == null || check.isEmpty()) {
                    DesktopTrackLog.log("$X11_LOG_TAG: _NET protocol is not supported")
                    null
                } else {
                    x
                }
            }.getOrElse { t ->
                fail("cannot initialize", t)
                null
            }
        }
    }
}

/**
 * The native memory the X round trips need, borrowed from `sun.misc.Unsafe` — which
 * jdk.unsupported opens to everyone, so this needs no flags and no dependency. The original
 * reached for JNA's Memory; this is the same 64-byte out-parameter block and the same
 * 128-byte event, allocated and freed by hand.
 */
private class NativeMemory private constructor(private val unsafe: Any) {

    private val allocateMethod: Method = unsafe.javaClass.getDeclaredMethod(
        "allocateMemory", java.lang.Long.TYPE,
    ).apply { isAccessible = true }
    private val freeMethod: Method = unsafe.javaClass.getDeclaredMethod(
        "freeMemory", java.lang.Long.TYPE,
    ).apply { isAccessible = true }
    private val setMethod: Method = unsafe.javaClass.getDeclaredMethod(
        "setMemory", java.lang.Long.TYPE, java.lang.Long.TYPE, java.lang.Byte.TYPE,
    ).apply { isAccessible = true }
    private val readIntMethod: Method = unsafe.javaClass.getDeclaredMethod(
        "getInt", java.lang.Long.TYPE,
    ).apply { isAccessible = true }
    private val readLongMethod: Method = unsafe.javaClass.getDeclaredMethod(
        "getLong", java.lang.Long.TYPE,
    ).apply { isAccessible = true }
    private val writeIntMethod: Method = unsafe.javaClass.getDeclaredMethod(
        "putInt", java.lang.Long.TYPE, java.lang.Integer.TYPE,
    ).apply { isAccessible = true }
    private val writeLongMethod: Method = unsafe.javaClass.getDeclaredMethod(
        "putLong", java.lang.Long.TYPE, java.lang.Long.TYPE,
    ).apply { isAccessible = true }

    fun allocate(bytes: Long): Long = allocateMethod.invoke(unsafe, bytes) as Long

    fun free(address: Long) {
        if (address != 0L) freeMethod.invoke(unsafe, address)
    }

    fun zero(address: Long, bytes: Long) {
        setMethod.invoke(unsafe, address, bytes, 0.toByte())
    }

    fun readInt(address: Long): Int = readIntMethod.invoke(unsafe, address) as Int

    fun readLong(address: Long): Long = readLongMethod.invoke(unsafe, address) as Long

    fun writeInt(address: Long, value: Int) {
        writeIntMethod.invoke(unsafe, address, value)
    }

    fun writeLong(address: Long, value: Long) {
        writeLongMethod.invoke(unsafe, address, value)
    }

    companion object {

        fun create(): NativeMemory? = runCatching {
            val theUnsafe = Class.forName("sun.misc.Unsafe")
                .getDeclaredField("theUnsafe")
                .apply { isAccessible = true }
                .get(null) ?: throw IllegalStateException("Unsafe.theUnsafe was null")
            NativeMemory(theUnsafe)
        }.getOrNull()
    }
}

/** The original's own walking accessor — a declared method, or up through the superclasses. */
private fun method(aClass: Class<*>, name: String, vararg parameterTypes: Class<*>): Method {
    var aClass: Class<*>? = aClass
    while (aClass != null) {
        try {
            return aClass.getDeclaredMethod(name, *parameterTypes).apply { isAccessible = true }
        } catch (_: NoSuchMethodException) {
            aClass = aClass.superclass
        }
    }
    throw NoSuchMethodException(name)
}

private fun method(aClass: Class<*>, name: String, parameterCount: Int): Method {
    for (m in aClass.declaredMethods) {
        if (m.parameterCount == parameterCount && m.name == name) {
            return m.apply { isAccessible = true }
        }
    }
    throw NoSuchMethodException(name)
}

private fun field(aClass: Class<*>, name: String): Field =
    aClass.getDeclaredField(name).apply { isAccessible = true }
