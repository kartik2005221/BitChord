package com.music.bitchord.desktop

import com.music.bitchord.data.canvas.SpotifyCanvasQuery

/**
 * The web player's current TOTP secret and the Spotify library's query hashes, read out of
 * its own page and bundle. Spotify rotates them without warning: the shipped table sat at
 * v12 while the live one was already v61, so `/api/token` answered an expired version and
 * no token ever came back — a freshly signed-in account read as "Spotify sign-in expired".
 *
 * A port of the extension's `extension/lib/spotify.ts`:
 * - the page's HTML links the bundle, `open.spotifycdn.com/cdn/build/web-player/web-player.<hex>.js`;
 * - the bundle declares its TOTP secrets as `{secret:'…',version:N}` literals, newest winning;
 * - operation hashes as `"op","query","<64 hex>"` declarations.
 *
 * Nothing here sends the session cookie: the landing page and the bundle are served to
 * signed-out visitors too. [force] is for a read after something went stale — a rejected
 * `totpVer` for the secret, "PersistedQueryNotFound" for a hash — and callers reread once.
 * Every leg goes to the shipped constants (`DesktopSpotifyToken.TOTP_SECRETS` and
 * `SpotifyLibrary`'s) when the bundle can't be read or no longer declares the values.
 */
internal object DesktopSpotifyBundle {

    private const val WEB_PLAYER = "https://open.spotify.com"

    /** A good read lasts half a day; a missed one is retried sooner. */
    private const val FOUND_TTL_MS = 12L * 60 * 60 * 1000
    private const val MISSED_TTL_MS = 30L * 60 * 1000

    /** The operations SpotifyLibrary asks for, and the sections declared in the main bundle. */
    private val OPERATIONS = listOf("libraryV3", "fetchPlaylist", "fetchLibraryTracks")

    internal class Secret(val version: Int, val codes: IntArray)

    internal class WebPlayer(
        val secret: Secret?,
        val hashes: Map<String, String>,
        /** Whether every value came from the live bundle; a partial read is retried sooner. */
        val complete: Boolean,
        val at: Long,
    )

    @Volatile private var cache: WebPlayer? = null

    /** The current read, or the last one while it is still inside its TTL. [force] reads fresh. */
    fun webPlayer(force: Boolean = false): WebPlayer? {
        if (!force) cache?.let { cached ->
            val ttl = if (cached.complete) FOUND_TTL_MS else MISSED_TTL_MS
            if (System.currentTimeMillis() < cached.at + ttl) return cached
        }
        val html = canvasGet(WEB_PLAYER) ?: return null
        val bundle = bundleUrl(html)?.let { canvasGet(it) }.orEmpty()
        val secret = secretsIn(bundle).firstOrNull()
        val hashes = OPERATIONS.mapNotNull { operation ->
            SpotifyCanvasQuery.findQueryHash(bundle, operation)?.let { operation to it }
        }.toMap()
        val player = WebPlayer(
            secret = secret,
            hashes = hashes,
            complete = secret != null && hashes.keys.containsAll(OPERATIONS),
            at = System.currentTimeMillis(),
        )
        cache = player
        if (!player.complete) {
            DesktopTrackLog.log(
                "spotify: the web player read incomplete (bundled secret=${secret?.version}, hashes=${hashes.keys})",
            )
        }
        return player
    }

    /** The main bundle's URL, or null when the page didn't link one this shape. */
    private fun bundleUrl(html: String): String? =
        Regex("""https://open\.spotifycdn\.com/cdn/build/web-player/web-player\.[0-9a-f]+\.js""")
            .find(html)?.value

    /** `{secret:'…',version:N}` as the bundle declares them, newest first. */
    internal fun secretsIn(script: String): List<Secret> =
        Regex("""\{secret:(['"])((?:\\.|(?!\1).)*)\1,version:(\d+)\}""").findAll(script)
            .mapNotNull { match ->
                val version = match.groupValues[3].toIntOrNull() ?: return@mapNotNull null
                runCatching { unescapeJs(match.groupValues[2]) }
                    .map { literal -> Secret(version, literal.map { it.code }.toIntArray()) }
                    .getOrNull()
            }
            .sortedByDescending { it.version }
            .toList()

    private val ESCAPE = Regex("""\\(u\{[0-9a-fA-F]+\}|u[0-9a-fA-F]{4}|x[0-9a-fA-F]{2}|[\s\S])""")
    private val UNMATCHED = Regex("""[u{}]""")

    /** A JavaScript string literal's body, unescaped without touching `eval`, as the extension does. */
    private fun unescapeJs(body: String): String = ESCAPE.replace(body) { match ->
        val escape = match.groupValues[1]
        when {
            escape.first() == 'u' -> String(
                Character.toChars(
                    escape.replace(UNMATCHED, "").toInt(16),
                ),
            )
            escape.first() == 'x' -> escape.drop(1).toInt(16).toChar().toString()
            else -> when (escape) {
                "n" -> "\n"
                "r" -> "\r"
                "t" -> "\t"
                "b" -> "\b"
                "f" -> "\u000C"
                "v" -> "\u000B"
                "0" -> "\u0000"
                else -> escape
            }
        }
    }
}
