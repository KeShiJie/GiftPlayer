package com.keke.giftplayer.animation.download

/**
 * Created by keke on 2026/06/23.
 * Desc: Animation resource download priority.
 */
enum class AnimationDownloadPriority(val level: Int) {
    /** Highest download priority. */
    Highest(4),

    /** High download priority. */
    High(3),

    /** Medium download priority. */
    Medium(2),

    /** Lowest download priority. */
    Low(1),
}
