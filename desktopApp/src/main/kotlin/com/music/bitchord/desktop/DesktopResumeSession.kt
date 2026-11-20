package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song

internal data class DesktopResumeSelection(val song: Song, val positionMs: Long)

/** The queue is available independently of the opt-in, but must not select a player when disabled. */
internal fun desktopResumeSelection(
    persistence: DesktopPersistence,
    queue: DesktopQueue,
    inParty: Boolean,
): DesktopResumeSelection? {
    if (inParty || !persistence.restoreLastSession()) return null
    val song = queue.current ?: return null
    val position = if (persistence.resumeSongId().let { it.isNotBlank() && it == song.videoId }) {
        persistence.resumePosition()
    } else 0L
    return DesktopResumeSelection(song, position)
}

/** Shared by queue changes, pause/seek observation, the timer, and every clean exit path. */
internal fun desktopCheckpointPlayback(
    persistence: DesktopPersistence,
    queue: DesktopQueue,
    playback: DesktopPlaybackState,
    inParty: Boolean,
    saveQueue: Boolean = false,
    flush: Boolean = false,
    onExit: Boolean = false,
) {
    // With restore disabled (or before startup selection), an untouched player must not erase
    // the previous playhead just because the window was closed.
    if (!inParty && !(onExit && playback.song == null)) {
        if (saveQueue) persistence.saveQueue(queue.songs, queue.index)
        val song = queue.current
        if (song != null && song.videoId.isNotBlank()) {
            val matches = playback.song?.videoId == song.videoId
            // During a queue change the new selection may not have reached the engine yet.
            // A zero checkpoint is safe; the outgoing track's position is not.
            if (matches || saveQueue) {
                persistence.saveResumePosition(song.videoId, if (matches) playback.positionMs else 0L, queue.index)
            }
        }
    }
    if (flush) DesktopPlaybackWrites.flush()
}
