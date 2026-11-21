package com.music.bitchord.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopSettingsCategoriesTest {
    @Test
    fun everySectionBelongsToExactlyOneCategory() {
        val sections = DesktopSettingsCategory.entries.flatMap { it.sections }
        assertEquals(DesktopSettingsSection.entries.toSet(), sections.toSet())
        assertEquals(DesktopSettingsSection.entries.size, sections.size)
    }

    @Test
    fun categoryPagesAreDistinctFromExistingSettingsDestinations() {
        val categories = DesktopSettingsCategory.entries
        assertEquals(categories.size, categories.map { it.page }.toSet().size)
        categories.forEach { assertEquals(it, DesktopSettingsCategory.forPage(it.page)) }
        listOf(
            DesktopSettingsPage.MAIN,
            DesktopSettingsPage.EQUALIZER,
            DesktopSettingsPage.AUDIO_OUTPUT,
            DesktopSettingsPage.LISTEN_TOGETHER,
            DesktopSettingsPage.LYRICS_SOURCES,
            DesktopSettingsPage.TRANSLATION_LANGUAGE,
            DesktopSettingsPage.SPOTIFY_CANVAS,
            DesktopSettingsPage.INTEGRATIONS,
            DesktopSettingsPage.LICENSES,
        ).forEach { assertNull(DesktopSettingsCategory.forPage(it), it.name) }
    }

    @Test
    fun backingOutOfAnAudioOutputVisitRestoresItsCategoryBeforeTheIndex() {
        val main = DesktopSettingsPage.MAIN
        val audio = DesktopSettingsCategory.AUDIO.page
        val output = DesktopSettingsPage.AUDIO_OUTPUT
        assertEquals(audio, settingsParentForBack(output, listOf(main, audio)))
        assertEquals(main, settingsParentForBack(audio, listOf(main)))
        assertNull(settingsParentForBack(main, emptyList()))
        assertNull(settingsParentForBack(output, emptyList()))
    }
}
