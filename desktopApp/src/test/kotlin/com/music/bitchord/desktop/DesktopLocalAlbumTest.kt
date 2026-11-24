package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DesktopLocalAlbumTest {
    @Test
    fun readsAlbumTrackAndDiscFromRealAudioTags() {
        val fixture = Path.of(checkNotNull(javaClass.getResource("/local-album/tagged.flac")).toURI())
        val file = Files.createTempFile("track with spaces ", ".flac")
        try {
            Files.copy(fixture, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            assertEquals(
                DesktopLocalTags("Track 10", "Guest Artist", "An Album", "Album Artist", track = 10, disc = 2),
                DesktopLocalTags.read(file),
            )
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun sortsDiscAndTrackNumericallyWithStableTiesAndUntaggedTracksLast() {
        val untagged = song("Untitled")
        val otherUntagged = song("Another untitled", disc = 1)
        val tenth = song("Ten", track = 10, disc = 1)
        val second = song("Two", track = 2)
        val secondTie = song("Another two", track = 2, disc = 1)
        val secondDisc = song("First on disc two", track = 1, disc = 2)
        val shuffled = listOf(untagged, secondDisc, tenth, second, otherUntagged, secondTie)

        assertEquals(listOf(second, secondTie, tenth, secondDisc, untagged, otherUntagged), shuffled.sortedForLocalAlbum())
        assertEquals(listOf(untagged, otherUntagged), listOf(untagged, otherUntagged).sortedForLocalAlbum())
    }

    @Test
    fun albumArtistGroupsTracksAcrossGuestsAndSeparatesSameTitledAlbums() {
        val first = song("First").copy(artist = "Guest", localAlbumArtist = "Artist")
        val second = song("Second")
        val unrelated = song("Other").copy(artist = "Other Artist")
        assertEquals(first.desktopLocalAlbumKey(), second.desktopLocalAlbumKey())
        assertNotEquals(first.desktopLocalAlbumKey(), unrelated.desktopLocalAlbumKey())
    }

    private fun song(title: String, track: Int? = null, disc: Int? = null) =
        Song(title, title, "Artist", null, albumName = "Album", localTrackNumber = track, localDiscNumber = disc)
}
