package com.music.bitchord.desktop

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.model.Song
import java.security.MessageDigest
import java.util.HexFormat
import java.util.UUID

private fun DesktopPlaylistPinScope.quickAddKey(): String {
    val hash = MessageDigest.getInstance("SHA-256").digest(downloadOwner.toByteArray())
    return "quick_add_" + HexFormat.of().formatHex(hash)
}

internal fun DesktopPersistence.quickAddTarget(scope: DesktopPlaylistPinScope): DesktopPlaylistDestination? {
    val stored = string(scope.quickAddKey())
    val (provider, id) = stored.split(':', limit = 2).takeIf { it.size == 2 } ?: return null
    if (id.isBlank() || provider !in setOf("local", "youtube")) return null
    return DesktopPlaylistDestination(id, account = provider == "youtube")
}

internal fun DesktopPersistence.saveQuickAddTarget(scope: DesktopPlaylistPinScope, target: DesktopPlaylistDestination?) {
    val value = target?.let { "${if (it.account) "youtube" else "local"}:${it.id}" }.orEmpty()
    saveString(scope.quickAddKey(), value)
}

internal fun DesktopPlaylist.quickAdd(song: Song): Pair<DesktopPlaylist, String>? {
    if (songs.any { it.videoId == song.videoId }) return null
    val entry = "desktop-quick-add-${UUID.randomUUID()}"
    return copy(songs = songs + song.copy(setVideoId = entry)) to entry
}

internal fun DesktopPlaylist.undoQuickAdd(entryId: String, videoId: String): DesktopPlaylist? {
    val index = songs.indexOfFirst { it.setVideoId == entryId && it.videoId == videoId }
    if (index < 0) return null
    return copy(songs = songs.toMutableList().apply { removeAt(index) })
}

internal data class DesktopQuickAddOption(val destination: DesktopPlaylistDestination, val title: String)

internal data class DesktopQuickAddReceipt(
    val target: DesktopQuickAddOption,
    val songId: String,
    val entryId: String?,
    val session: Pair<DesktopPlaylistPinScope, Int>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DesktopQuickAddButtons(
    targetName: String?,
    alreadyAdded: Boolean,
    busy: Boolean,
    onAdd: () -> Unit,
    onPick: () -> Unit,
    tint: Color = DesktopSecondary,
) {
    val label = when {
        targetName == null -> "Choose a quick-add playlist"
        alreadyAdded -> "Already in $targetName"
        else -> "Add to $targetName"
    }
    Row {
        QuickAddTooltip(label) {
            IconButton(onClick = onAdd, enabled = !busy && !alreadyAdded, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (alreadyAdded) Icons.Rounded.Check else Icons.Rounded.Add,
                    label,
                    tint = if (alreadyAdded) DesktopAccent else tint,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        QuickAddTooltip("Choose quick-add playlist") {
            IconButton(onClick = onPick, enabled = !busy, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.KeyboardArrowUp, "Choose quick-add playlist", tint = tint, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddTooltip(label: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        focusable = false,
        content = content,
    )
}
