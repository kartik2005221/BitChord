package com.music.bitchord.desktop

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

internal data class DesktopPageRefresh(val loading: Boolean, val run: () -> Unit)

internal fun <T, K> desktopBackIndex(history: List<T>, current: K, keyOf: (T) -> K): Int =
    history.indexOfLast { keyOf(it) != current }

@Composable
internal fun DesktopSidebarActions(
    collapsed: Boolean,
    backLabel: String,
    canGoBack: Boolean,
    refresh: DesktopPageRefresh?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    val buttons: @Composable () -> Unit = {
        DesktopSidebarAction(
            Icons.AutoMirrored.Rounded.ArrowBack,
            backLabel,
            tooltip = "$backLabel (Alt+Left)",
            enabled = canGoBack,
            onClick = onBack,
        )
        val label = DesktopStrings["refresh", "Refresh"]
        DesktopSidebarAction(
            Icons.Rounded.Refresh,
            label,
            tooltip = if (refresh == null) "Refresh unavailable on this page" else label,
            enabled = refresh?.loading == false,
            loading = refresh?.loading == true,
            onClick = onRefresh,
        )
    }
    val actionGroup = Modifier.fillMaxWidth()
        .onFocusChanged { onFocusChanged(it.hasFocus) }
        .focusGroup()
    if (collapsed) {
        Column(actionGroup, horizontalAlignment = Alignment.CenterHorizontally) { buttons() }
    } else {
        Row(
            actionGroup.height(40.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) { buttons() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DesktopSidebarAction(
    icon: ImageVector,
    label: String,
    tooltip: String,
    enabled: Boolean,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState(),
    ) {
        IconButton(
            modifier = Modifier.size(40.dp).semantics {
                contentDescription = label
                if (loading) stateDescription = "Refreshing"
            },
            enabled = enabled,
            onClick = onClick,
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}
