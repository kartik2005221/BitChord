package com.music.bitchord.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** Follow the OS until the listener explicitly picks a theme. */
internal enum class DesktopThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark");

    companion object {
        fun fromStored(value: String): DesktopThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

/**
 * One persisted theme selection, with snapshot state so reads of palette tokens inside Compose
 * automatically invalidate on a live change. No window recreation or account-state reset.
 */
internal object DesktopTheme {
    private const val KEY = "desktop_theme_mode"
    private const val KEY_PITCH_BLACK = "desktop_pitch_black"
    private const val KEY_APPEARANCE_MIGRATION = "desktop_appearance_migration"
    private const val APPEARANCE_MIGRATION_VERSION = 1

    private val persistence = DesktopPersistence()

    init {
        migrateAppearance(persistence)
    }

    var mode: DesktopThemeMode by mutableStateOf(
        DesktopThemeMode.fromStored(persistence.string(KEY, DesktopThemeMode.SYSTEM.name)),
    )
        private set

    private var systemDark: Boolean by mutableStateOf(DesktopSystemTheme.isDark())

    /** Saved independently of the theme mode; migration enables it once and users may opt out. */
    var pitchBlack: Boolean by mutableStateOf(persistence.boolean(KEY_PITCH_BLACK, false))
        private set

    /** Effective theme. Use this for *all* UI decisions, never mode == LIGHT/DARK. */
    val isDark: Boolean get() = resolveDark(mode, systemDark)
    val isLight: Boolean get() = !isDark
    val palette: DesktopThemePalette get() = resolvePalette(mode, systemDark, pitchBlack)

    /** Pitch black is offered for Dark and System modes, but never affects effective Light mode. */
    internal fun supportsPitchBlack(choice: DesktopThemeMode): Boolean = choice != DesktopThemeMode.LIGHT

    /** One-time migration: preserve theme mode and seed the new default without clobbering later choices. */
    internal fun migrateAppearance(persistence: DesktopPersistence) {
        val version = persistence.int(KEY_APPEARANCE_MIGRATION, 0)
        val migration = appearanceMigration(version) ?: return
        persistence.saveBoolean(KEY_PITCH_BLACK, migration.pitchBlack)
        persistence.saveInt(KEY_APPEARANCE_MIGRATION, migration.version)
    }

    internal data class AppearanceMigration(val version: Int, val pitchBlack: Boolean)

    internal fun appearanceMigration(currentVersion: Int): AppearanceMigration? =
        if (currentVersion >= APPEARANCE_MIGRATION_VERSION) null
        else AppearanceMigration(APPEARANCE_MIGRATION_VERSION, pitchBlack = true)

    internal fun resolvePalette(
        choice: DesktopThemeMode,
        osDark: Boolean,
        pitchBlack: Boolean,
    ): DesktopThemePalette = when {
        !resolveDark(choice, osDark) -> LIGHT_PALETTE
        supportsPitchBlack(choice) && pitchBlack -> PITCH_BLACK_PALETTE
        else -> DARK_PALETTE
    }

    internal fun resolveDark(choice: DesktopThemeMode, osDark: Boolean): Boolean = when (choice) {
        DesktopThemeMode.SYSTEM -> osDark
        DesktopThemeMode.LIGHT -> false
        DesktopThemeMode.DARK -> true
    }

    /** Called from the main Compose window after sampling the OS on a worker dispatcher. */
    fun updateSystemDark(value: Boolean) {
        if (systemDark != value) systemDark = value
    }

    fun selectMode(value: DesktopThemeMode) {
        if (mode == value) return
        persistence.saveString(KEY, value.name)
        mode = value
    }

    fun updatePitchBlack(enabled: Boolean) {
        if (pitchBlack == enabled) return
        persistence.saveBoolean(KEY_PITCH_BLACK, enabled)
        pitchBlack = enabled
    }
}

/** Semantic desktop tokens; artwork overlays may deliberately keep white-on-black treatment. */
internal data class DesktopThemePalette(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val glass: Color,
    val glassStrong: Color,
    val barGlass: Color,
    val barEdge: Color,
    val accent: Color,
    val onAccent: Color,
    val foreground: Color,
    val secondary: Color,
    val divider: Color,
    val destructive: Color,
    val cardFill: Color,
    val cardInsetFill: Color,
    val cardEdge: Color,
    val rowHover: Color,
    val scrim: Color,
    val searchSurface: Color,
    val topPlayerSurface: Color,
    val floatingPlayerSurface: Color,
    val subtleFill: Color,
    val stripedRow: Color,
    val navAccent: Color,
    val navSelectedSurface: Color,
    val sidebarSurface: Color,
    val chromeSurface: Color,
    val lyricsSurface: Color,
    val replaySurface: Color,
)

internal val DARK_PALETTE = DesktopThemePalette(
    background = Color(0xFF0B192C),
    surface = Color(0xFF0B192C),
    surfaceRaised = Color(0xFF1E3E62),
    glass = Color(0x661E3E62),
    glassStrong = Color(0xCC0B192C),
    barGlass = Color(0xFF152E4B),
    barEdge = Color(0x1AFFFFFF),
    accent = Color(0xFFFF6500),
    onAccent = Color.Black,
    foreground = Color.White,
    secondary = Color(0xFFAFBED0),
    divider = Color(0xFF294765),
    destructive = Color(0xFFFF453A),
    cardFill = Color(0xFF10243B),
    cardInsetFill = Color(0xFF1E3E62),
    cardEdge = Color(0x1FFFFFFF),
    rowHover = Color(0x1AFFFFFF),
    scrim = Color(0x8C000000),
    searchSurface = Color(0xFF1E3E62),
    topPlayerSurface = Color(0xFF1A3554),
    floatingPlayerSurface = Color(0xFF142C47),
    subtleFill = Color(0x14FFFFFF),
    stripedRow = Color(0x0AFFFFFF),
    navAccent = Color(0xFFFF6500),
    navSelectedSurface = Color(0xFF1E3E62),
    sidebarSurface = Color(0xFF0B192C),
    chromeSurface = Color(0xFF0B192C),
    lyricsSurface = Color(0xFF0B192C),
    replaySurface = Color(0xFF0B192C),
)

/** Pure-black base surfaces, preserving intentional card, control, artwork, and accent contrast. */
internal val PITCH_BLACK_PALETTE = DARK_PALETTE.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceRaised = Color(0xFF10243B),
    glass = Color(0xCC10243B),
    glassStrong = Color(0xF20B192C),
    barGlass = Color(0xFF142C47),
    searchSurface = Color(0xFF1E3E62),
    topPlayerSurface = Color(0xFF1A3554),
    floatingPlayerSurface = Color(0xFF10243B),
    sidebarSurface = Color(0xFF0B192C),
    chromeSurface = Color(0xFF0B192C),
    lyricsSurface = Color.Black,
    replaySurface = Color.Black,
)

/**
 * Pale blue daylight surfaces with the same navy and orange identity. Navigation uses a darker
 * orange for small icons and text; primary orange retains a black, high-contrast label.
 */
internal val LIGHT_PALETTE = DesktopThemePalette(
    background = Color(0xFFF3F7FC),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFE4EDF7),
    glass = Color(0xDDEFF1F5),
    glassStrong = Color(0xF4F7F8FC),
    barGlass = Color(0xE8E7EBF1),
    barEdge = Color(0x251D2636),
    accent = Color(0xFFFF6500),
    onAccent = Color.Black,
    foreground = Color(0xFF0B192C),
    secondary = Color(0xFF3B526A),
    divider = Color(0xFFC9D8E8),
    destructive = Color(0xFFBE3545),
    cardFill = Color(0xFFFFFFFF),
    cardInsetFill = Color(0xFFE9F1F9),
    cardEdge = Color(0x241D2636),
    rowHover = Color(0x1E25324D),
    scrim = Color(0x750D0E13),
    searchSurface = Color(0xFFDBE8F5),
    topPlayerSurface = Color(0xFFDCE9F6),
    floatingPlayerSurface = Color(0xFFE1EDF8),
    subtleFill = Color(0x111D2636),
    stripedRow = Color(0x0D1D2636),
    navAccent = Color(0xFFA33A00),
    navSelectedSurface = Color(0xFFD6E5F4),
    sidebarSurface = Color(0xFFE5EFF9),
    chromeSurface = Color(0xFFEDF4FB),
    lyricsSurface = Color(0xFFEAF2FA),
    replaySurface = Color(0xFFF3F7FC),
)
