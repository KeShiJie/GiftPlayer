package com.keke.giftplayer.internal

import android.content.Context
import com.keke.giftplayer.animation.core.AnimationFormat
import com.keke.giftplayer.animation.player.SvgaAnimationPlayer
import com.keke.giftplayer.animation.plugin.AnimationPlayerAdapter
import com.keke.giftplayer.animation.plugin.AnimationPlayerPlugin

/**
 * Created by keke on 2026/09/16.
 * Desc: 管理内置和外部动画播放器插件。
 */
internal object AnimationPlayerPluginRegistry {
    private val builtInSvgaPlugin = object : AnimationPlayerPlugin {
        override val format = AnimationFormat.Svga

        override fun create(context: Context): AnimationPlayerAdapter {
            return SvgaAnimationPlayer(context)
        }
    }

    @Volatile
    private var plugins: Map<AnimationFormat, AnimationPlayerPlugin> =
        mapOf(AnimationFormat.Svga to builtInSvgaPlugin)

    fun replaceExternalPlugins(externalPlugins: List<AnimationPlayerPlugin>) {
        val registered = linkedMapOf<AnimationFormat, AnimationPlayerPlugin>(
            AnimationFormat.Svga to builtInSvgaPlugin,
        )
        externalPlugins.forEach { plugin ->
            require(plugin.format != AnimationFormat.Auto) {
                "AnimationFormat.Auto cannot be registered as a player plugin."
            }
            require(registered.putIfAbsent(plugin.format, plugin) == null) {
                "A player plugin for ${plugin.format} is already registered."
            }
        }
        plugins = registered.toMap()
    }

    fun find(format: AnimationFormat): AnimationPlayerPlugin? {
        return plugins[format]
    }
}
