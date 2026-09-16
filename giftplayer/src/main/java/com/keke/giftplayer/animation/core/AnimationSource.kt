package com.keke.giftplayer.animation.core

/**
 * Created by keke on 2026/4/16.
 * Desc: Animation source.
 */
sealed class AnimationSource {
    data class Url(
        val url: String,
        /** 下载等待队列中的优先级数值；数值越大越先下载，由接入方定义。 */
        val downloadPriority: Int = 0,
    ) : AnimationSource()
    data class FilePath(val path: String) : AnimationSource()
    data class Asset(val name: String) : AnimationSource()
}
