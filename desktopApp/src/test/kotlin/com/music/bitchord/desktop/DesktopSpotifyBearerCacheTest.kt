package com.music.bitchord.desktop

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DesktopSpotifyBearerCacheTest {
    @Test
    fun lateHarvestCannotCrossAccountOrSurviveSavingTheSameCookieAgain() = runBlocking {
        val first = DesktopSpotifyToken.Session("a")
        val next = DesktopSpotifyToken.Session("b")
        val reopened = DesktopSpotifyToken.Session("a")
        var active = first
        val cache = DesktopSpotifyBearerCache(activeSession = { active }, now = { 0L })
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val old = async {
            cache.get(first) { cookie ->
                assertEquals("a", cookie)
                started.complete(Unit)
                finish.await()
                bearer("old")
            }
        }
        started.await()
        active = next
        active = reopened
        finish.complete(Unit)
        assertNull(old.await())
        assertNull(cache.current(first))
        assertNull(cache.current(reopened))
        assertNull(cache.get(next) { error("Previous session was harvested") })
        val fresh = bearer("fresh")
        assertSame(fresh, cache.get(reopened) { fresh })
        assertSame(fresh, cache.get(reopened) { error("Current session missed the cache") })
    }

    @Test
    fun explicitRefreshDiscardsInFlightTokenForTheSameSessionAndCanRetry() = runBlocking {
        val session = DesktopSpotifyToken.Session("a")
        val cache = DesktopSpotifyBearerCache(activeSession = { session }, now = { 0L })
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val old = async {
            cache.get(session) {
                started.complete(Unit)
                finish.await()
                bearer("old")
            }
        }
        started.await()
        cache.clear()
        finish.complete(Unit)
        assertNull(old.await())
        assertNull(cache.get(session) { null })
        val fresh = bearer("fresh")
        assertSame(fresh, cache.get(session) { fresh })
    }

    @Test
    fun requiresAnExplicitNonAnonymousBearerToProveOwnership() {
        assertNull(DesktopSpotifyLibraryAuth.parseTokenPayload("""{"accessToken":"test","isAnonymous":true}"""))
        val unspecified = assertNotNull(DesktopSpotifyLibraryAuth.parseTokenPayload("""{"accessToken":"test"}"""))
        assertFalse(unspecified.confirmedAuthenticated)
        val authenticated = assertNotNull(
            DesktopSpotifyLibraryAuth.parseTokenPayload("""{"accessToken":"test","isAnonymous":false}"""),
        )
        assertTrue(authenticated.confirmedAuthenticated)
    }

    private fun bearer(value: String) = DesktopSpotifyBearer(value, 120_000L, null, true)
}
