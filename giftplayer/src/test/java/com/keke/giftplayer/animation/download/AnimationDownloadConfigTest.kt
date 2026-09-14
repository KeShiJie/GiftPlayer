package com.keke.giftplayer.animation.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Created by keke on 2026/09/14.
 * Desc: Verifies timeout defaults, boundaries, and validation through configuration copies.
 */
class AnimationDownloadConfigTest {
    @Test
    fun defaultTimeoutsMatchNetworkPolicy() {
        val config = AnimationDownloadConfig()
        assertEquals(20_000, config.connectTimeoutMillis)
        assertEquals(30_000, config.readTimeoutMillis)
        assertEquals(180_000L, config.downloadTimeoutMillis)
    }

    @Test
    fun connectionAndReadTimeoutsRejectNonPositiveValues() {
        for (value in listOf(0, -1, Int.MIN_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                AnimationDownloadConfig(connectTimeoutMillis = value)
            }
            assertThrows(IllegalArgumentException::class.java) {
                AnimationDownloadConfig(readTimeoutMillis = value)
            }
        }
    }

    @Test
    fun totalTimeoutRejectsNonPositiveValuesIncludingCopies() {
        val config = AnimationDownloadConfig()
        for (value in listOf(0L, -1L, Long.MIN_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                config.copy(downloadTimeoutMillis = value)
            }
        }
    }

    @Test
    fun acceptsPositiveBoundariesWithoutNarrowingTotalTimeout() {
        val config = AnimationDownloadConfig(
            connectTimeoutMillis = 1,
            readTimeoutMillis = Int.MAX_VALUE,
            downloadTimeoutMillis = Int.MAX_VALUE.toLong() + 1L,
        )
        assertEquals(1, config.connectTimeoutMillis)
        assertEquals(Int.MAX_VALUE, config.readTimeoutMillis)
        assertEquals(2_147_483_648L, config.downloadTimeoutMillis)
        assertEquals(1L, config.copy(downloadTimeoutMillis = 1L).downloadTimeoutMillis)
    }
}
