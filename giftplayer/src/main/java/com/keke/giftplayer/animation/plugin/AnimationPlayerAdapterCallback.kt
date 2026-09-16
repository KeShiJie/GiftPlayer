package com.keke.giftplayer.animation.plugin

import com.keke.giftplayer.animation.core.AnimationError

/**
 * Created by keke on 2026/09/16.
 * Desc: 动画引擎适配器向 GiftPlayer 分发播放状态的回调。
 */
interface AnimationPlayerAdapterCallback {
    fun onReady()
    fun onStart()
    fun onProgress(progress: Float)
    fun onRepeat()
    fun onComplete()
    fun onCancel()
    fun onError(error: AnimationError)
}
