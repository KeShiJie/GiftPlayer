package com.keke.giftplayer.animation.core

/**
 * Created by keke on 2026/09/14.
 * Desc: Waiting-message lifetime; playing messages never expire.
 */
data class AnimationQueueConfig(
    val messageTtlMillis: Long? = null,
) {
    init {
        require(messageTtlMillis == null || messageTtlMillis > 0) {
            "messageTtlMillis must be positive or null."
        }
    }
}
