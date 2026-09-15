package com.keke.giftplayer.animation.download

import java.io.File

/**
 * Created by keke on 2026/06/23.
 * Desc: 动画资源下载配置。
 */
data class AnimationDownloadConfig(
    /** 自定义动画缓存目录；为 null 时使用应用 cacheDir 下的 cacheDirName 目录。 */
    val customCacheDir: File? = null,

    /** 默认缓存目录名，仅在 customCacheDir 为 null 时生效。 */
    val cacheDirName: String = DEFAULT_CACHE_DIR_NAME,

    /** 动画缓存容量上限，单位为字节，默认 300 MiB；超过上限时清理可删除的旧缓存。 */
    val maxCacheSizeBytes: Long = DEFAULT_MAX_CACHE_SIZE_BYTES,

    /** 缓存过期清理阈值，单位为毫秒，默认 30 天。 */
    val maxCacheAgeMillis: Long = DEFAULT_MAX_CACHE_AGE_MILLIS,

    /** 最大并发下载数量，默认 2；其余任务按优先级排队。 */
    val maxConcurrentDownloads: Int = DEFAULT_MAX_CONCURRENT_DOWNLOADS,

    /** 是否在初始化时清理过期缓存及超出容量上限的缓存。 */
    val clearExpiredOnInit: Boolean = true,

    /** 是否启用 FileDownloader 断点续传，默认开启。 */
    val enableResumeDownload: Boolean = true,

    /** 同一资源连续续传失败达到此次数后清理临时文件，默认 3 次；不是自动重试次数。 */
    val maxResumableFailureCount: Int = DEFAULT_MAX_RESUMABLE_FAILURE_COUNT,

    /** 是否随动画库自动初始化 FileDownloader；关闭后需由调用方负责初始化。 */
    val autoSetupFileDownloader: Boolean = true,

    /** 默认连接工厂的连接超时，单位为毫秒，默认 20 秒，必须大于 0；不影响已建立的连接。 */
    val connectTimeoutMillis: Int = 20_000,

    /** 默认连接工厂等待读取数据的超时，单位为毫秒，默认 30 秒，必须大于 0；自定义连接工厂自行配置。 */
    val readTimeoutMillis: Int = 30_000,

    /** 单次下载总超时，单位为毫秒，默认 180 秒，必须大于 0；从启动下载开始计时，不含排队时间。 */
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
