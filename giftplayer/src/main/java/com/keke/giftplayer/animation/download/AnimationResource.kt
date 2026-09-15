package com.keke.giftplayer.animation.download

import com.keke.giftplayer.animation.core.AnimationFormat

/**
 * Created by keke on 2026/06/23.
 * Desc: 动画资源
 */
data class AnimationResource(
    /** 稳定的业务资源 ID，例如礼品 ID、活动动画 ID 或头像框 ID. */
    val id: String? = null,

    val url: String,

    /** 资源版本，用于区分同一 ID 的不同版本. */
    val version: String? = null,

    /** 文件MD5值。如果服务器提供 */
    val md5: String? = null,

    val format: AnimationFormat = AnimationFormat.Auto,

    /** 资源类别，例如礼物、房间或公共区域。 */
    val category: String? = null,

    val priority: AnimationDownloadPriority,
)
