package com.keke.giftplayer.animation.widget

import android.content.Context
import android.util.AttributeSet
import com.keke.giftplayer.animation.download.AnimationDownloadCallback
import com.keke.giftplayer.animation.download.AnimationDownloadTask
import com.keke.giftplayer.animation.download.AnimationResource
import com.keke.giftplayer.animation.download.AnimationResourceManager
import com.keke.giftplayer.animation.core.AnimationError
import com.keke.giftplayer.internal.GiftPlayerLog
import com.keke.giftplayer.animation.core.AnimationRequest
import com.keke.giftplayer.animation.core.AnimationSource
import java.io.File

/**
 * Created by keke on 2026/06/18.
 * Desc: 支持 URL 下载的基础动画播放器。 适合普通播放场景
 */
open class GiftAnimationPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AnimationPlayerView(context, attrs, defStyleAttr) {

    /** 当前调用方的下载等待句柄；取消等待不一定会取消共享下载。 */
    private var downloadTask: AnimationDownloadTask? = null

    /** 当前播放使用的缓存文件；停止播放或切换资源时解除保护。 */
    private var protectedPlaybackFile: File? = null

    /** 下载请求序号，用于忽略过期回调。 */
    private var downloadRequestId = 0L

    /**
     *  播放动画：URL 资源先下载，本地文件和 Asset 资源交给父类处理。
     *  @param request 动画播放请求。
     */
    override fun play(request: AnimationRequest) {
        if (!isMainThread()) {
            runOnMain { play(request) }
            return
        }
        cancelDownload()
        releaseProtectedPlaybackFile()
        // URL 资源需要先经过下载流程。
        val source = request.source
        if (source is AnimationSource.Url) {
            playUrl(request, source.url, source.downloadPriority)
        } else {
            super.play(request)
        }
    }

    // 停止当前播放并取消下载等待。
    override fun stop(clear: Boolean) {
        if (!isMainThread()) {
            runOnMain { stop(clear) }
            return
        }
        cancelDownload()
        releaseProtectedPlaybackFile()
        super.stop(clear)
    }

    /** 释放播放器、取消下载等待，并解除当前缓存文件的保护。 */
    override fun release() {
        if (!isMainThread()) {
            runOnMain { release() }
            return
        }
        cancelDownload()
        releaseProtectedPlaybackFile()
        super.release()
    }

    /** 通过下载管理器获取 URL 资源，再以本地文件形式播放。 */
    private fun playUrl(request: AnimationRequest, url: String, downloadPriority: Int) {
        // 回调必须匹配当前请求序号才继续处理。
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
            downloadPriority = downloadPriority,
        )
        downloadTask = AnimationResourceManager.download(
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

    /** 将下载文件包装为 FilePath 资源，交给通用播放流程。 */
    private fun playDownloadedFile(
        currentId: Long,
        request: AnimationRequest,
        file: File,
    ) {
        if (!isCurrentDownload(currentId)) return
        protectPlaybackFile(file)
        super.play(request.copy(source = AnimationSource.FilePath(file.absolutePath)))
    }

    /** 分发下载阶段的错误，仅允许当前请求触发回调。 */
    private fun dispatchDownloadError(
        currentId: Long,
        request: AnimationRequest,
        error: AnimationError,
    ) {
        if (!isCurrentDownload(currentId)) return
        GiftPlayerLog.e("download error: ${error.javaClass.simpleName}")
        animationCallback?.onError(request, error)
    }

    /** 取消当前下载等待，并使旧回调失效。 */
    private fun cancelDownload() {
        downloadRequestId += 1
        downloadTask?.let {
            GiftPlayerLog.i("download cancel requested")
            it.cancel()
        }
        downloadTask = null
    }

    /** 生成新的下载请求序号。 */
    private fun nextDownloadRequestId(): Long {
        downloadRequestId += 1
        return downloadRequestId
    }

    /** 判断回调是否属于当前下载请求。 */
    private fun isCurrentDownload(currentId: Long): Boolean {
        return currentId == downloadRequestId
    }

    /** 保护当前播放文件，避免被缓存清理删除。 */
    private fun protectPlaybackFile(file: File) {
        releaseProtectedPlaybackFile()
        protectedPlaybackFile = file
        AnimationResourceManager.protectFile(file)
    }

    /** 解除当前播放文件的保护，允许后续按最近使用时间清理。 */
    private fun releaseProtectedPlaybackFile() {
        protectedPlaybackFile?.let(AnimationResourceManager::unprotectFile)
        protectedPlaybackFile = null
    }

}
