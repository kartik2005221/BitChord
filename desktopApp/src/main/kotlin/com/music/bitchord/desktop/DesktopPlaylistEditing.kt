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

    companion object {
        // Imported tracks have no server entry ID. The original position identifies
        // an occurrence for this one editing session, including exact duplicates.
        fun local(songs: List<Song>) = DesktopPlaylistOrder(
            songs.mapIndexed { index, song -> song.copy(setVideoId = index.toString()) },
        )
    }

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

internal fun persistLocalPlaylistEdit(
    persistence: DesktopPersistence,
    pins: DesktopPlaylistPins,
    playlists: List<DesktopPlaylist>,
    edit: DesktopPlaylistEdit,
    title: String? = null,
    order: List<String>? = null,
): List<DesktopPlaylist>? {
    val local = playlists.firstOrNull { it.id == edit.listing.browseId } ?: return null
    val updated = when (edit.action) {
        PlaylistEditAction.RENAME -> local.copy(title = title?.trim()?.takeIf(String::isNotBlank) ?: return null)
        PlaylistEditAction.DELETE -> null
        PlaylistEditAction.REMOVE -> local.withoutOccurrence(edit.listing, edit.index ?: return null) ?: return null
        PlaylistEditAction.REORDER -> {
            if (edit.listing.songs != local.songs || order?.size != local.songs.size) return null
            val positions = order.map { it.toIntOrNull() ?: return null }
            if (positions.toSet().size != local.songs.size || positions.any { it !in local.songs.indices }) return null
            local.copy(songs = positions.map(local.songs::get))
        }
    }
    val next = playlists.mapNotNull { if (it.id == local.id) updated else it }
    persistence.savePlaylists(next)
    if (updated == null) {
        pins.remove(local.id, null)
    } else {
        pins.refresh(listOf(DesktopPlaylistPin(updated.id, updated.title, updated.songs.firstOrNull()?.thumbnailUrl, null)))
    }
    return next
}
