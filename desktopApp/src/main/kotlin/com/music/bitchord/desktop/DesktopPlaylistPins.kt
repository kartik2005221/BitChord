package com.music.bitchord.desktop

import com.music.bitchord.data.spotify.SPOTIFY_PAGE_PREFIX
import com.music.bitchord.data.spotify.SpotifyLibrary
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class DesktopPlaylistPinScope(
    val accountId: String?,
    val profileId: String?,
    val provider: String = "youtube",
) {
    internal val downloadOwner: String
        get() = listOf(provider, accountId.orEmpty(), profileId.orEmpty()).joinToString("|")

    companion object {
        fun spotify(cookie: String): DesktopPlaylistPinScope? {
            val credential = cookie.trim().takeIf(String::isNotBlank) ?: return null
            // Legacy scope. Its hash identifies exactly one credential, never an account.
            val digest = MessageDigest.getInstance("SHA-256").digest(credential.toByteArray(Charsets.UTF_8))
            return DesktopPlaylistPinScope(Base64.getUrlEncoder().withoutPadding().encodeToString(digest), null, "spotify")
        }
    }
}

/** A null scope is reserved for device-wide local playlists. */
internal data class DesktopPlaylistPin(
    val browseId: String,
    val title: String,
    val artworkUrl: String?,
    val scope: DesktopPlaylistPinScope?,
) {
    val identity get() = browseId.removePrefix("VL") to scope

    fun isVisibleIn(youtube: DesktopPlaylistPinScope, spotify: DesktopPlaylistPinScope?): Boolean =
        scope == null || scope == youtube || scope == spotify
}

/** Spotify and YouTube's built-in lists can resolve to account-relative content. */
internal fun isSpotifyLikedPlaylist(browseId: String): Boolean =
    browseId == SPOTIFY_PAGE_PREFIX + SpotifyLibrary.LIKED_ID

internal fun requiresPlaylistDownloadOwner(browseId: String): Boolean =
    isSpotifyLikedPlaylist(browseId) ||
        browseId.removePrefix("VL") in accountRelativeYouTubePlaylists

private val accountRelativeYouTubePlaylists = setOf("LM", "SE")

internal fun DesktopPlaylistPin.downloadedSnapshot(
    records: List<DesktopPlaylistDownload>,
): DesktopPlaylistDownload? = records.firstOrNull { record ->
    record.playlist && record.id.removePrefix("VL") == identity.first &&
        (!requiresPlaylistDownloadOwner(browseId) || record.pinOwner == scope?.downloadOwner && scope != null)
}

internal class DesktopPlaylistPins(persistence: DesktopPersistence) {
    private val preferences = persistence.preferences
    private val mutablePins = MutableStateFlow(
        DesktopPreferenceChunks.read(preferences, KEY_PINS).orEmpty()
            .lineSequence().mapNotNull(::decode).distinctBy { it.identity }.toList(),
    )
    val pins = mutablePins.asStateFlow()
    private var legacy = persistence.string(KEY_LEGACY).lines()
        .filter(String::isNotBlank).map { it.removePrefix("VL") }.toSet()

    /** Refresh titles and covers when their playlist is available. Claim old pins only when seen. */
    fun refresh(known: List<DesktopPlaylistPin>) {
        val byIdentity = known.associateBy { it.identity }
        val current = pins.value.map { previous ->
            byIdentity[previous.identity]?.let { fresh ->
                fresh.copy(artworkUrl = fresh.artworkUrl ?: previous.artworkUrl)
            } ?: previous
        }
        // Legacy entries had no account data. Keep them out of other profiles until their
        // playlist has been resolved in a known scope.
        val byId = known.associateBy { it.identity.first }
        val adopted = legacy.mapNotNull(byId::get).filterNot { it.browseId.startsWith(SPOTIFY_PAGE_PREFIX) }
        save((current + adopted).distinctBy { it.identity })
        if (adopted.isNotEmpty()) {
            legacy = legacy - adopted.map { it.identity.first }.toSet()
            DesktopPreferenceChunks.write(preferences, KEY_LEGACY, legacy.joinToString("\n"))
        }
    }

    fun migrateSpotifyScope(binding: DesktopSpotifyPinBinding) {
        val migrated = pins.value.map { pin ->
            if (pin.scope == binding.legacy && pin.browseId.startsWith(SPOTIFY_PAGE_PREFIX)) {
                pin.copy(scope = binding.account)
            } else pin
        }
        // Retain the position of the first pin and prefer metadata already saved for the account.
        val accountPins = pins.value.filter { it.scope == binding.account }.associateBy { it.identity }
        save(migrated.map { accountPins[it.identity] ?: it }.distinctBy { it.identity })
    }

    fun toggle(pin: DesktopPlaylistPin) {
        refresh(listOf(pin))
        val current = pins.value
        save(
            if (current.any { it.identity == pin.identity }) current.filterNot { it.identity == pin.identity }
            else current + pin,
        )
    }

    fun remove(browseId: String, scope: DesktopPlaylistPinScope?) {
        val identity = browseId.removePrefix("VL")
        save(pins.value.filterNot { it.identity == (identity to scope) })
    }

    private fun save(updated: List<DesktopPlaylistPin>) {
        if (updated == pins.value) return
        DesktopPreferenceChunks.write(preferences, KEY_PINS, updated.joinToString("\n", transform = ::encode))
        mutablePins.value = updated
    }

    private companion object {
        const val KEY_PINS = "sidebar_playlist_pins"
        const val KEY_LEGACY = "pinned_playlists"

        fun encode(pin: DesktopPlaylistPin): String = listOf(
            pin.browseId,
            pin.title,
            pin.artworkUrl.orEmpty(),
            pin.scope?.provider.orEmpty(),
            pin.scope?.accountId.orEmpty(),
            pin.scope?.profileId.orEmpty(),
        ).joinToString("|") { Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray(Charsets.UTF_8)) }

        fun decode(line: String): DesktopPlaylistPin? = runCatching {
            val fields = line.split('|').map { Base64.getUrlDecoder().decode(it).toString(Charsets.UTF_8) }
            if (fields.size != 6 || fields[0].isBlank() || fields[1].isBlank()) return null
            val scope = when (fields[3]) {
                "" -> null
                "youtube", "spotify" -> DesktopPlaylistPinScope(
                    fields[4].takeIf(String::isNotBlank),
                    fields[5].takeIf(String::isNotBlank),
                    fields[3],
                )
                else -> return null
            }
            DesktopPlaylistPin(fields[0], fields[1], fields[2].takeIf(String::isNotBlank), scope)
        }.getOrNull()
    }
}

/** A Spotify account and the one legacy cookie scope the same authenticated session proves. */
internal class DesktopSpotifyPinBinding(
    val legacy: DesktopPlaylistPinScope,
    val account: DesktopPlaylistPinScope,
)

internal class DesktopSpotifyPinIdentity(private val persistence: DesktopPersistence) {
    fun cached(cookie: String): DesktopPlaylistPinScope? {
        val legacy = DesktopPlaylistPinScope.spotify(cookie) ?: return null
        return accountScope(persistence.string(key(legacy)).takeUnless { it == CONFLICT }.orEmpty())
    }

    fun rememberVerified(cookie: String, accountId: String): DesktopSpotifyPinBinding? {
        val legacy = DesktopPlaylistPinScope.spotify(cookie) ?: return null
        val account = accountScope(accountId) ?: return null
        // If a credential ever resolves to a different account, do not move its old records.
        val key = key(legacy)
        val old = persistence.string(key)
        if (old == CONFLICT) return null
        if (old.isNotBlank() && old != accountId) {
            persistence.saveString(key, CONFLICT)
            return null
        }
        if (old.isBlank()) persistence.saveString(key, accountId)
        return DesktopSpotifyPinBinding(legacy, account)
    }

    private fun key(legacy: DesktopPlaylistPinScope) = "spotify_pin_account_${legacy.accountId}"

    private fun accountScope(accountId: String): DesktopPlaylistPinScope? =
        accountId.takeIf { it.isNotBlank() && it.none { ch -> ch == '|' || ch.isISOControl() } }
            ?.let { DesktopPlaylistPinScope("account:$it", null, "spotify") }

    private companion object {
        const val CONFLICT = "|conflicting_accounts"
    }
}
