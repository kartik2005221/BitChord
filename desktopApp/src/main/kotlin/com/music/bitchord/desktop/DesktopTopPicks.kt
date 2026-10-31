package com.music.bitchord.desktop

import com.music.bitchord.data.AppleArtistPhotos
import com.music.bitchord.data.TopPicks
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.TopPick
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
import java.util.Locale

/**
 * Home's "Top Picks for You" row, desktop-side. The shared builder, [TopPicks],
 * does the reading and the choosing; this holder carries the row's lifetime the
 * way the phone's MainViewModel does:
 *
 *  - a row is one hour of the clock — the first build inside an hour owns it,
 *    and the next hour calmly builds another;
 *  - a row belongs to a listener — a switch of accounts leaves it behind with
 *    the hour it came from;
 *  - the built row is stored (encoded by the shared builder, chunked into the
 *    app's preferences), so a cold start opens Home onto the last row while
 *    the new one is being built;
 *  - the queues behind a card are fetched only on the tap — browsing costs
 *    nothing.
 */
internal object DesktopTopPicks {
    private const val TAG = "top-picks"

    /** The row's stored slot, as the downloads' records and the like are stored: one key. */
    private const val KEY = "top_picks_row"

    private val _picks = MutableStateFlow<List<TopPick>?>(null)
    val picks: StateFlow<List<TopPick>?> = _picks.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private var job: Job? = null
    /** The listener identity the build in flight was asked for. */
    private var jobIdentity: String? = null
    /** The clock-hour the row on screen was built (or attempted) in, e.g. `2026-10-30T19`. */
    private var slotShown: String? = null
    /** The listener identity the row on screen was built for. */
    private var identityShown: String? = null

    /**
     * The hour the clock says now, in the same shape the stored row carries.
     * The digits are asked for under [Locale.ROOT], so a row filed under one
     * locale still reads back when the machine's formatting rules differ.
     */
    fun slot(now: LocalDateTime = LocalDateTime.now()): String =
        "${now.toLocalDate()}T" + "%02d".format(Locale.ROOT, now.hour)

    /**
     * True when the row on screen is not of the current hour, not for this
     * listener, or there is none. The hour is owned also by a build that found
     * nothing, so a failed build is not re-asked every minute until the clock
     * moves.
     */
    fun isStale(identity: String? = null): Boolean =
        slotShown == null || slotShown != slot() ||
            (identity != null && identity != identityShown)

    /**
     * The phone's loadTopPicks: paint the stored row at once, then build the
     * hour's row — unless the stored one is already it. While anything is on
     * screen it stays up, so Home never waits on the network. An account that
     * changed under a build retires it, and the last one wins.
     */
    fun maybeRefresh(
        scope: CoroutineScope,
        identity: String?,
        persistence: DesktopPersistence,
        rebuild: Boolean = false,
        shelves: suspend () -> List<HomeShelf>,
    ) {
        if (job?.isActive == true) {
            if (identity == jobIdentity) return
            // The account changed under the build — it answers for nobody now.
            job?.cancel()
        }
        jobIdentity = identity
        val slot = slot()
        job = scope.launch {
            // Superseded between the call and the launch: the builds ahead of
            // this one speak for the listener.
            if (jobIdentity != identity) return@launch
            // The store belongs off the drawing thread, the rule the rest of
            // the desktop keeps for its own preferences.
            val stored = withContext(Dispatchers.IO) {
                persistence.string(KEY)?.let(TopPicks::decode)
            }
                ?.takeIf { (identity == null || it.listener == identity) && it.picks.isNotEmpty() }
            stored?.let { AppleArtistPhotos.seed(it.photos) }
            if (stored != null) {
                _picks.value = stored.picks
                slotShown = stored.day
                identityShown = stored.listener
            } else if (!rebuild) {
                _picks.value = null
                identityShown = null
            }
            if (!rebuild && stored?.day == slot) {
                _loading.value = false
                return@launch
            }
            _loading.value = _picks.value == null
            val picks = runCatching {
                // The feed must land first — the builder reads the account's
                // mixes off it instead of asking again. Not forever, though: a
                // slow or broken feed builds from what answered within eight
                // seconds, as on the phone.
                withTimeoutOrNull(8_000L) { shelves() }.orEmpty()
                    .let { TopPicks.build(it, TopPicks.LocalTaste()) }
            }.onFailure {
                if (it !is CancellationException) {
                    DesktopTrackLog.log("$TAG: build failed — ${it.message}")
                }
            }.getOrNull()?.takeIf { it.isNotEmpty() }
            if (picks == null) {
                if (jobIdentity != identity) return@launch
                if (_picks.value == null) _picks.value = emptyList()
                _loading.value = false
                // The hour is owned even by the failure, so the minute-tick on
                // Home stops retrying until the clock moves.
                slotShown = slot
                identityShown = identity
                return@launch
            }
            if (jobIdentity != identity) return@launch
            _picks.value = picks
            slotShown = slot
            identityShown = identity
            val encoded = TopPicks.encode(
                TopPicks.Stored(
                    listener = identity,
                    day = slot,
                    picks = picks,
                    photos = AppleArtistPhotos.snapshot(),
                ),
            )
            withContext(Dispatchers.IO) { persistence.saveString(KEY, encoded) }
            _loading.value = false
        }
    }
}
