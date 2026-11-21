package com.music.bitchord.desktop

/**
 * Playlist navigation lives in the sidebar on every page. A restored signed-in session must load
 * its library even when Home is selected; only an already-loaded shelf can skip the request.
 */
internal object DesktopLibraryLoadPolicy {
    fun shouldFetch(
        accountChanged: Boolean,
        hasLoadedLibrary: Boolean,
        playlistDialogOpen: Boolean,
        libraryVisible: Boolean,
        refreshRequested: Boolean = false,
    ): Boolean = refreshRequested || accountChanged || !hasLoadedLibrary || (playlistDialogOpen && !libraryVisible)
}
