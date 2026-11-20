package com.music.bitchord.desktop

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopPlaybackWritesTest {
    @Test fun `replaced keys run in their latest submission order`() {
        val writer = DesktopPlaybackWriter()
        val entered = CountDownLatch(1); val unblock = CountDownLatch(1)
        val saved = mutableListOf<String>()
        writer.submit("block") { entered.countDown(); unblock.await() }
        assertTrue(entered.await(3, TimeUnit.SECONDS))
        try {
            writer.submit("queue") { saved += "old queue" }
            writer.submit("position") { saved += "position" }
            writer.submit("queue") { saved += "new queue" }
        } finally { unblock.countDown() }
        writer.flush()
        assertEquals(listOf("position", "new queue"), saved)
    }
    @Test fun `blocked old write cannot hold submit or overwrite the newest snapshot`() {
        val writer = DesktopPlaybackWriter()
        val entered = CountDownLatch(1); val unblock = CountDownLatch(1)
        val writes = java.util.Collections.synchronizedList(mutableListOf<Int>())
        writer.submit("queue") { entered.countDown(); unblock.await(); writes += 0 }
        assertTrue(entered.await(3, TimeUnit.SECONDS))
        try {
            repeat(1000) { value -> writer.submit("queue") { writes += value + 1 } }
        } finally { unblock.countDown() }
        writer.flush()
        assertEquals(1000, writes.last())
        assertTrue(writes.size <= 3, "redundant writes should coalesce: ${writes.size}")
    }

    @Test fun `a failed write does not strand newer queue and history snapshots`() {
        val failures = mutableListOf<Throwable>(); val saved = mutableListOf<String>()
        val writer = DesktopPlaybackWriter { failures += it }
        writer.submit("broken") { error("disk unavailable") }
        writer.submit("queue") { saved += "queue+index" }
        writer.submit("history") { saved += "history" }
        writer.flush()
        assertEquals(listOf("queue+index", "history"), saved)
        assertEquals(1, failures.size)
    }
}
