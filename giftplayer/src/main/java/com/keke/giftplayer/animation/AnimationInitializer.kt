package com.keke.giftplayer.animation

import android.content.Context
import com.keke.giftplayer.animation.download.AnimationDownloadConfig
import com.keke.giftplayer.animation.download.AnimationResourceManager

/**
 * Created by keke on 2026/06/23.
 * Desc: Animation library initialization entry point.
 */
object AnimationInitializer {

    /** Initializes the animation library with the default download and cache policy when omitted. */
    @JvmStatic
    @JvmOverloads
    fun init(
        context: Context,
        downloadConfig: AnimationDownloadConfig = AnimationDownloadConfig(),
    ) {
        AnimationResourceManager.init(context, downloadConfig)
    }
}
