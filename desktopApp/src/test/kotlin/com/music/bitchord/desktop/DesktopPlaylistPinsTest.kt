package com.music.bitchord.desktop

import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
