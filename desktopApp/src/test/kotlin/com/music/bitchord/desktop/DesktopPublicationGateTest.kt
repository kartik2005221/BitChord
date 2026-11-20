package com.music.bitchord.desktop

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopPublicationGateTest {
    private fun CountDownLatch.awaitOrFail() = assertTrue(await(5, TimeUnit.SECONDS))

    @Test fun stalePublisherReadsCurrentPrivacyAfterClear() {
        val gate = DesktopPublicationGate()
        val privacy = AtomicBoolean(false)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val sent = mutableListOf<String?>()
        val old = thread {
            entered.countDown()
            release.awaitOrFail()
            gate.run { sent += externalArtworkUrl("art", privacy.get()) }
        }
        entered.awaitOrFail()
        privacy.set(true)
        gate.run { sent += externalArtworkUrl("art", privacy.get()) }
        release.countDown()
        old.join(5_000)
        assertFalse(old.isAlive)
        assertEquals(listOf(null, null), sent)
    }

    @Test fun clearWaitsForInFlightSendAndStopPreventsLaterSend() {
        val gate = DesktopPublicationGate()
        val privacy = AtomicBoolean(false)
        var started = true
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val clearing = CountDownLatch(1)
        val sent = mutableListOf<String?>()
        val old = thread {
            gate.run {
                val art = externalArtworkUrl("art", privacy.get())
                entered.countDown()
                release.awaitOrFail()
                if (started) sent += art
            }
        }
        entered.awaitOrFail()
        privacy.set(true)
        val clear = thread {
            clearing.countDown()
            gate.run { if (started) sent += externalArtworkUrl("art", privacy.get()) }
        }
        clearing.awaitOrFail()
        release.countDown()
        old.join(5_000)
        clear.join(5_000)
        assertFalse(old.isAlive)
        assertFalse(clear.isAlive)
        assertEquals(listOf("art", null), sent)
        gate.run { started = false }
        gate.run { if (started) sent += externalArtworkUrl("art", privacy.get()) }
        assertEquals(listOf("art", null), sent)
    }
}
