package com.keke.giftplayer.animation.core

/**
 * Created by keke on 2026/4/16.
 * Desc: Animation playback callback.
 */
interface AnimationCallback {
    fun onLoadStart(request: AnimationRequest) {}
    fun onStart(request: AnimationRequest) {}
    fun onProgress(request: AnimationRequest, progress: Float) {}
    fun onRepeat(request: AnimationRequest) {}
    fun onComplete(request: AnimationRequest) {}
    fun onCancel(request: AnimationRequest) {}
    fun onError(request: AnimationRequest, error: AnimationError) {}
}
