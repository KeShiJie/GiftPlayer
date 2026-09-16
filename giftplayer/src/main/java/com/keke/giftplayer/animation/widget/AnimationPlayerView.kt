package com.keke.giftplayer.animation.widget

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.keke.giftplayer.animation.core.AnimationCallback
import com.keke.giftplayer.animation.core.AnimationError
import com.keke.giftplayer.animation.core.AnimationFormat
import com.keke.giftplayer.internal.GiftPlayerLog
import com.keke.giftplayer.animation.core.AnimationRequest
import com.keke.giftplayer.animation.loader.AnimationFormatDetector
import com.keke.giftplayer.animation.loader.AnimationSourceResolveCallback
import com.keke.giftplayer.animation.loader.AnimationSourceResolveTask
import com.keke.giftplayer.animation.loader.AnimationSourceResolver
import com.keke.giftplayer.animation.loader.NoopAnimationSourceResolveTask
import com.keke.giftplayer.animation.loader.ResolvedAnimationSource
import com.keke.giftplayer.animation.plugin.AnimationPlayerAdapter
import com.keke.giftplayer.animation.plugin.AnimationPlayerAdapterCallback
import com.keke.giftplayer.internal.AnimationPlayerPluginRegistry

/**
 * Created by keke on 2026/4/16.
 * Desc: 统一动画播放器视图
 */
open class AnimationPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val sourceResolver = AnimationSourceResolver()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentResolveTask: AnimationSourceResolveTask = NoopAnimationSourceResolveTask
    private var currentPlayer: AnimationPlayerAdapter? = null
    private var currentRequest: AnimationRequest? = null
    protected var animationCallback: AnimationCallback? = null
    //当前播放代次
    private var playbackGeneration = 0L
    //是否已释放
    private var released = false
    //因不可见而暂停
    private var pausedForVisibility = false

    open fun setCallback(callback: AnimationCallback?) {
        this.animationCallback = callback
    }

    open fun play(request: AnimationRequest) {
        if (!isMainThread()) {
            runOnMain { play(request) }
            return
        }
        released = false
        pausedForVisibility = false
        val currentId = nextRequestId()
        GiftPlayerLog.info("Playback", "Play Requested", "Playback Generation=$currentId | ${GiftPlayerLog.requestSummary(request)}")
        currentRequest = request
        currentResolveTask.cancel()
        currentPlayer?.release()
        currentPlayer = null
        removeAllViews()
        animationCallback?.onLoadStart(request)
        currentResolveTask = sourceResolver.resolve(request.source, object : AnimationSourceResolveCallback {
            override fun onSuccess(source: ResolvedAnimationSource) {
                runOnMain {
                    if (!isCurrent(currentId)) return@runOnMain
                    val format = AnimationFormatDetector.detect(request.format, source)
                    if (format == AnimationFormat.Auto) {
                        GiftPlayerLog.error(
                            "Playback",
                            "Format Detection Failed",
                            "Playback Generation=$currentId | ${GiftPlayerLog.traceSummary(request)} | ${GiftPlayerLog.resolvedSourceSummary(source)}",
                        )
                        dispatchError(currentId, request, AnimationError.UnsupportedFormat(request.source))
                        return@runOnMain
                    }
                    GiftPlayerLog.info(
                        "Playback",
                        "Source Resolved",
                        "Playback Generation=$currentId | ${GiftPlayerLog.traceSummary(request)} | ${GiftPlayerLog.resolvedSourceSummary(source)} | Format=${GiftPlayerLog.formatName(format)}",
                    )
                    val plugin = AnimationPlayerPluginRegistry.find(format)
                    if (plugin == null) {
                        dispatchError(currentId, request, AnimationError.PlayerPluginMissing(format))
                        return@runOnMain
                    }
                    val player = plugin.create(context)
                    currentPlayer = player
                    addView(
                        player.view,
                        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
                    )
                    player.load(source, request, createPlayerCallback(currentId, request))
                }
            }

            override fun onError(error: AnimationError) {
                runOnMain {
                    dispatchError(currentId, request, error)
                }
            }
        })
    }

    open fun pause() {
        if (!isMainThread()) {
            runOnMain { pause() }
            return
        }
        currentPlayer?.pause()
    }

    open fun resume() {
        if (!isMainThread()) {
            runOnMain { resume() }
            return
        }
        currentPlayer?.resume()
    }

    open fun stop(clear: Boolean = true) {
        if (!isMainThread()) {
            runOnMain { stop(clear) }
            return
        }
        val request = currentRequest
        val stoppedRequestId = playbackGeneration
        playbackGeneration += 1
        GiftPlayerLog.info(
            "Playback",
            "Stop Playback",
            "Playback Generation=$stoppedRequestId | ${request?.let(GiftPlayerLog::traceSummary) ?: "Trace ID=None"} | Next Playback Generation=$playbackGeneration | Clear=$clear | Has Request=${request != null}",
        )
        currentResolveTask.cancel()
        currentPlayer?.stop(clear)
        currentRequest = null
        request?.let { animationCallback?.onCancel(it) }
    }

    open fun release() {
        if (!isMainThread()) {
            runOnMain { release() }
            return
        }
        if (released) return
        released = true
        val releasedRequestId = playbackGeneration
        playbackGeneration += 1
        GiftPlayerLog.info(
            "Playback",
            "Release Player",
            "Playback Generation=$releasedRequestId | ${currentRequest?.let(GiftPlayerLog::traceSummary) ?: "Trace ID=None"} | Next Playback Generation=$playbackGeneration",
        )
        currentResolveTask.cancel()
        currentResolveTask = NoopAnimationSourceResolveTask
        currentPlayer?.release()
        currentPlayer = null
        removeAllViews()
        currentRequest = null
        animationCallback = null
        pausedForVisibility = false
    }

    fun isPlaying(): Boolean {
        return currentPlayer?.isPlaying() == true
    }

    protected fun clearPlaybackForNewRequest() {
        if (!isMainThread()) {
            runOnMain { clearPlaybackForNewRequest() }
            return
        }
        playbackGeneration += 1
        currentResolveTask.cancel()
        currentPlayer?.release()
        currentPlayer = null
        removeAllViews()
        currentRequest = null
        pausedForVisibility = false
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        val request = currentRequest ?: return
        if (!request.pauseWhenInvisible) return
        if (visibility == VISIBLE) {
            if (pausedForVisibility) {
                pausedForVisibility = false
                resume()
            }
        } else if (isPlaying()) {
            pausedForVisibility = true
            pause()
        }
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    private fun createPlayerCallback(
        currentId: Long,
        request: AnimationRequest,
    ): AnimationPlayerAdapterCallback {
        return object : AnimationPlayerAdapterCallback {
            override fun onReady() {
                runOnMain {
                    if (isCurrent(currentId)) {
                        GiftPlayerLog.info(
                            "Playback",
                            "Load Completed",
                            "Playback Generation=$currentId | ${GiftPlayerLog.traceSummary(request)}",
                        )
                        if (request.autoPlay) {
                            currentPlayer?.play()
                        }
                    }
                }
            }

            override fun onStart() {
                runOnMain {
                    if (isCurrent(currentId)) {
                        GiftPlayerLog.info("Playback", "Playback Started", "Playback Generation=$currentId | ${GiftPlayerLog.requestSummary(request)}")
                        animationCallback?.onStart(request)
                    }
                }
            }

            override fun onProgress(progress: Float) {
                runOnMain {
                    if (isCurrent(currentId)) animationCallback?.onProgress(request, progress)
                }
            }

            override fun onRepeat() {
                runOnMain {
                    if (isCurrent(currentId)) animationCallback?.onRepeat(request)
                }
            }

            override fun onComplete() {
                runOnMain {
                    if (isCurrent(currentId)) {
                        GiftPlayerLog.info(
                            "Playback",
                            "Playback Completed",
                            "Playback Generation=$currentId | ${GiftPlayerLog.traceSummary(request)}",
                        )
                        animationCallback?.onComplete(request)
                    }
                }
            }

            override fun onCancel() {
                runOnMain {
                    if (isCurrent(currentId)) {
                        GiftPlayerLog.info(
                            "Playback",
                            "Playback Cancelled",
                            "Playback Generation=$currentId | ${GiftPlayerLog.traceSummary(request)}",
                        )
                        animationCallback?.onCancel(request)
                    }
                }
            }

            override fun onError(error: AnimationError) {
                runOnMain {
                    dispatchError(currentId, request, error)
                }
            }
        }
    }

    private fun dispatchError(
        currentId: Long,
        request: AnimationRequest,
        error: AnimationError,
    ) {
        if (!isCurrent(currentId)) return
        GiftPlayerLog.error(
            "Playback",
            "Playback Failed",
            "Playback Generation=$currentId | ${GiftPlayerLog.traceSummary(request)} | ${GiftPlayerLog.errorSummary(error)}",
        )
        currentPlayer?.release()
        currentPlayer = null
        removeAllViews()
        animationCallback?.onError(request, error)
    }

    private fun nextRequestId(): Long {
        playbackGeneration += 1
        return playbackGeneration
    }

    private fun isCurrent(currentId: Long): Boolean {
        return !released && currentId == playbackGeneration
    }

    protected fun runOnMain(action: () -> Unit) {
        if (isMainThread()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    protected fun isMainThread(): Boolean {
        return Looper.myLooper() == Looper.getMainLooper()
    }
}
