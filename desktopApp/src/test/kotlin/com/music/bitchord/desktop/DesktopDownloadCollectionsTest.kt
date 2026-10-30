package com.music.bitchord.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A grouping record exists so a batch download cannot be silently rewritten into forty separate
 * ones between the tap and the Downloads page. These pin the merge, the drop and the storage round
 * the cards are built on.
 */
class DesktopDownloadCollectionsTest {

    private val persistence = DesktopPersistence()

    private fun collection(id: String, videoIds: List<String>, playlist: Boolean = true) =
        DesktopDownloadedCollection(
            id = id,
            title = "Release $id",
            subtitle = "Playlist · 40 songs",
            thumbnailUrl = "https://example.org/art.png",
            playlist = playlist,
            videoIds = videoIds,
        )

    @Test
    fun `the release the page listed is the record, in the order the page listed it`() {
        val recorded = DesktopDownloadCollections.record(
            existing = emptyList(),
            id = "PL1",
            title = "Night Drive",
            subtitle = "Playlist · 40 songs",
            thumbnailUrl = "https://example.org/art.png",
            playlist = true,
            videoIds = listOf("a", "b", "c"),
        )
        assertEquals(1, recorded.size)
        assertEquals(listOf("a", "b", "c"), recorded.single().videoIds)
        assertTrue(recorded.single().playlist)
    }

    @Test
    fun `re-asking for a release rewrites the one entry and keeps what the older ask knew`() {
        val first = DesktopDownloadCollections.record(emptyList(), "PL1", "Night Drive", "s", null, true, listOf("a", "b", "c"))
        // The page's order changed between visits; the newest ask leads, the rest follows, deduped.
        val second = DesktopDownloadCollections.record(first, "PL1", "Night Drive", "s", null, true, listOf("b", "c", "d"))
        assertEquals(listOf("PL1"), second.map { it.id })
        assertEquals(listOf("b", "c", "d", "a"), second.single().videoIds)
    }

    @Test
    fun `a different release beside it is a second record, newest first`() {
        val first = DesktopDownloadCollections.record(emptyList(), "PL1", "Night Drive", "s", null, true, listOf("a"))
        val second = DesktopDownloadCollections.record(first, "MPREb_2", "Evening", "s", null, false, listOf("b"))
        assertEquals(listOf("MPREb_2", "PL1"), second.map { it.id })
        assertFalse(second[0].playlist)
        // A re-record beside it does not rewrite the other record's billing.
        assertTrue(second[1].playlist)
    }

    @Test
    fun `a re-record without fresh artwork keeps the art the earlier visit knew`() {
        val first = DesktopDownloadCollections.record(emptyList(), "PL1", "Night Drive", "s", "https://example.org/art.png", true, listOf("a"))
        val second = DesktopDownloadCollections.record(first, "PL1", "Night Drive", "s", null, true, listOf("a", "b"))
        assertEquals("https://example.org/art.png", second.single().thumbnailUrl)
    }

    @Test
    fun `a blank id and an empty tap record nothing`() {
        val groups = listOf(collection("PL1", listOf("a")))
        assertEquals(groups, DesktopDownloadCollections.record(groups, "", "x", "s", null, true, listOf("a")))
        assertEquals(groups, DesktopDownloadCollections.record(groups, "PL2", "x", "s", null, true, emptyList()))
    }

    @Test
    fun `pruning drops a record whose files are all gone and keeps one still partly here`() {
        val groups = listOf(collection("PL1", listOf("a", "b")), collection("PL2", listOf("c")))
        assertEquals(listOf("PL2"), DesktopDownloadCollections.prune(groups, setOf("c")).map { it.id })
        // Only "a" of PL1's two is left: the record stays, one member fewer.
        assertEquals(listOf("PL1"), DesktopDownloadCollections.prune(groups, setOf("a")).map { it.id })
        assertEquals(emptyList(), DesktopDownloadCollections.prune(groups, emptySet()))
    }

    @Test
    fun `forgetting removes the one record and leaves the rest`() {
        val groups = listOf(collection("PL1", listOf("a")), collection("PL2", listOf("b")))
        assertEquals(listOf("PL2"), DesktopDownloadCollections.forget(groups, "PL1").map { it.id })
    }

    @Test
    fun `records survive a save and load roundtrip`() {
        val groups = listOf(
            collection("PL1", listOf("a", "b", "c")),
            collection("MPREb_9", listOf("d"), playlist = false),
        )
        DesktopDownloadCollections.save(persistence, groups)
        assertEquals(groups, DesktopDownloadCollections.load(persistence))
        // Saving again with one record drops the stale duplicate rather than doubling the store.
        DesktopDownloadCollections.save(persistence, listOf(collection("PL1", listOf("x"))))
        assertEquals(listOf("PL1"), DesktopDownloadCollections.load(persistence).map { it.id })
    }
}
