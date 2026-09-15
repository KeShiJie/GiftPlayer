package com.keke.giftplayer.animation.loader

import com.keke.giftplayer.animation.core.AnimationFormat

/**
 * Created by keke on 2026/4/16.
 * Desc: 动画格式检测
 */
object AnimationFormatDetector {
    fun detect(explicitFormat: AnimationFormat, source: ResolvedAnimationSource): AnimationFormat {
        if (explicitFormat != AnimationFormat.Auto) {
            return explicitFormat
        }
        val value = when (source) {
            is ResolvedAnimationSource.Asset -> source.name
            is ResolvedAnimationSource.FilePath -> source.path
        }
        val path = value.substringBefore('?').substringBefore('#').lowercase()
        return when {
            path.endsWith(".svga") -> AnimationFormat.Svga
            path.endsWith(".pag") -> AnimationFormat.Pag
            path.endsWith(".vap") || path.endsWith(".mp4") -> AnimationFormat.Vap
            else -> AnimationFormat.Auto
        }
    }
}
