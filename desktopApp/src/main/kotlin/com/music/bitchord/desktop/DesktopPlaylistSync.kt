package com.music.bitchord.desktop

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

/**
 * Keeps the playlists downloaded whole in step with their online copies, while
 * [syncEnabled] is on — the desktop half of the phone's PlaylistSync. WorkManager
 * and the notifications stay behind on the phone: a ticker in DesktopApp answers
 * for the [PERIOD_MILLIS] period and the [CATCH_UP_MILLIS] catch-up, and what the
 * phone asked as a notification the Downloads page asks as a strip.
 *
 * Nothing about the tracks found is stored before it has been dealt with. That
 * is what makes a check safe to lose at any point: the app closing, the process
 * dying or a page failing all leave the remaining tracks new, and the next check
 * simply finds them again.
 *
 * Only ever adds. A track the listener deleted is still named by its playlist's
 * record, so it does not come back; a track removed online keeps its file.
 */
object DesktopPlaylistSync {

    private const val TAG = "playlist-sync"

    /** Milliseconds since the last whole check, one long written as text — the store's own shape. */
    internal const val KEY_LAST_CHECK = "playlist_sync_checked"

    /** The toggle the Settings row sets and the Downloads page strip obeys. */
    internal const val KEY_SETTING = "sync_downloaded_playlists"

    /** When the app comes up with the last whole check older than this, one is asked for at once. */
    internal const val CATCH_UP_MILLIS = 6L * 60 * 60 * 1000

    /** How long a computer that stays up sits before the next whole check. */
    internal const val PERIOD_MILLIS = 12L * 60 * 60 * 1000

    /** The most one playlist may fetch without being asked; about 1.7 GB of lossless. */
    internal const val ASK_ABOVE = 50

    /** Between requests for pages and for playlists: none of this is in a hurry. */
    private const val PAUSE_MILLIS = 400L

    /** One check at a time, however many of the triggers fire together. */
    private val running = Mutex()

    /** Playlists holding more than [ASK_ABOVE] new songs, waiting for the strip's tap. */
    private val _asks = MutableStateFlow<List<DesktopPlaylistSyncAsk>>(emptyList())
    val asks: StateFlow<List<DesktopPlaylistSyncAsk>> = _asks.asStateFlow()

    /** The setting read from the store: off until it is asked for, as on the phone. */
    fun syncEnabled(persistence: DesktopPersistence): Boolean =
        persistence.boolean(KEY_SETTING, false)

    /** Milliseconds since the last check that read every playlist to its end. */
    internal fun lastCompleteCheck(persistence: DesktopPersistence): Long =
        persistence.string(KEY_LAST_CHECK).takeIf(String::isNotBlank)?.toLongOrNull() ?: 0L

    /** Stamps the moment a whole check ended; only a complete check may call this. */
    internal fun stampCompleteCheck(persistence: DesktopPersistence) {
        persistence.saveString(KEY_LAST_CHECK, System.currentTimeMillis().toString())
    }

    // ---- A check ------------------------------------------------------------

    /**
     * Goes through every eligible playlist's listing in turn.
     *
     * Only a check that read every playlist to its end is stamped as done, so
     * one that failed partway is due again the next time the app is opened.
     * The wiring answers the questions the check asks, each of them fresh every
     * time: the app's playlist records live in its composable state and may be
     * changed under a check by the listener.
     *
     * There is no metered network to respect on a wired computer; the setting
     * is the only gate, asked again before every playlist.
     *
     * @param approved also fetches the batches over [ASK_ABOVE]. An approval
     *   waits its turn; anything else would only repeat the check already
     *   running, so a check under way answers for it.
     */
    suspend fun check(
        persistence: DesktopPersistence,
        approved: Boolean = false,
        syncOn: () -> Boolean,
        collections: () -> List<DesktopDownloadedCollection>,
        recordById: (String) -> DesktopDownloadedCollection?,
        savedIds: () -> Set<String>,
        downloadNow: (List<Song>) -> Set<String>,
        remember: (id: String, title: String, subtitle: String, thumbnailUrl: String?, videoIds: List<String>) -> Unit,
    ) {
        if (approved) running.lock() else if (!running.tryLock()) return
        try {
            if (!syncOn()) return
            var whole = true
            collections().map { it.id }.forEach { id ->
                if (!syncOn()) return
                val record = recordById(id) ?: return@forEach
                val browseId = syncableBrowseId(record) ?: return@forEach
                val listing = readWholePlaylist(
                    first = { YtMusicRepository.browseSongs(browseId) },
                    more = { YtMusicRepository.moreSongs(it) },
                )
                if (!listing.complete) whole = false
                update(record, listing, approved, savedIds, downloadNow, remember)
                delay(PAUSE_MILLIS)
            }
            if (whole) stampCompleteCheck(persistence)
        } finally {
            running.unlock()
        }
    }

    private suspend fun update(
        record: DesktopDownloadedCollection,
        listing: Listing,
        approved: Boolean,
        savedIds: () -> Set<String>,
        downloadNow: (List<Song>) -> Set<String>,
        remember: (id: String, title: String, subtitle: String, thumbnailUrl: String?, videoIds: List<String>) -> Unit,
    ) {
        val fresh = newTracks(listing.songs, record)
        if (fresh.isEmpty()) {
            _asks.value = _asks.value.filterNot { it.playlistId == record.id }
            return
        }
        if (fresh.size > ASK_ABOVE && !approved) {
            _asks.value =
                _asks.value.filterNot { it.playlistId == record.id } +
                    DesktopPlaylistSyncAsk(record.id, record.title, fresh.size)
            return
        }
        // Already on this computer for some other reason: joins the playlist
        // without being fetched again.
        val (onDevice, missing) = fresh.partition { it.videoId in savedIds() }
        val cover = listing.cover ?: record.thumbnailUrl?.takeIf { it.startsWith("http") }
        val fetch = missing.map { song ->
            if (song.thumbnailUrl.isNullOrBlank() && cover != null) song.copy(thumbnailUrl = cover) else song
        }
        val handled = onDevice.mapTo(HashSet()) { it.videoId } + downloadNow(fetch)

        // Read again before the record goes back: the listener may have deleted
        // this download meanwhile, and writing it up would bring it back from
        // the dead.
        val current = recordById(record.id) ?: return
        val order = listingToRecord(listing.songs, current, handled)
        remember(current.id, current.title, current.subtitle, current.thumbnailUrl, order.map(Song::videoId))

        val arrived = missing.count { it.videoId in handled && it.videoId in savedIds() }
        if (arrived > 0) DesktopTrackLog.log("$TAG: $arrived new songs of \"${record.title}\" arrived")
    }

    // ---- Reading a playlist -------------------------------------------------

    /**
     * @property complete whether this reached the end of the playlist. False
     *   when a request failed; [songs] is then whatever came before it, which
     *   is still worth acting on since a check only ever adds.
     */
    internal class Listing(val songs: List<Song>, val complete: Boolean, val cover: String? = null)

    /**
     * Every track in a playlist, however many pages it runs to.
     *
     * Its own walk rather than one that stops at a page limit and turns a
     * failed page into a shorter list without saying so. Every page is read
     * every time: a new track can be anywhere in a playlist, and one track
     * added and one removed leave its count unchanged.
     *
     * Only the rows a page lists as its own, so the suggestions YouTube offers
     * to round a playlist out are never taken for part of it.
     *
     * @param pause the gap between pages, [PAUSE_MILLIS] for a live check and
     *   nothing in the tests.
     */
    internal suspend fun readWholePlaylist(
        first: suspend () -> Result<YtMusicRepository.SongPage>,
        more: suspend (String) -> Result<YtMusicRepository.SongPage>,
        pause: Long = PAUSE_MILLIS,
    ): Listing {
        val opening = first().getOrNull() ?: return Listing(emptyList(), complete = false)
        val cover = opening.header?.thumbnailUrl
        val songs = LinkedHashMap<String, Song>()
        opening.songs.forEach { songs.putIfAbsent(it.videoId, it) }
        var next = opening.continuation
        while (next != null) {
            delay(pause)
            val page = more(next).getOrNull() ?: return Listing(songs.values.toList(), complete = false, cover)
            val before = songs.size
            page.songs.forEach { songs.putIfAbsent(it.videoId, it) }
            // A page with nothing new is a feed that has looped back on itself
            // with a token still attached, not one with more to give.
            if (songs.size == before) break
            next = page.continuation
        }
        return Listing(songs.values.toList(), complete = true, cover)
    }

    /**
     * What a check looks at, under the id YouTube answers to: a playlist is
     * filed under whichever id it was downloaded through, with or without its
     * `VL`.
     *
     * Null for an album, and for the two kinds of playlist with no YouTube copy
     * to compare against: Spotify's own and those imported to this device.
     */
    internal fun syncableBrowseId(record: DesktopDownloadedCollection): String? {
        val id = record.id
        return when {
            !record.playlist || ':' in id || id.startsWith("MP") -> null
            id.startsWith("VL") -> id
            else -> "VL$id"
        }
    }

    /** The tracks in [listing] that [record] has never named. */
    internal fun newTracks(listing: List<Song>, record: DesktopDownloadedCollection): List<Song> {
        val known = record.videoIds.toHashSet()
        return listing.filter { it.videoId !in known }
    }

    /**
     * [listing] without whatever is still to be dealt with, in the playlist's
     * own order. A track left out stays new, so the next check tries it again.
     */
    internal fun listingToRecord(listing: List<Song>, record: DesktopDownloadedCollection, handled: Set<String>): List<Song> {
        val known = record.videoIds.toHashSet()
        return listing.filter { it.videoId in known || it.videoId in handled }
    }
}

/** One "New songs in ⟨playlist⟩" the strip at the head of the Downloads page asks about. */
data class DesktopPlaylistSyncAsk(
    /** The collection record's id — a check's answer runs back through the wiring that keeps it. */
    val playlistId: String,
    val title: String,
    val count: Int,
)
