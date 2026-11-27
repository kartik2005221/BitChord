package com.music.bitchord.desktop

import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * What switching AutoPlay off does to the queue.
 *
 * Android's `dropAutoplayTracksFromQueue`: the suggestions after the current track go, because
 * switching it off is the listener saying they do not want them. Everything they queued by hand,
 * and everything already played, stays exactly where it is.
 */
class DesktopAutoplayQueueTest {

    private fun song(id: String, fromAutoplay: Boolean = false) =
        Song(videoId = id, title = id, artist = "a", thumbnailUrl = "", fromAutoplay = fromAutoplay)

    @Test
    fun `the suggestions after the current track are dropped`() {
        val queue = DesktopQueue(
            songs = listOf(
                song("played"),
                song("now"),
                song("mine"),
                song("mix1", fromAutoplay = true),
                song("mix2", fromAutoplay = true),
            ),
            index = 1,
        )
        val trimmed = queue.withoutAutoplay()
        assertEquals(listOf("played", "now", "mine"), trimmed.songs.map { it.videoId })
        // The current track has not moved out from under playback.
        assertEquals(1, trimmed.index)
        assertEquals("now", trimmed.songs[trimmed.index].videoId)
    }

    @Test
    fun `a suggestion already played stays in the history`() {
        val queue = DesktopQueue(
            songs = listOf(song("mixPlayed", fromAutoplay = true), song("now"), song("mix", fromAutoplay = true)),
            index = 1,
        )
        val trimmed = queue.withoutAutoplay()
        assertEquals(listOf("mixPlayed", "now"), trimmed.songs.map { it.videoId })
    }

    @Test
    fun `the current track is never dropped, even if AutoPlay queued it`() {
        val queue = DesktopQueue(songs = listOf(song("now", fromAutoplay = true)), index = 0)
        assertEquals(listOf("now"), queue.withoutAutoplay().songs.map { it.videoId })
    }

    @Test
    fun `a queue with nothing from AutoPlay is handed back untouched`() {
        val queue = DesktopQueue(songs = listOf(song("a"), song("b"), song("c")), index = 0)
        assertSame(queue, queue.withoutAutoplay())
    }

    @Test
    fun `a queue at its last track has nothing after it to drop`() {
        val queue = DesktopQueue(songs = listOf(song("a"), song("b", fromAutoplay = true)), index = 1)
        assertSame(queue, queue.withoutAutoplay())
    }

    @Test
    fun `a station seed can fall back to a provider title's romanization`() = runBlocking {
        val providerRow = Song("jiosaavn:tokyo", "東京 (Tōkyō)", "Artíst", null, durationText = "3:30")
        val youtubeRow = Song("yt-tokyo", "Tokyo", "Artist", null, durationText = "3:30")
        val queries = mutableListOf<String>()

        val seed = DesktopAutoplay.youtubeSeedFor(providerRow) { query, filter ->
            assertEquals(SearchFilter.SONGS, filter)
            queries += query
            val results = if (query == "tōkyō artíst") {
                listOf(
                    SearchResult.Track(youtubeRow.copy(videoId = "yt-live", title = "Tokyo (Live)")),
                    SearchResult.TopTrack(youtubeRow),
                )
            } else {
                emptyList()
            }
            Result.success(results)
        }

        assertEquals("yt-tokyo", seed)
        assertEquals(listOf("東京 artíst", "東京", "tōkyō artíst"), queries)
    }
}
