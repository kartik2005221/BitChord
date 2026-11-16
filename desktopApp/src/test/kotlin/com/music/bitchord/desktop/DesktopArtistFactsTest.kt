package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Artist facts: the tag filter, the genre chart they feed, and the enrichment of the artist rows. */
class DesktopArtistFactsTest {

    // ── The tag filter ───────────────────────────────────────────────────────

    @Test
    fun `a vocabulary tag becomes its display genre`() {
        assertEquals("Pop", DesktopArtistFacts.canonical("pop"))
        assertEquals("Hip-Hop", DesktopArtistFacts.canonical("hip hop"))
        assertEquals("R&B", DesktopArtistFacts.canonical("randb"))
        assertEquals("Drum & Bass", DesktopArtistFacts.canonical("drum and bass"))
    }

    @Test
    fun `an alias lands on the genre it stands for`() {
        assertEquals("R&B", DesktopArtistFacts.canonical("rnb"))
        assertEquals("Hip-Hop", DesktopArtistFacts.canonical("hiphop"))
        assertEquals("Lo-Fi", DesktopArtistFacts.canonical("lofi"))
        assertEquals("Rock", DesktopArtistFacts.canonical("Classic Rock"))
        assertEquals("Electronic", DesktopArtistFacts.canonical("dance"))
    }

    @Test
    fun `punctuation and spacing do not change the answer`() {
        assertEquals("Hip-Hop", DesktopArtistFacts.canonical("hip-hop"))
        assertEquals("R&B", DesktopArtistFacts.canonical("r&b"))
        assertEquals("Singer-Songwriter", DesktopArtistFacts.canonical("  singer   songwriter "))
    }

    @Test
    fun `a tag that is no genre contributes nothing`() {
        assertNull(DesktopArtistFacts.canonical("seen live"))
        assertNull(DesktopArtistFacts.canonical("favourites"))
        assertNull(DesktopArtistFacts.canonical("albums i own"))
        assertNull(DesktopArtistFacts.canonical("awesome"))
        assertNull(DesktopArtistFacts.canonical(""))
        assertNull(DesktopArtistFacts.canonical("   "))
        assertNull(DesktopArtistFacts.canonical("!!!"))
    }

    // ── The genre chart and the artist-row enrichment ───────────────────────

    private class FakeFacts(
        private val genres: Map<String, List<String>> = emptyMap(),
        private val images: Map<String, String> = emptyMap(),
        private val browseIds: Map<String, String> = emptyMap(),
    ) : DesktopArtistFactsSource {
        val noticed = mutableListOf<String>()

        override fun imageFor(artist: String): String? = images[artist]
        override fun browseIdFor(artist: String): String? = browseIds[artist]
        override fun genresFor(artist: String): List<String> = genres[artist].orEmpty()
        override fun noticed(artist: String) {
            noticed += artist
        }
    }

    private fun song(id: String, title: String, artist: String) =
        Song(videoId = id, title = title, artist = artist, thumbnailUrl = null)

    private fun bucket(month: String, vararg tracks: Triple<Song, Long, Int>) =
        DesktopListeningStats.Bucket(month).apply {
            tracks.forEach { (song, ms, plays) ->
                this.tracks[song.videoId] = DesktopReplayEntry(song, ms, plays)
            }
        }

    private val today = LocalDate.of(2026, 11, 1)

    @Test
    fun `genres sum the listening of every artist tagged with them`() {
        val facts = FakeFacts(
            genres = mapOf(
                "Artist A" to listOf("Rock", "Indie"),
                "Artist B" to listOf("Rock"),
            ),
        )
        val summary = DesktopListeningStats.summarise(
            listOf(
                bucket(
                    "2026-10",
                    Triple(song("a", "One", "Artist A"), 60_000L, 2),
                    Triple(song("b", "Two", "Artist B"), 40_000L, 1),
                ),
            ),
            DesktopReplayPeriod.ALL_TIME,
            today,
            facts,
        )
        assertEquals(
            listOf("Rock" to 100_000L, "Indie" to 60_000L),
            summary.genres.map { it.title to it.ms },
        )
        assertEquals(3, summary.genres.first().plays)
        assertEquals(2, summary.genres[1].plays)
    }

    @Test
    fun `a genre wears the first artist art that carried it`() {
        val facts = FakeFacts(
            genres = mapOf("Artist A" to listOf("Rock"), "Artist B" to listOf("Rock")),
            images = mapOf("Artist A" to "https://example/a.jpg"),
        )
        val summary = DesktopListeningStats.summarise(
            listOf(
                bucket(
                    "2026-10",
                    Triple(song("a", "One", "Artist A"), 60_000L, 1),
                    Triple(song("b", "Two", "Artist B"), 90_000L, 1),
                ),
            ),
            DesktopReplayPeriod.ALL_TIME,
            today,
            facts,
        )
        assertEquals("https://example/a.jpg", summary.genres.single().artworkUrl)
    }

    @Test
    fun `an artist with no tag yet does not vote, and the chart can be empty`() {
        val facts = FakeFacts(genres = mapOf("Artist A" to emptyList()))
        val summary = DesktopListeningStats.summarise(
            listOf(bucket("2026-10", Triple(song("a", "One", "Artist A"), 60_000L, 1))),
            DesktopReplayPeriod.ALL_TIME,
            today,
            facts,
        )
        assertTrue(summary.genres.isEmpty())
        assertTrue(
            DesktopListeningStats.summarise(
                listOf(bucket("2026-10", Triple(song("a", "One", "Artist A"), 60_000L, 1))),
                DesktopReplayPeriod.ALL_TIME,
                today,
            ).genres.isEmpty(),
        )
    }

    @Test
    fun `an artist row takes the looked-up face and page over the sleeve`() {
        val facts = FakeFacts(
            images = mapOf("Artist A" to "https://example/a.jpg"),
            browseIds = mapOf("Artist B" to "browse-b"),
        )
        val summary = DesktopListeningStats.summarise(
            listOf(
                bucket(
                    "2026-10",
                    Triple(song("a", "One", "Artist A"), 60_000L, 1),
                    Triple(song("b", "Two", "Artist B"), 40_000L, 1),
                ),
            ),
            DesktopReplayPeriod.ALL_TIME,
            today,
            facts,
        )
        assertEquals("https://example/a.jpg", summary.artists.first { it.title == "Artist A" }.artworkUrl)
        assertEquals("browse-b", summary.artists.first { it.title == "Artist B" }.browseId)
        assertNull(summary.artists.first { it.title == "Artist B" }.artworkUrl)
    }

    @Test
    fun `top artists still missing a face or a page are asked about`() {
        val facts = FakeFacts(images = mapOf("Artist A" to "https://example/a.jpg"))
        DesktopListeningStats.summarise(
            listOf(
                bucket(
                    "2026-10",
                    Triple(song("a", "One", "Artist A"), 60_000L, 1),
                    Triple(song("b", "Two", "Artist B"), 40_000L, 1),
                    Triple(song("c", "Three", "Artist C"), 20_000L, 1),
                ),
            ),
            DesktopReplayPeriod.ALL_TIME,
            today,
            facts,
        )
        // Artist A has an image and no page → asked. B and C have neither → asked.
        assertEquals(setOf("Artist A", "Artist B", "Artist C"), facts.noticed.toSet())
    }

    @Test
    fun `an artist fully known is not asked about`() {
        val facts = FakeFacts(
            images = mapOf("Artist A" to "https://example/a.jpg"),
            browseIds = mapOf("Artist A" to "browse-a"),
        )
        DesktopListeningStats.summarise(
            listOf(bucket("2026-10", Triple(song("a", "One", "Artist A"), 60_000L, 1))),
            DesktopReplayPeriod.ALL_TIME,
            today,
            facts,
        )
        assertTrue(facts.noticed.isEmpty())
    }

    @Test
    fun `facts absent, the charts read exactly as before them`() {
        val with = DesktopListeningStats.summarise(
            listOf(bucket("2026-10", Triple(song("a", "One", "Artist A"), 60_000L, 1))),
            DesktopReplayPeriod.ALL_TIME,
            today,
            FakeFacts(images = mapOf("Artist A" to "https://example/a.jpg")),
        )
        val without = DesktopListeningStats.summarise(
            listOf(bucket("2026-10", Triple(song("a", "One", "Artist A"), 60_000L, 1))),
            DesktopReplayPeriod.ALL_TIME,
            today,
        )
        assertEquals("https://example/a.jpg", with.artists.single().artworkUrl)
        assertNull(without.artists.single().artworkUrl)
        assertNull(without.artists.single().browseId)
        assertTrue(without.genres.isEmpty())
    }
}
