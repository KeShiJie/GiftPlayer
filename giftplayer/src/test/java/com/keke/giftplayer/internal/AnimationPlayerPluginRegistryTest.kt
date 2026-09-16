package com.keke.giftplayer.internal

import android.content.Context
import com.keke.giftplayer.animation.core.AnimationFormat
import com.keke.giftplayer.animation.plugin.AnimationPlayerAdapter
import com.keke.giftplayer.animation.plugin.AnimationPlayerPlugin
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Created by keke on 2026/09/16.
 * Desc: 验证内置播放器和外部播放器插件的注册规则。
 */
class AnimationPlayerPluginRegistryTest {
    @After
    fun resetRegistry() {
        AnimationPlayerPluginRegistry.replaceExternalPlugins(emptyList())
    }

    @Test
    fun svgaIsAlwaysRegistered() {
        AnimationPlayerPluginRegistry.replaceExternalPlugins(emptyList())

        assertNotNull(AnimationPlayerPluginRegistry.find(AnimationFormat.Svga))
    }

    @Test
    fun externalPagAndVapPluginsAreRegistered() {
        val pagPlugin = FakePlugin(AnimationFormat.Pag)
        val vapPlugin = FakePlugin(AnimationFormat.Vap)

        AnimationPlayerPluginRegistry.replaceExternalPlugins(listOf(pagPlugin, vapPlugin))

        assertSame(pagPlugin, AnimationPlayerPluginRegistry.find(AnimationFormat.Pag))
        assertSame(vapPlugin, AnimationPlayerPluginRegistry.find(AnimationFormat.Vap))
    }

    @Test
    fun duplicateFormatIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AnimationPlayerPluginRegistry.replaceExternalPlugins(
                listOf(
                    FakePlugin(AnimationFormat.Pag),
                    FakePlugin(AnimationFormat.Pag),
                ),
            )
        }
    }

    @Test
    fun replacingBuiltInSvgaPluginIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AnimationPlayerPluginRegistry.replaceExternalPlugins(
                listOf(FakePlugin(AnimationFormat.Svga)),
            )
        }
    }

    @Test
    fun autoFormatIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AnimationPlayerPluginRegistry.replaceExternalPlugins(
                listOf(FakePlugin(AnimationFormat.Auto)),
            )
        }
    }

    private class FakePlugin(
        override val format: AnimationFormat,
    ) : AnimationPlayerPlugin {
        override fun create(context: Context): AnimationPlayerAdapter {
            error("Player creation is outside the registry contract under test.")
        }
    }
}
