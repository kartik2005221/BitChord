package com.music.bitchord.desktop

import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.ui.graphics.argbPixels
import com.music.bitchord.ui.graphics.imageBitmapOf
import com.music.bitchord.ui.icons.BitChordIcons

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.decodeToImageBitmap
import java.net.URI

/**
 * Decoded artwork, kept to a budget.
 *
 * It used to be an unbounded map. Every distinct URL a session ever loaded stayed decoded for the
 * life of the process, so browsing Explore and Search for a while was hundreds of megabytes of
 * bitmaps nothing would ever ask for again — which is where the gigabyte people reported was going.
 */
internal object DesktopArtworkCache {

    /**
     * A sixth of the heap, within reason.
     *
     * Derived rather than fixed so the budget still makes sense against whatever `-Xmx` the
     * launcher was given, and clamped at both ends so a small heap still caches something and a
     * large one does not decide it may hold half a gigabyte of thumbnails.
     */
    internal val budgetBytes: Long =
        (Runtime.getRuntime().maxMemory() / 6).coerceIn(32L * 1024 * 1024, 128L * 1024 * 1024)

    /** Four bytes a pixel, which is what every format here decodes to. */
    private val images = DesktopByteBudgetLru<ImageBitmap>(budgetBytes) {
        it.width.toLong() * it.height.toLong() * 4L
    }

    /** Everything held, dropped — what the Storage settings' "Clear image cache" does. */
    fun clear() {
        images.clear()
        DesktopBlurredArtworkCache.clear()
    }

    suspend fun load(url: String?): ImageBitmap? {
        if (url.isNullOrBlank()) return null
        images.get(url)?.let { return it }
        return runCatching {
            URI(url).toURL().openConnection().apply {
                connectTimeout = 10_000
                readTimeout = 10_000
            }.getInputStream().use { stream -> stream.readBytes().decodeToImageBitmap() }
        }.getOrNull()?.also { images.put(url, it) }
    }
}

/**
 * The blurred answer to "what does this cover look like" — a blob of its own
 * colours, nothing more. Small decodes blurred once and kept per URL: at
 * [BLUR_PX] a pixel is four bytes, so a session's worth of them is a rounding
 * error next to the sharp cache above.
 */
internal object DesktopBlurredArtworkCache {

    private val images = DesktopByteBudgetLru<ImageBitmap>(16L * 1024 * 1024) {
        it.width.toLong() * it.height.toLong() * 4L
    }

    fun clear() = images.clear()

    suspend fun load(url: String?): ImageBitmap? {
        if (url.isNullOrBlank()) return null
        images.get(url)?.let { return it }
        val small = runCatching {
            URI(url).toURL().openConnection().apply {
                connectTimeout = 10_000
                readTimeout = 10_000
            }.getInputStream().use { stream -> stream.readBytes().decodeToImageBitmap() }
        }.getOrNull() ?: return null
        return small.boxBlurred(BLUR_PASSES).also { images.put(url, it) }
    }

    /** Three small box passes approximate a broad Gaussian blur at thumbnail scale. */
    private fun ImageBitmap.boxBlurred(passes: Int): ImageBitmap {
        val width = width
        val height = height
        if (width < 2 || height < 2) return this
        var source = argbPixels()
        var target = IntArray(source.size)
        val radius = (minOf(width, height) / 12).coerceAtLeast(2)

        repeat(passes) {
            boxBlurPass(source, target, width, height, radius, horizontal = true)
            boxBlurPass(target, source, width, height, radius, horizontal = false)
        }
        return imageBitmapOf(source, width, height)
    }

    private fun boxBlurPass(
        source: IntArray,
        target: IntArray,
        width: Int,
        height: Int,
        radius: Int,
        horizontal: Boolean,
    ) {
        val major = if (horizontal) width else height
        val minor = if (horizontal) height else width
        val window = radius * 2 + 1
        repeat(minor) { fixed ->
            var red = 0
            var green = 0
            var blue = 0
            fun pixel(at: Int): Int {
                val position = at.coerceIn(0, major - 1)
                return if (horizontal) source[fixed * width + position]
                else source[position * width + fixed]
            }
            for (offset in -radius..radius) {
                val color = pixel(offset)
                red += color shr 16 and 0xFF
                green += color shr 8 and 0xFF
                blue += color and 0xFF
            }
            repeat(major) { moving ->
                val index = if (horizontal) fixed * width + moving else moving * width + fixed
                target[index] = argb(red / window, green / window, blue / window)
                val leaving = pixel(moving - radius)
                val entering = pixel(moving + radius + 1)
                red += (entering shr 16 and 0xFF) - (leaving shr 16 and 0xFF)
                green += (entering shr 8 and 0xFF) - (leaving shr 8 and 0xFF)
                blue += (entering and 0xFF) - (leaving and 0xFF)
            }
        }
    }

    private fun argb(red: Int, green: Int, blue: Int): Int =
        0xFF000000.toInt() or (red shl 16) or (green shl 8) or blue

    private const val BLUR_PASSES = 3
}

/** Artwork, fetched at the size the surface drawing it actually needs. */
@Composable
fun DesktopArtwork(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
    px: Int? = null,
) {
    val blurredCovers by DesktopAppearanceSettings.blurCovers.collectAsState()
    // The blur reads a deliberately tiny decode of its own — a blob needs no
    // detail, and a 96px download costs a fraction of what the sharp one did.
    val shown = if (blurredCovers) url?.artworkAt(BLUR_PX) else if (px != null) url.artworkAt(px) else url
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = shown, key2 = blurredCovers) {
        value = withContext(Dispatchers.IO) {
            if (blurredCovers) DesktopBlurredArtworkCache.load(shown) else DesktopArtworkCache.load(shown)
        }
    }
    if (bitmap == null) {
        Box(modifier.background(Color(0xFF2B2B35)))
    } else if (blurredCovers) {
        // The frosted tile: the cover's own colours beyond recognition, a scrim
        // over them, and the note the player's empty sleeve carries — so a
        // blurred row reads as intentional rather than broken.
        Box(modifier) {
            Image(
                painter = BitmapPainter(bitmap!!),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.matchParentSize(),
            )
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.16f)))
            Icon(
                imageVector = BitChordIcons.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.align(Alignment.Center).size(18.dp),
            )
        }
    } else {
        Image(
            painter = BitmapPainter(bitmap!!),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
        )
    }
}

private const val BLUR_PX = 96
