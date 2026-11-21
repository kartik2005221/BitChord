package com.music.bitchord

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.LocalMusicSort
import com.music.bitchord.ui.screens.collectionComparator
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalMusicSortTest {
    @Test
    fun groupsSortByNameOrMostRecentTrackDate() {
        val groups = listOf(
            Song("1", "One", "C", null, localDateAddedSeconds = 1, localDateModifiedSeconds = 6),
            Song("2", "Two", "a", null, localDateAddedSeconds = 4, localDateModifiedSeconds = 5),
            Song("3", "Three", "C", null, localDateAddedSeconds = 3, localDateModifiedSeconds = 2),
            Song("4", "Four", "B", null),
        ).groupBy(Song::artist).entries

        fun titles(sort: LocalMusicSort) =
            groups.sortedWith(sort.collectionComparator({ it.key }, { it.value })).map { it.key }

        assertEquals(listOf("a", "B", "C"), titles(LocalMusicSort.TITLE_ASC))
        assertEquals(listOf("C", "B", "a"), titles(LocalMusicSort.TITLE_DESC))
        assertEquals(listOf("a", "C", "B"), titles(LocalMusicSort.DATE_ADDED))
        assertEquals(listOf("C", "a", "B"), titles(LocalMusicSort.DATE_MODIFIED))
    }
}
