package com.keke.giftplayer.animation.loader

/**
 * Created by keke on 2026/4/16.
 * Desc: 已解决的动画源。
 */
sealed class ResolvedAnimationSource {
    data class FilePath(val path: String) : ResolvedAnimationSource()
    data class Asset(val name: String) : ResolvedAnimationSource()
}
