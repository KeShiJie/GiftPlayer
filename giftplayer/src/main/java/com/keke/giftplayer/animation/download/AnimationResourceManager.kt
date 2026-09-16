package com.keke.giftplayer.animation.download

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.keke.giftplayer.GiftPlayerConfig
import com.keke.giftplayer.internal.GiftPlayerLog
import com.liulishuo.filedownloader.BaseDownloadTask
import com.liulishuo.filedownloader.FileDownloadListener
import com.liulishuo.filedownloader.FileDownloader
import com.liulishuo.filedownloader.util.FileDownloadUtils
import com.joya.lib_download.FileDownloaderInitializer
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by keke on 2026/06/23.
 * Desc: 动画资源下载与缓存管理器。
 */
internal object AnimationResourceManager {

    /** 串行保护任务表和队列状态的修改。 */
    private val lock = Any()

    /** 外部回调在主线程分发，调用方可直接更新 UI。 */
    private val mainHandler = Handler(Looper.getMainLooper())

    private val timeoutExecutor = ScheduledThreadPoolExecutor(1) { runnable ->
        Thread(runnable, "animation-download-timeout").apply { isDaemon = true }
    }.apply { removeOnCancelPolicy = true }

    /** 当前配置，调用方可调整缓存容量和下载并发策略。 */
    @Volatile
    private var config = GiftPlayerConfig()

    @Volatile
    private var appContext: Context? = null

    /** 等待或下载中的任务；每个 resourceKey 只对应一个实际下载任务。 */
    private val runningTasks = ConcurrentHashMap<String, RunningDownload>()

    /** 正在播放或已交给播放器的文件，缓存清理时跳过。 */
    private val protectedFiles = ConcurrentHashMap<String, AtomicInteger>()

    /** 由 GiftPlayer 统一初始化。 */
    fun initialize(context: Context, config: GiftPlayerConfig): Unit = synchronized(lock) {
        appContext = context.applicationContext
        this.config = config
        cacheDir().mkdirs()
        if (config.autoSetupFileDownloader) {
            FileDownloaderInitializer.init(
                context, null, config.connectTimeoutMillis, config.readTimeoutMillis,
            )
        }
        if (config.clearExpiredOnInitialize) {
            clearExpired()
        }
    }

    /** 批量预加载资源，为每次资源请求返回独立的任务句柄。 */
    fun preload(
        resources: List<AnimationResource>,
    ): List<AnimationDownloadTask> {
        return preload(resources, NoopAnimationDownloadCallback)
    }

    /** 批量预加载资源，并向调用方回调每个资源的处理结果。 */
    fun preload(
        resources: List<AnimationResource>,
        callback: AnimationDownloadCallback,
    ): List<AnimationDownloadTask> {
        return resources.map { resource ->
            download(resource, callback)
        }
    }

    /** 下载资源，复用有效缓存及已有下载任务。 */
    fun download(
        resource: AnimationResource,
        callback: AnimationDownloadCallback,
    ): AnimationDownloadTask {
        val urlError = validateUrl(resource.url)
        if (urlError != null) {
            notifyError(callback, resource, urlError)
            return CompletedAnimationDownloadTask
        }
        val resourceKey = buildResourceKey(resource)
        val targetFile = buildCacheFile(resource, resourceKey)
        val downloadFile = buildDownloadFile(targetFile)
        val failureFile = buildFailureFile(targetFile)
        val callbackKey = UUID.randomUUID().toString()
        var cachedFile: File? = null

        synchronized(lock) {
            val current = runningTasks[resourceKey]
            if (current != null) {
                current.priority = maxPriority(current.priority, resource.priority)
                current.callbacks[callbackKey] = DownloadCallbackEntry(resource, callback)
                scheduleDownloadsLocked()
                return ActiveAnimationDownloadTask(resourceKey, callbackKey)
            }
            cachedFile = getValidCachedFile(resource)
            if (cachedFile == null) {
                cleanupDownloadFilesIfNeeded(downloadFile, failureFile)
                targetFile.delete()
                targetFile.parentFile?.mkdirs()
                runningTasks[resourceKey] = RunningDownload(
                    resourceKey = resourceKey,
                    resource = resource,
                    targetFile = targetFile,
                    downloadFile = downloadFile,
                    failureFile = failureFile,
                    priority = resource.priority,
                ).apply {
                    callbacks[callbackKey] = DownloadCallbackEntry(resource, callback)
                }
                scheduleDownloadsLocked()
            }
        }

        cachedFile?.let { file ->
            file.setLastModified(System.currentTimeMillis())
            notifySuccess(callback, resource, file)
            return CompletedAnimationDownloadTask
        }

        return ActiveAnimationDownloadTask(resourceKey, callbackKey)
    }

    /** 返回有效缓存文件，并移除无效缓存。 */
    fun getCachedFile(resource: AnimationResource): File? {
        return getValidCachedFileIfNotDownloading(resource)
    }

    /** 判断资源是否存在有效缓存文件。 */
    fun isCached(resource: AnimationResource): Boolean {
        return getValidCachedFileIfNotDownloading(resource) != null
    }

    /** 标记文件正在播放或即将播放，缓存清理时跳过。 */
    @JvmStatic
    fun protectFile(file: File) {
        protectedFiles.compute(file.absolutePath) { _, count ->
            count?.apply { incrementAndGet() } ?: AtomicInteger(1)
        }
    }

    /** 解除文件保护，允许后续缓存清理删除。 */
    @JvmStatic
    fun unprotectFile(file: File) {
        protectedFiles.compute(file.absolutePath) { _, count ->
            val current = count ?: return@compute null
            if (current.decrementAndGet() <= 0) null else current
        }
    }

    /** 清理过期文件及超出容量上限的缓存。 */
    @JvmStatic
    fun clearExpired() {
        val dir = cacheDir()
        if (!dir.isDirectory) return
        val now = System.currentTimeMillis()
        dir.listFiles()
            ?.filter { it.isFile && !isCacheGroupProtected(it) }
            ?.forEach { file ->
                val expired = now - file.lastModified() > config.maxCacheAgeMillis
                if (expired) {
                    deleteCacheEntry(file)
                }
            }
        trimCacheSize()
    }

    /** 清理所有未受保护且不在下载中的缓存文件。 */
    @JvmStatic
    fun clearAll() {
        val dir = cacheDir()
        if (!dir.isDirectory) return
        dir.listFiles()
            ?.filter { it.isFile && !isCacheGroupProtected(it) }
            ?.forEach { file ->
                deleteCacheEntry(file)
            }
    }

    /** 返回当前动画缓存大小，单位为字节。 */
    @JvmStatic
    fun getCacheSize(): Long {
        return cacheDir().listFiles()
            ?.filter { it.isFile && !it.isFailureFile() }
            ?.sumOf { it.length() }
            ?: 0L
    }

    /** 创建底层 FileDownloader 下载任务。 */
    private fun createDownloadTask(download: RunningDownload): BaseDownloadTask {
        GiftPlayerLog.i("download start: ${download.targetFile.name}, priority=${download.priority}")
        return FileDownloader.getImpl()
            .create(download.resource.url)
            .setPath(download.downloadFile.absolutePath)
            .setForceReDownload(!config.resumeDownloadEnabled)
            .setListener(object : FileDownloadListener() {
                override fun pending(task: BaseDownloadTask?, soFarBytes: Int, totalBytes: Int) = Unit

                override fun progress(task: BaseDownloadTask?, soFarBytes: Int, totalBytes: Int) {
                    notifyProgress(download, soFarBytes.toLong(), totalBytes.toLong())
                }

                override fun completed(task: BaseDownloadTask?) {
                    handleDownloadCompleted(download)
                }

                override fun paused(task: BaseDownloadTask?, soFarBytes: Int, totalBytes: Int) {
                    handleDownloadError(
                        running = download,
                        error = null,
                        removeCacheFile = false,
                        countFailure = false,
                        reschedule = true,
                    )
                }

                override fun error(task: BaseDownloadTask?, e: Throwable?) {
                    handleDownloadError(
                        running = download,
                        error = e,
                        removeCacheFile = false,
                        countFailure = true,
                        reschedule = true,
                    )
                }

                override fun warn(task: BaseDownloadTask?) {
                    handleDownloadError(
                        running = download,
                        error = null,
                        removeCacheFile = false,
                        countFailure = true,
                        reschedule = true,
                    )
                }
            })
    }

    /** 按照优先级启动等待任务，并遵守配置的并发上限。 */
    private fun scheduleDownloadsLocked() {
        val maxConcurrent = maxOf(1, config.maxConcurrentDownloads)
        while (runningTasks.values.count { it.state == DownloadState.Downloading } < maxConcurrent) {
            val next = runningTasks.values
                .filter { it.state == DownloadState.Queued && it.callbacks.isNotEmpty() }
                .sortedWith(
                    compareByDescending<RunningDownload> { it.priority.level }
                        .thenBy { it.sequence }
                )
                .firstOrNull() ?: return
            next.state = DownloadState.Downloading
            try {
                val task = createDownloadTask(next)
                next.task = task
                val timeoutMillis = config.downloadTimeoutMillis
                next.timeout = timeoutExecutor.schedule({
                    handleDownloadError(
                        running = next,
                        error = TimeoutException("Animation download timed out after $timeoutMillis ms."),
                        removeCacheFile = false,
                        countFailure = true,
                        reschedule = true,
                        pauseUnderlying = true,
                    )
                }, timeoutMillis, TimeUnit.MILLISECONDS)
                task.start()
            } catch (error: Exception) {
                handleDownloadError(next, error, false, true, false, pauseUnderlying = true)
            }
        }
    }

    /** 校验下载文件并分发处理结果。 */
    private fun handleDownloadCompleted(running: RunningDownload): Unit = synchronized(lock) {
        if (runningTasks[running.resourceKey] !== running) return@synchronized
        runningTasks.remove(running.resourceKey)
        cancelTimeoutLocked(running)
        running.task = null
        val targetFile = running.targetFile
        val downloadFile = running.downloadFile
        if (isValidCacheFile(downloadFile, targetFile, running.resource) && moveDownloadFileToCache(downloadFile, targetFile)) {
            running.failureFile.delete()
            targetFile.setLastModified(System.currentTimeMillis())
            GiftPlayerLog.i("download success: ${targetFile.name}, size=${targetFile.length()}")
            protectFile(targetFile)
            notifySuccess(
                entries = running.callbacks.values.toList(),
                file = targetFile,
                afterAll = {
                    unprotectFile(targetFile)
                    trimCacheSize()
                },
            )
        } else {
            deleteDownloadFiles(running)
            val error = IllegalStateException("Downloaded animation file is invalid.")
            GiftPlayerLog.e("download invalid: ${targetFile.name}")
            running.callbacks.values.forEach { entry ->
                notifyError(entry.callback, entry.resource, error)
            }
        }
        scheduleDownloadsLocked()
    }

    /** 下载失败、暂停或收到警告时分发错误并清理任务状态。 */
    private fun handleDownloadError(
        running: RunningDownload,
        error: Throwable?,
        removeCacheFile: Boolean,
        countFailure: Boolean,
        reschedule: Boolean,
        pauseUnderlying: Boolean = false,
    ): Unit = synchronized(lock) {
        if (runningTasks[running.resourceKey] !== running) return@synchronized
        runningTasks.remove(running.resourceKey)
        cancelTimeoutLocked(running)
        // 暂停前先移除任务身份记录，下载器可能同步触发暂停回调。
        if (pauseUnderlying) pauseDownloadLocked(running)
        running.task = null
        if (removeCacheFile) running.targetFile.delete()
        if (countFailure) {
            val failureCount = increaseFailureCount(running.failureFile)
            if (failureCount >= maxResumableFailureCount()) {
                deleteDownloadFiles(running)
                GiftPlayerLog.e("download resumable cache cleared: ${running.targetFile.name}, failures=$failureCount")
            }
        }
        GiftPlayerLog.e("download failed: ${running.targetFile.name}, error=${error?.message.orEmpty()}", error)
        running.callbacks.values.forEach { entry ->
            notifyError(entry.callback, entry.resource, error)
        }
        if (reschedule) {
            scheduleDownloadsLocked()
        }
    }

    private fun cancelTimeoutLocked(running: RunningDownload) {
        running.timeout?.cancel(false)
        running.timeout = null
    }

    private fun pauseDownloadLocked(running: RunningDownload) {
        runCatching { running.task?.pause() }.onFailure {
            GiftPlayerLog.e("download pause failed: ${it.javaClass.simpleName}", it)
        }
    }

    /** 返回有效缓存文件，并移除损坏的缓存。 */
    private fun getValidCachedFile(resource: AnimationResource): File? {
        if (validateUrl(resource.url) != null) return null
        val resourceKey = buildResourceKey(resource)
        val file = buildCacheFile(resource, resourceKey)
        if (isValidCacheFile(file, resource)) {
            GiftPlayerLog.i("download cache hit: ${file.name}, size=${file.length()}")
            return file
        }
        if (file.exists()) file.delete()
        val downloadFile = buildDownloadFile(file)
        if (isValidCacheFile(downloadFile, file, resource) && moveDownloadFileToCache(downloadFile, file)) {
            buildFailureFile(file).delete()
            file.setLastModified(System.currentTimeMillis())
            GiftPlayerLog.i("download cache recovered: ${file.name}, size=${file.length()}")
            return file
        }
        return null
    }

    /** 返回有效缓存，避免读取正在写入的未完成文件。 */
    private fun getValidCachedFileIfNotDownloading(resource: AnimationResource): File? {
        if (validateUrl(resource.url) != null) return null
        val resourceKey = buildResourceKey(resource)
        return synchronized(lock) {
            if (runningTasks.containsKey(resourceKey)) {
                null
            } else {
                getValidCachedFile(resource)
            }
        }
    }

    /** 校验缓存文件是否满足播放所需的基本条件。 */
    private fun isValidCacheFile(file: File, resource: AnimationResource): Boolean {
        return isValidCacheFile(file, file, resource)
    }

    /** 校验文件是否满足播放所需的基本条件，通过 targetFile 确定临时文件对应的原始格式。 */
    private fun isValidCacheFile(file: File, targetFile: File, resource: AnimationResource): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        if (file.isGitLfsPointer()) return false
        if (!resource.md5.isNullOrBlank() && !file.md5().equals(resource.md5, ignoreCase = true)) return false
        val shouldCheckPag = targetFile.extension.equals("pag", ignoreCase = true) ||
            resource.format == com.keke.giftplayer.animation.core.AnimationFormat.Pag
        return if (shouldCheckPag) {
            file.hasHeader(byteArrayOf('P'.code.toByte(), 'A'.code.toByte(), 'G'.code.toByte()))
        } else {
            true
        }
    }

    /** 校验 URL 是否支持下载。 */
    private fun validateUrl(url: String): Throwable? {
        return when {
            url.isBlank() -> IllegalArgumentException("Animation url is blank.")
            !url.startsWith("http://") && !url.startsWith("https://") ->
                IllegalArgumentException("Only http and https animation urls are supported.")
            else -> null
        }
    }

    /** 为资源生成稳定的缓存 Key。 */
    private fun buildResourceKey(resource: AnimationResource): String {
        val id = resource.id?.takeIf { it.isNotBlank() }
        val version = resource.version?.takeIf { it.isNotBlank() }
        return when {
            id != null && version != null -> "${resource.category.orEmpty()}_${id}_$version"
            id != null -> "${resource.category.orEmpty()}_${id}_${resource.url.md5()}"
            else -> resource.url.md5()
        }.sanitizeFileName()
    }

    /** 根据资源 Key 生成缓存文件路径。 */
    private fun buildCacheFile(resource: AnimationResource, resourceKey: String): File {
        val extension = resolveExtension(resource)
        val fileName = buildString {
            append(resourceKey)
            if (extension != null) {
                append('.')
                append(extension)
            }
        }
        return File(cacheDir(), fileName)
    }

    /** 生成断点续传临时文件路径，正式缓存仅保存完整且通过校验的文件。 */
    private fun buildDownloadFile(targetFile: File): File {
        return File(targetFile.parentFile, "${targetFile.name}$DOWNLOAD_FILE_SUFFIX")
    }

    /** 生成断点续传失败次数记录文件的路径。 */
    private fun buildFailureFile(targetFile: File): File {
        return File(targetFile.parentFile, "${targetFile.name}$FAILURE_FILE_SUFFIX")
    }

    /** 生成 FileDownloader 内部保存未完成内容的临时文件路径。 */
    private fun buildFileDownloaderTempFile(downloadFile: File): File {
        return File(FileDownloadUtils.getTempPath(downloadFile.absolutePath))
    }

    /** 判断是否为尚未移入正式缓存的下载文件。 */
    private fun File.isDownloadFile(): Boolean {
        return name.endsWith(DOWNLOAD_FILE_SUFFIX)
    }

    /** 判断是否为 FileDownloader 使用的未完成临时文件。 */
    private fun File.isFileDownloaderTempFile(): Boolean {
        return name.endsWith("$DOWNLOAD_FILE_SUFFIX$FILE_DOWNLOADER_TEMP_SUFFIX")
    }

    /** 判断是否为断点续传失败次数记录文件。 */
    private fun File.isFailureFile(): Boolean {
        return name.endsWith(FAILURE_FILE_SUFFIX)
    }

    /** 优先使用 URL 扩展名，无扩展名时使用显式指定的格式。 */
    private fun resolveExtension(resource: AnimationResource): String? {
        val urlExtension = resource.url.substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('.', "")
            .takeIf { it.isNotBlank() && it.length <= MAX_EXTENSION_LENGTH }
            ?.lowercase()
        if (urlExtension != null) return urlExtension
        return when (resource.format) {
            com.keke.giftplayer.animation.core.AnimationFormat.Svga -> "svga"
            com.keke.giftplayer.animation.core.AnimationFormat.Vap -> "mp4"
            com.keke.giftplayer.animation.core.AnimationFormat.Pag -> "pag"
            com.keke.giftplayer.animation.core.AnimationFormat.Auto -> null
        }
    }

    /** 按最近使用时间清理旧文件，使缓存容量满足上限。 */
    private fun trimCacheSize() {
        val dir = cacheDir()
        val files = dir.listFiles()
            ?.filter { it.isFile && !it.isFailureFile() && !isCacheGroupProtected(it) }
            ?.sortedWith(
                compareBy<File> { it.isDownloadFile() || it.isFileDownloaderTempFile() }
                    .thenBy { it.lastModified() }
            )
            ?: return
        var totalSize = getCacheSize()
        for (file in files) {
            if (totalSize <= config.maxCacheSizeBytes) break
            val size = cacheGroupSize(file)
            if (deleteCacheEntry(file)) {
                totalSize -= size
            }
        }
    }

    /** 动画缓存目录。 */
    private fun cacheDir(): File {
        val context = appContext ?: error("AnimationResourceManager is not initialized.")
        return config.customCacheDir ?: File(context.cacheDir, config.cacheDirName)
    }

    /** 判断文件是否受到缓存清理保护。 */
    private fun isProtected(file: File): Boolean {
        return protectedFiles.containsKey(file.absolutePath) ||
            runningTasks.values.any { running ->
                file.absolutePath == running.targetFile.absolutePath ||
                    file.absolutePath == running.downloadFile.absolutePath ||
                    file.absolutePath == buildFileDownloaderTempFile(running.downloadFile).absolutePath ||
                    file.absolutePath == running.failureFile.absolutePath
            }
    }

    /** 同一缓存组中任意文件受保护时，整个组都不能被清理。 */
    private fun isCacheGroupProtected(file: File): Boolean {
        return cacheGroupFiles(file).any { isProtected(it) }
    }

    /** 删除资源缓存组及对应的断点续传辅助文件。 */
    private fun deleteCacheEntry(file: File): Boolean {
        var deleted = false
        cacheGroupFiles(file).forEach { groupFile ->
            if (groupFile.exists()) {
                deleted = groupFile.delete() || deleted
            }
        }
        return deleted
    }

    /** 返回缓存组大小，不包含失败次数记录文件。 */
    private fun cacheGroupSize(file: File): Long {
        return cacheGroupFiles(file)
            .filter { it.isFile && !it.isFailureFile() }
            .sumOf { it.length() }
    }

    /** 返回同一资源的正式缓存、下载文件、未完成临时文件及失败次数记录文件。 */
    private fun cacheGroupFiles(file: File): List<File> {
        val targetFile = resolveCacheGroupTargetFile(file)
        val downloadFile = buildDownloadFile(targetFile)
        return listOf(
            targetFile,
            downloadFile,
            buildFileDownloaderTempFile(downloadFile),
            buildFailureFile(targetFile),
        ).distinctBy { it.absolutePath }
    }

    /** 根据任意缓存辅助文件推导正式缓存文件路径。 */
    private fun resolveCacheGroupTargetFile(file: File): File {
        val targetName = when {
            file.name.endsWith("$DOWNLOAD_FILE_SUFFIX$FILE_DOWNLOADER_TEMP_SUFFIX") ->
                file.name.removeSuffix("$DOWNLOAD_FILE_SUFFIX$FILE_DOWNLOADER_TEMP_SUFFIX")
            file.name.endsWith(DOWNLOAD_FILE_SUFFIX) ->
                file.name.removeSuffix(DOWNLOAD_FILE_SUFFIX)
            file.name.endsWith(FAILURE_FILE_SUFFIX) ->
                file.name.removeSuffix(FAILURE_FILE_SUFFIX)
            else -> file.name
        }
        return File(file.parentFile, targetName)
    }

    /** 将已完成下载移入正式缓存，确保播放器只接收完整文件。 */
    private fun moveDownloadFileToCache(downloadFile: File, targetFile: File): Boolean {
        if (!downloadFile.isFile) return false
        targetFile.parentFile?.mkdirs()
        if (targetFile.exists() && !targetFile.delete()) return false
        if (downloadFile.renameTo(targetFile)) return true
        return runCatching {
            downloadFile.copyTo(targetFile, overwrite = true)
            if (!downloadFile.delete()) {
                targetFile.delete()
                false
            } else {
                true
            }
        }.getOrDefault(false)
    }

    /** 删除下载文件、FileDownloader 未完成临时文件及失败次数记录。 */
    private fun deleteDownloadFiles(download: RunningDownload) {
        download.downloadFile.delete()
        buildFileDownloaderTempFile(download.downloadFile).delete()
        download.failureFile.delete()
    }

    /** 同一资源续传失败次数达到阈值后清理临时文件。 */
    private fun cleanupDownloadFilesIfNeeded(downloadFile: File, failureFile: File) {
        if (readFailureCount(failureFile) < maxResumableFailureCount()) return
        downloadFile.delete()
        buildFileDownloaderTempFile(downloadFile).delete()
        failureFile.delete()
    }

    /** 递增断点续传失败次数。 */
    private fun increaseFailureCount(failureFile: File): Int {
        val count = readFailureCount(failureFile) + 1
        runCatching {
            failureFile.parentFile?.mkdirs()
            failureFile.writeText(count.toString())
        }
        return count
    }

    /** 读取断点续传失败次数。 */
    private fun readFailureCount(failureFile: File): Int {
        return runCatching {
            failureFile.takeIf { it.isFile }
                ?.readText()
                ?.trim()
                ?.toIntOrNull()
                ?: 0
        }.getOrDefault(0)
    }

    /** 断点续传失败次数阈值，最小为 1。 */
    private fun maxResumableFailureCount(): Int {
        return maxOf(1, config.maxResumableFailureCount)
    }

    /** 主线程分发进度，并过滤已经完成、取消或被替换的下载。 */
    private fun notifyProgress(running: RunningDownload, downloadedBytes: Long, totalBytes: Long) {
        runOnMain {
            val entries = synchronized(lock) {
                if (runningTasks[running.resourceKey] !== running) return@runOnMain
                running.callbacks.toMap()
            }
            entries.forEach { (key, entry) ->
                val active = synchronized(lock) {
                    runningTasks[running.resourceKey] === running && running.callbacks[key] === entry
                }
                if (active) {
                    runCatching {
                        entry.callback.onProgress(entry.resource, downloadedBytes, totalBytes)
                    }.onFailure {
                        GiftPlayerLog.e("download progress callback failed", it)
                    }
                }
            }
        }
    }

    /** 在主线程分发成功回调，全部回调结束后执行清理。 */
    private fun notifySuccess(
        entries: List<DownloadCallbackEntry>,
        file: File,
        afterAll: () -> Unit,
    ) {
        if (entries.isEmpty()) {
            afterAll()
            return
        }
        entries.forEachIndexed { index, entry ->
            runOnMain {
                try {
                    runCatching {
                        entry.callback.onSuccess(entry.resource, file)
                    }.onFailure {
                        GiftPlayerLog.e("download callback failed: ${it.javaClass.simpleName}")
                    }
                } finally {
                    if (index == entries.lastIndex) afterAll()
                }
            }
        }
    }

    /** 在主线程分发成功回调。 */
    private fun notifySuccess(
        callback: AnimationDownloadCallback,
        resource: AnimationResource,
        file: File,
    ) {
        runOnMain {
            runCatching {
                callback.onSuccess(resource, file)
            }.onFailure {
                GiftPlayerLog.e("download callback failed: ${it.javaClass.simpleName}")
            }
        }
    }

    /** 在主线程分发错误回调。 */
    private fun notifyError(
        callback: AnimationDownloadCallback,
        resource: AnimationResource,
        error: Throwable?,
    ) {
        runOnMain {
            runCatching {
                callback.onError(resource, error)
            }.onFailure {
                GiftPlayerLog.e("download callback failed: ${it.javaClass.simpleName}")
            }
        }
    }

    /** 在主线程执行外部回调。 */
    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    /** 选择较高的优先级。 */
    private fun maxPriority(
        current: AnimationDownloadPriority,
        incoming: AnimationDownloadPriority,
    ): AnimationDownloadPriority {
        return if (incoming.level > current.level) incoming else current
    }

    /** 判断文件是否为 Git LFS 指针文本。 */
    private fun File.isGitLfsPointer(): Boolean {
        if (length() > GIT_LFS_POINTER_MAX_SIZE) return false
        return runCatching {
            inputStream().use { input ->
                val buffer = ByteArray(GIT_LFS_POINTER_PREFIX.size)
                input.read(buffer) == buffer.size && buffer.contentEquals(GIT_LFS_POINTER_PREFIX)
            }
        }.getOrDefault(false)
    }

    /** 判断文件头是否匹配指定字节序列。 */
    private fun File.hasHeader(header: ByteArray): Boolean {
        return runCatching {
            inputStream().use { input ->
                val buffer = ByteArray(header.size)
                input.read(buffer) == buffer.size && buffer.contentEquals(header)
            }
        }.getOrDefault(false)
    }

    /** 计算文件的 MD5。 */
    private fun File.md5(): String {
        val digest = MessageDigest.getInstance("MD5")
        inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { "%02x".format(it) }
    }

    /** 计算字符串的 MD5。 */
    private fun String.md5(): String {
        val digest = MessageDigest.getInstance("MD5").digest(toByteArray())
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    /** 替换文件名中的非法字符。 */
    private fun String.sanitizeFileName(): String {
        return replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "animation_${hashCode()}" }
    }

    /** 实际下载任务状态。 */
    private enum class DownloadState {
        Queued,
        Downloading,
    }

    /** 单个调用方的回调及原始资源信息。 */
    private data class DownloadCallbackEntry(
        val resource: AnimationResource,
        val callback: AnimationDownloadCallback,
    )

    /** 处于等待或下载状态的实际任务。 */
    private class RunningDownload(
        val resourceKey: String,
        val resource: AnimationResource,
        val targetFile: File,
        val downloadFile: File,
        val failureFile: File,
        var priority: AnimationDownloadPriority,
    ) {
        val sequence = nextSequence()
        val callbacks = ConcurrentHashMap<String, DownloadCallbackEntry>()
        var task: BaseDownloadTask? = null
        var timeout: ScheduledFuture<*>? = null
        var state: DownloadState = DownloadState.Queued
    }

    /** 已完成操作使用的空任务句柄。 */
    private object CompletedAnimationDownloadTask : AnimationDownloadTask {
        override fun cancel() = Unit
    }

    /** 默认预加载回调，不向外分发结果。 */
    private object NoopAnimationDownloadCallback : AnimationDownloadCallback {
        override fun onSuccess(resource: AnimationResource, file: File) = Unit

        override fun onError(resource: AnimationResource, error: Throwable?) = Unit
    }

    /** 当前调用方的下载任务句柄。 */
    private class ActiveAnimationDownloadTask(
        private val resourceKey: String,
        private val callbackKey: String,
    ) : AnimationDownloadTask {
        override fun cancel() {
            synchronized(lock) {
                val running = runningTasks[resourceKey] ?: return
                if (running.callbacks.remove(callbackKey) == null) return
                if (running.callbacks.isEmpty()) {
                    runningTasks.remove(resourceKey)
                    cancelTimeoutLocked(running)
                    pauseDownloadLocked(running)
                    running.task = null
                    GiftPlayerLog.i("download cancel requested: ${running.targetFile.name}")
                    scheduleDownloadsLocked()
                }
            }
        }
    }

    /** 生成入队序号，同优先级时较早入队的任务先执行。 */
    private fun nextSequence(): Long = synchronized(lock) {
        taskSequence += 1
        taskSequence
    }

    private const val BUFFER_SIZE = 8 * 1024
    private const val MAX_EXTENSION_LENGTH = 8
    private const val GIT_LFS_POINTER_MAX_SIZE = 512L
    private const val DOWNLOAD_FILE_SUFFIX = ".download"
    private const val FILE_DOWNLOADER_TEMP_SUFFIX = ".temp"
    private const val FAILURE_FILE_SUFFIX = ".failure"
    private val GIT_LFS_POINTER_PREFIX = "version https://git-lfs.github.com/spec".toByteArray()
    private var taskSequence = 0L
}
