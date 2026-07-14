package com.keke.giftplayer.animation.download

import com.keke.giftplayer.animation.core.AnimationFormat

/**
 * Created by keke on 2026/06/23.
 * Desc: Animation resource descriptor.
 */
data class AnimationResource(
    /** Stable business resource id, such as a gift id, campaign animation id, or avatar frame id. */
    val id: String? = null,

    /** Actual download URL. */
    val url: String,

    /** Resource version, used to distinguish revisions of the same id. */
    val version: String? = null,

    /** File MD5. When provided by the server, downloaded files must match it. */
    val md5: String? = null,

    /** Animation format, used to detect extensionless URLs. */
    val format: AnimationFormat = AnimationFormat.Auto,

    /** Resource category, such as gift, room, or common. */
    val category: String? = null,

    /** Download priority. */
    val priority: AnimationDownloadPriority,
)
