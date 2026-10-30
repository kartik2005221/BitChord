package com.music.bitchord.desktop

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * A release the user asked for as a whole — a playlist or an album — kept so the Downloads page can
 * group it, which is what Android's download store calls a saved collection.
 *
 * The reason this exists at all is the same one Android's comment gives: a batch download used to be
 * indistinguishable from forty separate ones the moment it finished. Where a track lands is a flat
 * file named after it, so what reached the Downloads list was forty rows, and the only thing that
 * could group them back up was whatever album tag each row happened to carry — which an album
 * page's rows don't carry at all and a playlist's rows never can, because a playlist is not an
 * album and its tracks are off forty different ones. The thing the user tapped was the one thing
 * not written down anywhere. This writes it down.
 *
 * The files stay flat on purpose, exactly as they are on Android: the folder belongs to the
 * listener, other players must be able to open it, and a playlist is not a folder on disk.
 */
@Serializable
data class DesktopDownloadedCollection(
    /** The release's own id — a browse id with the `VL` prefix a browse is addressed by taken off. */
    val id: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String? = null,
    /** Playlists and albums are grouped alike but not billed alike. */
    val playlist: Boolean,
    /** The songs of the release, in the running order the page listed. */
    val videoIds: List<String>,
)

/** The store, and the three things a grouping record has to survive: a second ask, a deletion, a restart. */
object DesktopDownloadCollections {

    private const val KEY = "downloaded_collections"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(persistence: DesktopPersistence): List<DesktopDownloadedCollection> =
        persistence.string(KEY).takeIf(String::isNotBlank)
            ?.let { raw -> runCatching { json.decodeFromString<List<DesktopDownloadedCollection>>(raw) }.getOrNull() }
            ?: emptyList()

    fun save(persistence: DesktopPersistence, collections: List<DesktopDownloadedCollection>) =
        persistence.saveString(KEY, json.encodeToString(collections.distinctBy(DesktopDownloadedCollection::id)))

    /**
     * Remembers that [videoIds] were asked for as one release rather than one at a time.
     *
     * Recorded at the tap rather than on completion, and keyed by the id that was tapped, so
     * re-downloading the same release updates one entry instead of accumulating near-duplicates.
     * The order is the running order the page had, which is what makes the entry read back as the
     * release rather than as a bag of tracks: a re-ask leads with the order it was given, and
     * whatever the earlier ask knew that this one does not follows it, de-duplicated.
     *
     * Nothing here asserts the files exist. That is deliberate and matches the rule underneath
     * ([DesktopDownloadManager.delete] knows where a file really is): a record lists what was
     * asked, and [prune] is what decides whether it is still worth showing.
     */
    fun record(
        existing: List<DesktopDownloadedCollection>,
        id: String,
        title: String,
        subtitle: String,
        thumbnailUrl: String?,
        playlist: Boolean,
        videoIds: List<String>,
    ): List<DesktopDownloadedCollection> {
        if (id.isBlank() || videoIds.isEmpty()) return existing
        val merged = (videoIds + existing.firstOrNull { it.id == id }?.videoIds.orEmpty()).distinct()
        val entry = DesktopDownloadedCollection(id, title, subtitle, thumbnailUrl, playlist, merged)
        // The record the user just tapped goes to the front, which is how the card row reads.
        return listOf(entry) + existing.filterNot { it.id == id }
    }

    /** Removes the record, record-only; deleting a group deletes its files before this is called. */
    fun forget(existing: List<DesktopDownloadedCollection>, id: String): List<DesktopDownloadedCollection> =
        existing.filterNot { it.id == id }

    /**
     * Drops every record whose songs are all gone.
     *
     * A grouping is worth showing while at least one of its files is still here, and the cards would
     * otherwise outlive the music they name: deleting a track one at a time is not an operation on
     * the record, but the moment the release's last file goes, the card would open an empty page
     * forever. Kept members stay grouped even when only some of them are left.
     */
    fun prune(existing: List<DesktopDownloadedCollection>, onDiskIds: Set<String>): List<DesktopDownloadedCollection> =
        existing.filter { collection -> collection.videoIds.any { it in onDiskIds } }
}
