package com.keke.giftplayer.animation.plugin

import android.content.Context
import com.keke.giftplayer.animation.core.AnimationFormat

/**
 * Created by keke on 2026/09/16.
 * Desc: 一种动画格式的播放器插件工厂。
 */
interface AnimationPlayerPlugin {
    val format: AnimationFormat

    fun create(context: Context): AnimationPlayerAdapter
}
