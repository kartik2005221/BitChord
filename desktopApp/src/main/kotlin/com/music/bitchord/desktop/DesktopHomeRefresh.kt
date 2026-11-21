package com.music.bitchord.desktop

import com.music.bitchord.data.model.HomeShelf

/**
 * The core feed a refresh adopts: every shelf with something on it, in the
 * feed's own order, keeping only the first of each heading.
 *
 * [seen] is reset to exactly those headings rather than merged with the old
 * ones. That is what makes the button useful: the recently played and
 * supplementary shelves that follow the core feed are published through
 * `publishHomeShelves`, which drops any heading already seen, so merging into
 * the morning's page would quietly throw the evening's shelves away.
 */
internal fun refreshedHomeShelves(shelves: List<HomeShelf>, seen: MutableSet<String>): List<HomeShelf> {
    seen.clear()
    return shelves.filter { it.items.isNotEmpty() && seen.add(it.title.lowercase()) }
}
