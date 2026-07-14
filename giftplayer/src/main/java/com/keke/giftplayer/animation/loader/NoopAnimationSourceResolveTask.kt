package com.keke.giftplayer.animation.loader

/**
 * Created by keke on 2026/4/16.
 * Desc: No-op animation source resolve task.
 */
internal object NoopAnimationSourceResolveTask : AnimationSourceResolveTask {
    override fun cancel() = Unit
}
