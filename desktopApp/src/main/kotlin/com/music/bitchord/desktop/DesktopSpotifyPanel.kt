package com.music.bitchord.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.spotify.SpotifyLibrary
import com.music.bitchord.data.spotify.SpotifyPlaylist

/**
 * The side panel's Spotify view: the account's playlists, one click from the
 * top bar's Spotify mark — the thing the browser detour used to pretend to be.
 *
 * Reads the library fresh every time the panel opens (it is a column that
 * comes and goes; a page would cache, a peek should not) and hands a playlist
 * to the same route the Library grid opens it by, so the tracks fill in and
 * match to YouTube Music exactly as they do from there.
 */
@Composable
internal fun SpotifySidePanel(
    signedIn: Boolean,
    onSignIn: () -> Unit,
    onOpenPlaylist: (SpotifyPlaylist) -> Unit,
    modifier: Modifier = Modifier,
) {
    sealed interface State {
        data object Loading : State
        data object Empty : State
        data class Ready(val playlists: List<SpotifyPlaylist>) : State
        data class Failed(val message: String) : State
    }

    var state by remember { mutableStateOf<State>(State.Loading) }
    // A fresh read whenever the panel comes up — and whenever Retry is asked.
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(signedIn, refresh) {
        if (!signedIn) return@LaunchedEffect
        state = State.Loading
        runCatching { SpotifyLibrary.playlists() }
            .onSuccess { state = if (it.isEmpty()) State.Empty else State.Ready(it) }
            .onFailure {
                DesktopTrackLog.log("spotify: panel playlists failed: ${it.message}")
                state = State.Failed(it.message ?: "Could not reach Spotify")
            }
    }

    when {
        !signedIn -> Column(
            modifier = modifier.fillMaxSize().padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = DesktopStrings["spotify_panel_signed_out", "See your Spotify playlists here"],
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onSignIn) {
                Text(DesktopStrings["spotify_panel_sign_in", "Sign in to Spotify"])
            }
        }
        state is State.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(26.dp))
        }
        state is State.Failed -> Column(
            modifier = modifier.fillMaxSize().padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = DesktopStrings["spotify_panel_unreachable", "Spotify is unreachable"] +
                    " — " + (state as State.Failed).message,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { refresh++ }) {
                Text(DesktopStrings["retry", "Retry"])
            }
        }
        state is State.Empty -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = DesktopStrings["spotify_panel_empty", "No playlists in this account"],
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
            )
        }
        state is State.Ready -> LazyColumn(modifier = modifier.fillMaxSize()) {
            items((state as State.Ready).playlists, key = { it.id }) { playlist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenPlaylist(playlist) }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DesktopArtwork(
                        url = playlist.imageUrl,
                        contentDescription = playlist.name,
                        px = 88,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = playlist.name,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = playlist.owner ?: SPOTIFY_SHELF,
                            color = Color.White.copy(alpha = 0.55f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}
