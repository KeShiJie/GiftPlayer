package com.keke.giftplayer.animation.player

import com.keke.giftplayer.animation.core.AnimationError

/**
 * Created by keke on 2026/4/16.
 * Desc: 内部动画播放器回调。
 */
internal interface AnimationPlayerCallback {
    fun onReady()
    fun onStart()
    fun onProgress(progress: Float)
    fun onRepeat()
    fun onComplete()
    fun onCancel()
    fun onError(error: AnimationError)
}
