package com.music.bitchord.desktop

import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.QueueSource
import com.music.bitchord.playback.QueueTimeline
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class DesktopCollectionOrderTest {
    @Test fun `filtered sorted playlist starts at the tapped duplicate and keeps manual queue priority`() {
        val first = song("track", "Alpha acoustic", "first")
        val second = song("track", "Alpha acoustic", "second")
        val last = song("c", "Canary acoustic", "third")
        val source = listOf(first, song("d", "Drums"), last, second)
        val shown = source.inCollectionOrder(DesktopCollectionSort.TITLE_ASC).matchingCollectionRows("acoustic")
        assertEquals(listOf(0, 3, 2), shown.map { it.index })

        val (index, playable) = checkNotNull(shown.map { it.value }.playableContextAt(1))
        val manual = song("m", "Manual").copy(queueTier = QueueTier.USER_QUEUE, queueEntryId = "manual")
        val queue = QueueTimeline.buildContextQueue(
            currentTimeline = listOf(song("old", "Old"), manual),
            currentIndex = 0,
            newContextSongs = playable,
            selectedIndex = index,
            contextSource = QueueSource("Mix", PlaybackSourceType.BROWSE, "VLmix"),
        )
        assertEquals(listOf("first", "second", null, "third"), queue.timeline.map { it.setVideoId })
        assertEquals("second", queue.timeline[queue.startIndex].setVideoId)
        assertNotEquals(queue.timeline[0].queueEntryId, queue.timeline[1].queueEntryId)
        assertEquals("manual", queue.timeline[queue.startIndex + 1].queueEntryId)
        assertEquals("VLmix", queue.timeline[queue.startIndex].playbackSourceId)
    }

    @Test fun `sorting continuation rows keeps every loaded occurrence and its source position`() {
        val first = listOf(song("track", "Song", "first"), song("track", "Song", "second"))
        val more = listOf(song("track", "Song", "third"), song("end", "Last", "fourth"))
        val shown = (first + more).inCollectionOrder(DesktopCollectionSort.TITLE_ASC)
        assertEquals(listOf(3, 0, 1, 2), shown.map { it.index })
        assertEquals(listOf("fourth", "first", "second", "third"), shown.map { it.value.setVideoId })
    }

    @Test fun `matching one Spotify occurrence preserves its exact queue seat`() {
        val pending = song("sp:duplicate", "Duplicate")
        val resolved = song("yt-track", "Duplicate")
        val collection = DesktopCollection(
            "spotify:playlist:one", "Mix", "", null, BrowseType.PLAYLIST,
            listOf(pending, resolved, pending, song("z", "Other")),
        )
        val shown = collection.songs.inCollectionOrder(DesktopCollectionSort.TITLE_DESC)
        val selected = shown[3]
        assertEquals(2, selected.index)
        assertNull(shown.map { it.value }.playableContextAt(3))

        val changed = collection.resolveSpotifyOccurrence(selected.index, "duplicate", resolved)
        assertEquals(listOf(pending, resolved, resolved, collection.songs.last()), changed.songs)
        val updated = shown.map { if (it.index == selected.index) resolved else it.value }
        val (index, playable) = checkNotNull(updated.playableContextAt(3))
        assertEquals(2, index)
        assertEquals(listOf("z", "yt-track", "yt-track"), playable.map { it.videoId })
        assertEquals(changed, changed.resolveSpotifyOccurrence(2, "duplicate", pending))
    }

    @Test fun `default collection order preserves manual entries with or without local file dates`() {
        val entries = listOf(
            song("z", "Z", "first").copy(localDateAddedSeconds = 20),
            song("a", "A", "second"),
            song("b", "B", "third").copy(localDateAddedSeconds = 10),
        )
        assertEquals(entries, entries.inCollectionOrder(DesktopCollectionSort.DEFAULT).map { it.value })
        assertEquals(listOf("a", "b", "z"), entries.inCollectionOrder(DesktopCollectionSort.TITLE_ASC).map { it.value.videoId })
    }

    private fun song(id: String, title: String, occurrence: String? = null) =
        Song(videoId = id, title = title, artist = "Artist", thumbnailUrl = null, setVideoId = occurrence)
}
