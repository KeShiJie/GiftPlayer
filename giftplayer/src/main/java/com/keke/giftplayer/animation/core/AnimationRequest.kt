package com.keke.giftplayer.animation.core

/**
 * Created by keke on 2026/4/16.
 * Desc: Animation playback request.
 *
 * @property source Animation source, supports URL, local file, and assets.
 * @property format Animation format, auto-detected by default.
 * @property loopCount Loop count.
 * @property autoPlay Whether playback starts automatically after load.
 * @property scaleType Animation scaling mode.
 * @property fillMode Frame fill mode after playback ends.
 * @property enableAudio Whether embedded audio is enabled.
 * @property pauseWhenInvisible Whether playback pauses when the view becomes invisible.
 */
data class AnimationRequest(
    val source: AnimationSource,
    val format: AnimationFormat = AnimationFormat.Auto,
    val loopCount: Int = 1,
    val autoPlay: Boolean = true,
    val scaleType: AnimationScaleType = AnimationScaleType.FitCenter,
    val fillMode: AnimationFillMode = AnimationFillMode.Forward,
    val enableAudio: Boolean = true,
    val pauseWhenInvisible: Boolean = false,
)
