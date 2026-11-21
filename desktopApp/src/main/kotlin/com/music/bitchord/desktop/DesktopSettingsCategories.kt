package com.music.bitchord.desktop

/** Desktop keeps Android's section names where they still describe the available controls. */
internal enum class DesktopSettingsSection {
    ACCOUNT,
    LISTEN_TOGETHER,
    SOURCES,
    AUDIO_QUALITY,
    DOWNLOADS,
    OUTPUT_PRECISION,
    AUDIO_OUTPUT,
    PLAYBACK,
    DESKTOP_PLAYBACK,
    APPEARANCE,
    PLAYER_APPEARANCE,
    LYRICS,
    LOCAL_MUSIC,
    STORAGE,
    YOUR_DATA,
    WINDOW_BEHAVIOR,
    LANGUAGE,
    ADVANCED,
    APP_UPDATES,
    ABOUT,
}

internal enum class DesktopSettingsCategory(
    val page: DesktopSettingsPage,
    val titleKey: String,
    val fallbackTitle: String,
    val description: String,
    vararg sections: DesktopSettingsSection,
) {
    ACCOUNT(
        DesktopSettingsPage.ACCOUNT_SETTINGS, "account_integrations", "Account & integrations",
        "Connected services and Listen together",
        DesktopSettingsSection.ACCOUNT, DesktopSettingsSection.LISTEN_TOGETHER,
    ),
    AUDIO(
        DesktopSettingsPage.AUDIO_SETTINGS, "d_audio_and_sources", "Audio & sources",
        "Streaming quality, sources and output devices",
        DesktopSettingsSection.AUDIO_QUALITY, DesktopSettingsSection.SOURCES,
        DesktopSettingsSection.AUDIO_OUTPUT, DesktopSettingsSection.OUTPUT_PRECISION,
    ),
    PLAYBACK(
        DesktopSettingsPage.PLAYBACK_SETTINGS, "playback", "Playback",
        "Automix, equalizer and queue behavior",
        DesktopSettingsSection.PLAYBACK, DesktopSettingsSection.DESKTOP_PLAYBACK,
    ),
    APPEARANCE(
        DesktopSettingsPage.APPEARANCE_SETTINGS, "appearance", "Appearance",
        "Theme, fonts, UI scale and player artwork",
        DesktopSettingsSection.APPEARANCE, DesktopSettingsSection.PLAYER_APPEARANCE,
    ),
    LYRICS(
        DesktopSettingsPage.LYRICS_SETTINGS, "open_lyrics", "Lyrics",
        "Sync, sources and translation",
        DesktopSettingsSection.LYRICS,
    ),
    LIBRARY(
        DesktopSettingsPage.LIBRARY_SETTINGS, "d_library_and_downloads", "Library & downloads",
        "Download quality, local music, storage and Replay",
        DesktopSettingsSection.DOWNLOADS, DesktopSettingsSection.LOCAL_MUSIC,
        DesktopSettingsSection.STORAGE, DesktopSettingsSection.YOUR_DATA,
    ),
    DESKTOP(
        DesktopSettingsPage.DESKTOP_SETTINGS, "d_desktop_settings", "Desktop",
        "System tray, language and diagnostics",
        DesktopSettingsSection.WINDOW_BEHAVIOR, DesktopSettingsSection.LANGUAGE,
        DesktopSettingsSection.ADVANCED,
    ),
    ABOUT(
        DesktopSettingsPage.ABOUT_SETTINGS, "about", "About",
        "Version, updates and licenses",
        DesktopSettingsSection.APP_UPDATES, DesktopSettingsSection.ABOUT,
    );

    val sections = sections.toList()
    val title get() = DesktopStrings[titleKey, fallbackTitle]

    companion object {
        fun forPage(page: DesktopSettingsPage): DesktopSettingsCategory? = entries.firstOrNull { it.page == page }
    }
}
