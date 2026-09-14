package com.keke.giftplayer.animation.download

import java.io.File

/**
 * Created by keke on 2026/06/23.
 * Desc: Animation resource download configuration.
 */
data class AnimationDownloadConfig(
    /** Custom animation cache directory. Uses application cacheDir/cacheDirName when null. */
    val customCacheDir: File? = null,

    /** Default cache directory name, used only when customCacheDir is null. */
    val cacheDirName: String = DEFAULT_CACHE_DIR_NAME,

    /** Maximum animation cache size. Old entries are evicted when this limit is exceeded. */
    val maxCacheSizeBytes: Long = DEFAULT_MAX_CACHE_SIZE_BYTES,

    /** Maximum cache age before an entry can be cleaned. */
    val maxCacheAgeMillis: Long = DEFAULT_MAX_CACHE_AGE_MILLIS,

    /** Maximum number of concurrent downloads. */
    val maxConcurrentDownloads: Int = DEFAULT_MAX_CONCURRENT_DOWNLOADS,

    /** Whether expired and oversized cache entries should be cleaned on init. */
    val clearExpiredOnInit: Boolean = true,

    /** Whether FileDownloader resumable download is enabled. */
    val enableResumeDownload: Boolean = true,

    /** Clears partial files after this many consecutive resume failures for the same resource. */
    val maxResumableFailureCount: Int = DEFAULT_MAX_RESUMABLE_FAILURE_COUNT,

    /** Whether FileDownloader should be initialized with the animation library. */
    val autoSetupFileDownloader: Boolean = true,

    /** Whether library logging is enabled. */
    val isLogEnabled: Boolean = true,

    /** Connection timeout for the default connection creator; existing connections are unchanged. */
    val connectTimeoutMillis: Int = 20_000,

    /** Read timeout for the default connection creator; custom creators own their timeout policy. */
    val readTimeoutMillis: Int = 30_000,

    /** Total time from download start, excluding time waiting for a concurrency slot. */
    val downloadTimeoutMillis: Long = 180_000L,
) {
    init {
        require(connectTimeoutMillis > 0) { "connectTimeoutMillis must be positive." }
        require(readTimeoutMillis > 0) { "readTimeoutMillis must be positive." }
        require(downloadTimeoutMillis > 0) { "downloadTimeoutMillis must be positive." }
    }

    companion object {
        const val DEFAULT_CACHE_DIR_NAME = "animation"
        const val DEFAULT_MAX_CONCURRENT_DOWNLOADS = 2
        const val DEFAULT_MAX_RESUMABLE_FAILURE_COUNT = 3
        private const val DAYS_30 = 30L
        private const val HOURS_PER_DAY = 24L
        private const val MINUTES_PER_HOUR = 60L
        private const val SECONDS_PER_MINUTE = 60L
        private const val MILLIS_PER_SECOND = 1000L
        const val DEFAULT_MAX_CACHE_SIZE_BYTES = 300L * 1024L * 1024L
        const val DEFAULT_MAX_CACHE_AGE_MILLIS =
            DAYS_30 * HOURS_PER_DAY * MINUTES_PER_HOUR * SECONDS_PER_MINUTE * MILLIS_PER_SECOND
    }
}
