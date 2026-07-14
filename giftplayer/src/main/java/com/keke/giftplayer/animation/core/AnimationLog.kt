package com.keke.giftplayer.animation.core

import android.util.Log
import com.keke.giftplayer.animation.loader.ResolvedAnimationSource

/**
 * Created by keke on 2026/4/16.
 * Desc: Animation playback logger.
 */
object AnimationLog {
    const val TAG = "AnimationPlayer"
    @Volatile
    private var enabled = true

    @JvmStatic
    fun setEnabled(value: Boolean) {
        enabled = value
    }

    @JvmStatic
    fun isEnabled(): Boolean {
        return enabled
    }

    fun i(message: String) {
        if (!enabled) return
        Log.i(TAG, message)
    }

    fun w(message: String) {
        if (!enabled) return
        Log.w(TAG, message)
    }

    fun e(message: String) {
        if (!enabled) return
        Log.e(TAG, message)
    }

    fun sourceType(source: AnimationSource): String {
        return when (source) {
            is AnimationSource.Asset -> "asset"
            is AnimationSource.FilePath -> "file"
            is AnimationSource.Url -> "url"
        }
    }

    fun resolvedSourceType(source: ResolvedAnimationSource): String {
        return when (source) {
            is ResolvedAnimationSource.Asset -> "asset"
            is ResolvedAnimationSource.FilePath -> "file"
        }
    }

    fun requestSummary(request: AnimationRequest): String {
        return "source=${sourceType(request.source)}, format=${request.format}, loop=${request.loopCount}, " +
            "scale=${request.scaleType}, fill=${request.fillMode}, audio=${request.enableAudio}, " +
            "pauseWhenInvisible=${request.pauseWhenInvisible}"
    }

    fun errorSummary(error: AnimationError): String {
        return when (error) {
            is AnimationError.InvalidSource -> "InvalidSource(${error.reason})"
            is AnimationError.UnsupportedFormat -> "UnsupportedFormat(source=${sourceType(error.source)})"
            is AnimationError.DownloadFailed -> "DownloadFailed(${error.cause?.message.orEmpty()})"
            is AnimationError.FileNotFound -> "FileNotFound"
            is AnimationError.DecodeFailed -> "DecodeFailed(format=${error.format}, cause=${error.cause?.message.orEmpty()})"
            is AnimationError.RenderFailed -> "RenderFailed(format=${error.format}, cause=${error.cause?.message.orEmpty()})"
            is AnimationError.Cancelled -> "Cancelled(${error.reason})"
        }
    }
}
