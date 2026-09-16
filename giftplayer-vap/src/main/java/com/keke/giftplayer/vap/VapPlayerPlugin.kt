package com.keke.giftplayer.vap

import android.content.Context
import com.keke.giftplayer.animation.core.AnimationFormat
import com.keke.giftplayer.animation.plugin.AnimationPlayerAdapter
import com.keke.giftplayer.animation.plugin.AnimationPlayerPlugin

/**
 * Created by keke on 2026/09/16.
 * Desc: 将 VAP 播放能力接入 GiftPlayer。
 */
class VapPlayerPlugin : AnimationPlayerPlugin {
    override val format = AnimationFormat.Vap

    override fun create(context: Context): AnimationPlayerAdapter {
        return VapAnimationPlayer(context)
    }
}
