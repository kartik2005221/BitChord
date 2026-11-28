package com.music.bitchord.desktop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Desktop-native vector equivalents of the BitChord Android/iOS context menu symbols.
 * Uses Compose geometry rather than the Android-only SF Symbols package.
 * Material thumbs-up icons are used directly for Like.
 */
internal object DesktopPhoneMenuIcons {
    private val ink = SolidColor(Color.Black)

    val Share: ImageVector by lazy {
        ImageVector.Builder(name = "mobile_share", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f).apply {
            path(stroke = ink, strokeLineWidth = 2.1f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(12f, 15f)
                lineTo(12f, 3f)
                moveTo(7.5f, 7.5f)
                lineTo(12f, 3f)
                lineTo(16.5f, 7.5f)
                moveTo(7f, 10f)
                lineTo(5.8f, 10f)
                curveTo(4.4f, 10f, 4f, 11f, 4f, 12.5f)
                lineTo(4f, 19f)
                curveTo(4f, 20.5f, 4.8f, 21f, 6.2f, 21f)
                lineTo(17.8f, 21f)
                curveTo(19.2f, 21f, 20f, 20.5f, 20f, 19f)
                lineTo(20f, 12.5f)
                curveTo(20f, 11f, 19.6f, 10f, 18.2f, 10f)
                lineTo(17f, 10f)
            }
        }.build()
    }

    val Info: ImageVector by lazy {
        ImageVector.Builder(name = "mobile_info", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f).apply {
            path(stroke = ink, strokeLineWidth = 2.1f, strokeLineCap = StrokeCap.Round) {
                moveTo(12f, 2.5f)
                curveTo(6.7f, 2.5f, 2.5f, 6.7f, 2.5f, 12f)
                curveTo(2.5f, 17.3f, 6.7f, 21.5f, 12f, 21.5f)
                curveTo(17.3f, 21.5f, 21.5f, 17.3f, 21.5f, 12f)
                curveTo(21.5f, 6.7f, 17.3f, 2.5f, 12f, 2.5f)
                moveTo(12f, 10.5f)
                lineTo(12f, 16.5f)
            }
            path(fill = ink) {
                moveTo(12f, 7f)
                lineTo(12.01f, 7f)
                close()
            }
        }.build()
    }
}
