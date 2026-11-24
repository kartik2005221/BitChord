package com.music.bitchord.desktop

import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.Song

internal fun DesktopPlaylist.toCollection() = DesktopCollection(
    browseId = id,
    title = title,
    subtitle = "Playlist • ${songs.size} songs",
    thumbnailUrl = songs.firstOrNull()?.thumbnailUrl,
    type = BrowseType.PLAYLIST,
    songs = songs,
)

internal fun DesktopPlaylist.withoutOccurrence(listing: DesktopCollection, index: Int): DesktopPlaylist? {
    if (listing.browseId != id || listing.songs != songs || index !in songs.indices) return null
    return copy(songs = songs.toMutableList().apply { removeAt(index) })
}

internal fun DesktopPlaylist.addTracks(tracks: List<Song>): DesktopPlaylist {
    val present = songs.mapTo(HashSet()) { it.videoId }
    return copy(songs = songs + tracks.filter { present.add(it.videoId) })
}

internal fun DesktopCollection.appendPage(token: String, rows: List<Song>, next: String?): DesktopCollection {
    val seen = seenContinuations + token
    check(next == null || next !in seen) { "The server repeated a page of this collection" }
    val songs = if (type == BrowseType.PLAYLIST) {
        val entryIds = songs.mapNotNullTo(HashSet()) { it.setVideoId }
        check(rows.all { it.setVideoId?.let(entryIds::add) ?: true }) {
            "The server repeated playlist entries"
        }
        songs + rows
    } else {
        (songs + rows).distinctBy(Song::videoId)
    }
    return copy(songs = songs, continuation = next, seenContinuations = seen)
}

internal class DesktopPlaylistOrder(val entries: List<Song>) {
    private val original = entries.map { requireNotNull(it.setVideoId) }

    fun validate(fresh: List<Song>, target: List<String>): List<String> {
        check(fresh.map { it.setVideoId } == original &&
            original.size == original.toSet().size &&
            target.size == original.size && target.toSet() == original.toSet()
        ) { "The playlist changed. Reopen the editor and try again." }
        return original
    }
}

internal enum class PlaylistEditAction { RENAME, DELETE, REMOVE, REORDER }

internal data class DesktopPlaylistEdit(
    val listing: DesktopCollection,
    val generation: Long,
    val action: PlaylistEditAction,
    val index: Int?,
)
