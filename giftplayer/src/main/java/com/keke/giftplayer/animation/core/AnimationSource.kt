package com.keke.giftplayer.animation.core

import com.keke.giftplayer.animation.download.AnimationDownloadPriority

/**
 * Created by keke on 2026/4/16.
 * Desc: Animation source.
 */
sealed class AnimationSource {
    data class Url(
        val url: String,
        val priority: AnimationDownloadPriority = AnimationDownloadPriority.Highest,
    ) : AnimationSource()
    data class FilePath(val path: String) : AnimationSource()
    data class Asset(val name: String) : AnimationSource()
}
