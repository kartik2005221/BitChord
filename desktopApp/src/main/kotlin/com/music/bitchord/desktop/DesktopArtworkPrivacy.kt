package com.music.bitchord.desktop

/** OS media integrations cannot reliably render our blur, so publish no artwork in privacy mode. */
internal fun externalArtworkUrl(url: String?, privacy: Boolean): String? =
    url?.takeIf { !privacy && it.isNotBlank() }

enum class ArtworkPrivacyScope { BROWSING, PLAYER, AUXILIARY }

internal const val ARTWORK_REVEAL_MS = 10_000L
internal fun artworkPrivacyStrength(value: Int): Int = value.coerceIn(1, 3)
internal fun artworkPrivacyActive(master: Boolean, selected: Boolean, revealUntil: Long, now: Long, external: Boolean = false): Boolean =
    master && selected && (external || now >= revealUntil)

internal class ArtworkPrivacyPreferences(private val persistence: DesktopPersistence) {
    fun scope(scope: ArtworkPrivacyScope): Boolean = persistence.boolean(scopeKey(scope), true)
    fun setScope(scope: ArtworkPrivacyScope, value: Boolean) = persistence.saveBoolean(scopeKey(scope), value)
    fun strength(): Int = artworkPrivacyStrength(persistence.int(STRENGTH_KEY, 2))
    fun setStrength(value: Int) = persistence.saveInt(STRENGTH_KEY, artworkPrivacyStrength(value))

    private fun scopeKey(scope: ArtworkPrivacyScope) = "artwork_privacy_scope_${scope.name.lowercase()}"
    companion object { const val STRENGTH_KEY = "artwork_privacy_strength" }
}
