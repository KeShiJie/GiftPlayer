package com.keke.giftplayer.animation.widget

import com.keke.giftplayer.animation.core.AnimationQueueConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Created by keke on 2026/09/14.
 * Desc: Queue ordering, cancellation, expiry, and reentrant mutation contracts.
 */
class ReadyPlaybackQueueTest {
    @Test
    fun laterReadyMessageBypassesDownloadWithoutPreemptingCurrent() {
        val queue = ReadyPlaybackQueue<String>()
        val slow = queue.add("slow", 0, null)
        val cached = queue.add("cached", 1, null)
        queue.markReady(cached)
        assertSame(cached, queue.takeNext())
        queue.markReady(slow)
        assertNull(queue.takeNext())
        queue.remove(cached)
        assertSame(slow, queue.takeNext())
    }

    @Test
    fun readyOrderWinsOverEnqueueOrder() {
        val queue = ReadyPlaybackQueue<String>()
        val first = queue.add("first", 0, null)
        val second = queue.add("second", 0, null)
        val third = queue.add("third", 0, null)
        queue.markReady(third)
        queue.markReady(first)
        queue.markReady(second)
        for (entry in listOf(third, first, second)) {
            assertSame(entry, queue.takeNext())
            queue.remove(entry)
        }
    }

    @Test
    fun duplicateResourcesRemainIndependentMessages() {
        val queue = ReadyPlaybackQueue<String>()
        val first = queue.add("same-url", 0, null)
        val second = queue.add("same-url", 0, null)
        assertTrue(queue.markReady(first))
        assertFalse(queue.markReady(first))
        assertTrue(queue.markReady(second))
        assertSame(first, queue.takeNext())
        queue.remove(first)
        assertSame(second, queue.takeNext())
        assertFalse(queue.remove(first))
        assertSame(second, queue.current)
    }

    @Test
    fun pausedQueueDoesNotAdvanceWhenCurrentCompletes() {
        val queue = ReadyPlaybackQueue<String>()
        val first = queue.add("first", 0, null)
        val next = queue.add("next", 0, null)
        queue.markReady(first)
        queue.markReady(next)
        queue.takeNext()
        queue.paused = true
        queue.remove(first)
        assertNull(queue.takeNext())
        queue.paused = false
        assertSame(next, queue.takeNext())
    }

    @Test
    fun clearWaitingPreservesCurrentAndRejectsStaleReadiness() {
        val queue = ReadyPlaybackQueue<String>()
        val playing = queue.add("playing", 0, null)
        val downloading = queue.add("downloading", 0, null)
        val ready = queue.add("ready", 0, null)
        queue.markReady(playing)
        queue.takeNext()
        queue.markReady(ready)
        assertEquals(listOf(downloading, ready), queue.clearWaiting())
        assertSame(playing, queue.current)
        assertFalse(queue.markReady(downloading))
        assertFalse(queue.markReady(ready))
        queue.remove(playing)
        assertNull(queue.takeNext())
    }

    @Test
    fun messagesAddedDuringCancellationSurviveDetachedBatch() {
        val queue = ReadyPlaybackQueue<String>()
        val old = queue.add("url", 0, null)
        val cancelled = queue.clearWaiting()
        val replacement = queue.add("url", 0, null)
        cancelled.forEach { assertFalse(queue.remove(it)) }
        assertFalse(queue.markReady(old))
        assertTrue(queue.markReady(replacement))
        assertSame(replacement, queue.takeNext())
    }

    @Test
    fun ttlStartsAtEnqueueAndDoesNotApplyToCurrent() {
        val queue = ReadyPlaybackQueue<String>()
        val playing = queue.add("playing", 0, 10)
        val ready = queue.add("ready", 0, 10)
        val downloading = queue.add("downloading", 1, 10)
        val unlimited = queue.add("unlimited", 0, null)
        queue.markReady(playing)
        queue.markReady(ready)
        queue.takeNext()
        assertTrue(queue.expired(9).isEmpty())
        assertEquals(listOf(ready), queue.expired(10))
        assertEquals(listOf(ready, downloading), queue.expired(11))
        assertFalse(queue.expired(Long.MAX_VALUE).contains(unlimited))
        assertSame(playing, queue.current)
    }

    @Test
    fun removingExpiredReadyEntryAllowsFollowingMessage() {
        val queue = ReadyPlaybackQueue<String>()
        val expired = queue.add("expired", 0, 10)
        val next = queue.add("next", 1, null)
        queue.markReady(expired)
        queue.markReady(next)
        queue.expired(10).forEach(queue::remove)
        assertSame(next, queue.takeNext())
    }

    @Test
    fun configurationDefaultsToUnlimitedLifetime() {
        assertNull(AnimationQueueConfig().messageTtlMillis)
        assertEquals(1L, AnimationQueueConfig(1).messageTtlMillis)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroLifetimeIsRejected() {
        AnimationQueueConfig(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeLifetimeIsRejected() {
        AnimationQueueConfig(-1)
    }
}
