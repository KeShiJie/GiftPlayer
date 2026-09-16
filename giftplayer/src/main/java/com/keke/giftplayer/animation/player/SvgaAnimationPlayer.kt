package com.keke.giftplayer.animation.player

import android.content.Context
import android.util.Pair
import android.view.View
import com.keke.giftplayer.animation.core.AnimationError
import com.keke.giftplayer.animation.core.AnimationFillMode
import com.keke.giftplayer.animation.core.AnimationFormat
import com.keke.giftplayer.internal.GiftPlayerLog
import com.keke.giftplayer.animation.core.AnimationRequest
import com.keke.giftplayer.animation.loader.ResolvedAnimationSource
import com.keke.giftplayer.animation.plugin.AnimationPlayerAdapter
import com.keke.giftplayer.animation.plugin.AnimationPlayerAdapterCallback
import com.keke.giftplayer.gift.svga.SVGACallbackAdapter
import com.keke.giftplayer.gift.svga.SVGACache
import com.keke.giftplayer.gift.svga.SVGAImageView
import com.keke.giftplayer.gift.svga.SVGAParser
import com.keke.giftplayer.gift.svga.SVGAVideoEntity
import java.io.File

/**
 * Created by keke on 2026/4/16.
 * Desc: SVGA
 */
internal class SvgaAnimationPlayer(
    context: Context,
) : AnimationPlayerAdapter {
    private val svgaView = SVGAImageView(context)
    private var callback: AnimationPlayerAdapterCallback? = null
    private var loaded = false
    private var released = false

    override val view: View = svgaView

    override fun load(
        source: ResolvedAnimationSource,
        request: AnimationRequest,
        callback: AnimationPlayerAdapterCallback,
    ) {
        this.callback = callback
        released = false
        loaded = false
        GiftPlayerLog.i("svga load start, source=${GiftPlayerLog.resolvedSourceType(source)}")
        svgaView.scaleType = request.scaleType.toImageScaleType()
        svgaView.loops = request.loopCount
        svgaView.fillMode = request.fillMode.toSvgaFillMode()
        svgaView.clearsAfterDetached = false
        svgaView.callback = object : SVGACallbackAdapter() {
            override fun onFinished() {
                if (!released) callback.onComplete()
            }

            override fun onRepeat() {
                if (!released) callback.onRepeat()
            }

            override fun onStep(frame: Int, percentage: Double) {
                if (!released) callback.onProgress(percentage.toFloat().coerceIn(0f, 1f))
            }

            override fun onPause() {
            }
        }

        val parser = SVGAParser(svgaView.context)
        val parseCompletion = object : SVGAParser.ParseCompletion {
            override fun onComplete(videoItem: SVGAVideoEntity) {
                if (released) {
                    videoItem.clear()
                    return
                }
                GiftPlayerLog.i("svga load success")
                svgaView.setVideoItem(videoItem)
                loaded = true
                callback.onReady()
            }

            override fun onError() {
                if (!released) {
                    GiftPlayerLog.e("svga decode failed")
                    callback.onError(AnimationError.DecodeFailed(AnimationFormat.Svga, null))
                }
            }

            override fun getViewSize(): Pair<Int, Int> {
                return Pair.create(svgaView.width, svgaView.height)
            }
        }

        when (source) {
            is ResolvedAnimationSource.Asset -> parser.decodeFromAssets(source.name, parseCompletion)
            is ResolvedAnimationSource.FilePath -> {
                val file = File(source.path)
                if (!file.isFile) {
                    callback.onError(AnimationError.FileNotFound(source.path))
                    return
                }
                parser.decodeFromInputStream(
                    file.inputStream(),
                    SVGACache.buildCacheKey(source.path),
                    parseCompletion,
                    closeInputStream = true,
                )
            }
        }
    }

    override fun play() {
        if (!released && loaded) {
            GiftPlayerLog.i("svga play")
            callback?.onStart()
            svgaView.startAnimation()
        }
    }

    override fun pause() {
        if (!released) {
            svgaView.pauseAnimation()
        }
    }

    override fun resume() {
        play()
    }

    override fun stop(clear: Boolean) {
        if (!released) {
            GiftPlayerLog.i("svga stop clear=$clear")
            svgaView.stopAnimation(clear)
        }
    }

    override fun release() {
        if (released) return
        GiftPlayerLog.i("svga release")
        released = true
        loaded = false
        svgaView.callback = null
        svgaView.stopAnimation(true)
        svgaView.clear()
        callback = null
    }

    override fun isPlaying(): Boolean {
        return svgaView.isAnimating
    }

    private fun AnimationFillMode.toSvgaFillMode(): SVGAImageView.FillMode {
        return when (this) {
            AnimationFillMode.Backward -> SVGAImageView.FillMode.Backward
            AnimationFillMode.Forward -> SVGAImageView.FillMode.Forward
            AnimationFillMode.Clear -> SVGAImageView.FillMode.Clear
        }
    }
}
