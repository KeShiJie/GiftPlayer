package com.keke.giftplayer.animation.loader

import com.keke.giftplayer.animation.core.AnimationError
import com.keke.giftplayer.animation.core.AnimationLog
import com.keke.giftplayer.animation.core.AnimationSource
import java.io.File

/**
 * Created by keke on 2026/4/16.
 * Desc: 动画源解析器
 */
class AnimationSourceResolver {
    fun resolve(
        source: AnimationSource,
        callback: AnimationSourceResolveCallback,
    ): AnimationSourceResolveTask {
        return when (source) {
            is AnimationSource.Asset -> resolveAsset(source, callback)
            is AnimationSource.FilePath -> resolveFile(source, callback)
            is AnimationSource.Url -> resolveUrl(source, callback)
        }
    }

    private fun resolveAsset(
        source: AnimationSource.Asset,
        callback: AnimationSourceResolveCallback,
    ): AnimationSourceResolveTask {
        if (source.name.isBlank()) {
            AnimationLog.e("resolve asset failed: blank name")
            callback.onError(AnimationError.InvalidSource("Asset name is blank."))
        } else {
            callback.onSuccess(ResolvedAnimationSource.Asset(source.name))
        }
        return NoopAnimationSourceResolveTask
    }

    private fun resolveFile(
        source: AnimationSource.FilePath,
        callback: AnimationSourceResolveCallback,
    ): AnimationSourceResolveTask {
        val path = source.path
        when {
            path.isBlank() -> {
                AnimationLog.e("resolve file failed: blank path")
                callback.onError(AnimationError.InvalidSource("File path is blank."))
            }
            !File(path).isFile -> {
                AnimationLog.e("resolve file failed: file not found")
                callback.onError(AnimationError.FileNotFound(path))
            }
            else -> callback.onSuccess(ResolvedAnimationSource.FilePath(path))
        }
        return NoopAnimationSourceResolveTask
    }

    private fun resolveUrl(
        source: AnimationSource.Url,
        callback: AnimationSourceResolveCallback,
    ): AnimationSourceResolveTask {
        AnimationLog.e("resolve url failed: use BaseAnimationPlayerView for URL sources")
        callback.onError(AnimationError.InvalidSource("Use BaseAnimationPlayerView for URL sources."))
        return NoopAnimationSourceResolveTask
    }
}
