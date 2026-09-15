package com.keke.giftplayer.animation.player

import android.widget.ImageView
import com.keke.giftplayer.animation.core.AnimationScaleType
import com.tencent.qgame.animplayer.util.ScaleType as VapScaleType
import org.libpag.PAGScaleMode

/**
 * Created by keke on 2026/4/16.
 */
internal fun AnimationScaleType.toImageScaleType(): ImageView.ScaleType {
    return when (this) {
        AnimationScaleType.FitCenter -> ImageView.ScaleType.FIT_CENTER
        AnimationScaleType.CenterCrop -> ImageView.ScaleType.CENTER_CROP
        AnimationScaleType.FitXY -> ImageView.ScaleType.FIT_XY
    }
}

internal fun AnimationScaleType.toVapScaleType(): VapScaleType {
    return when (this) {
        AnimationScaleType.FitCenter -> VapScaleType.FIT_CENTER
        AnimationScaleType.CenterCrop -> VapScaleType.CENTER_CROP
        AnimationScaleType.FitXY -> VapScaleType.FIT_XY
    }
}

internal fun AnimationScaleType.toPagScaleMode(): Int {
    return when (this) {
        AnimationScaleType.FitCenter -> PAGScaleMode.LetterBox
        AnimationScaleType.CenterCrop -> PAGScaleMode.Zoom
        AnimationScaleType.FitXY -> PAGScaleMode.Stretch
    }
}
