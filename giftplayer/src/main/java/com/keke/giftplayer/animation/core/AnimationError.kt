package com.keke.giftplayer.animation.core

/**
 * Created by keke on 2026/4/16.
 * Desc: 动画播放错误类型
 */
sealed class AnimationError {
    data class InvalidSource(val reason: String) : AnimationError()
    data class UnsupportedFormat(val source: AnimationSource) : AnimationError()
    data class PlayerPluginMissing(val format: AnimationFormat) : AnimationError()
    data class DownloadFailed(val cause: Throwable?) : AnimationError()
    data class FileNotFound(val path: String) : AnimationError()
    data class DecodeFailed(val format: AnimationFormat, val cause: Throwable?) : AnimationError()
    data class RenderFailed(val format: AnimationFormat, val cause: Throwable?) : AnimationError()
    data class StartTimeout(val timeoutMillis: Long) : AnimationError()
    data class Cancelled(val reason: String) : AnimationError()
}
