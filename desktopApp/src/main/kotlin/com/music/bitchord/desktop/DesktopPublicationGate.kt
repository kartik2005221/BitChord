package com.music.bitchord.desktop

/** Serializes final metadata decisions with publication and shutdown. */
internal class DesktopPublicationGate {
    private val lock = Any()
    fun <T> run(block: () -> T): T = synchronized(lock, block)
}
