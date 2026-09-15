package com.keke.giftplayer.animation.loader

/**
 * Created by keke on 2026/4/16.
 * Desc: 空操作动画源解析
 */
internal object NoopAnimationSourceResolveTask : AnimationSourceResolveTask {
    override fun cancel() = Unit
}
