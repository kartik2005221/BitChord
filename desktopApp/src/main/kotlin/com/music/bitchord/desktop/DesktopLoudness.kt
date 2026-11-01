package com.music.bitchord.desktop

import com.music.bitchord.playback.smart.TrackAnalysis
import kotlin.math.pow

/**
 * The desktop's cheap loudness match between the two decks of a blend.
 *
 * The phone normalizes each player on its own — a per-track gain that glides rather than steps,
 * behind a peak limiter. The desktop has neither processor in the blend path, but the shared
 * analysis already carries [TrackAnalysis.levelWindows]: RMS levels on a 0..1 scale that spans
 * 60 dBFS. Comparing the two decks' levels over the windows the blend actually plays gives a
 * relative trim — a quiet master entering is brought up against the loud one leaving — bounded
 * well inside what the equal-power faders can absorb, and applied to the incoming fader alone,
 * during the blend only, so neither track's solo playback is touched.
 */
internal object DesktopLoudness {

    /** How far the trim may move the incoming deck, in dB, either way. */
    const val MAX_TRIM_DB = 6.0

    /** The span [TrackAnalysis.levelWindows]' 0..1 scale stands for, in dB. */
    private const val LEVEL_SPAN_DB = 60.0

    /**
     * The mean level a deck holds over [fromSeconds]..[untilSeconds] of its own timeline, on the
     * 0..1 scale, or null when the analysis records no levels there. Windows outside the span are
     * skipped; one that straddles an edge contributes only the samples inside it.
     */
    fun meanLevel(
        analysis: TrackAnalysis,
        fromSeconds: Double,
        untilSeconds: Double,
    ): Double? {
        if (untilSeconds <= fromSeconds) return null
        var sum = 0.0
        var count = 0
        for (window in analysis.levelWindows) {
            val frameRate = window.frameRate.takeIf { it > 0 } ?: continue
            for (index in window.values.indices) {
                val time = window.startTime + index / frameRate
                if (time < fromSeconds || time > untilSeconds) continue
                val value = window.values[index].toDouble()
                if (!value.isFinite()) continue
                sum += value
                count += 1
            }
        }
        return if (count > 0) sum / count else null
    }

    /**
     * The gain the incoming deck's fader is multiplied by so it enters at the outgoing deck's
     * loudness: 10^((outgoing − incoming) · 60 / 20) on the 0..1 level scale, clamped to
     * ±[MAX_TRIM_DB] dB. No level on either side — no recorded levels at all, as with every track
     * the desktop analyser has measured so far — answers 1f and the blend runs untrimmed.
     */
    fun trimBetween(
        outgoing: TrackAnalysis?,
        incoming: TrackAnalysis?,
        outFromSeconds: Double,
        outUntilSeconds: Double,
        inFromSeconds: Double,
        inUntilSeconds: Double,
    ): Float {
        if (outgoing == null || incoming == null) return 1f
        val outgoingLevel = meanLevel(outgoing, outFromSeconds, outUntilSeconds) ?: return 1f
        val incomingLevel = meanLevel(incoming, inFromSeconds, inUntilSeconds) ?: return 1f
        val db = ((outgoingLevel - incomingLevel) * LEVEL_SPAN_DB).coerceIn(-MAX_TRIM_DB, MAX_TRIM_DB)
        return 10.0.pow(db / 20.0).toFloat()
    }
}
