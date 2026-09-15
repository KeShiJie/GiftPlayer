package com.keke.giftplayer.animation.player

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.View
import android.view.TextureView
import com.keke.giftplayer.animation.core.AnimationError
import com.keke.giftplayer.animation.core.AnimationFillMode
import com.keke.giftplayer.animation.core.AnimationFormat
import com.keke.giftplayer.animation.core.AnimationLog
import com.keke.giftplayer.animation.core.AnimationRequest
import com.keke.giftplayer.animation.loader.ResolvedAnimationSource
import org.libpag.PAGFile
import org.libpag.PAGView

/**
 * Created by keke on 2026/4/16.
 * Desc:PAG动画播放器。
 */
internal class PagAnimationPlayer(
    context: Context,
) : AnimationPlayer {
    private val pagView = PAGView(context)
    private var callback: AnimationPlayerCallback? = null
    private var loaded = false
    private var released = false
    private var stoppedByUser = false
    private var pendingPlay = false
    private var waitingForAttach = false

    override val view: View = pagView

    private val listener = object : PAGView.PAGViewListener {
        override fun onAnimationStart(view: PAGView?) {
            if (!released) callback?.onStart()
        }

        override fun onAnimationEnd(view: PAGView?) {
            if (!released) callback?.onComplete()
        }

        override fun onAnimationCancel(view: PAGView?) {
            if (!released && stoppedByUser) callback?.onCancel()
        }

        override fun onAnimationRepeat(view: PAGView?) {
            if (!released) callback?.onRepeat()
        }

        override fun onAnimationUpdate(view: PAGView?) {
            if (!released) callback?.onProgress((view?.progress ?: 0.0).toFloat().coerceIn(0f, 1f))
        }
    }

    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            waitingForAttach = false
            startPlayWhenReady()
        }

        override fun onViewDetachedFromWindow(v: View) = Unit
    }

    private val surfaceListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            startPlayWhenReady()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            startPlayWhenReady()
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            return false
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
    }

    init {
        pagView.surfaceTextureListener = surfaceListener
    }

    override fun load(
        resolvedSource: ResolvedAnimationSource,
        request: AnimationRequest,
        callback: AnimationPlayerCallback,
    ) {
        this.callback = callback
        released = false
        loaded = false
        stoppedByUser = false
        pendingPlay = false
        waitingForAttach = false
        AnimationLog.i("pag load start, source=${AnimationLog.resolvedSourceType(resolvedSource)}")
        pagView.setRepeatCount(request.loopCount)
        pagView.setScaleMode(request.scaleType.toPagScaleMode())
        pagView.addListener(listener)
        val success = when (resolvedSource) {
            is ResolvedAnimationSource.Asset -> {
                pagView.composition = PAGFile.Load(pagView.context.assets, resolvedSource.name)
                pagView.composition != null
            }
            is ResolvedAnimationSource.FilePath -> pagView.setPath(resolvedSource.path)
        }
        if (success) {
            AnimationLog.i(
                "pag load success, duration=${pagView.duration()}, view=${pagView.width}x${pagView.height}, " +
                    "attached=${pagView.isAttachedToWindow}, surface=${pagView.isAvailable}"
            )
            pagView.progress = 0.0
            pagView.flush()
            loaded = true
            callback.onReady()
        } else {
            AnimationLog.e("pag decode failed")
            callback.onError(AnimationError.DecodeFailed(AnimationFormat.Pag, null))
        }
    }

    override fun play() {
        if (!released && loaded) {
            AnimationLog.i(
                "pag play, view=${pagView.width}x${pagView.height}, attached=${pagView.isAttachedToWindow}, " +
                    "surface=${pagView.isAvailable}, duration=${pagView.duration()}"
            )
            stoppedByUser = false
            pendingPlay = true
            startPlayWhenReady()
        }
    }

    override fun pause() {
        if (!released) {
            pendingPlay = false
            waitingForAttach = false
            stoppedByUser = false
            pagView.stop()
        }
    }

    override fun resume() {
        play()
    }

    override fun stop(clear: Boolean) {
        if (!released) {
            AnimationLog.i("pag stop clear=$clear")
            pendingPlay = false
            waitingForAttach = false
            stoppedByUser = true
            pagView.stop()
            if (clear) {
                pagView.progress = 0.0
                pagView.flush()
            }
        }
    }

    override fun release() {
        if (released) return
        AnimationLog.i("pag release")
        released = true
        pendingPlay = false
        waitingForAttach = false
        stoppedByUser = true
        pagView.removeOnAttachStateChangeListener(attachListener)
        pagView.surfaceTextureListener = null
        pagView.removeListener(listener)
        pagView.stop()
        pagView.freeCache()
        pagView.composition = null
        callback = null
        loaded = false
    }

    override fun isPlaying(): Boolean {
        return pagView.isPlaying
    }

    private fun startPlayWhenReady() {
        if (released || !loaded || !pendingPlay) return
        if (!pagView.isAttachedToWindow) {
            if (!waitingForAttach) {
                waitingForAttach = true
                pagView.addOnAttachStateChangeListener(attachListener)
            }
            return
        }
        if (!pagView.isAvailable) {
            return
        }
        if (pagView.width <= 0 || pagView.height <= 0) {
            pagView.post {
                startPlayWhenReady()
            }
            return
        }
        waitingForAttach = false
        pagView.removeOnAttachStateChangeListener(attachListener)
        pendingPlay = false
        pagView.progress = 0.0
        pagView.flush()
        pagView.play()
    }
}
