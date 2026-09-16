package com.keke.giftplayer

/**
 * Created by keke on 2026/09/16.
 * Desc: Demo 业务自行定义的下载和播放优先级。
 */
internal enum class DemoDownloadPriority(val level: Int) {
    ImmediateGift(100),
    RealtimeGift(80),
    BatchPreload(10),
}

internal enum class DemoPlaybackPriority(val level: Int) {
    SelfGift(100),
    NormalGift(50),
}
