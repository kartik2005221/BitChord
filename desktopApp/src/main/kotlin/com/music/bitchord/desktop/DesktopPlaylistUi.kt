package com.music.bitchord.desktop

import com.music.bitchord.ui.screens.desktopTopScrollFade

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.model.PlaylistPrivacy
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UserPlaylist
import com.music.bitchord.ui.icons.BitChordIcons
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import kotlinx.coroutines.launch

/** Where a track goes: one of the account's playlists, a local one, or a new one. */
@Composable
internal fun DesktopPlaylistDialog(
    target: DesktopPlaylistTarget?,
    accountPlaylists: List<UserPlaylist>,
    localPlaylists: List<DesktopPlaylist>,
    signedIn: Boolean,
    /** Whether the current YouTube session can accept the target's eligible tracks. */
    canUseAccount: Boolean,
    busy: Boolean,
    error: String?,
    completed: Set<DesktopPlaylistDestination>,
    onPick: (List<DesktopPlaylistDestination>) -> Unit,
    onCreate: (String, PlaylistPrivacy) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(target == null) }
    var selected by remember { mutableStateOf(emptySet<DesktopPlaylistDestination>()) }
    var query by remember { mutableStateOf("") }
    val playlistListState = rememberLazyListState()
    LaunchedEffect(query, creating) {
        if (!creating) playlistListState.scrollToItem(0)
    }

    DesktopDialogFrame(onDismiss = { if (!busy) onDismiss() }) {
        if (creating) {
            NewPlaylistForm(
                // Nowhere to go back to when the dialog opened onto the form; its own dismiss is
                // the way out.
                onBack = if (target == null || busy) null else ({ creating = false }),
                signedIn = signedIn && canUseAccount &&
                    (target == null || target.canCreateOnAccount),
                busy = busy,
                error = error,
                onCreate = onCreate,
            )
            return@DesktopDialogFrame
        }

        val album = target?.album == true
        DialogHeading(if (album) "Add to playlists" else "Add to playlist", target?.title.orEmpty())
        error?.let {
            DialogError(it, modifier = Modifier.heightIn(max = 116.dp).verticalScroll(rememberScrollState()))
        }
        if (target?.playableSongs.isNullOrEmpty()) {
            if (busy) {
                DialogSectionHeading("Loading album…")
                CircularProgressIndicator(color = DesktopAccent, modifier = Modifier.padding(22.dp).size(20.dp))
            }
            TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.padding(horizontal = 14.dp)) {
                Text(DesktopStrings["cancel", "Cancel"])
            }
            return@DesktopDialogFrame
        }

        DialogAction(BitChordIcons.Plus, "New playlist", enabled = !busy) { creating = true }
        val eligibleAccount = if (signedIn && canUseAccount) accountPlaylists else emptyList()
        val account = remember(eligibleAccount, query) {
            filterDesktopPlaylists(eligibleAccount, query, UserPlaylist::title)
        }
        val local = remember(localPlaylists, query) {
            filterDesktopPlaylists(localPlaylists, query, DesktopPlaylist::title)
        }
        val available = eligibleAccount.map { DesktopPlaylistDestination(it.playlistId, account = true) } +
            localPlaylists.map { DesktopPlaylistDestination(it.id, account = false) }
        val pending = selected.filter { it in available && it !in completed }

        fun pick(destination: DesktopPlaylistDestination) {
            if (album) {
                selected = if (destination in selected) selected - destination else selected + destination
            } else {
                onPick(listOf(destination))
            }
        }

        if (album) {
            val accountCount = target?.accountTracks?.size ?: 0
            val playableCount = target?.playableSongs?.size ?: 0
            val total = target?.songs?.size ?: 0
            if (total > playableCount) DialogSectionHeading("${total - playableCount} unavailable tracks will be skipped")
            if (signedIn && accountCount < playableCount) {
                DialogSectionHeading(
                    if (accountCount == 0) "These tracks can be added to playlists on this computer"
                    else "$accountCount of $playableCount tracks can be added to your YouTube Music playlists",
                )
            }
        } else if (signedIn && !canUseAccount) {
            DialogSectionHeading("This track can be added to playlists on this computer")
        }
        if (available.isNotEmpty()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(DesktopStrings["find_a_playlist", "Find a playlist"]) },
                leadingIcon = { Icon(BitChordIcons.Search, null) },
                trailingIcon = if (query.isEmpty()) null else ({
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Rounded.Close, DesktopStrings["clear_search", "Clear search"])
                    }
                }),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp),
            )
        }
        if (account.isEmpty() && local.isEmpty()) {
            Text(
                when {
                    available.isNotEmpty() -> DesktopStrings["no_matching_playlists", "No matching playlists"]
                    !signedIn -> "No playlists on this computer yet. Sign in to see the ones on your account."
                    else -> "No playlists yet. Create one to keep these tracks."
                },
                style = MaterialTheme.typography.bodySmall,
                color = DesktopSecondary,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
            )
        } else {
            LazyColumn(
                state = playlistListState,
                modifier = Modifier.heightIn(max = 290.dp).desktopTopScrollFade(playlistListState),
            ) {
                if (account.isNotEmpty()) {
                    item(key = "heading:account") { DialogSectionHeading("On your account") }
                    items(account.size, key = { "account:${account[it].playlistId}" }) { index ->
                        val playlist = account[index]
                        val destination = DesktopPlaylistDestination(playlist.playlistId, account = true)
                        val done = destination in completed
                        DialogAction(
                            icon = BitChordIcons.Queue,
                            label = playlist.title,
                            subtitle = if (done) "Added" else playlist.subtitle,
                            enabled = !busy && !done,
                            checked = (done || destination in selected).takeIf { album },
                        ) { pick(destination) }
                    }
                }
                if (local.isNotEmpty()) {
                    item(key = "heading:local") { DialogSectionHeading("On this computer") }
                    items(local.size, key = { "local:${local[it].id}" }) { index ->
                        val playlist = local[index]
                        val destination = DesktopPlaylistDestination(playlist.id, account = false)
                        val done = destination in completed
                        DialogAction(
                            icon = BitChordIcons.Queue,
                            label = playlist.title,
                            subtitle = if (done) "Added" else "${playlist.songs.size} songs",
                            enabled = !busy && !done,
                            checked = (done || destination in selected).takeIf { album },
                        ) { pick(destination) }
                    }
                }
            }
        }
        if (album) {
            DialogButtons(
                busy = busy,
                confirmLabel = if (pending.isEmpty()) "Add" else "Add to ${pending.size}",
                confirmEnabled = pending.isNotEmpty(),
                onConfirm = { onPick(pending) },
                onDismiss = onDismiss,
            )
        } else if (busy) {
            CircularProgressIndicator(color = DesktopAccent, modifier = Modifier.padding(22.dp).size(20.dp))
        }
        Spacer(Modifier.height(6.dp))
    }
}

/** Name and visibility, and nothing else. */
@Composable
private fun NewPlaylistForm(
    onBack: (() -> Unit)?,
    signedIn: Boolean,
    busy: Boolean,
    error: String?,
    onCreate: (String, PlaylistPrivacy) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var privacy by remember { mutableStateOf(PlaylistPrivacy.PRIVATE) }
    val focusRequester = remember { FocusRequester() }
    // The form exists to be typed into; opening it already focused saves the click that would
    // otherwise always follow.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val submit = { if (name.isNotBlank() && !busy) onCreate(name.trim(), privacy) }

    Row(
        Modifier.fillMaxWidth().padding(start = if (onBack != null) 8.dp else 22.dp, end = 22.dp, top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        onBack?.let {
            IconButton(onClick = it) {
                Icon(Icons.Rounded.ArrowBack, DesktopStrings["back", "Back"], tint = DesktopForeground)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                DesktopStrings["new_playlist", "New playlist"],
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = DesktopForeground,
            )
            Text(
                if (signedIn) "Saved to your YouTube Music account" else "Saved on this computer",
                style = MaterialTheme.typography.bodySmall,
                color = DesktopSecondary,
            )
        }
    }
    error?.let { DialogError(it) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 16.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(DesktopForeground.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (name.isEmpty()) {
                Text(DesktopStrings["playlist_name", "Playlist name"], style = MaterialTheme.typography.bodyLarge, color = DesktopSecondary)
            }
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = DesktopForeground),
                cursorBrush = SolidColor(DesktopAccent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        }
        if (name.isNotEmpty()) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).clickable { name = "" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, DesktopStrings["clear_name", "Clear name"], tint = DesktopSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }

    if (signedIn) {
        DialogSectionHeading("Who can see it")
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlaylistPrivacy.entries.forEach { option ->
                PrivacyPill(option.icon, option.label, option == privacy) { privacy = option }
            }
        }
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(color = DesktopAccent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(12.dp))
        }
        TextButton(onClick = submit, enabled = name.isNotBlank() && !busy) {
            Text(DesktopStrings["create_playlist", "Create playlist"], color = if (name.isBlank() || busy) DesktopSecondary else DesktopAccent)
        }
    }
}

/** A name for something that already exists — renaming a playlist. */
@Composable
internal fun DesktopRenamePlaylistDialog(
    current: String,
    busy: Boolean,
    error: String?,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(current) { mutableStateOf(current) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val submit = { if (name.isNotBlank() && name.trim() != current && !busy) onRename(name.trim()) }

    DesktopDialogFrame(onDismiss = onDismiss) {
        DialogHeading("Rename playlist", current)
        error?.let { DialogError(it) }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 14.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(DesktopForeground.copy(alpha = 0.08f))
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = DesktopForeground),
                cursorBrush = SolidColor(DesktopAccent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        }
        DialogButtons(
            busy = busy,
            confirmLabel = DesktopStrings["rename", "Rename"],
            confirmEnabled = name.isNotBlank() && name.trim() != current,
            onConfirm = submit,
            onDismiss = onDismiss,
        )
    }
}

/** The one destructive playlist action, so the one that asks first. */
@Composable
internal fun DesktopDeletePlaylistDialog(
    title: String,
    local: Boolean,
    busy: Boolean,
    error: String?,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    DesktopDialogFrame(onDismiss = onDismiss) {
        DialogHeading(
            "Delete playlist",
            if (local) "Delete “$title” from this computer? Your downloaded files will be kept."
            else "Delete “$title” from your YouTube Music account and every connected device?",
        )
        error?.let { DialogError(it) }
        DialogButtons(
            busy = busy,
            confirmLabel = DesktopStrings["delete", "Delete"],
            confirmEnabled = true,
            onConfirm = onDelete,
            onDismiss = onDismiss,
        )
    }
}

@Composable
internal fun DesktopRemovePlaylistOccurrenceDialog(
    title: String,
    song: Song?,
    busy: Boolean,
    error: String?,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    DesktopDialogFrame(onDismiss = onDismiss) {
        DialogHeading("Remove from playlist", "Remove “${song?.title.orEmpty()}” from “$title”?")
        error?.let { DialogError(it) }
        DialogButtons(
            busy = busy,
            confirmLabel = "Remove",
            confirmEnabled = song != null,
            onConfirm = onRemove,
            onDismiss = onDismiss,
        )
    }
}

@Composable
internal fun DesktopPlaylistReorderPage(
    title: String,
    order: DesktopPlaylistOrder?,
    busy: Boolean,
    error: String?,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val original = order?.entries.orEmpty()
    var songs by remember(order) { mutableStateOf(original) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var selectedEntry by remember(order) { mutableStateOf<String?>(null) }
    var destination by remember(order) { mutableStateOf("") }
    var dragPosition by remember(order) { mutableStateOf<Float?>(null) }
    val selectedIndex = songs.indexOfFirst { it.setVideoId == selectedEntry }
    val targetIndex = (destination.toIntOrNull() ?: 0) - 1
    val handleWidth = with(LocalDensity.current) { 48.dp.toPx() }

    fun move(from: Int, to: Int) {
        if (from !in songs.indices || to !in songs.indices || from == to) return
        songs = songs.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun moveToPointer(pointer: Float) {
        val row = listState.layoutInfo.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - pointer) }
        val from = songs.indexOfFirst { it.setVideoId == selectedEntry }
        if (row != null && from >= 0 && from != row.index) {
            val first = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            move(from, row.index)
            // Keep the viewport at the same position when its first row moves.
            listState.requestScrollToItem(first, offset)
        }
    }

    LaunchedEffect(dragPosition != null, order) {
        if (dragPosition == null) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val pointer = dragPosition ?: break
            val layout = listState.layoutInfo
            val edge = minOf(handleWidth, (layout.viewportEndOffset - layout.viewportStartOffset) / 3f)
            if (edge <= 0) continue
            val speed = when {
                pointer < layout.viewportStartOffset + edge -> (pointer - layout.viewportStartOffset - edge) / edge
                pointer > layout.viewportEndOffset - edge -> (pointer - layout.viewportEndOffset + edge) / edge
                else -> 0f
            }
            if (speed != 0f) {
                listState.scrollBy(speed.coerceIn(-1f, 1f) * edge / 4f)
                moveToPointer(pointer)
            }
        }
    }

    Column(modifier.fillMaxSize().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Reorder playlist", style = MaterialTheme.typography.headlineSmall)
                Text(title, color = DesktopSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
            TextButton(
                onClick = { onSave(songs.map { requireNotNull(it.setVideoId) }) },
                enabled = !busy && dragPosition == null && songs != original,
            ) { Text("Done") }
        }
        error?.let { DialogError(it) }
        if (order == null) {
            if (error == null) Text("Loading the complete playlist…", color = DesktopSecondary)
        } else {
            Text("Drag a handle or use the move buttons.", color = DesktopSecondary, modifier = Modifier.padding(vertical = 8.dp))
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth()
                    // The list owns the gesture because rows may leave the viewport while scrolling.
                    .pointerInput(order, busy, handleWidth) {
                        if (busy) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (down.position.x >= handleWidth) return@awaitEachGesture
                            val row = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                down.position.y >= it.offset && down.position.y < it.offset + it.size
                            } ?: return@awaitEachGesture
                            val entry = row.key as String
                            val beforeDrag = songs
                            try {
                                while (true) {
                                    // A handle owns vertical motion before the LazyColumn sees it.
                                    val change = awaitPointerEvent(PointerEventPass.Initial).changes
                                        .firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) return@awaitEachGesture
                                    if (dragPosition == null) {
                                        if (abs(change.position.y - down.position.y) < viewConfiguration.touchSlop) continue
                                        selectedEntry = entry
                                    }
                                    change.consume()
                                    dragPosition = change.position.y
                                    moveToPointer(change.position.y)
                                }
                                songs = beforeDrag
                            } finally {
                                dragPosition = null
                                destination = (songs.indexOfFirst { it.setVideoId == entry } + 1).toString()
                            }
                        }
                    },
                state = listState,
            ) {
                itemsIndexed(songs, key = { _, song -> requireNotNull(song.setVideoId) }) { index, song ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (song.setVideoId == selectedEntry) DesktopForeground.copy(alpha = 0.08f) else Color.Transparent)
                            .clickable(enabled = !busy) {
                                selectedEntry = song.setVideoId
                                destination = (index + 1).toString()
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.DragHandle, "Drag ${song.title}", tint = DesktopSecondary)
                        }
                        Text(
                            "${index + 1}. ${song.title} · ${song.artist}",
                            color = DesktopForeground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(enabled = !busy && index > 0, onClick = { move(index, index - 1) }) {
                            Icon(Icons.Rounded.KeyboardArrowUp, "Move ${song.title} up")
                        }
                        IconButton(enabled = !busy && index < songs.lastIndex, onClick = { move(index, index + 1) }) {
                            Icon(Icons.Rounded.KeyboardArrowDown, "Move ${song.title} down")
                        }
                    }
                }
            }
            run {
                val canMove = !busy && dragPosition == null && selectedIndex >= 0 &&
                    targetIndex in songs.indices && targetIndex != selectedIndex
                val jump = {
                    if (canMove) {
                        move(selectedIndex, targetIndex)
                        scope.launch { listState.scrollToItem(targetIndex) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = destination,
                        onValueChange = { destination = it.filter(Char::isDigit).take(5) },
                        label = { Text("Position") },
                        enabled = !busy && selectedIndex >= 0,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { jump() }),
                        modifier = Modifier.width(108.dp),
                    )
                    TextButton(onClick = jump, enabled = canMove) { Text("Move") }
                }
            }
        }
    }
}

@Composable
private fun DesktopDialogFrame(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        val shape = RoundedCornerShape(20.dp)
        Box(
            Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .clip(shape)
                .desktopBarGlass(shape),
        ) {
            // Swallows clicks that land on the panel's own background so they do not reach the
            // scrim behind it and dismiss what was just clicked.
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            )
            Column(Modifier.padding(bottom = 8.dp)) { content() }
        }
    }
}

@Composable
private fun DialogHeading(title: String, subtitle: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = DesktopForeground,
        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 20.dp),
    )
    if (subtitle.isNotBlank()) {
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = DesktopSecondary,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 6.dp),
        )
    }
}

@Composable
private fun DialogSectionHeading(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = DesktopSecondary,
        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun DialogError(message: String, modifier: Modifier = Modifier) {
    Text(
        message,
        style = MaterialTheme.typography.bodySmall,
        color = DesktopAccent,
        modifier = modifier.padding(start = 22.dp, end = 22.dp, top = 10.dp),
    )
}

@Composable
private fun DialogAction(
    icon: ImageVector,
    label: String,
    subtitle: String = "",
    enabled: Boolean = true,
    checked: Boolean? = null,
    onClick: () -> Unit,
) {
    val action = if (checked == null) Modifier.clickable(enabled = enabled, onClick = onClick)
    else Modifier.toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onClick() })
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(action)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (checked == null) {
            Icon(icon, null, tint = DesktopForeground.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
        } else {
            Checkbox(checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = DesktopForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = DesktopSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DialogButtons(
    busy: Boolean,
    confirmLabel: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(color = DesktopAccent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(12.dp))
        }
        TextButton(onClick = onDismiss, enabled = !busy) { Text(DesktopStrings["cancel", "Cancel"], color = DesktopSecondary) }
        TextButton(onClick = onConfirm, enabled = confirmEnabled && !busy) {
            Text(confirmLabel, color = if (confirmEnabled && !busy) DesktopAccent else DesktopSecondary)
        }
    }
}

@Composable
private fun PrivacyPill(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) DesktopAccent.copy(alpha = 0.22f) else DesktopForeground.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            null,
            tint = if (selected) DesktopAccent else DesktopSecondary,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) DesktopForeground else DesktopSecondary,
        )
    }
}

private val PlaylistPrivacy.icon: ImageVector
    get() = when (this) {
        PlaylistPrivacy.PRIVATE -> Icons.Rounded.Lock
        PlaylistPrivacy.UNLISTED -> Icons.Rounded.Link
        PlaylistPrivacy.PUBLIC -> Icons.Rounded.Public
    }
