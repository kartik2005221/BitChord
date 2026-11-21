package com.music.bitchord.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DesktopPinnedPlaylistRow(
    pin: DesktopPlaylistPin,
    selected: Boolean,
    collapsed: Boolean,
    onOpen: () -> Unit,
    onUnpin: () -> Unit,
) {
    val openModifier = Modifier.clip(RoundedCornerShape(7.dp))
        .background(if (selected) DesktopTheme.palette.navSelectedSurface else Color.Transparent)
        .desktopHoverFill(alpha = if (selected) 0.035f else 0.075f)
        .clickable(role = Role.Button, onClick = onOpen)
    val artwork: @Composable () -> Unit = {
        DesktopArtwork(
            pin.artworkUrl,
            modifier = Modifier.size(28.dp).clip(RoundedCornerShape(5.dp)),
            px = 64,
            scope = ArtworkPrivacyScope.BROWSING,
            fallback = {
                Text(pin.title.trim().take(1).uppercase(), color = Color.White, style = MaterialTheme.typography.labelMedium)
            },
        )
    }

    if (collapsed) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text(pin.title) } },
            state = rememberTooltipState(),
        ) {
            Box(
                Modifier.fillMaxWidth().height(42.dp).then(openModifier)
                    .semantics { contentDescription = pin.title },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Box(
                        Modifier.align(Alignment.CenterStart).width(3.dp).height(18.dp)
                            .background(DesktopTheme.palette.navAccent),
                    )
                }
                artwork()
            }
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).height(42.dp).then(openModifier).padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                artwork()
                Spacer(Modifier.width(8.dp))
                Text(
                    pin.title,
                    color = if (selected) DesktopForeground else DesktopSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text("Unpin playlist") } },
                state = rememberTooltipState(),
            ) {
                IconButton(onClick = onUnpin, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.PushPin, "Unpin ${pin.title}", tint = DesktopSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
