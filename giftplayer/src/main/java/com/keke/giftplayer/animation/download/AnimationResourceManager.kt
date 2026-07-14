package com.keke.giftplayer.animation.download

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.keke.giftplayer.animation.core.AnimationLog
import com.keke.giftplayer.gift.svga.utils.log.SVGALogger
import com.liulishuo.filedownloader.BaseDownloadTask
import com.liulishuo.filedownloader.FileDownloadListener
import com.liulishuo.filedownloader.FileDownloader
import com.liulishuo.filedownloader.util.FileDownloadUtils
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by keke on 2026/06/23.
 * Desc: Animation resource download and cache manager.
 */
object AnimationResourceManager {

    /** Serializes task table and queue state changes. */
    private val lock = Any()

    /** External callbacks are dispatched on the main thread so callers can update UI directly. */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Current configuration. Hosts can tune cache size and concurrency policy. */
    private var config = AnimationDownloadConfig()

    private var appContext: Context? = null

    /** Running tasks. A resourceKey can have only one real download task. */
    private val runningTasks = ConcurrentHashMap<String, RunningDownload>()

    /** Files currently playing or handed to the player; cache cleanup skips them. */
    private val protectedFiles = ConcurrentHashMap<String, AtomicInteger>()

    /** Initializes the download manager. Repeated calls update configuration and applicationContext. */
    @JvmStatic
    fun init(context: Context, config: AnimationDownloadConfig = AnimationDownloadConfig()) {
        appContext = context.applicationContext
        this.config = config
        AnimationLog.setEnabled(config.isLogEnabled)
        SVGALogger.setLogEnabled(config.isLogEnabled)
        cacheDir().mkdirs()
        if (config.autoSetupFileDownloader) {
            FileDownloaderInitializer.init(context)
        }
        if (config.clearExpiredOnInit) {
            clearExpired()
        }
    }

    /** Preloads resources in batch and returns one task handle per caller. */
    @JvmStatic
    fun preload(
        context: Context,
        resources: List<AnimationResource>,
    ): List<AnimationDownloadTask> {
        return preload(context, resources, NoopAnimationDownloadCallback)
    }

    /** Preloads resources in batch and forwards each resource result to the caller. */
    @JvmStatic
    fun preload(
        context: Context,
        resources: List<AnimationResource>,
        callback: AnimationDownloadCallback,
    ): List<AnimationDownloadTask> {
        ensureInit(context)
        return resources.map { resource ->
            download(context, resource, callback)
        }
    }

    /** Downloads a resource. Valid cache entries and existing tasks are reused. */
    @JvmStatic
    fun download(
        context: Context,
        resource: AnimationResource,
        callback: AnimationDownloadCallback,
    ): AnimationDownloadTask {
        ensureInit(context)
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

    /** Returns a valid cached file. Invalid cache entries are removed. */
    @JvmStatic
    fun getCachedFile(context: Context, resource: AnimationResource): File? {
        ensureInit(context)
        return getValidCachedFileIfNotDownloading(resource)
    }

    /** Returns whether the resource has a valid cached file. */
    @JvmStatic
    fun isCached(context: Context, resource: AnimationResource): Boolean {
        ensureInit(context)
        return getValidCachedFileIfNotDownloading(resource) != null
    }

    /** Marks a file as playing or about to play so cache cleanup skips it. */
    @JvmStatic
    fun protectFile(file: File) {
        protectedFiles.compute(file.absolutePath) { _, count ->
            count?.apply { incrementAndGet() } ?: AtomicInteger(1)
        }
    }

    /** Removes file protection so later cache cleanup may delete it. */
    @JvmStatic
    fun unprotectFile(file: File) {
        protectedFiles.compute(file.absolutePath) { _, count ->
            val current = count ?: return@compute null
            if (current.decrementAndGet() <= 0) null else current
        }
    }

    /** Cleans expired files and trims oversized cache. */
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

    /** Clears all cache files that are neither protected nor downloading. */
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

    /** Returns current animation cache size. */
    @JvmStatic
    fun getCacheSize(): Long {
        return cacheDir().listFiles()
            ?.filter { it.isFile && !it.isFailureFile() }
            ?.sumOf { it.length() }
            ?: 0L
    }

    /** Creates the underlying FileDownloader task. */
    private fun createDownloadTask(download: RunningDownload): BaseDownloadTask {
        AnimationLog.i("download start: ${download.targetFile.name}, priority=${download.priority}")
        return FileDownloader.getImpl()
            .create(download.resource.url)
            .setPath(download.downloadFile.absolutePath)
            .setForceReDownload(!config.enableResumeDownload)
            .setListener(object : FileDownloadListener() {
                override fun pending(task: BaseDownloadTask?, soFarBytes: Int, totalBytes: Int) = Unit

                override fun progress(task: BaseDownloadTask?, soFarBytes: Int, totalBytes: Int) = Unit

                override fun completed(task: BaseDownloadTask?) {
                    handleDownloadCompleted(download.resourceKey)
                }

                override fun paused(task: BaseDownloadTask?, soFarBytes: Int, totalBytes: Int) {
                    handleDownloadError(
                        resourceKey = download.resourceKey,
                        error = null,
                        removeCacheFile = false,
                        countFailure = false,
                        reschedule = false,
                    )
                }

                override fun error(task: BaseDownloadTask?, e: Throwable?) {
                    handleDownloadError(
                        resourceKey = download.resourceKey,
                        error = e,
                        removeCacheFile = false,
                        countFailure = true,
                        reschedule = true,
                    )
                }

                override fun warn(task: BaseDownloadTask?) {
                    handleDownloadError(
                        resourceKey = download.resourceKey,
                        error = null,
                        removeCacheFile = false,
                        countFailure = true,
                        reschedule = true,
                    )
                }
            })
    }

    /** Starts queued tasks by priority while respecting configured concurrency. */
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
            next.task = createDownloadTask(next)
            next.task?.start()
        }
    }

    /** Validates the downloaded file and broadcasts the result. */
    private fun handleDownloadCompleted(resourceKey: String) {
        val running = synchronized(lock) {
            runningTasks.remove(resourceKey)
        } ?: return
        running.task = null
        val targetFile = running.targetFile
        val downloadFile = running.downloadFile
        if (isValidCacheFile(downloadFile, targetFile, running.resource) && moveDownloadFileToCache(downloadFile, targetFile)) {
            running.failureFile.delete()
            targetFile.setLastModified(System.currentTimeMillis())
            AnimationLog.i("download success: ${targetFile.name}, size=${targetFile.length()}")
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
            AnimationLog.e("download invalid: ${targetFile.name}")
            running.callbacks.values.forEach { entry ->
                notifyError(entry.callback, entry.resource, error)
            }
        }
        synchronized(lock) {
            scheduleDownloadsLocked()
        }
    }

    /** Broadcasts errors and cleans task state on failure, pause, or warning. */
    private fun handleDownloadError(
        resourceKey: String,
        error: Throwable?,
        removeCacheFile: Boolean,
        countFailure: Boolean,
        reschedule: Boolean,
    ) {
        val running = synchronized(lock) {
            runningTasks.remove(resourceKey)
        } ?: return
        running.task = null
        if (removeCacheFile) running.targetFile.delete()
        if (countFailure) {
            val failureCount = increaseFailureCount(running.failureFile)
            if (failureCount >= maxResumableFailureCount()) {
                deleteDownloadFiles(running)
                AnimationLog.e("download resumable cache cleared: ${running.targetFile.name}, failures=$failureCount")
            }
        }
        AnimationLog.e("download failed: ${running.targetFile.name}, error=${error?.message.orEmpty()}")
        running.callbacks.values.forEach { entry ->
            notifyError(entry.callback, entry.resource, error)
        }
        if (reschedule) {
            synchronized(lock) {
                scheduleDownloadsLocked()
            }
        }
    }

    /** Returns a valid cached file and removes broken cache entries. */
    private fun getValidCachedFile(resource: AnimationResource): File? {
        if (validateUrl(resource.url) != null) return null
        val resourceKey = buildResourceKey(resource)
        val file = buildCacheFile(resource, resourceKey)
        if (isValidCacheFile(file, resource)) {
            AnimationLog.i("download cache hit: ${file.name}, size=${file.length()}")
            return file
        }
        if (file.exists()) file.delete()
        val downloadFile = buildDownloadFile(file)
        if (isValidCacheFile(downloadFile, file, resource) && moveDownloadFileToCache(downloadFile, file)) {
            buildFailureFile(file).delete()
            file.setLastModified(System.currentTimeMillis())
            AnimationLog.i("download cache recovered: ${file.name}, size=${file.length()}")
            return file
        }
        return null
    }

    /** Returns valid cache while avoiding partial files currently being written. */
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

    /** Validates whether a cached file can be played. */
    private fun isValidCacheFile(file: File, resource: AnimationResource): Boolean {
        return isValidCacheFile(file, file, resource)
    }

    /** Validates whether a cached file can be played; targetFile preserves the original temp file format. */
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

    /** Validates whether the URL can be downloaded. */
    private fun validateUrl(url: String): Throwable? {
        return when {
            url.isBlank() -> IllegalArgumentException("Animation url is blank.")
            !url.startsWith("http://") && !url.startsWith("https://") ->
                IllegalArgumentException("Only http and https animation urls are supported.")
            else -> null
        }
    }

    /** Builds a stable cache key for the resource. */
    private fun buildResourceKey(resource: AnimationResource): String {
        val id = resource.id?.takeIf { it.isNotBlank() }
        val version = resource.version?.takeIf { it.isNotBlank() }
        return when {
            id != null && version != null -> "${resource.category.orEmpty()}_${id}_$version"
            id != null -> "${resource.category.orEmpty()}_${id}_${resource.url.md5()}"
            else -> resource.url.md5()
        }.sanitizeFileName()
    }

    /** Builds the cache file path from a resource key. */
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

    /** Builds the resumable download temp file; official cache stores only complete playable files. */
    private fun buildDownloadFile(targetFile: File): File {
        return File(targetFile.parentFile, "${targetFile.name}$DOWNLOAD_FILE_SUFFIX")
    }

    /** Builds the resumable download failure counter file. */
    private fun buildFailureFile(targetFile: File): File {
        return File(targetFile.parentFile, "${targetFile.name}$FAILURE_FILE_SUFFIX")
    }

    /** Builds the temp file used internally by FileDownloader for partial content. */
    private fun buildFileDownloaderTempFile(downloadFile: File): File {
        return File(FileDownloadUtils.getTempPath(downloadFile.absolutePath))
    }

    /** Returns whether this is a completed download file not yet promoted to cache. */
    private fun File.isDownloadFile(): Boolean {
        return name.endsWith(DOWNLOAD_FILE_SUFFIX)
    }

    /** Returns whether this is a partial file being written by FileDownloader. */
    private fun File.isFileDownloaderTempFile(): Boolean {
        return name.endsWith("$DOWNLOAD_FILE_SUFFIX$FILE_DOWNLOADER_TEMP_SUFFIX")
    }

    /** Returns whether this is a resumable download failure counter file. */
    private fun File.isFailureFile(): Boolean {
        return name.endsWith(FAILURE_FILE_SUFFIX)
    }

    /** Prefers the URL extension; falls back to explicit format when the URL has no extension. */
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

    /** Trims cache size by deleting old files by last-used time. */
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

    /** Ensures initialization. Uses default configuration when callers did not explicitly initialize. */
    private fun ensureInit(context: Context) {
        if (appContext == null) {
            init(context, config)
        }
    }

    /** Animation cache directory. */
    private fun cacheDir(): File {
        val context = appContext ?: error("AnimationResourceManager is not initialized.")
        return config.customCacheDir ?: File(context.cacheDir, config.cacheDirName)
    }

    /** Returns whether a file is protected from cache cleanup. */
    private fun isProtected(file: File): Boolean {
        return protectedFiles.containsKey(file.absolutePath) ||
            runningTasks.values.any { running ->
                file.absolutePath == running.targetFile.absolutePath ||
                    file.absolutePath == running.downloadFile.absolutePath ||
                    file.absolutePath == buildFileDownloaderTempFile(running.downloadFile).absolutePath ||
                    file.absolutePath == running.failureFile.absolutePath
            }
    }

    /** A cache group cannot be cleaned when any file in the group is protected. */
    private fun isCacheGroupProtected(file: File): Boolean {
        return cacheGroupFiles(file).any { isProtected(it) }
    }

    /** Deletes a resource cache group and its resumable download helper files. */
    private fun deleteCacheEntry(file: File): Boolean {
        var deleted = false
        cacheGroupFiles(file).forEach { groupFile ->
            if (groupFile.exists()) {
                deleted = groupFile.delete() || deleted
            }
        }
        return deleted
    }

    /** Returns cache group size. Failure counter files are excluded from cache size. */
    private fun cacheGroupSize(file: File): Long {
        return cacheGroupFiles(file)
            .filter { it.isFile && !it.isFailureFile() }
            .sumOf { it.length() }
    }

    /** Returns official cache, download temp, partial, and failure counter files for the same resource. */
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

    /** Resolves the official cache file path from any cache helper file. */
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

    /** Moves a completed download into official cache so the player only receives complete files. */
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

    /** Deletes the download file, FileDownloader partial file, and failure counter. */
    private fun deleteDownloadFiles(download: RunningDownload) {
        download.downloadFile.delete()
        buildFileDownloaderTempFile(download.downloadFile).delete()
        download.failureFile.delete()
    }

    /** Clears partial files after too many resume failures for the same resource. */
    private fun cleanupDownloadFilesIfNeeded(downloadFile: File, failureFile: File) {
        if (readFailureCount(failureFile) < maxResumableFailureCount()) return
        downloadFile.delete()
        buildFileDownloaderTempFile(downloadFile).delete()
        failureFile.delete()
    }

    /** Increments resumable download failure count. */
    private fun increaseFailureCount(failureFile: File): Int {
        val count = readFailureCount(failureFile) + 1
        runCatching {
            failureFile.parentFile?.mkdirs()
            failureFile.writeText(count.toString())
        }
        return count
    }

    /** Reads resumable download failure count. */
    private fun readFailureCount(failureFile: File): Int {
        return runCatching {
            failureFile.takeIf { it.isFile }
                ?.readText()
                ?.trim()
                ?.toIntOrNull()
                ?: 0
        }.getOrDefault(0)
    }

    /** Resume failure threshold, at least 1. */
    private fun maxResumableFailureCount(): Int {
        return maxOf(1, config.maxResumableFailureCount)
    }

    /** Dispatches success callbacks on the main thread and runs cleanup after all callbacks finish. */
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
                        AnimationLog.e("download callback failed: ${it.javaClass.simpleName}")
                    }
                } finally {
                    if (index == entries.lastIndex) afterAll()
                }
            }
        }
    }

    /** Dispatches a success callback on the main thread. */
    private fun notifySuccess(
        callback: AnimationDownloadCallback,
        resource: AnimationResource,
        file: File,
    ) {
        runOnMain {
            runCatching {
                callback.onSuccess(resource, file)
            }.onFailure {
                AnimationLog.e("download callback failed: ${it.javaClass.simpleName}")
            }
        }
    }

    /** Dispatches an error callback on the main thread. */
    private fun notifyError(
        callback: AnimationDownloadCallback,
        resource: AnimationResource,
        error: Throwable?,
    ) {
        runOnMain {
            runCatching {
                callback.onError(resource, error)
            }.onFailure {
                AnimationLog.e("download callback failed: ${it.javaClass.simpleName}")
            }
        }
    }

    /** Runs an external callback on the main thread. */
    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    /** Selects the higher priority. */
    private fun maxPriority(
        current: AnimationDownloadPriority,
        incoming: AnimationDownloadPriority,
    ): AnimationDownloadPriority {
        return if (incoming.level > current.level) incoming else current
    }

    /** Returns whether the file is a Git LFS pointer text file. */
    private fun File.isGitLfsPointer(): Boolean {
        if (length() > GIT_LFS_POINTER_MAX_SIZE) return false
        return runCatching {
            inputStream().use { input ->
                val buffer = ByteArray(GIT_LFS_POINTER_PREFIX.size)
                input.read(buffer) == buffer.size && buffer.contentEquals(GIT_LFS_POINTER_PREFIX)
            }
        }.getOrDefault(false)
    }

    /** Returns whether the file header matches the given byte sequence. */
    private fun File.hasHeader(header: ByteArray): Boolean {
        return runCatching {
            inputStream().use { input ->
                val buffer = ByteArray(header.size)
                input.read(buffer) == buffer.size && buffer.contentEquals(header)
            }
        }.getOrDefault(false)
    }

    /** Calculates file MD5. */
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

    /** Calculates string MD5. */
    private fun String.md5(): String {
        val digest = MessageDigest.getInstance("MD5").digest(toByteArray())
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    /** Sanitizes invalid filename characters. */
    private fun String.sanitizeFileName(): String {
        return replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "animation_${hashCode()}" }
    }

    /** Real download task state. */
    private enum class DownloadState {
        Queued,
        Downloading,
    }

    /** Callback and original resource info for one caller. */
    private data class DownloadCallbackEntry(
        val resource: AnimationResource,
        val callback: AnimationDownloadCallback,
    )

    /** Real download task that is waiting or downloading. */
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
        var state: DownloadState = DownloadState.Queued
    }

    /** Empty task handle for completed operations. */
    private object CompletedAnimationDownloadTask : AnimationDownloadTask {
        override fun cancel() = Unit
    }

    /** Default preload callback that does not forward results. */
    private object NoopAnimationDownloadCallback : AnimationDownloadCallback {
        override fun onSuccess(resource: AnimationResource, file: File) = Unit

        override fun onError(resource: AnimationResource, error: Throwable?) = Unit
    }

    /** Download task handle for the current caller. */
    private class ActiveAnimationDownloadTask(
        private val resourceKey: String,
        private val callbackKey: String,
    ) : AnimationDownloadTask {
        override fun cancel() {
            synchronized(lock) {
                val running = runningTasks[resourceKey] ?: return
                running.callbacks.remove(callbackKey)
                if (running.callbacks.isEmpty()) {
                    runningTasks.remove(resourceKey)
                    running.task?.pause()
                    running.task = null
                    AnimationLog.i("download cancel requested: ${running.targetFile.name}")
                    scheduleDownloadsLocked()
                }
            }
        }
    }

    /** Generates queue sequence. Older tasks run first when priority ties. */
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
