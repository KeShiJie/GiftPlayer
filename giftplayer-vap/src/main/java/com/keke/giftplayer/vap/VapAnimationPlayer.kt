package com.keke.giftplayer.vap

import android.content.Context
import android.view.View
import com.keke.giftplayer.animation.core.AnimationError
import com.keke.giftplayer.animation.core.AnimationFormat
import com.keke.giftplayer.animation.core.AnimationRequest
import com.keke.giftplayer.animation.core.AnimationScaleType
import com.keke.giftplayer.animation.loader.ResolvedAnimationSource
import com.keke.giftplayer.animation.plugin.AnimationPlayerAdapter
import com.keke.giftplayer.animation.plugin.AnimationPlayerAdapterCallback
import com.tencent.qgame.animplayer.AnimConfig
import com.tencent.qgame.animplayer.AnimView
import com.tencent.qgame.animplayer.file.AssetsFileContainer
import com.tencent.qgame.animplayer.file.FileContainer
import com.tencent.qgame.animplayer.inter.IAnimListener
import com.tencent.qgame.animplayer.util.ScaleType
import java.io.File

/**
 * Created by keke on 2026/09/16.
 * Desc: VAP 播放器适配器。
 */
internal class VapAnimationPlayer(
    context: Context,
) : AnimationPlayerAdapter {
    private val animView = AnimView(context)
    private var callback: AnimationPlayerAdapterCallback? = null
    private var request: AnimationRequest? = null
    private var source: ResolvedAnimationSource? = null
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
                callback?.onProgress(((frameIndex + 1).toFloat() / total).coerceIn(0f, 1f))
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
                callback?.onError(
                    AnimationError.RenderFailed(
                        AnimationFormat.Vap,
                        IllegalStateException("VAP error $errorType: ${errorMsg.orEmpty()}"),
                    ),
                )
            }
        }
    }

    override fun load(
        source: ResolvedAnimationSource,
        request: AnimationRequest,
        callback: AnimationPlayerAdapterCallback,
    ) {
        this.callback = callback
        this.request = request
        this.source = source
        released = false
        stoppedByUser = false
        failed = false
        totalFrames = 0
        animView.setAnimListener(listener)
        animView.setLoop(request.loopCount)
        animView.setMute(!request.enableAudio)
        animView.setScaleType(request.scaleType.toVapScaleType())
        callback.onReady()
    }

    override fun play() {
        if (released || request == null) return
        stoppedByUser = false
        when (val resolvedSource = source) {
            is ResolvedAnimationSource.Asset -> {
                animView.startPlay(AssetsFileContainer(animView.context.assets, resolvedSource.name))
            }

            is ResolvedAnimationSource.FilePath -> {
                animView.startPlay(FileContainer(File(resolvedSource.path)))
            }

            null -> callback?.onError(AnimationError.InvalidSource("VAP source is not resolved."))
        }
    }

    override fun pause() {
        if (!released) {
            stoppedByUser = false
            animView.stopPlay()
        }
    }

    override fun resume() = play()

    override fun stop(clear: Boolean) {
        if (!released) {
            stoppedByUser = true
            animView.stopPlay()
        }
    }

    override fun release() {
        if (released) return
        released = true
        stoppedByUser = true
        animView.setAnimListener(null)
        animView.stopPlay()
        callback = null
        request = null
        source = null
    }

    override fun isPlaying(): Boolean = animView.isRunning()

    private fun AnimationScaleType.toVapScaleType(): ScaleType {
        return when (this) {
            AnimationScaleType.FitCenter -> ScaleType.FIT_CENTER
            AnimationScaleType.CenterCrop -> ScaleType.CENTER_CROP
            AnimationScaleType.FitXY -> ScaleType.FIT_XY
        }
    }
}
