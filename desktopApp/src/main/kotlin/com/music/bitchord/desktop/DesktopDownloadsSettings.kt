package com.music.bitchord.desktop

import java.util.prefs.Preferences

internal enum class DownloadsOpeningTab(val label: String) {
    PLAYLISTS("Playlists"), SONGS("Songs"), ARTISTS("Artists"), ALBUMS("Albums"), LAST_USED("Last used")
}

internal class DesktopDownloadsSettings(private val prefs: Preferences) {
    fun openingTab(): DownloadsOpeningTab = runCatching {
        DownloadsOpeningTab.valueOf(prefs.get("downloads_opening_tab", "PLAYLISTS"))
    }.getOrDefault(DownloadsOpeningTab.PLAYLISTS)

    fun setOpeningTab(value: DownloadsOpeningTab) = prefs.put("downloads_opening_tab", value.name)
    fun lastTab(): DownloadsTab = runCatching {
        DownloadsTab.valueOf(prefs.get("downloads_last_tab", "PLAYLISTS"))
    }.getOrDefault(DownloadsTab.PLAYLISTS)
    fun setLastTab(value: DownloadsTab) = prefs.put("downloads_last_tab", value.name)
    fun autoUpdate(): Boolean = prefs.getBoolean("downloads_auto_update_on_open", false)
    fun setAutoUpdate(value: Boolean) = prefs.putBoolean("downloads_auto_update_on_open", value)

    fun initialTab(): DownloadsTab = when (val choice = openingTab()) {
        DownloadsOpeningTab.LAST_USED -> lastTab()
        else -> DownloadsTab.valueOf(choice.name)
    }
}

/** Removed tracks remain in snapshots, so compare against the merged result, not the live list. */
internal fun shouldAutoUpdatePlaylist(enabled: Boolean, existingIds: List<String>, freshIds: List<String>): Boolean =
    enabled && (freshIds + existingIds).distinct() != existingIds

/** Return a complete listing or a failure; callers must not save a partial result. */
internal suspend fun <T, K> collectDownloadListing(
    initial: List<T>,
    continuation: String?,
    key: (T) -> K,
    fetch: suspend (String) -> Result<Pair<List<T>, String?>>,
): Result<List<T>> {
    var rows = initial
    var next = continuation
    val visited = mutableSetOf<String>()
    while (next != null) {
        val token = next
        if (!visited.add(token)) return Result.failure(IllegalStateException("Repeated continuation"))
        val page = fetch(token).getOrElse {
            if (it is kotlinx.coroutines.CancellationException) throw it
            return Result.failure(it)
        }
        rows = (rows + page.first).distinctBy(key)
        next = page.second
    }
    return Result.success(rows)
}
