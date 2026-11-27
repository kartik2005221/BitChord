package com.music.bitchord.data.innertube

import com.music.bitchord.data.YtMusicRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A playlist's second page as YouTube Music sends it now: rows appended by an
 * `onResponseReceivedActions` action rather than under `continuationContents`.
 * Shape trimmed from a live response for a 200-song playlist (#947).
 */
class PlaylistContinuationTest {

    private fun row(videoId: String, setVideoId: String) = """
        { "musicResponsiveListItemRenderer": {
          "playlistItemData": { "videoId": "$videoId", "playlistSetVideoId": "$setVideoId" },
          "flexColumns": [
            { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "Song $videoId" } ] } } },
            { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "Artist" } ] } } }
          ]
        } }"""

    private val appendedPage = Json.parseToJsonElement(
        """
        { "onResponseReceivedActions": [ { "appendContinuationItemsAction": { "continuationItems": [
          ${row("aaaaaaaaaaa", "SET1")},
          ${row("aaaaaaaaaaa", "SET2")},
          { "continuationItemRenderer": { "continuationEndpoint": { "continuationCommand": { "token": "PAGE3" } } } }
        ] } } ] }
        """,
    )

    @Test
    fun `an appended continuation page reads as the playlist's next rows`() {
        val page = assertNotNull(InnertubeParser.parsePlaylistShelf(appendedPage))
        assertEquals(listOf("SET1", "SET2"), page.songs.map { it.setVideoId })
        assertEquals("PAGE3", page.continuation)
    }

    private fun firstPage(token: String? = "PAGE2", owned: Boolean = true): JsonObject {
        val continuation = token?.let { """, "continuations": [{"nextContinuationData": {"continuation": "$it"}}]""" }.orEmpty()
        val header = if (owned) """"header": {"musicEditablePlaylistDetailHeaderRenderer": {}}, """ else ""
        return Json.parseToJsonElement("""
            {$header "contents": { "twoColumnBrowseResultsRenderer": {
                "secondaryContents": { "musicPlaylistShelfRenderer": {
                    "contents": [${row("aaaaaaaaaaa", "SET0")}]$continuation
                } }
            } } }
        """).jsonObject
    }

    @Test
    fun authoritativeListingIncludesAllOccurrences() = runBlocking {
        val requests = mutableListOf<String>()
        val entries = YtMusicRepository.collectPlaylistEntries(firstPage(), requireOwner = true) { token ->
            requests += token
            if (token == "PAGE2") appendedPage.jsonObject else Json.parseToJsonElement(
                """{"continuationContents":{"musicPlaylistShelfContinuation":{"contents":[${row("bbbbbbbbbbb", "SET3")}]}}}"""
            ).jsonObject
        }
        assertEquals(listOf("PAGE2", "PAGE3"), requests)
        assertEquals(listOf("SET0", "SET1", "SET2", "SET3"), entries.map { it.setVideoId })
        assertEquals(List(3) { "aaaaaaaaaaa" } + "bbbbbbbbbbb", entries.map { it.videoId })
    }

    @Test
    fun authoritativeListingRejectsPartialAndRepeatedPages() = runBlocking {
        val invalid = listOf(
            "{}",
            """{"continuationContents":{"musicPlaylistShelfContinuation":{"contents":[{"musicResponsiveListItemRenderer":{}}]}}}""",
            """{"continuationContents":{"musicPlaylistShelfContinuation":{"contents":[${row("aaaaaaaaaaa", "")}]}}}""",
            """{"continuationContents":{"musicPlaylistShelfContinuation":{"contents":[${row("aaaaaaaaaaa", "SET0")}]}}}""",
            """{"continuationContents":{"musicPlaylistShelfContinuation":{"contents":[],"continuations":[{"nextContinuationData":{"continuation":"PAGE2"}}]}}}""",
        )
        invalid.forEach { json ->
            assertFailsWith<IllegalStateException> {
                YtMusicRepository.collectPlaylistEntries(firstPage(), requireOwner = true) { Json.parseToJsonElement(json).jsonObject }
            }
        }
    }

    @Test
    fun authoritativeListingRejectsAnUnverifiedOwnerAndStopsAtTheCap() = runBlocking {
        assertFailsWith<IllegalStateException> {
            YtMusicRepository.collectPlaylistEntries(firstPage(owned = false), requireOwner = true) {
                error("Unexpected continuation")
            }
        }
        var pages = 1
        val failure = assertFailsWith<IllegalStateException> {
            YtMusicRepository.collectPlaylistEntries(firstPage(), requireOwner = true) {
                pages++
                Json.parseToJsonElement(
                    """{"continuationContents":{"musicPlaylistShelfContinuation":{"contents":[],"continuations":[{"nextContinuationData":{"continuation":"next-$pages"}}]}}}"""
                ).jsonObject
            }
        }
        assertEquals(YtMusicRepository.MAX_PAGES, pages)
        assertTrue(failure.message.orEmpty().contains("page limit"))
    }

}
