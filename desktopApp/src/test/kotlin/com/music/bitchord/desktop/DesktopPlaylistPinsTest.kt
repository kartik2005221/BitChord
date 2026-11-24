package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopPlaylistPinsTest {
    private val preferences = Preferences.userRoot().node("bitchord-test-playlist-pins-${System.nanoTime()}")
    private val persistence = DesktopPersistence(preferences)
    private val personal = DesktopPlaylistPinScope("account", "personal")
    private val brand = DesktopPlaylistPinScope("account", "brand")
    private val otherAccount = DesktopPlaylistPinScope("different-account", "personal")

    @AfterTest
    fun cleanUp() {
        preferences.removeNode()
    }

    @Test
    fun preservesOrderAndMetadataAcrossRelaunchAndRemovals() {
        val first = DesktopPlaylistPin("local-id", "Local 🎵\ncollection", null, null)
        val second = DesktopPlaylistPin("VLprivate", "Private playlist", "https://example.org/cover", personal)
        val pins = DesktopPlaylistPins(persistence)
        pins.toggle(first)
        pins.toggle(second)

        val reopened = DesktopPlaylistPins(persistence)
        assertEquals(listOf(first, second), reopened.pins.value)
        reopened.remove("private", personal)
        assertEquals(listOf(first), DesktopPlaylistPins(persistence).pins.value)
    }

    @Test
    fun keepsAccountRelativePlaylistPinsSeparateForEachProfile() {
        val personalPin = DesktopPlaylistPin("VLLM", "My liked music", null, personal)
        val brandPin = DesktopPlaylistPin("VLLM", "Brand liked music", null, brand)
        val local = DesktopPlaylistPin("local-id", "Local", null, null)
        val pins = DesktopPlaylistPins(persistence)
        listOf(personalPin, brandPin, local).forEach(pins::toggle)

        val restored = DesktopPlaylistPins(persistence).pins.value
        assertEquals(listOf(personalPin, local), restored.filter { it.isVisibleIn(personal, null) })
        assertEquals(listOf(brandPin, local), restored.filter { it.isVisibleIn(brand, null) })
        assertEquals(listOf(local), restored.filter { it.isVisibleIn(otherAccount, null) })
    }

    @Test
    fun updatesRenamedPinsWithoutDuplicatingBrowseIdAliases() {
        val original = DesktopPlaylistPin("PL123", "Original", "https://example.org/cover", personal)
        val renamed = original.copy(browseId = "VLPL123", title = "Renamed", artworkUrl = null)
        val pins = DesktopPlaylistPins(persistence)
        pins.toggle(original)
        pins.refresh(listOf(renamed))

        assertEquals(
            listOf(renamed.copy(artworkUrl = original.artworkUrl)),
            DesktopPlaylistPins(persistence).pins.value,
        )
        pins.toggle(original)
        assertTrue(pins.pins.value.isEmpty())
    }

    @Test
    fun onlyAdoptsLegacyPinsAfterTheirPlaylistIsResolved() {
        persistence.saveString("pinned_playlists", "VLknown\nVLunknown\nVLknown")
        val pins = DesktopPlaylistPins(persistence)
        assertTrue(pins.pins.value.isEmpty())
        val known = DesktopPlaylistPin("known", "Known", null, personal)
        pins.refresh(listOf(known))
        assertEquals(listOf(known), DesktopPlaylistPins(persistence).pins.value)
        assertEquals("unknown", persistence.string("pinned_playlists"))
        assertFalse(known.isVisibleIn(brand, null))
    }

    @Test
    fun hidesSpotifyPinsOnSignOutAndOtherSessions() {
        val spotify = DesktopPlaylistPinScope.spotify("session-a")
        val otherSpotify = DesktopPlaylistPinScope.spotify("session-b")
        val pin = DesktopPlaylistPin("spotify:playlist:liked", "Liked Songs", null, spotify)
        DesktopPlaylistPins(persistence).toggle(pin)
        val restored = DesktopPlaylistPins(persistence).pins.value.single()

        assertNull(DesktopPlaylistPinScope.spotify(""))
        assertNotEquals(spotify, otherSpotify)
        assertNotEquals("session-a", restored.scope?.accountId)
        assertTrue(restored.isVisibleIn(personal, DesktopPlaylistPinScope.spotify("session-a")))
        assertFalse(restored.isVisibleIn(personal, otherSpotify))
        assertFalse(restored.isVisibleIn(personal, null))
    }
    @Test
    fun migratesOnlyPinsProvedByTheCurrentCookieAndRestoresThemAfterRotation() {
        val identity = DesktopSpotifyPinIdentity(persistence)
        val pins = DesktopPlaylistPins(persistence)
        val oldSession = DesktopPlaylistPinScope.spotify("session-a")
        val otherSession = DesktopPlaylistPinScope.spotify("session-b")
        val likedA = DesktopPlaylistPin("spotify:playlist:liked", "Liked by A", null, oldSession)
        val likedB = likedA.copy(title = "Liked by B", scope = otherSession)
        val regularA = likedA.copy(browseId = "spotify:playlist:abc", title = "Regular playlist")
        listOf(likedA, likedB, regularA).forEach(pins::toggle)
        assertNull(identity.cached("session-a"))

        val first = assertNotNull(identity.rememberVerified("session-a", "account-a"))
        pins.migrateSpotifyScope(first)
        val stable = likedA.copy(scope = first.account)
        assertEquals(
            listOf(stable, regularA.copy(scope = first.account)),
            pins.pins.value.filter { it.isVisibleIn(personal, first.account) },
        )
        assertTrue(likedB in pins.pins.value)
        assertNull(identity.cached("rotated-a"))
        val rotated = assertNotNull(identity.rememberVerified("rotated-a", "account-a"))
        pins.migrateSpotifyScope(rotated)
        assertEquals(first.account, rotated.account)
        assertEquals(first.account, DesktopSpotifyPinIdentity(persistence).cached("rotated-a"))
        assertTrue(stable in DesktopPlaylistPins(persistence).pins.value)
        assertNull(identity.cached(""))

        val other = assertNotNull(identity.rememberVerified("session-b", "account-b"))
        pins.migrateSpotifyScope(other)
        assertEquals(
            listOf(likedB.copy(scope = other.account)),
            pins.pins.value.filter { it.isVisibleIn(personal, other.account) },
        )
        assertTrue(pins.pins.value.none { it.isVisibleIn(personal, null) })
    }

    @Test
    fun neverClaimsUnscopedSpotifyPinsOrConflictingCookieBindings() {
        persistence.saveString("pinned_playlists", "spotify:playlist:liked")
        val identity = DesktopSpotifyPinIdentity(persistence)
        val pins = DesktopPlaylistPins(persistence)
        val binding = assertNotNull(identity.rememberVerified("session", "account-a"))
        pins.refresh(listOf(DesktopPlaylistPin("spotify:playlist:liked", "Liked Songs", null, binding.account)))
        assertTrue(pins.pins.value.isEmpty())
        assertEquals("spotify:playlist:liked", persistence.string("pinned_playlists"))
        assertNull(identity.rememberVerified("session", "account-b"))
        assertNull(identity.cached("session"))
        assertNull(identity.rememberVerified("session", "account-b"))
    }

    @Test
    fun movesOnlyOwnedLikedSnapshotsAndPreservesAnExistingStableSnapshot() {
        val identity = DesktopSpotifyPinIdentity(persistence)
        val binding = assertNotNull(identity.rememberVerified("session-a", "account-a"))
        val other = assertNotNull(identity.rememberVerified("session-b", "account-b"))
        val previous = DesktopPlaylistDownload(
            "spotify:playlist:liked", "Previous liked", "", null, true,
            listOf(Song(videoId = "old", title = "Old", artist = "Artist", thumbnailUrl = null)),
            1L, 2L, binding.legacy.downloadOwner,
        )
        val stable = previous.copy(
            title = "Current liked",
            songs = listOf(Song(videoId = "new", title = "New", artist = "Artist", thumbnailUrl = null)),
            createdAt = 3L, updatedAt = 4L, pinOwner = binding.account.downloadOwner,
        )
        val ownerless = previous.copy(pinOwner = null)
        val anotherOwner = previous.copy(pinOwner = other.account.downloadOwner)
        val regular = ownerless.copy(id = "spotify:playlist:regular")
        val all = listOf(previous, ownerless, anotherOwner, stable, regular)
        val migrated = migrateSpotifyDownloadOwner(all, binding)
        val merged = stable.copy(songs = stable.songs + previous.songs, createdAt = 1L)
        assertEquals(listOf(ownerless, anotherOwner, merged, regular), migrated)
        assertEquals(migrated, migrateSpotifyDownloadOwner(migrated, binding))
        assertEquals(listOf(merged, regular), migrated.filter { it.isVisibleToSpotify(binding.account) })
        assertEquals(listOf(anotherOwner, regular), migrated.filter { it.isVisibleToSpotify(other.account) })
        assertEquals(listOf(regular), migrated.filter { it.isVisibleToSpotify(null) })
    }

    @Test
    fun usesOfflineSnapshotsOnlyWhenTheyMatchThePinnedPlaylistAndItsOwner() {
        val liked = DesktopPlaylistPin("VLLM", "Liked Music", null, personal)
        val likedSnapshot = DesktopPlaylistDownload(
            "VLLM", "Liked Music", "", null, true, emptyList(), 0L, 0L,
            pinOwner = personal.downloadOwner,
        )
        val restoredLiked = decodeRecord(likedSnapshot.toPreferenceLine())
        assertEquals(likedSnapshot, restoredLiked)
        assertEquals(likedSnapshot, liked.downloadedSnapshot(listOf(likedSnapshot)))
        assertNull(liked.copy(scope = brand).downloadedSnapshot(listOf(likedSnapshot)))
        assertNull(liked.downloadedSnapshot(listOf(likedSnapshot.copy(pinOwner = null))))
        assertNull(liked.downloadedSnapshot(listOf(likedSnapshot.copy(playlist = false))))

        val playlist = liked.copy(browseId = "PL123")
        val oldSnapshot = likedSnapshot.copy(id = "VLPL123", pinOwner = null)
        assertEquals(oldSnapshot, playlist.downloadedSnapshot(listOf(oldSnapshot)))

        val spotifyPlaylist = playlist.copy(browseId = "spotify:playlist:regular")
        val oldSpotifySnapshot = oldSnapshot.copy(id = spotifyPlaylist.browseId)
        assertEquals(oldSpotifySnapshot, spotifyPlaylist.downloadedSnapshot(listOf(oldSpotifySnapshot)))
    }

}
