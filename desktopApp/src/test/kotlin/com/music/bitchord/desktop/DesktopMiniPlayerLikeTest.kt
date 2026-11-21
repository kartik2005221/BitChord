package com.music.bitchord.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import com.music.bitchord.data.NerdStats
import com.music.bitchord.data.model.Song
import org.junit.Test
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DesktopMiniPlayerLikeTest {
    @BeforeTest fun installHost() { com.music.bitchord.ui.player.PlayerPlatform.install(DesktopPlayerHost) }

    @Test fun likeAndMoreStayClickableInCompactDockWithoutExpanding() = runDesktopComposeUiTest(width = 480, height = 100) {
        var liked by mutableStateOf(false)
        var expanded = 0
        var likes = 0
        var menus = 0
        val song = Song("song", "Song", "Artist", null)
        setContent {
            MaterialTheme {
                DesktopMiniPlayer(
                    song = song, isPlaying = false, isLoading = false, previousEnabled = false,
                    progress = 0f, volume = 0.5f, shuffle = false, repeatMode = DesktopRepeatMode.OFF,
                    sidePanel = null, onExpand = { expanded++ }, onPlayPause = {}, onPrevious = {},
                    onNext = {}, onShuffleChange = {}, onRepeatModeChange = {}, onVolumeChange = {},
                    onOpenAudioOutput = {}, onOpenLyrics = {}, onOpenPip = {}, onOpenQueue = {},
                    onOpenDetails = {}, liked = liked, onToggleLike = { likes++; liked = !liked },
                    songMenu = { DesktopMoreButton(onClick = { menus++ }) },
                )
            }
        }
        onNodeWithContentDescription("Like").assertIsDisplayed().assertIsOff().performMouseInput { click() }
        onNodeWithContentDescription("Remove from Liked Music").assertIsDisplayed().assertIsOn()
        onNodeWithContentDescription("More options").assertIsDisplayed().performMouseInput { click() }
        runOnIdle {
            assertEquals(1, likes)
            assertEquals(1, menus)
            assertEquals(0, expanded)
        }
        onNodeWithContentDescription("Remove from Liked Music").performMouseInput { click() }
        onNodeWithContentDescription("Like").assertIsDisplayed()
        runOnIdle { assertEquals(2, likes); assertEquals(0, expanded) }
    }

    @Test fun moreOpensSongMenuAndDelegatesAction() = runDesktopComposeUiTest(width = 760, height = 700) {
        val song = Song("song", "Song", "Artist", null)
        var queued: Song? = null
        var expanded = 0
        var menuOpen by mutableStateOf(false)
        val portal = DesktopMenuPortalState()
        val actions = DesktopSongActions(
            signedIn = true, disliked = false, downloaded = false, downloadInProgress = false,
            sleepTimerMinutes = null, sleepAfterTrack = false, onToggleDislike = {},
            onAddToPlaylist = {}, onDownload = {}, onRemoveDownload = {}, onStartRadio = {},
            onPlayNext = {}, onAddToQueue = { queued = it }, onOpenAlbum = {}, onOpenArtist = {},
            onSleepTimer = {}, onSleepAfterTrack = {}, onShare = {},
        )
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDesktopMenuPortal provides portal) {
                    Box(Modifier.fillMaxSize().desktopMenuPointer(portal), contentAlignment = Alignment.BottomCenter) {
                        DesktopMiniPlayer(
                            song = song, isPlaying = false, isLoading = false, previousEnabled = false,
                            progress = 0f, volume = 0.5f, shuffle = false, repeatMode = DesktopRepeatMode.OFF,
                            sidePanel = null, onExpand = { expanded++ }, onPlayPause = {}, onPrevious = {}, onNext = {},
                            onShuffleChange = {}, onRepeatModeChange = {}, onVolumeChange = {},
                            onOpenAudioOutput = {}, onOpenLyrics = {}, onOpenPip = {}, onOpenQueue = {},
                            onOpenDetails = {}, liked = false, onToggleLike = {},
                            songMenu = {
                                Box {
                                    DesktopMoreButton(onClick = { menuOpen = true })
                                    if (menuOpen) {
                                        DesktopSongMenuFor(song, false, actions, {}, null, null, onDismiss = { menuOpen = false })
                                    }
                                }
                            },
                        )
                        DesktopMenuPortal(portal)
                    }
                }
            }
        }
        val more = onNodeWithContentDescription("More options")
        val buttonTop = more.fetchSemanticsNode().boundsInRoot.top
        more.performMouseInput { click() }
        val addToQueue = onNodeWithText("Add to queue").assertIsDisplayed()
        assertTrue(addToQueue.fetchSemanticsNode().boundsInRoot.bottom < buttonTop)
        addToQueue.performMouseInput { click() }
        runOnIdle { assertEquals(song, queued); assertEquals(0, expanded) }
        onNodeWithText("Add to queue").assertDoesNotExist()
    }

    @Test fun qualityFollowsTrackSearchAndMeasuredStream() = runDesktopComposeUiTest(width = 480, height = 100) {
        var song: Song? by mutableStateOf(Song("first", "First", "Artist", null))
        NerdStats.current.value = null
        NerdStats.racingLossless.value = setOf("first")
        try {
            setContent {
                MaterialTheme {
                    DesktopMiniPlayer(
                        song = song, isPlaying = false, isLoading = false, previousEnabled = false,
                        progress = 0f, volume = 0.5f, shuffle = false, repeatMode = DesktopRepeatMode.OFF,
                        sidePanel = null, onExpand = {}, onPlayPause = {}, onPrevious = {}, onNext = {},
                        onShuffleChange = {}, onRepeatModeChange = {}, onVolumeChange = {},
                        onOpenAudioOutput = {}, onOpenLyrics = {}, onOpenPip = {}, onOpenQueue = {}, onOpenDetails = {},
                    )
                }
            }
            onNodeWithContentDescription("Upgrading quality").assertIsDisplayed()
            runOnIdle { NerdStats.current.value = NerdStats.Snapshot("audio/flac", 1411, 96000, 2, 24) }
            onNodeWithContentDescription("Hi-Res Lossless").assertIsDisplayed()
            runOnIdle {
                song = Song("second", "Second", "Artist", null)
                NerdStats.current.value = NerdStats.Snapshot("audio/aac", 320, 44100, 2)
            }
            onNodeWithContentDescription("High quality").assertIsDisplayed()
            onNodeWithContentDescription("Upgrading quality").assertDoesNotExist()
            runOnIdle { song = null }
            onNodeWithContentDescription("High quality").assertDoesNotExist()
        } finally {
            NerdStats.current.value = null
            NerdStats.racingLossless.value = emptySet()
        }
    }

    @Test fun emptyDockDoesNotShowSongActions() = runDesktopComposeUiTest(width = 480, height = 100) {
        setContent {
            MaterialTheme {
                DesktopMiniPlayer(
                    song = null, isPlaying = false, isLoading = false, previousEnabled = false,
                    progress = 0f, volume = 0.5f, shuffle = false, repeatMode = DesktopRepeatMode.OFF,
                    sidePanel = null, onExpand = {}, onPlayPause = {}, onPrevious = {}, onNext = {},
                    onShuffleChange = {}, onRepeatModeChange = {}, onVolumeChange = {},
                    onOpenAudioOutput = {}, onOpenLyrics = {}, onOpenPip = {}, onOpenQueue = {},
                    onOpenDetails = {}, liked = false, onToggleLike = {},
                    songMenu = { DesktopMoreButton(onClick = {}) },
                )
            }
        }
        onNodeWithContentDescription("Like").assertDoesNotExist()
        onNodeWithContentDescription("Remove from Liked Music").assertDoesNotExist()
        onNodeWithContentDescription("More options").assertDoesNotExist()
    }
}
