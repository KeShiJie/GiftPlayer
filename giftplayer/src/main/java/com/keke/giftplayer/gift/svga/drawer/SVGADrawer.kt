package com.keke.giftplayer.gift.svga.drawer

import android.graphics.Canvas
import android.widget.ImageView
import com.keke.giftplayer.gift.svga.SVGAVideoEntity
import com.keke.giftplayer.gift.svga.entities.SVGAVideoSpriteFrameEntity
import com.keke.giftplayer.gift.svga.utils.Pools
import com.keke.giftplayer.gift.svga.utils.SVGAScaleInfo
import kotlin.math.max

/**
 * Created by cuiminghui on 2017/3/29.
 */

open internal class SVGADrawer(val videoItem: SVGAVideoEntity) {

    val scaleInfo = SVGAScaleInfo()

    private val spritePool = Pools.SimplePool<SVGADrawerSprite>(max(1, videoItem.spriteList.size))

    inner class SVGADrawerSprite(var matteKeyValue: String? = null, var imageKeyValue: String? = null, var frameEntityValue: SVGAVideoSpriteFrameEntity? = null) {
        val matteKey get() = matteKeyValue
        val imageKey get() = imageKeyValue
        val frameEntity get() = frameEntityValue!!
    }

    internal fun requestFrameSprites(frameIndex: Int): List<SVGADrawerSprite> {
        return videoItem.spriteList.mapNotNull {
            if (frameIndex >= 0 && frameIndex < it.frames.size) {
                it.imageKey?.let { imageKey ->
                    if (!imageKey.endsWith(".matte") && it.frames[frameIndex].alpha <= 0.0) {
                        return@mapNotNull null
                    }
                    return@mapNotNull (spritePool.acquire() ?: SVGADrawerSprite()).apply {
                        matteKeyValue = it.matteKey
                        imageKeyValue = it.imageKey
                        frameEntityValue = it.frames[frameIndex]
                    }
                }
            }
            return@mapNotNull null
        }
    }

    internal fun releaseFrameSprites(sprites: List<SVGADrawerSprite>) {
        sprites.forEach { spritePool.release(it) }
    }

    open fun drawFrame(canvas: Canvas, frameIndex: Int, scaleType: ImageView.ScaleType) {
        scaleInfo.performScaleType(
            canvas.width.toFloat(),
            canvas.height.toFloat(),
            videoItem.videoSize.width.toFloat(),
            videoItem.videoSize.height.toFloat(),
            scaleType,
        )
    }

}
