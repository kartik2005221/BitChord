package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.isUnresolvedSpotify

internal enum class DesktopCollectionSort {
    DEFAULT,
    TITLE_ASC,
    TITLE_DESC,
}

/** Indices refer to the loaded collection before sorting or filtering. */
internal fun List<Song>.inCollectionOrder(sort: DesktopCollectionSort): List<IndexedValue<Song>> {
    val rows = withIndex().toList()
    return when (sort) {
        DesktopCollectionSort.DEFAULT -> rows
        DesktopCollectionSort.TITLE_ASC ->
            rows.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.value.title })
        DesktopCollectionSort.TITLE_DESC ->
            rows.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.value.title })
    }
}

internal fun List<IndexedValue<Song>>.matchingCollectionRows(query: String): List<IndexedValue<Song>> {
    val needle = query.trim()
    if (needle.isEmpty()) return this
    return filter { (_, song) ->
        song.title.contains(needle, ignoreCase = true) ||
            song.artist.contains(needle, ignoreCase = true) ||
            song.albumName.orEmpty().contains(needle, ignoreCase = true)
    }
}

internal fun Song.isPlayableCollectionTrack(): Boolean = !isUnresolvedSpotify

/** Resolve the selected position through placeholder removal, even when video IDs repeat. */
internal fun List<Song>.playableContextAt(selectedIndex: Int): IndexedValue<List<Song>>? {
    if (getOrNull(selectedIndex)?.isPlayableCollectionTrack() != true) return null
    var selectedPosition = 0
    val playable = filterIndexed { index, song ->
        val ready = song.isPlayableCollectionTrack()
        if (ready && index < selectedIndex) selectedPosition++
        ready
    }
    return IndexedValue(selectedPosition, playable)
}

/** Spotify pages grow by appending, and resolve their rows in place. */
internal fun DesktopCollection.resolveSpotifyOccurrence(
    sourceIndex: Int,
    spotifyId: String,
    replacement: Song,
): DesktopCollection {
    if (songs.getOrNull(sourceIndex)?.unresolvedSpotifyTrack()?.id != spotifyId) return this
    return copy(songs = songs.toMutableList().also { it[sourceIndex] = replacement })
}
