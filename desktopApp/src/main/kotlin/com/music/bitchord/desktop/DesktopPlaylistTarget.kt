package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song

internal data class DesktopPlaylistTarget(
    val title: String,
    val songs: List<Song>,
    val album: Boolean,
) {
    val playableSongs: List<Song> = songs.filter { it.unresolvedSpotifyTrack() == null }
    val accountVideoIds: List<String> =
        playableSongs.map(Song::videoId).filter(DesktopSearchClient::isVideoId).distinct()

    companion object {
        fun track(song: Song) = DesktopPlaylistTarget("${song.title} — ${song.artist}", listOf(song), album = false)
    }
}

internal data class DesktopPlaylistDestination(val id: String, val account: Boolean)

/**
 * Re-read the destination before each attempt, including a retry after a failed or
 * interrupted request. Confirmed batches are also remembered in case a read lags a write.
 */
internal suspend fun appendAlbumToAccountPlaylist(
    videoIds: List<String>,
    confirmed: MutableSet<String>,
    readIds: suspend () -> Set<String>,
    append: suspend (List<String>) -> Unit,
) {
    val existing = readIds() + confirmed
    val missing = videoIds.distinct().filterNot(existing::contains)
    for (batch in missing.chunked(50)) {
        append(batch)
        confirmed.addAll(batch)
    }
}

internal suspend fun loadDesktopAlbumTracks(
    first: List<Song>,
    continuation: String?,
    loadPage: suspend (String) -> Pair<List<Song>, String?>,
): List<Song> {
    val songs = first.toMutableList()
    val visited = HashSet<String>()
    var next = continuation
    while (next != null) {
        check(visited.size < 100 && visited.add(next)) { "Could not load the complete album" }
        val (rows, token) = loadPage(next)
        check(rows.isNotEmpty()) { "Could not load the complete album" }
        songs += rows
        next = token
    }
    return songs
}
