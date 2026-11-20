package com.music.bitchord.desktop

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

/** Home's refresh: the page keeps its cards and its place while a fresh feed is fetched. */
@Composable
internal fun DesktopHomeRefreshButton(refreshing: Boolean, onRefresh: () -> Unit) {
    IconButton(onClick = onRefresh, enabled = !refreshing) {
        if (refreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "Refresh recommendations",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
