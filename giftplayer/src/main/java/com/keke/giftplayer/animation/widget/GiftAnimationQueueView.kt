package com.keke.giftplayer.animation.widget

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import com.keke.giftplayer.animation.core.AnimationCallback
import com.keke.giftplayer.animation.core.AnimationError
import com.keke.giftplayer.internal.GiftPlayerLog
import com.keke.giftplayer.animation.core.AnimationQueueConfig
import com.keke.giftplayer.animation.core.AnimationRequest
import com.keke.giftplayer.animation.core.AnimationSource
import com.keke.giftplayer.animation.download.AnimationDownloadCallback
import com.keke.giftplayer.animation.download.AnimationDownloadTask
import com.keke.giftplayer.animation.download.AnimationResource
import com.keke.giftplayer.animation.download.AnimationResourceManager
import java.io.File

/**
 * Created by keke on 2026/09/14.
 * Desc: 队列动画播放器。
 * 就绪优先模式，适用场景：房间礼物
 * url1入列 正在下载，url2入列，如url2文件已有缓存或者比url1先下载成功，则先播放url2，待url1下载完成后播放url1。
 */
open class GiftAnimationQueueView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AnimationPlayerView(context, attrs, defStyleAttr) {
    private class Message(val original: AnimationRequest) {
        var playback = original.copy()
        var download: AnimationDownloadTask? = null
        var file: File? = null
        var expiry: Runnable? = null
        var prepared = false
    }

    private val queue = ReadyPlaybackQueue<Message>()
    private val handler = Handler(Looper.getMainLooper())
    private var callback: AnimationCallback? = null
    private var disposed = false
    private var pumpPosted = false
    private var config = AnimationQueueConfig()
    private val pump = Runnable {
        pumpPosted = false
        advance()
    }

    private val bridge = object : AnimationCallback {
        override fun onStart(request: AnimationRequest) {
            postPlaybackEvent(request) {
                if (queue.paused) superPause()
                forward(request) { onStart(it) }
            }
        }

        override fun onProgress(request: AnimationRequest, progress: Float) =
            postPlaybackEvent(request) { forward(request) { onProgress(it, progress) } }

        override fun onRepeat(request: AnimationRequest) =
            postPlaybackEvent(request) { forward(request) { onRepeat(it) } }

        override fun onComplete(request: AnimationRequest) =
            postPlaybackEvent(request) { terminal(request) { onComplete(it) } }

        override fun onCancel(request: AnimationRequest) =
            postPlaybackEvent(request) { terminal(request) { onCancel(it) } }

        override fun onError(request: AnimationRequest, error: AnimationError) = postPlaybackEvent(request) {
            terminal(request) {
                onError(it, if (error is AnimationError.UnsupportedFormat) {
                    AnimationError.UnsupportedFormat(it.source)
                } else error)
            }
        }
    }

    init {
        super.setCallback(bridge)
    }

    override fun setCallback(callback: AnimationCallback?) {
        runOnMain { if (!disposed) this.callback = callback }
    }

    /** 消息入队时保存当前配置。 */
    fun setQueueConfig(config: AnimationQueueConfig) {
        runOnMain { if (!disposed) this.config = config }
    }

    /** 此播放器的 play 与默认优先级 enqueue 一样，都会追加播放消息。 */
    override fun play(request: AnimationRequest) = enqueue(request)

    /**
     * 追加一条播放消息。播放优先级由接入方定义，数值越大越先从已准备消息中播放。
     */
    @JvmOverloads
    fun enqueue(request: AnimationRequest, playbackPriority: Int = 0) {
        runOnMain {
            if (disposed) return@runOnMain
            val entry = queue.add(
                value = Message(request),
                playbackPriority = playbackPriority,
                now = SystemClock.elapsedRealtime(),
                ttlMillis = config.messageTtlMillis,
            )
            GiftPlayerLog.i(
                "queue enqueue: queueId=${entry.sequence}, playbackPriority=$playbackPriority, " +
                    requestLog(request),
            )
            notifyClient { onLoadStart(request) }
            if (!queue.isWaiting(entry) || disposed) return@runOnMain
            entry.ttlMillis?.let { ttl ->
                val expiry = Runnable { expireWaiting(); scheduleAdvance() }
                entry.value.expiry = expiry
                handler.postDelayed(expiry, (ttl - (SystemClock.elapsedRealtime() - entry.enqueuedAt)).coerceAtLeast(0))
            }
            try {
                prepare(entry)
            } catch (error: Exception) {
                preparationFailed(entry, error)
            }
        }
    }

    /** 仅清除尚未开始加载到播放器的消息。 */
    fun clearQueue() {
        runOnMain {
            val removed = queue.clearWaiting()
            removed.forEach { clean(it.value) }
            removed.forEach { entry -> notifyClient { onCancel(entry.value.original) } }
        }
    }

    override fun pause() {
        runOnMain {
            if (disposed) return@runOnMain
            queue.paused = true
            superPause()
        }
    }

    private fun superPause() = super.pause()

    override fun resume() {
        runOnMain {
            if (disposed) return@runOnMain
            queue.paused = false
            if (queue.current != null) super.resume()
            scheduleAdvance()
        }
    }

    override fun stop(clear: Boolean) {
        runOnMain {
            if (disposed) return@runOnMain
            val removed = queue.clearWaiting().toMutableList()
            queue.current?.let { queue.remove(it); removed.add(0, it) }
            // 先使队列状态失效，父类可能同步触发 onCancel 回调。
            super.stop(clear)
            removed.forEach { clean(it.value) }
            removed.forEach { entry -> notifyClient { onCancel(entry.value.original) } }
        }
    }

    /** 释放后不可再使用；其他线程已排队的调用也会被忽略。 */
    override fun release() {
        runOnMain {
            if (disposed) return@runOnMain
            disposed = true
            handler.removeCallbacksAndMessages(null)
            pumpPosted = false
            val removed = queue.clearWaiting().toMutableList()
            queue.current?.let { queue.remove(it); removed.add(it) }
            super.release()
            removed.forEach { clean(it.value) }
            callback = null
        }
    }

    private fun prepare(entry: ReadyPlaybackQueue.Entry<Message>) {
        val message = entry.value
        val source = message.original.source
        if (source !is AnimationSource.Url) {
            if (source is AnimationSource.FilePath) protect(message, File(source.path))
            message.prepared = true
            ready(entry)
            return
        }
        val task = AnimationResourceManager.download(
            AnimationResource(
                url = source.url,
                format = message.original.format,
                downloadPriority = source.downloadPriority,
            ),
            object : AnimationDownloadCallback {
                override fun onSuccess(resource: AnimationResource, file: File) {
                    runOnMain {
                        if (!queue.isWaiting(entry) || disposed || message.prepared) return@runOnMain
                        message.prepared = true
                        message.download = null
                        protect(message, file)
                        message.playback = message.original.copy(source = AnimationSource.FilePath(file.absolutePath))
                        GiftPlayerLog.i(
                            "queue ready: queueId=${entry.sequence}, playbackPriority=${entry.playbackPriority}, " +
                                "${requestLog(message.original)}, file=${file.name}",
                        )
                        ready(entry)
                    }
                }

                override fun onError(resource: AnimationResource, error: Throwable?) {
                    runOnMain { preparationFailed(entry, error) }
                }
            },
        )
        // 缓存命中或校验失败时，回调可能先于 download 返回执行。
        if (queue.isWaiting(entry) && !message.prepared && !disposed) message.download = task
        else task.cancel()
    }

    private fun protect(message: Message, file: File) {
        AnimationResourceManager.protectFile(file)
        message.file = file
    }

    private fun preparationFailed(entry: ReadyPlaybackQueue.Entry<Message>, error: Throwable?) {
        if (!queue.isWaiting(entry) || disposed || entry.value.prepared) return
        queue.remove(entry)
        clean(entry.value)
        notifyClient { onError(entry.value.original, AnimationError.DownloadFailed(error)) }
        scheduleAdvance()
    }

    private fun ready(entry: ReadyPlaybackQueue.Entry<Message>) {
        expireWaiting()
        if (queue.markReady(entry)) scheduleAdvance()
    }

    private fun expireWaiting() {
        val expired = queue.expired(SystemClock.elapsedRealtime()).filter(queue::remove)
        expired.forEach { clean(it.value) }
        expired.forEach { entry ->
            notifyClient { onError(entry.value.original, AnimationError.Cancelled("Queue message expired.")) }
        }
    }

    private fun scheduleAdvance() {
        if (disposed || pumpPosted) return
        pumpPosted = true
        handler.post(pump)
    }

    private fun advance() {
        if (disposed) return
        expireWaiting()
        if (disposed) return
        val entry = queue.takeNext() ?: return
        entry.value.expiry?.let(handler::removeCallbacks)
        entry.value.expiry = null
        try {
            GiftPlayerLog.i(
                "queue playback start -------------> queueId=${entry.sequence}, " +
                    "playbackPriority=${entry.playbackPriority}, " +
                    requestLog(entry.value.original),
            )
            super.play(entry.value.playback)
        } catch (error: Exception) {
            terminal(entry.value.playback) {
                onError(it, AnimationError.DecodeFailed(it.format, error))
            }
        }
    }

    private fun forward(request: AnimationRequest, event: AnimationCallback.(AnimationRequest) -> Unit) {
        val message = queue.current?.value ?: return
        if (message.playback === request) notifyClient { event(message.original) }
    }

    private fun postPlaybackEvent(request: AnimationRequest, event: () -> Unit) {
        if (disposed) return
        // 部分引擎在实际启动前回调 onStart，调用方的状态修改需要延后执行。
        handler.post {
            if (!disposed && queue.current?.value?.playback === request) event()
        }
    }

    private fun terminal(request: AnimationRequest, event: AnimationCallback.(AnimationRequest) -> Unit) {
        val entry = queue.current ?: return
        if (entry.value.playback !== request) return
        queue.remove(entry)
        clearPlaybackForNewRequest()
        clean(entry.value)
        GiftPlayerLog.i(
            "queue playback end: queueId=${entry.sequence}, playbackPriority=${entry.playbackPriority}, " +
                requestLog(entry.value.original),
        )
        try {
            notifyClient { event(entry.value.original) }
        } finally {
            scheduleAdvance()
        }
    }

    private fun requestLog(request: AnimationRequest): String {
        val source = when (val source = request.source) {
            is AnimationSource.Url -> "downloadPriority=${source.downloadPriority}，url=${source.url}"
            is AnimationSource.Asset -> "asset=${source.name}"
            is AnimationSource.FilePath -> "file=${source.path}"
        }
        return "$source, format=${request.format}, loop=${request.loopCount}"
    }

    private fun clean(message: Message) {
        message.expiry?.let(handler::removeCallbacks)
        message.expiry = null
        val task = message.download
        message.download = null
        task?.cancel()
        message.file?.let(AnimationResourceManager::unprotectFile)
        message.file = null
    }

    private fun notifyClient(event: AnimationCallback.() -> Unit) {
        if (disposed) return
        try {
            callback?.event()
        } catch (error: Exception) {
            GiftPlayerLog.e("queue callback failed: ${error.javaClass.simpleName}")
        }
    }
}
