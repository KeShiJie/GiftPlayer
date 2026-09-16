package com.keke.giftplayer.animation.widget

import java.util.PriorityQueue

/**
 * Created by keke on 2026/09/14.
 * Desc: 按播放优先级和入队顺序调度已就绪资源。
 */
internal class ReadyPlaybackQueue<T> {
    class Entry<T>(
        val value: T,
        val playbackPriority: Int,
        val sequence: Long,
        val enqueuedAt: Long,
        val ttlMillis: Long?,
    ) {
        fun isExpired(now: Long): Boolean = ttlMillis?.let { now - enqueuedAt >= it } == true
    }

    private val waiting = linkedSetOf<Entry<T>>()
    private val ready = PriorityQueue<Entry<T>>(
        compareByDescending<Entry<T>> { it.playbackPriority }
            .thenBy { it.sequence },
    )
    private var sequence = 0L
    var current: Entry<T>? = null
        private set
    var paused = false

    fun add(value: T, playbackPriority: Int, now: Long, ttlMillis: Long?): Entry<T> =
        Entry(value, playbackPriority, nextSequence(), now, ttlMillis).also(waiting::add)

    fun isWaiting(entry: Entry<T>): Boolean = entry in waiting

    fun markReady(entry: Entry<T>): Boolean {
        if (entry !in waiting || entry in ready) return false
        ready.offer(entry)
        return true
    }

    fun takeNext(): Entry<T>? {
        if (paused || current != null) return null
        val entry = ready.poll() ?: return null
        waiting.remove(entry)
        current = entry
        return entry
    }

    fun remove(entry: Entry<T>): Boolean {
        if (current === entry) {
            current = null
            return true
        }
        if (!waiting.remove(entry)) return false
        ready.remove(entry)
        return true
    }

    fun expired(now: Long): List<Entry<T>> = waiting.filter { it.isExpired(now) }

    /** Detach the whole batch before calling any external cancellation callbacks. */
    fun clearWaiting(): List<Entry<T>> = waiting.toList().also {
        waiting.clear()
        ready.clear()
    }

    private fun nextSequence(): Long {
        sequence += 1
        return sequence
    }
}
