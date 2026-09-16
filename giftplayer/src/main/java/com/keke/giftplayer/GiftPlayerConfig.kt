package com.keke.giftplayer

import java.io.File

/**
 * Created by keke on 2026/09/16.
 * Desc: GiftPlayer 全局配置。
 */
data class GiftPlayerConfig(
    /** 自定义缓存目录。为 null 时使用应用 cacheDir 下的 [cacheDirName]。 */
    val customCacheDir: File? = null,

    /** 默认缓存目录名，仅在 [customCacheDir] 为 null 时生效。 */
    val cacheDirName: String = DEFAULT_CACHE_DIR_NAME,

    /** 缓存容量上限，单位为字节。清理时优先删除最久未使用且未被播放保护的文件。 */
    val maxCacheSizeBytes: Long = DEFAULT_MAX_CACHE_SIZE_BYTES,

    /** 缓存最长保留时间，单位为毫秒。设置为 0 表示缓存文件可立即被过期清理。 */
    val maxCacheAgeMillis: Long = DEFAULT_MAX_CACHE_AGE_MILLIS,

    /** 最大并发下载数，其余任务按照优先级和入队顺序等待。 */
    val maxConcurrentDownloads: Int = DEFAULT_MAX_CONCURRENT_DOWNLOADS,

    /** 是否允许底层下载器复用临时文件继续下载。 */
    val resumeDownloadEnabled: Boolean = true,

    /** 同一资源连续续传失败达到该次数后删除临时文件；这不是自动重试次数。 */
    val maxResumableFailureCount: Int = DEFAULT_MAX_RESUMABLE_FAILURE_COUNT,

    /** 是否由 GiftPlayer 初始化底层 FileDownloader。关闭后由宿主应用负责初始化。 */
    val autoSetupFileDownloader: Boolean = true,

    /** 建立网络连接的超时时间，单位为毫秒。 */
    val connectTimeoutMillis: Int = 20_000,

    /** 已连接后等待读取数据的超时时间，单位为毫秒。 */
    val readTimeoutMillis: Int = 30_000,

    /** 单个任务从开始下载到结束的总超时，单位为毫秒，不包含排队时间。 */
    val downloadTimeoutMillis: Long = 180_000L,

    /** 是否向 Android Logcat 输出日志，默认关闭。 */
    val logcatEnabled: Boolean = false,

    /**
     * 自定义日志接收器。即使 [logcatEnabled] 为 false，也会收到日志。
     * SDK 会在进程内强引用该对象，请传入 Application 级实现，不要捕获 Activity 或 View。
     */
    val logger: GiftPlayerLogger? = null,
) {
    init {
        require(cacheDirName.isNotBlank()) { "cacheDirName must not be blank." }
        require(maxCacheSizeBytes >= 0) { "maxCacheSizeBytes must not be negative." }
        require(maxCacheAgeMillis >= 0) { "maxCacheAgeMillis must not be negative." }
        require(maxConcurrentDownloads > 0) { "maxConcurrentDownloads must be positive." }
        require(maxResumableFailureCount > 0) { "maxResumableFailureCount must be positive." }
        require(connectTimeoutMillis > 0) { "connectTimeoutMillis must be positive." }
        require(readTimeoutMillis > 0) { "readTimeoutMillis must be positive." }
        require(downloadTimeoutMillis > 0) { "downloadTimeoutMillis must be positive." }
    }

    companion object {
        const val DEFAULT_CACHE_DIR_NAME = "animation"
        const val DEFAULT_MAX_CONCURRENT_DOWNLOADS = 2
        const val DEFAULT_MAX_RESUMABLE_FAILURE_COUNT = 3
        const val DEFAULT_MAX_CACHE_SIZE_BYTES = 300L * 1024L * 1024L
        const val DEFAULT_MAX_CACHE_AGE_MILLIS = 30L * 24L * 60L * 60L * 1000L
    }
}
