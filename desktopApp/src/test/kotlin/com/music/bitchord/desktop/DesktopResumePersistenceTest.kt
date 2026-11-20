package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import java.util.UUID
import java.util.Base64
import java.util.prefs.Preferences
import kotlin.test.*

class DesktopResumePersistenceTest {
    private val node = Preferences.userRoot().node("com.music.bitchord.tests/${UUID.randomUUID()}")
    private val store = DesktopPersistence(node)
    private fun song(id: Int) = Song("id$id", "song$id", "artist", null)
    private fun read(key: String) = DesktopPreferenceChunks.read(node, key)

    @AfterTest fun cleanup() {
        DesktopPlaybackWrites.flush()
        node.removeNode()
    }

    @Test fun `bounded queue retains selected row and checkpoint identity`() {
        val songs = (0 until 500).map(::song)
        store.saveQueue(songs, 450)
        store.saveResumePosition("id450", 12345, 450)
        DesktopPlaybackWrites.flush()
        assertEquals(200, store.queue().size)
        assertEquals("id450", store.queue()[150].videoId)
        assertEquals("150", read("queue_index"))
        assertEquals("id450", read("resume_song_id"))
        assertEquals("12345", read("resume_position_ms"))
    }

    @Test fun `new queue does not attach old playhead identity to new song`() {
        store.saveQueue(listOf(song(1)), 0)
        store.saveResumePosition("id1", 1000, 0)
        store.saveQueue(listOf(song(2)), 0)
        DesktopPlaybackWrites.flush()
        assertEquals("id1", read("resume_song_id"))
        assertEquals("id2", store.queue().single().videoId)
    }

    @Test fun `invalid positions and indexes are safe`() {
        store.saveQueue(listOf(song(1)), Int.MAX_VALUE)
        store.saveResumePosition("id1", -100, Int.MAX_VALUE)
        DesktopPlaybackWrites.flush()
        assertEquals("0", read("queue_index"))
        assertEquals(0L, store.resumePosition())
        DesktopPreferenceChunks.write(node, "resume_position_ms", "not a number")
        assertEquals(0L, store.resumePosition())
        store.saveQueue(emptyList(), -5)
        DesktopPlaybackWrites.flush()
        assertTrue(store.queue().isEmpty())
    }

    @Test fun `different preference nodes do not share pending snapshots`() {
        val otherNode = Preferences.userRoot().node("com.music.bitchord.tests/${UUID.randomUUID()}")
        try {
            val other = DesktopPersistence(otherNode)
            store.saveQueue(listOf(song(1)), 0)
            other.saveQueue(listOf(song(2)), 0)
            DesktopPlaybackWrites.flush()
            assertEquals("id1", store.queue().single().videoId)
            assertEquals("id2", other.queue().single().videoId)
            assertNotNull(DesktopPreferenceChunks.read(node, "queue"))
            assertNotNull(DesktopPreferenceChunks.read(otherNode, "queue"))
        } finally {
            DesktopPlaybackWrites.flush()
            otherNode.removeNode()
        }
    }

    @Test fun `legacy queue and corrupt rows restore safely without a playhead`() {
        fun encoded(value: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())
        val legacy = listOf("legacy", "title", "artist", "", "1:00", "", "", "", "", "")
            .joinToString("|") { encoded(it) }
        DesktopPreferenceChunks.write(node, "queue", "bad\n!!!!!!!!|a|b|c|d|e|f|g|h|i\n$legacy")
        DesktopPreferenceChunks.write(node, "queue_index", "999999999999999999999")
        val queue = DesktopQueue.restored(store.queue(), store.string("queue_index", "0").toIntOrNull() ?: 0)
        assertEquals(listOf("legacy"), queue.songs.map { it.videoId })
        assertEquals(0L, desktopResumeSelection(store, queue, false)?.positionMs)
        DesktopPreferenceChunks.write(node, "resume_song_id", "another-song")
        DesktopPreferenceChunks.write(node, "resume_position_ms", "5000")
        assertEquals(0L, desktopResumeSelection(store, queue, false)?.positionMs)
        DesktopPreferenceChunks.write(node, "resume_song_id", "legacy")
        DesktopPreferenceChunks.write(node, "resume_position_ms", "bad")
        assertEquals(0L, desktopResumeSelection(store, queue, false)?.positionMs)
    }

    @Test fun `disabled restore and party never select persisted song`() {
        val queue = DesktopQueue(listOf(song(1)), 0)
        assertNotNull(desktopResumeSelection(store, queue, false))
        assertNull(desktopResumeSelection(store, queue, true))
        store.saveRestoreLastSession(false)
        assertNull(desktopResumeSelection(store, queue, false))
        assertEquals("id1", queue.current?.videoId)
    }

    @Test fun `pause seek periodic and final checkpoints honor identity and party isolation`() {
        val own = DesktopQueue(listOf(song(1)), 0)
        val party = DesktopQueue(listOf(song(2)), 0)
        fun state(queue: DesktopQueue, position: Long, playing: Boolean = false) =
            DesktopPlaybackState(song = queue.current, positionMs = position, isPlaying = playing)
        desktopCheckpointPlayback(store, own, state(own, 1000, true), false, saveQueue = true)
        desktopCheckpointPlayback(store, own, state(own, 2200), false) // pause
        DesktopPlaybackWrites.flush()
        assertEquals("2200", read("resume_position_ms"))
        desktopCheckpointPlayback(store, own, state(own, 3400), false) // seek while paused
        DesktopPlaybackWrites.flush()
        assertEquals("3400", read("resume_position_ms"))
        desktopCheckpointPlayback(store, own, state(own, 4000, true), false) // timer
        desktopCheckpointPlayback(store, party, state(party, 9000), true, saveQueue = true, flush = true)
        assertEquals("id1", read("resume_song_id"))
        assertEquals("4000", read("resume_position_ms"))
        assertEquals("id1", store.queue().single().videoId)
        desktopCheckpointPlayback(store, own, state(party, 9999), false) // stale engine state
        desktopCheckpointPlayback(store, own, state(own, 5100, true), false, saveQueue = true, flush = true)
        // The exit path's flush makes the final value visible before it shuts down the engine.
        assertEquals("5100", read("resume_position_ms"))
        assertEquals("id1", read("resume_song_id"))
    }

    @Test fun `queue change never saves outgoing song position as incoming progress`() {
        val previous = DesktopQueue(listOf(song(1)), 0)
        val next = DesktopQueue(listOf(song(2)), 0)
        desktopCheckpointPlayback(store, previous, DesktopPlaybackState(song = previous.current, positionMs = 8000), false, saveQueue = true)
        desktopCheckpointPlayback(store, next, DesktopPlaybackState(song = previous.current, positionMs = 8000), false, saveQueue = true, flush = true)
        assertEquals("id2", read("resume_song_id"))
        assertEquals("0", read("resume_position_ms"))
    }

    @Test fun `bounded queue keeps the correct occurrence of a repeated song`() {
        val songs = (0 until 400).map { if (it == 230 || it == 250) song(999) else song(it) }
        store.saveQueue(songs, 250)
        store.saveResumePosition("id999", 7000, 250)
        DesktopPlaybackWrites.flush()
        assertEquals("50", read("queue_index"))
        assertEquals("id999", store.queue()[50].videoId)
        assertEquals("id999", store.queue()[30].videoId)
    }

    @Test fun `queue window retains full history at tail and all of a short queue`() {
        val longQueue = (0 until 500).map(::song)
        store.saveQueue(longQueue, 499)
        store.saveResumePosition("id499", 1500, 499)
        DesktopPlaybackWrites.flush()
        assertEquals(200, store.queue().size)
        assertEquals("id300", store.queue().first().videoId)
        assertEquals("id499", store.queue().last().videoId)
        assertEquals("199", read("queue_index"))

        val shortQueue = (0 until 12).map(::song)
        store.saveQueue(shortQueue, 11)
        store.saveResumePosition("id11", 2500, 11)
        DesktopPlaybackWrites.flush()
        assertEquals(shortQueue, store.queue())
        assertEquals("11", read("queue_index"))
        assertEquals("id11", read("resume_song_id"))
    }

    @Test fun `closing an untouched player preserves the previous session`() {
        val own = DesktopQueue(listOf(song(1)), 0)
        desktopCheckpointPlayback(store, own, DesktopPlaybackState(song = own.current, positionMs = 1234), false,
            saveQueue = true, flush = true)
        desktopCheckpointPlayback(store, own, DesktopPlaybackState(), false, saveQueue = true, flush = true, onExit = true)
        assertEquals("id1", read("resume_song_id"))
        assertEquals("1234", read("resume_position_ms"))
    }
}
