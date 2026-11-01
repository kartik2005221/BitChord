package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * The folder readings of the computer's own files — the desktop port of the
 * phone's `LocalGroups` — and the Liked shelf's toggle order, which keeps the
 * newest like first the way the phone's offline Liked playlist does.
 */
class DesktopLocalMusicFoldersTest {

    private fun song(title: String, path: String?) = Song(
        videoId = if (path == null) "local:$title" else "local:$path",
        title = title,
        artist = "Artist",
        thumbnailUrl = null,
        localUri = path,
        localPath = path,
    )

    // -- localDirectory ------------------------------------------------------

    @Test
    fun `a file's directory is its path up to the last separator`() {
        assertEquals("/home/k/Downloads/Music/Rock", song("t", "/home/k/Downloads/Music/Rock/a.mp3").localDirectory())
    }

    @Test
    fun `a windows path reads as forward slashes`() {
        assertEquals("C:/Users/k/Music", song("t", "C:\\Users\\k\\Music\\a.mp3").localDirectory())
    }

    @Test
    fun `a pathless row has no directory`() {
        assertNull(song("t", null).localDirectory())
    }

    @Test
    fun `a bare file name has no directory`() {
        assertNull(song("t", "a.mp3").localDirectory())
    }

    // -- groupedForDesktopFolders -------------------------------------------

    @Test
    fun `two same-named folders stay two rows, their parent telling them apart`() {
        val files = listOf(song("T1", "/a/Rock/t1.mp3"), song("T2", "/b/Rock/t2.mp3"))
        val groups = files.groupedForDesktopFolders()
        assertEquals(listOf("/a/Rock", "/b/Rock"), groups.map { it.first })
    }

    @Test
    fun `folders sort by their name, then by where they sit`() {
        val files = listOf(
            song("T1", "/y/Alpha/t1.mp3"),
            song("T2", "/b/Beta/t2.mp3"),
            song("T3", "/a/Beta/t3.mp3"),
        )
        val groups = files.groupedForDesktopFolders()
        // Beta rows tie on folder name; /a/Beta leads by parent path.
        assertEquals(listOf("/a/Beta", "/b/Beta", "/y/Alpha"), groups.map { it.first })
    }

    @Test
    fun `a folder's rows read by title, insensitive to case`() {
        val files = listOf(song("beat", "/g/Folder/a.mp3"), song("Beatle", "/g/Folder/b.mp3"), song("Champ", "/g/Folder/c.mp3"))
        val groups = files.groupedForDesktopFolders()
        assertEquals(listOf("beat", "Beatle", "Champ"), groups.single().second.map(Song::title))
    }

    @Test
    fun `a pathless row belongs to no folder`() {
        val files = listOf(song("Here", "/g/Folder/a.mp3"), song("Gone", null))
        val groups = files.groupedForDesktopFolders()
        assertEquals(1, groups.size)
        assertFalse(groups[0].second.any { it.title == "Gone" })
    }

    // -- likedAfterToggle ----------------------------------------------------

    @Test
    fun `a like leads the shelf`() {
        assertEquals(listOf("b", "a", "c"), likedAfterToggle(listOf("a", "c"), "b"))
    }

    @Test
    fun `toggling a liked row takes it home`() {
        assertEquals(listOf("a", "b"), likedAfterToggle(listOf("a", "b", "c"), "c"))
    }

    @Test
    fun `an unlike takes only its row out`() {
        assertEquals(listOf("a", "c"), likedAfterToggle(listOf("a", "b", "c"), "b"))
    }

    @Test
    fun `an unknown id goes in as a like, leading the shelf`() {
        assertEquals(listOf("z", "a", "b"), likedAfterToggle(listOf("a", "b"), "z"))
    }
}
