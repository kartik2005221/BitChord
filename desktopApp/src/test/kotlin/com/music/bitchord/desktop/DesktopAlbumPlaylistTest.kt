package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DesktopAlbumPlaylistTest {
    private fun song(id: String) = Song(id, "Track $id", "Artist", null)

    @Test
    fun localAddsKeepExistingOccurrencesAndAppendDistinctTracksInOrder() {
        val first = song("first")
        val existing = listOf(first, song("second"), first)
        val incoming = listOf(song("third"), first, song("fourth"), song("third"))
        val updated = DesktopPlaylist(title = "Destination", songs = existing).addTracks(incoming)

        assertEquals(existing + listOf(song("third"), song("fourth")), updated.songs)
        assertEquals(updated, updated.addTracks(incoming))
    }

    @Test
    fun albumPagesKeepTrackOrderAndRejectRepeatedTokens() = runTest {
        val first = song("first")
        val second = song("second")
        val third = song("third")
        val songs = loadDesktopAlbumTracks(listOf(first), "page2") { token ->
            when (token) {
                "page2" -> listOf(second) to "page3"
                "page3" -> listOf(third) to null
                else -> error("Unexpected continuation")
            }
        }
        assertEquals(listOf(first, second, third), songs)
        assertFailsWith<IllegalStateException> {
            loadDesktopAlbumTracks(listOf(first), "page2") { listOf(second) to "page2" }
        }
    }

    @Test
    fun accountRetrySkipsExistingAndConfirmedRowsAfterAnAmbiguousFailure() = runTest {
        val album = (1..120).map { "track$it" }
        val confirmed = mutableSetOf<String>()
        val visible = mutableSetOf(album.first())
        val requests = mutableListOf<List<String>>()
        assertFailsWith<IllegalStateException> {
            appendAlbumToAccountPlaylist(album, confirmed, { visible }) { batch ->
                requests += batch
                if (requests.size == 2) {
                    visible += batch
                    error("Connection closed after the server accepted the request")
                }
            }
        }
        appendAlbumToAccountPlaylist(album, confirmed, { visible }) { requests += it }

        assertEquals(listOf(50, 50, 19), requests.map(List<String>::size))
        assertEquals(album.drop(1), requests.flatten())
    }

    @Test
    fun accountAddDoesNotWriteOverAnUnreadablePlaylist() = runTest {
        val writes = mutableListOf<List<String>>()
        assertFailsWith<IllegalStateException> {
            appendAlbumToAccountPlaylist(listOf("track"), mutableSetOf(), { error("Incomplete listing") }) {
                writes += it
            }
        }
        assertEquals(emptyList(), writes)
    }

    @Test
    fun accountReceivesOnlyDistinctRecognizedTracksFromTheAlbum() {
        val local = song("local:/tmp/source.flac")
        val youtube = song("XXXXXXXXXXX")
        val other = song("BBBBBBBBBBB")
        val target = DesktopPlaylistTarget("Album", listOf(local, youtube, other, youtube), album = true)

        assertEquals(listOf("XXXXXXXXXXX", "BBBBBBBBBBB"), target.accountVideoIds)
        assertEquals(listOf(local, youtube, other, youtube), target.playableSongs)
    }
}
