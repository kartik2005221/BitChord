package com.music.bitchord.desktop

/** Nested Settings visits are synchronous; ordinary destinations use the window history. */
internal fun settingsParentForBack(
    current: DesktopSettingsPage?,
    parents: List<DesktopSettingsPage>,
): DesktopSettingsPage? = if (current != null && current != DesktopSettingsPage.MAIN) parents.lastOrNull() else null

internal enum class DesktopHistoryTransition { UNCHANGED, RESTORED, RECORD, SETTINGS_CHILD }

/** Shared by the snapshot collector and regression tests; nested Settings never enters window history. */
internal fun desktopHistoryTransition(
    samePlace: Boolean,
    restoring: Boolean,
    before: DesktopSettingsPage?,
    after: DesktopSettingsPage?,
): DesktopHistoryTransition = when {
    samePlace -> DesktopHistoryTransition.UNCHANGED
    restoring -> DesktopHistoryTransition.RESTORED
    before != null && after != null -> DesktopHistoryTransition.SETTINGS_CHILD
    else -> DesktopHistoryTransition.RECORD
}

internal fun settingsBackLabel(parent: DesktopSettingsPage?): String = when (parent) {
    DesktopSettingsPage.MAIN -> "Back to Settings"
    DesktopSettingsPage.INTEGRATIONS -> "Back to Integrations"
    null -> "Back"
    else -> "Back"
}
