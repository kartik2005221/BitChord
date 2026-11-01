package com.music.bitchord.desktop

import com.music.bitchord.playback.smart.CurveWindow
import com.music.bitchord.playback.smart.TrackAnalysis
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The blend's loudness match: a level difference between the two decks becomes a bounded trim on
 * the incoming fader, and absent evidence becomes no trim at all.
 */
class DesktopLoudnessTest {

    private fun analysis(vararg windows: CurveWindow): TrackAnalysis =
        TrackAnalysis(levelWindows = windows.toList())

    /** One level window at the analyzer's own 10 frames a second. */
    private fun window(start: Double, values: FloatArray): CurveWindow =
        CurveWindow(startTime = start, frameRate = 10.0, values = values)

    @Test
    fun `a missing level on either side leaves the blend untrimmed`() {
        assertEquals(1f, DesktopLoudness.trimBetween(null, analysis(), 0.0, 1.0, 0.0, 1.0))
        assertEquals(1f, DesktopLoudness.trimBetween(analysis(), null, 0.0, 1.0, 0.0, 1.0))
        assertEquals(1f, DesktopLoudness.trimBetween(analysis(), analysis(), 0.0, 1.0, 0.0, 1.0))
    }

    @Test
    fun `two decks reading the same level leave each other alone`() {
        val one = analysis(window(0.0, floatArrayOf(0.7f, 0.7f)))
        val other = analysis(window(0.0, floatArrayOf(0.7f)))
        assertEquals(1f, DesktopLoudness.trimBetween(one, other, 0.0, 1.0, 0.0, 1.0))
    }

    @Test
    fun `a quieter incoming deck is brought up by the difference`() {
        // One level point on the 0..1 scale is 0.6 dB, so six of them is 3.6 dB of trim.
        val loud = analysis(window(0.0, floatArrayOf(0.9f)))
        val quiet = analysis(window(0.0, floatArrayOf(0.84f)))
        val trim = DesktopLoudness.trimBetween(loud, quiet, 0.0, 1.0, 0.0, 1.0)
        assertEquals(10.0.pow(3.6 / 20.0).toFloat(), trim, 1e-4f)
        // And the other way round it trims down by the same amount.
        val down = DesktopLoudness.trimBetween(quiet, loud, 0.0, 1.0, 0.0, 1.0)
        assertEquals(10.0.pow(-3.6 / 20.0).toFloat(), down, 1e-4f)
    }

    @Test
    fun `the trim is bounded to six decibels either way`() {
        val loud = analysis(window(0.0, floatArrayOf(1f)))
        val silence = analysis(window(0.0, floatArrayOf(0f)))
        val ceiling = DesktopLoudness.trimBetween(loud, silence, 0.0, 1.0, 0.0, 1.0)
        assertEquals(10.0.pow(6.0 / 20.0).toFloat(), ceiling, 1e-4f)
        val floor = DesktopLoudness.trimBetween(silence, loud, 0.0, 1.0, 0.0, 1.0)
        assertEquals(10.0.pow(-6.0 / 20.0).toFloat(), floor, 1e-4f)
        assertTrue(ceiling in 0.5f..2.0f && floor in 0.5f..2.0f)
    }

    @Test
    fun `only the windows the blend actually plays are averaged`() {
        val levels = analysis(
            window(0.0, floatArrayOf(0.5f, 0.5f, 0.5f)), // 0.0..0.3 s
            window(0.3, floatArrayOf(1f, 1f)), // 0.3..0.5 s
        )
        assertNull(DesktopLoudness.meanLevel(levels, 10.0, 11.0))
        assertEquals(1.0, DesktopLoudness.meanLevel(levels, 0.3, 0.5)!!, 1e-9)
        assertEquals(0.5, DesktopLoudness.meanLevel(levels, 0.0, 0.2)!!, 1e-9)
        // A window straddling the join contributes only the samples inside the span.
        assertEquals(0.75, DesktopLoudness.meanLevel(levels, 0.1, 0.4)!!, 1e-9)
    }

    @Test
    fun `a window without a frame rate is skipped rather than divided by`() {
        val levels = analysis(
            CurveWindow(startTime = 0.0, frameRate = 0.0, values = floatArrayOf(1f, 1f, 1f)),
            window(0.0, floatArrayOf(0.25f)),
        )
        assertEquals(0.25, DesktopLoudness.meanLevel(levels, 0.0, 1.0)!!, 1e-9)
    }
}
