package com.keke.giftplayer.animation.player

import android.content.Context
import android.view.View
import com.keke.giftplayer.animation.core.AnimationError
import com.keke.giftplayer.animation.core.AnimationFormat
import com.keke.giftplayer.animation.core.AnimationLog
import com.keke.giftplayer.animation.core.AnimationRequest
import com.keke.giftplayer.animation.loader.ResolvedAnimationSource
import com.tencent.qgame.animplayer.AnimConfig
import com.tencent.qgame.animplayer.AnimView
import com.tencent.qgame.animplayer.file.AssetsFileContainer
import com.tencent.qgame.animplayer.file.FileContainer
import com.tencent.qgame.animplayer.inter.IAnimListener
import java.io.File

/**
 * Created by keke on 2026/4/16.
 * Desc: VAP
 */
internal class VapAnimationPlayer(
    context: Context,
) : AnimationPlayer {
    private val animView = AnimView(context)
    private var callback: AnimationPlayerCallback? = null
    private var request: AnimationRequest? = null
    private var resolvedSource: ResolvedAnimationSource? = null
    private var released = false
    private var stoppedByUser = false
    private var failed = false
    private var totalFrames = 0

    override val view: View = animView

    private val listener = object : IAnimListener {
        override fun onVideoConfigReady(config: AnimConfig): Boolean {
            totalFrames = config.totalFrames
            return true
        }

        override fun onVideoStart() {
            if (!released) callback?.onStart()
        }

        override fun onVideoRender(frameIndex: Int, config: AnimConfig?) {
            if (released) return
            val total = config?.totalFrames?.takeIf { it > 0 } ?: totalFrames
            if (total > 0) {
                callback?.onProgress(((frameIndex + 1).toFloat() / total.toFloat()).coerceIn(0f, 1f))
            }
        }

        override fun onVideoComplete() {
            if (!released && !failed) callback?.onComplete()
        }

        override fun onVideoDestroy() {
            if (!released && stoppedByUser) callback?.onCancel()
        }

        override fun onFailed(errorType: Int, errorMsg: String?) {
            if (!released) {
                failed = true
                AnimationLog.e("vap render failed: type=$errorType, error=${errorMsg.orEmpty()}")
                callback?.onError(
                    AnimationError.RenderFailed(
                        AnimationFormat.Vap,
                        IllegalStateException("VAP error $errorType: ${errorMsg.orEmpty()}")
                    )
                )
            }
        }
    }

    override fun load(
        resolvedSource: ResolvedAnimationSource,
        request: AnimationRequest,
        callback: AnimationPlayerCallback,
    ) {
        this.callback = callback
        this.request = request
        this.resolvedSource = resolvedSource
        released = false
        stoppedByUser = false
        failed = false
        totalFrames = 0
        AnimationLog.i("vap load start, source=${AnimationLog.resolvedSourceType(resolvedSource)}")
        animView.setAnimListener(listener)
        animView.setLoop(request.loopCount)
        animView.setMute(!request.enableAudio)
        animView.setScaleType(request.scaleType.toVapScaleType())
        callback.onReady()
    }

    override fun play() {
        val currentRequest = request ?: return
        if (released) return
        AnimationLog.i("vap play")
        stoppedByUser = false
        when (val source = resolvedSource) {
            is ResolvedAnimationSource.Asset -> {
                animView.startPlay(AssetsFileContainer(animView.context.assets, source.name))
            }
            is ResolvedAnimationSource.FilePath -> {
                animView.startPlay(FileContainer(File(source.path)))
            }
            null -> {
                AnimationLog.e("vap play failed: source is null")
                callback?.onError(AnimationError.InvalidSource("VAP source is not resolved."))
            }
        }
    }

    override fun pause() {
        if (!released) {
            stoppedByUser = false
            animView.stopPlay()
        }
    }

    override fun resume() {
        play()
    }

    override fun stop(clear: Boolean) {
        if (!released) {
            AnimationLog.i("vap stop clear=$clear")
            stoppedByUser = true
            animView.stopPlay()
        }
    }

    override fun release() {
        if (released) return
        AnimationLog.i("vap release")
        released = true
        stoppedByUser = true
        animView.setAnimListener(null)
        animView.stopPlay()
        callback = null
        request = null
        resolvedSource = null
    }

    override fun isPlaying(): Boolean {
        return animView.isRunning()
    }
}
