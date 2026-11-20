package com.music.bitchord.desktop

import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import kotlin.test.*

class DesktopHomeRefreshTest {
    private fun shelf(title: String, items: Int = 1) = HomeShelf(
        title = title,
        items = List(items) { ShelfItem("Track $it", "Artist", thumbnailUrl = null, videoId = "id$it", browseId = null) },
    )

    @Test fun `a refreshed feed replaces the page and resets the seen headings`() {
        val seen = mutableSetOf("quick picks", "recents", "forgotten favourites")
        val fresh = listOf(
            shelf("Quick picks"),
            shelf("Empty shelf", items = 0),
            shelf("QUICK PICKS"),
            shelf("Mixed for you"),
        )
        val kept = refreshedHomeShelves(fresh, seen)
        assertEquals(listOf("Quick picks", "Mixed for you"), kept.map(HomeShelf::title))
        // Exactly the kept headings, so the recents and supplements that follow can append —
        // had the old ones survived here, publishHomeShelves would drop them all as seen.
        assertEquals(setOf("quick picks", "mixed for you"), seen)
    }

    @Test fun `a feed with nothing on it leaves the seen set empty rather than stale`() {
        val seen = mutableSetOf("quick picks")
        assertTrue(refreshedHomeShelves(listOf(shelf("Empty", items = 0)), seen).isEmpty())
        assertTrue(seen.isEmpty())
    }
}
