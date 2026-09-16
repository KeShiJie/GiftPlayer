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
    private var requestId = 0L
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
        GiftPlayerLog.i("play requestId=$currentId, ${GiftPlayerLog.requestSummary(request)}")
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
                        GiftPlayerLog.e("detect format failed requestId=$currentId, source=${GiftPlayerLog.resolvedSourceType(source)}")
                        dispatchError(currentId, request, AnimationError.UnsupportedFormat(request.source))
                        return@runOnMain
                    }
                    GiftPlayerLog.i(
                        "source resolved requestId=$currentId, resolvedSource=${GiftPlayerLog.resolvedSourceType(source)}, format=$format"
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
        val stoppedRequestId = requestId
        requestId += 1
        GiftPlayerLog.i(
            "stop requestId=$stoppedRequestId, nextGeneration=$requestId, " +
                    "clear=$clear, hasRequest=${request != null}",
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
        val releasedRequestId = requestId
        requestId += 1
        GiftPlayerLog.i(
            "release requestId=$releasedRequestId, nextGeneration=$requestId",
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
        requestId += 1
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
                        if (request.autoPlay) {
                            currentPlayer?.play()
                        }
                    }
                }
            }

            override fun onStart() {
                runOnMain {
                    if (isCurrent(currentId)) animationCallback?.onStart(request)
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
                    if (isCurrent(currentId)) animationCallback?.onComplete(request)
                }
            }

            override fun onCancel() {
                runOnMain {
                    if (isCurrent(currentId)) animationCallback?.onCancel(request)
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
        GiftPlayerLog.e("play error requestId=$currentId, error=${GiftPlayerLog.errorSummary(error)}")
        currentPlayer?.release()
        currentPlayer = null
        removeAllViews()
        animationCallback?.onError(request, error)
    }

    private fun nextRequestId(): Long {
        requestId += 1
        return requestId
    }

    private fun isCurrent(currentId: Long): Boolean {
        return !released && currentId == requestId
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
