package com.keke.giftplayer.animation.player

import android.widget.ImageView
import com.keke.giftplayer.animation.core.AnimationScaleType

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
