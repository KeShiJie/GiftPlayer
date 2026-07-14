package com.keke.giftplayer.animation.download

/**
 * Created by keke on 2026/06/23.
 * Desc: Animation resource download task handle.
 */
interface AnimationDownloadTask {
    /** Cancels the current caller's wait. The shared real download may continue. */
    fun cancel()
}
