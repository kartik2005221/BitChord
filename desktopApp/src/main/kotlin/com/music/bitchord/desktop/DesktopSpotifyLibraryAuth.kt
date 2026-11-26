package com.music.bitchord.desktop

import com.music.bitchord.data.Http
import com.music.bitchord.data.spotify.SpotifyLibrary
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import netscape.javascript.JSObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.nio.file.Files
import java.util.Base64
import java.util.UUID

/**
 * Spotify's mobile v1.8.1 token flow, adapted to the desktop's JavaFX WebEngine.
 *
 * Do not mint a token by reproducing Spotify's TOTP request here. The mobile release found that
 * such a token can be returned successfully but then rejected by Spotify's downstream endpoints.
 * Instead, let the actual web player mint its own token and capture the /api/token response.
 */
internal object DesktopSpotifyLibraryAuth {
    private const val BRIDGE_NAME = "BitChordSpotifyTokenBridge"
    private const val TOKEN_TIMEOUT_MS = 15_000L
    private const val DEFAULT_TOKEN_LIFETIME_MS = 3_600_000L
    private const val CLIENT_TOKEN_URL = "https://clienttoken.spotify.com/v1/clienttoken"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val bearerCache = DesktopSpotifyBearerCache(DesktopSpotifyToken::session)

    @Volatile private var clientSession: DesktopSpotifyToken.Session? = null
    @Volatile private var cachedClientId: String? = null
    @Volatile private var cachedClientToken: String? = null
    @Volatile private var clientTokenExpiresAtMs = 0L
    @Volatile private var cachedSession: SessionInfo? = null

    private data class SessionInfo(val clientVersion: String, val deviceId: String)

    suspend fun tokens(): SpotifyLibrary.Tokens? = withContext(Dispatchers.IO) {
        val session = DesktopSpotifyToken.session()
        if (session.cookie.isBlank()) return@withContext null
        val bearer = accessToken(session) ?: throw IllegalStateException(
            "Spotify is connected, but its web-player token could not be loaded. " +
                "Try Refresh, or choose Reconnect Spotify."
        )
        val client = clientToken(session, bearer.clientId)
        DesktopSpotifyToken.withCurrent(session) { SpotifyLibrary.Tokens(bearer.token, client) }
    }

    /** The stable identity must come from the authenticated bearer minted for this saved session. */
    suspend fun verifiedAccountId(session: DesktopSpotifyToken.Session): String? = withContext(Dispatchers.IO) {
        val access = accessToken(session)?.takeIf { it.confirmedAuthenticated } ?: return@withContext null
        val request = Request.Builder()
            .url("https://api.spotify.com/v1/me")
            .header("Authorization", "Bearer ${access.token}")
            .header("Accept", "application/json")
            .header("User-Agent", CANVAS_UA)
            .build()
        val accountId = runCatching {
            Http.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                val profile = json.parseToJsonElement(body).jsonObject
                val id = profile["id"]?.jsonPrimitive
                id?.takeIf { it.isString }?.contentOrNull?.takeIf(String::isNotBlank)
            }
        }.getOrNull()
        DesktopSpotifyToken.withCurrent(session) {
            accountId.takeIf { bearerCache.current(session) === access }
        }
    }

    /** Clear cached access/client tokens without discarding the user's saved Spotify login. */
    @Synchronized
    fun invalidate() {
        bearerCache.clear()
        clearClientToken()
    }

    private fun clearClientToken() {
        clientSession = null
        cachedClientId = null
        cachedClientToken = null
        clientTokenExpiresAtMs = 0L
        cachedSession = null
    }

    /** Cached companion header for the synchronous HTTP helpers in the Canvas provider. */
    @Synchronized
    fun cachedClientToken(): String? = cachedClientToken?.takeIf {
        clientSession === DesktopSpotifyToken.session() && System.currentTimeMillis() < clientTokenExpiresAtMs - 30_000
    }

    internal fun canvasClientToken(): String? {
        val session = DesktopSpotifyToken.session()
        return clientToken(session, bearerCache.current(session)?.clientId)
    }

    private suspend fun accessToken(session: DesktopSpotifyToken.Session): DesktopSpotifyBearer? =
        bearerCache.get(session) { cookie ->
            DesktopWebCookies.sessionMutex.withLock {
                if (DesktopSpotifyToken.session() !== session) return@withLock null
                harvestViaWebEngine(cookie)
            } ?: run {
                if (DesktopSpotifyToken.session() !== session) return@run null
                DesktopTrackLog.log("spotify: JavaFX token capture unavailable; trying local Chromium")
                DesktopSpotifyChromiumToken.harvest(cookie)?.let(::parseTokenPayload)
            }
        }

    /**
     * Opens a fresh web-player profile, applies the user's saved session cookie, and observes the
     * real token response. The page may mint an anonymous token first; only an authenticated token
     * is accepted, matching the mobile implementation.
     */
    private suspend fun harvestViaWebEngine(cookie: String): DesktopSpotifyBearer? {
        val result = CompletableDeferred<DesktopSpotifyBearer?>()
        val engineReady = CompletableDeferred<Unit>()
        val profile = runCatching { Files.createTempDirectory("bitchord-spotify-token-") }.getOrNull()
            ?: return null
        val engineRef = arrayOfNulls<WebEngine>(1)
        // JavaFX's JS-to-Java bridge retains only weak references to Java objects. Keep both the
        // real WebView and the bridge strongly reachable until the harvest has finished.
        val viewRef = arrayOfNulls<WebView>(1)
        val bridgeRef = arrayOfNulls<DesktopSpotifyTokenBridge>(1)

        try {
            Platform.runLater {
                if (!result.isActive) {
                    engineReady.cancel()
                    return@runLater
                }
                try {
                    DesktopWebCookies.reset()
                    DesktopWebCookies.put(
                        URI("https://open.spotify.com/"),
                        mapOf(
                            "Set-Cookie" to listOf(
                                "sp_dc=" + cookie + "; Domain=.spotify.com; Path=/; Secure; HttpOnly",
                            ),
                        ),
                    )

                    val view = WebView()
                    viewRef[0] = view
                    val engine = view.engine
                    engineRef[0] = engine
                    engine.userAgent = CANVAS_UA
                    engine.userDataDirectory = profile.toFile()

                    var reloadAttempted = false
                    val bridge = DesktopSpotifyTokenBridge(
                        handler = { payload ->
                            // The bridge is installed as documents load. Accept only the web
                            // player origin with the exact cookie this harvest was started for.
                            val spotify = URI("https://open.spotify.com/")
                            val origin = runCatching { URI(engine.location) }.getOrNull()
                            val cookies = DesktopWebCookies.get(spotify, emptyMap())["Cookie"].orEmpty()
                            if (origin?.scheme == "https" && origin.host == spotify.host &&
                                cookies.flatMap { it.split(';') }.map(String::trim)
                                    .filter { it.startsWith("sp_dc=") } == listOf("sp_dc=$cookie")
                            ) acceptTokenPayload(payload, result)
                        },
                        onReloadRequested = {
                            if (result.isActive && !reloadAttempted) {
                                reloadAttempted = true
                                Platform.runLater {
                                    if (result.isActive) engine.reload()
                                }
                            }
                        },
                    )
                    bridgeRef[0] = bridge
                    var lastHookAttemptMs = 0L

                    fun installHook(force: Boolean = false) {
                        if (!result.isActive) return
                        val now = System.currentTimeMillis()
                        if (!force && now - lastHookAttemptMs < 120L) return
                        lastHookAttemptMs = now
                        runCatching {
                            val window = engine.executeScript("window") as JSObject
                            window.setMember(BRIDGE_NAME, bridge)
                            engine.executeScript(HOOK_SCRIPT)
                        }
                    }

                    // Unlike Android's onPageStarted callback, a JavaFX RUNNING notification can
                    // arrive before the new document exists. Retry as the DOM and resources load.
                    engine.documentProperty().addListener { _, _, document ->
                        if (document != null) installHook(force = true)
                    }
                    engine.loadWorker.progressProperty().addListener { _, _, progress ->
                        if (progress.toDouble() in 0.01..0.99) installHook()
                    }
                    engine.loadWorker.stateProperty().addListener { _, _, state ->
                        when (state) {
                            Worker.State.SCHEDULED, Worker.State.RUNNING ->
                                installHook(force = true)
                            Worker.State.SUCCEEDED -> {
                                installHook(force = true)
                                // The web player might already have requested /api/token before
                                // JavaFX exposed its window. Replay the exact observed request
                                // *inside* the authenticated Spotify page instead of blindly
                                // reloading and losing our injected hook.
                                runCatching { engine.executeScript(TOKEN_RECOVERY_SCRIPT) }
                                    .onFailure {
                                        DesktopTrackLog.log(
                                            "spotify: token recovery script unavailable (" +
                                                it.javaClass.simpleName + ")"
                                        )
                                    }
                            }
                            Worker.State.FAILED, Worker.State.CANCELLED -> {
                                DesktopTrackLog.log("spotify: web-player load ended in " + state.name)
                                if (!result.isCompleted) result.complete(null)
                            }
                            else -> Unit
                        }
                    }

                    engine.load("https://open.spotify.com/")
                    engineReady.complete(Unit)
                } catch (failure: Throwable) {
                    engineReady.completeExceptionally(failure)
                    result.completeExceptionally(failure)
                }
            }

            return withTimeoutOrNull(TOKEN_TIMEOUT_MS) {
                engineReady.await()
                result.await()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            DesktopTrackLog.log("spotify: token WebEngine failed: " + (failure.message ?: failure.javaClass.simpleName))
            return null
        } finally {
            result.cancel()
            engineReady.cancel()
            runCatching {
                Platform.runLater {
                    engineRef[0]?.let { engine ->
                        runCatching { engine.load("about:blank") }
                    }
                    if (engineRef[0] != null) DesktopWebCookies.reset()
                    bridgeRef[0] = null
                    viewRef[0] = null
                }
            }
            withContext(Dispatchers.IO) {
                runCatching {
                    if (!profile.toFile().deleteRecursively()) {
                        Runtime.getRuntime().addShutdownHook(Thread { profile.toFile().deleteRecursively() })
                    }
                }
            }
        }
    }

    /** A token payload without an explicit authentication flag cannot prove pin ownership. */
    internal fun parseTokenPayload(payload: String?): DesktopSpotifyBearer? {
        if (payload.isNullOrBlank()) return null
        return runCatching {
            val root = json.parseToJsonElement(payload).jsonObject
            val token = root["accessToken"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() } ?: return@runCatching null
            val anonymous = root["isAnonymous"]?.jsonPrimitive?.contentOrNull
                ?.toBooleanStrictOrNull()
            if (anonymous == true) return@runCatching null
            val expiresAt = root["accessTokenExpirationTimestampMs"]?.jsonPrimitive?.contentOrNull
                ?.toLongOrNull()?.takeIf { it > System.currentTimeMillis() }
                ?: (System.currentTimeMillis() + DEFAULT_TOKEN_LIFETIME_MS)
            val clientId = root["clientId"]?.jsonPrimitive?.contentOrNull
            DesktopSpotifyBearer(token, expiresAt, clientId, confirmedAuthenticated = anonymous == false)
        }.getOrNull()
    }

    /** Receives raw /api/token response bodies from the Spotify page. */
    private fun acceptTokenPayload(payload: String?, result: CompletableDeferred<DesktopSpotifyBearer?>) {
        if (result.isCompleted) return
        parseTokenPayload(payload)?.let { result.complete(it) }
    }

    private val HOOK_SCRIPT = """
        (function () {
          if (window.__bitchordTokenHook) return;
          window.__bitchordTokenHook = true;
          var report = function (body) {
            try { window.BitChordSpotifyTokenBridge.onTokenPayload(String(body)); } catch (e) {}
          };
          var isToken = function (u) {
            try { return String(u).indexOf('/api/token') !== -1; } catch (e) { return false; }
          };
          var originalFetch = window.fetch;
          if (originalFetch) {
            window.fetch = function (input, init) {
              var url = (input && input.url) ? input.url : input;
              var response = originalFetch.apply(this, arguments);
              if (isToken(url)) {
                try {
                  response.then(function (res) {
                    res.clone().text().then(report).catch(function () {});
                  }).catch(function () {});
                } catch (e) {}
              }
              return response;
            };
          }
          var originalOpen = XMLHttpRequest.prototype.open;
          XMLHttpRequest.prototype.open = function (method, url) {
            this.__bitchordUrl = url;
            return originalOpen.apply(this, arguments);
          };
          var originalSend = XMLHttpRequest.prototype.send;
          XMLHttpRequest.prototype.send = function () {
            var xhr = this;
            try {
              xhr.addEventListener('load', function () {
                if (isToken(xhr.__bitchordUrl)) {
                  try { report(xhr.responseText); } catch (e) {}
                }
              });
            } catch (e) {}
            return originalSend.apply(this, arguments);
          };
        })();
    """.trimIndent()

    /**
     * JavaFX can expose the new document *after* Spotify's scripts requested /api/token.
     * Recover that request URL from the page's resource timing entries and ask the browser to
     * fetch it with its own authenticated cookies. No cookie or bearer is logged or exported.
     * A single delayed reload is requested only if interception and replay both fail.
     */
    private val TOKEN_RECOVERY_SCRIPT = """
        (function () {
          if (window.__bitchordSpotifyTokenRecovery) return;
          window.__bitchordSpotifyTokenRecovery = true;
          var bridge = window.BitChordSpotifyTokenBridge;
          if (!bridge) return;
          var attempted = {};
          var replay = function () {
            try {
              if (!window.fetch || !window.performance || !performance.getEntriesByType) return;
              var entries = performance.getEntriesByType('resource');
              for (var i = entries.length - 1; i >= 0; i--) {
                var url = entries[i].name;
                if (String(url).indexOf('https://open.spotify.com/api/token?') !== 0) continue;
                if (attempted[url]) continue;
                attempted[url] = true;
                window.fetch(url, {credentials: 'include', cache: 'no-store'})
                  .then(function (res) { return res.text(); })
                  .then(function (body) {
                    try { bridge.onTokenPayload(String(body)); } catch (e) {}
                  }).catch(function () {});
                break;
              }
            } catch (e) {}
          };
          replay();
          setTimeout(replay, 900);
          setTimeout(replay, 2500);
          setTimeout(replay, 4500);
          setTimeout(function () {
            try { bridge.onReloadRequested(); } catch (e) {}
          }, 6000);
        })();
    """.trimIndent()

    @Synchronized
    private fun clientToken(session: DesktopSpotifyToken.Session, clientId: String?): String? {
        if (DesktopSpotifyToken.session() !== session) return null
        if (clientSession !== session || cachedClientId != clientId) {
            clearClientToken()
            clientSession = session
            cachedClientId = clientId
        }
        val now = System.currentTimeMillis()
        cachedClientToken?.let { if (now < clientTokenExpiresAtMs - 30_000) return it }
        if (clientId == null) return null
        val player = session() ?: return null
        val osName = System.getProperty("os.name").orEmpty().lowercase()
        val platform = when {
            osName.contains("win") -> "windows"
            osName.contains("mac") -> "macos"
            else -> "linux"
        }
        val payload = buildJsonObject {
            putJsonObject("client_data") {
                put("client_version", player.clientVersion)
                put("client_id", clientId)
                putJsonObject("js_sdk_data") {
                    put("device_brand", "unknown")
                    put("device_model", "unknown")
                    put("os", platform)
                    put("os_version", System.getProperty("os.version").orEmpty())
                    put("device_id", player.deviceId)
                    put("device_type", "computer")
                }
            }
        }
        val request = Request.Builder()
            .url(CLIENT_TOKEN_URL)
            .post(payload.toString().toByteArray(Charsets.UTF_8).toRequestBody("application/json".toMediaType()))
            .header("Accept", "application/json")
            .header("User-Agent", CANVAS_UA)
            .build()
        val body = runCatching {
            Http.client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull() ?: return null
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        if (root["response_type"]?.jsonPrimitive?.contentOrNull != "RESPONSE_GRANTED_TOKEN_RESPONSE") return null
        val granted = root["granted_token"]?.jsonObject ?: return null
        val token = granted["token"]?.jsonPrimitive?.contentOrNull ?: return null
        val ttlSeconds = granted["expires_after_seconds"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 3600L
        cachedClientToken = token
        clientTokenExpiresAtMs = now + ttlSeconds * 1000
        return token
    }

    /** The current web-player version and its per-session device id, as the mobile release does. */
    private fun session(): SessionInfo? {
        cachedSession?.let { return it }
        val request = Request.Builder()
            .url("https://open.spotify.com")
            .header("User-Agent", CANVAS_UA)
            .build()
        val response = runCatching {
            Http.client.newCall(request).execute().use { result ->
                val html = result.body?.string()
                val spT = result.headers("Set-Cookie").firstNotNullOfOrNull { header ->
                    Regex("""sp_t=([^;]+)""").find(header)?.groupValues?.get(1)
                }
                html to spT
            }
        }.getOrNull() ?: return null
        val html = response.first ?: return null
        val configB64 = Regex("""<script id="appServerConfig" type="text/plain">([^<]+)</script>""")
            .find(html)?.groupValues?.get(1) ?: return null
        val clientVersion = runCatching {
            val config = String(Base64.getDecoder().decode(configB64), Charsets.UTF_8)
            json.parseToJsonElement(config).jsonObject["clientVersion"]?.jsonPrimitive?.contentOrNull
        }.getOrNull() ?: return null
        return SessionInfo(clientVersion, response.second ?: UUID.randomUUID().toString()).also {
            cachedSession = it
        }
    }

}


internal class DesktopSpotifyBearer(
    val token: String,
    val expiresAt: Long,
    val clientId: String?,
    val confirmedAuthenticated: Boolean,
)

/** The cache key is a login instance. Saving the same cookie again invalidates in-flight work. */
internal class DesktopSpotifyBearerCache(
    private val activeSession: () -> DesktopSpotifyToken.Session,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val harvestMutex = Mutex()
    private var cached: Pair<DesktopSpotifyToken.Session, DesktopSpotifyBearer>? = null
    private var generation = 0L

    @Synchronized
    fun clear() {
        cached = null
        generation++
    }

    @Synchronized
    fun current(session: DesktopSpotifyToken.Session): DesktopSpotifyBearer? =
        cached?.takeIf { (owner, token) ->
            owner === session && activeSession() === session && now() < token.expiresAt - 30_000L
        }?.second

    suspend fun get(
        session: DesktopSpotifyToken.Session,
        harvest: suspend (String) -> DesktopSpotifyBearer?,
    ): DesktopSpotifyBearer? {
        if (session.cookie.isBlank()) return null
        current(session)?.let { return it }
        val requestedGeneration = synchronized(this) { generation }
        return harvestMutex.withLock {
            if (activeSession() !== session) return@withLock null
            current(session)?.let { return@withLock it }
            if (synchronized(this) { generation != requestedGeneration }) return@withLock null
            val result = harvest(session.cookie) ?: return@withLock null
            synchronized(this) {
                if (activeSession() !== session || generation != requestedGeneration) return@synchronized null
                cached = session to result
                result
            }
        }
    }
}

/** Public JVM bridge surface required by JavaFX's JavaScript-to-Java bridge. */
class DesktopSpotifyTokenBridge(
    private val handler: (String?) -> Unit,
    private val onReloadRequested: () -> Unit,
) {
    fun onTokenPayload(payload: String?) = handler(payload)
    fun onReloadRequested() = onReloadRequested.invoke()
}
