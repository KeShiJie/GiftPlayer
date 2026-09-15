package com.keke.giftplayer.animation.player

import android.view.View
import com.keke.giftplayer.animation.core.AnimationRequest
import com.keke.giftplayer.animation.loader.ResolvedAnimationSource

/**
 * Created by keke on 2026/4/16.
 * Desc: 动画播放器适配器接
 */
internal interface AnimationPlayer {
    val view: View

    fun load(
        resolvedSource: ResolvedAnimationSource,
        request: AnimationRequest,
        callback: AnimationPlayerCallback,
    )

    fun play()
    fun pause()
    fun resume()
    fun stop(clear: Boolean)
    fun release()
    fun isPlaying(): Boolean
}
