package com.keke.giftplayer.animation.loader

import com.keke.giftplayer.animation.core.AnimationError

/**
 * Created by keke on 2026/4/16.
 * Desc: Animation source resolve callback.
 */
interface AnimationSourceResolveCallback {
    fun onSuccess(source: ResolvedAnimationSource)
    fun onError(error: AnimationError)
}
