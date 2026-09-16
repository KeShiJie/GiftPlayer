package com.keke.giftplayer.pag

import android.content.Context
import com.keke.giftplayer.animation.core.AnimationFormat
import com.keke.giftplayer.animation.plugin.AnimationPlayerAdapter
import com.keke.giftplayer.animation.plugin.AnimationPlayerPlugin

/**
 * Created by keke on 2026/09/16.
 * Desc: 将 libpag 播放能力接入 GiftPlayer。
 */
class PagPlayerPlugin : AnimationPlayerPlugin {
    override val format = AnimationFormat.Pag

    override fun create(context: Context): AnimationPlayerAdapter {
        return PagAnimationPlayer(context)
    }
}
