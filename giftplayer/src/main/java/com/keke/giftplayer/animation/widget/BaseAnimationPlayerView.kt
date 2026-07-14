package com.keke.giftplayer.animation.widget

import android.content.Context
import android.util.AttributeSet
import com.keke.giftplayer.animation.download.AnimationDownloadCallback
import com.keke.giftplayer.animation.download.AnimationDownloadTask
import com.keke.giftplayer.animation.download.AnimationResource
import com.keke.giftplayer.animation.download.AnimationResourceManager
import com.keke.giftplayer.animation.core.AnimationError
import com.keke.giftplayer.animation.core.AnimationLog
import com.keke.giftplayer.animation.core.AnimationRequest
import com.keke.giftplayer.animation.core.AnimationSource
import java.io.File

/**
 * Created by keke on 2026/06/18.
 * Desc: Base animation player view with URL download support.
 */
open class BaseAnimationPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AnimationPlayerView(context, attrs, defStyleAttr) {

    /** Download wait handle for the current caller. Canceling it does not necessarily cancel the shared download. */
    private var downloadTask: AnimationDownloadTask? = null

    /** Cached file currently used for playback. It is unprotected when playback stops or switches resources. */
    private var protectedPlaybackFile: File? = null

    /** Download request sequence. Used to ignore stale callbacks. */
    private var downloadRequestId = 0L

    /**
     *  Plays animation. URL sources are downloaded first; file and asset sources are passed to the parent flow.
     *  @param request Animation request.
     */
    override fun play(request: AnimationRequest) {
        if (!isMainThread()) {
            runOnMain { play(request) }
            return
        }
        cancelDownload()
        releaseProtectedPlaybackFile()
        // URL sources need the download flow.
        val source = request.source
        if (source is AnimationSource.Url) {
            playUrl(request, source.url, source.priority)
        } else {
            super.play(request)
        }
    }

    // Stops current playback and cancels any pending download.
    override fun stop(clear: Boolean) {
        if (!isMainThread()) {
            runOnMain { stop(clear) }
            return
        }
        cancelDownload()
        releaseProtectedPlaybackFile()
        super.stop(clear)
    }

    /** Releases the player view, cancels download, and unprotects the current cached file. */
    override fun release() {
        if (!isMainThread()) {
            runOnMain { release() }
            return
        }
        cancelDownload()
        releaseProtectedPlaybackFile()
        super.release()
    }

    /** Fetches a URL resource through the download manager, then plays it as a local file. */
    private fun playUrl(request: AnimationRequest, url: String, priority: com.keke.giftplayer.animation.download.AnimationDownloadPriority) {
        // Callbacks must match this request id before continuing.
        val currentId = nextDownloadRequestId()
        clearPlaybackForNewRequest()
        animationCallback?.onLoadStart(request)
        if (url.isBlank()) {
            dispatchDownloadError(currentId, request, AnimationError.InvalidSource("URL is blank."))
            return
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            dispatchDownloadError(currentId, request, AnimationError.InvalidSource("Only http and https URL are supported."))
            return
        }

        val resource = AnimationResource(
            url = url,
            format = request.format,
            priority = priority,
        )
        downloadTask = AnimationResourceManager.download(
            context = context,
            resource = resource,
            callback = object : AnimationDownloadCallback {
                override fun onSuccess(resource: AnimationResource, file: File) {
                    runOnMain {
                        if (!isCurrentDownload(currentId)) return@runOnMain
                        downloadTask = null
                        playDownloadedFile(currentId, request, file)
                    }
                }

                override fun onError(resource: AnimationResource, error: Throwable?) {
                    runOnMain {
                        if (!isCurrentDownload(currentId)) return@runOnMain
                        downloadTask = null
                        dispatchDownloadError(currentId, request, AnimationError.DownloadFailed(error))
                    }
                }
            },
        )
    }

    /** Wraps the downloaded file as a FilePath source and passes it to the shared playback flow. */
    private fun playDownloadedFile(
        currentId: Long,
        request: AnimationRequest,
        file: File,
    ) {
        if (!isCurrentDownload(currentId)) return
        protectPlaybackFile(file)
        super.play(request.copy(source = AnimationSource.FilePath(file.absolutePath)))
    }

    /** Dispatches download-stage errors. Only the current request can notify callbacks. */
    private fun dispatchDownloadError(
        currentId: Long,
        request: AnimationRequest,
        error: AnimationError,
    ) {
        if (!isCurrentDownload(currentId)) return
        AnimationLog.e("download error: ${error.javaClass.simpleName}")
        animationCallback?.onError(request, error)
    }

    /** Cancels the current download wait and invalidates stale callbacks. */
    private fun cancelDownload() {
        downloadRequestId += 1
        downloadTask?.let {
            AnimationLog.i("download cancel requested")
            it.cancel()
        }
        downloadTask = null
    }

    /** Creates a new download request id. */
    private fun nextDownloadRequestId(): Long {
        downloadRequestId += 1
        return downloadRequestId
    }

    /** Returns whether the callback belongs to the current download request. */
    private fun isCurrentDownload(currentId: Long): Boolean {
        return currentId == downloadRequestId
    }

    /** Protects the current playback file from cache cleanup. */
    private fun protectPlaybackFile(file: File) {
        releaseProtectedPlaybackFile()
        protectedPlaybackFile = file
        AnimationResourceManager.protectFile(file)
    }

    /** Removes current playback file protection so later cleanup can evict it by LRU policy. */
    private fun releaseProtectedPlaybackFile() {
        protectedPlaybackFile?.let(AnimationResourceManager::unprotectFile)
        protectedPlaybackFile = null
    }

}
