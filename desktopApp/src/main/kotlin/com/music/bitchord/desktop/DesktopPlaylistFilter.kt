package com.music.bitchord.desktop

import java.text.Normalizer
import java.util.Locale

private val combiningMarks = Regex("\\p{M}+")
private val playlistWordSeparator = Regex("(?U)\\s+")

private fun String.foldPlaylistSearch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFKD).replace(combiningMarks, "").lowercase(Locale.ROOT)

internal fun <T> filterDesktopPlaylists(playlists: List<T>, query: String, title: (T) -> String): List<T> {
    val words = query.foldPlaylistSearch().split(playlistWordSeparator).filter(String::isNotEmpty)
    if (words.isEmpty()) return playlists
    return playlists.filter { playlist ->
        val name = title(playlist).foldPlaylistSearch()
        words.all { it in name }
    }
}
