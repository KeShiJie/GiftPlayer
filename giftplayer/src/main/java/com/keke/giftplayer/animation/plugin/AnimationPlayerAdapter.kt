package com.keke.giftplayer.animation.plugin

import android.view.View
import com.keke.giftplayer.animation.core.AnimationRequest
import com.keke.giftplayer.animation.loader.ResolvedAnimationSource

/**
 * Created by keke on 2026/09/16.
 * Desc: 动画引擎接入 GiftPlayer 时需要实现的播放器适配接口。
 */
interface AnimationPlayerAdapter {
    val view: View

    fun load(
        source: ResolvedAnimationSource,
        request: AnimationRequest,
        callback: AnimationPlayerAdapterCallback,
    )

    fun play()
    fun pause()
    fun resume()
    fun stop(clear: Boolean)
    fun release()
    fun isPlaying(): Boolean
}
