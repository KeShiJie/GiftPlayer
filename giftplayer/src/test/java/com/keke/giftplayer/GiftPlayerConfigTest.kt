package com.keke.giftplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Created by keke on 2026/09/16.
 * Desc: 验证 GiftPlayer 全局配置的默认值和参数边界。
 */
class GiftPlayerConfigTest {
    @Test
    fun defaultsMatchSdkPolicy() {
        val config = GiftPlayerConfig()

        assertEquals(20_000, config.connectTimeoutMillis)
        assertEquals(30_000, config.readTimeoutMillis)
        assertEquals(180_000L, config.downloadTimeoutMillis)
        assertEquals(2, config.maxConcurrentDownloads)
        assertFalse(config.logcatEnabled)
        assertNull(config.logger)
    }

    @Test
    fun timeoutsRejectNonPositiveValues() {
        assertThrows(IllegalArgumentException::class.java) {
            GiftPlayerConfig(connectTimeoutMillis = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GiftPlayerConfig(readTimeoutMillis = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GiftPlayerConfig(downloadTimeoutMillis = 0L)
        }
    }

    @Test
    fun limitsRejectInvalidValues() {
        assertThrows(IllegalArgumentException::class.java) {
            GiftPlayerConfig(cacheDirName = " ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            GiftPlayerConfig(maxCacheSizeBytes = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GiftPlayerConfig(maxCacheAgeMillis = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GiftPlayerConfig(maxConcurrentDownloads = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GiftPlayerConfig(maxResumableFailureCount = 0)
        }
    }

    @Test
    fun acceptsLargeTotalTimeoutWithoutNarrowing() {
        val timeout = Int.MAX_VALUE.toLong() + 1L
        assertEquals(timeout, GiftPlayerConfig(downloadTimeoutMillis = timeout).downloadTimeoutMillis)
    }
}
