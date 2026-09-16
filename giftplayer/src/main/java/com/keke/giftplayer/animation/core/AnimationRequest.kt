package com.keke.giftplayer.animation.core

import java.util.UUID

/**
 * Created by keke on 2026/4/16.
 * Desc: 动画播放请求。
 *
 * @property source 动画资源来源，支持远程 URL、本地文件和 assets。
 * @property traceId 单次动画消息的链路标识。建议传入服务端礼物消息 ID；未传时自动生成。
 * @property format 动画格式，默认根据文件扩展名自动识别；无扩展名时建议显式指定。
 * @property loopCount 播放次数，默认播放 1 次。
 * @property autoPlay 是否在加载完成后自动播放，默认开启。
 * @property scaleType 动画缩放模式，默认等比例缩放并居中显示。
 * @property fillMode 播放结束后的画面保留方式，默认保留最后一帧；当前仅 SVGA 适配器使用。
 * @property enableAudio 是否播放动画内置音频，默认开启；当前仅 VAP 适配器使用此开关。
 * @property pauseWhenInvisible View 不可见时是否暂停播放，默认关闭。
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
    val traceId: String = UUID.randomUUID().toString(),
)
