package com.keke.giftplayer.animation

import android.content.Context
import com.keke.giftplayer.animation.core.AnimationLog
import com.keke.giftplayer.animation.core.AnimationLogListener
import com.keke.giftplayer.animation.download.AnimationDownloadConfig
import com.keke.giftplayer.animation.download.AnimationResourceManager

/**
 * Created by keke on 2026/06/23.
 * Desc: Animation library initialization entry point.
 */
object AnimationInitializer {

    /**
     * 初始化动画库，建议在 Application 中调用。
     * @param downloadConfig 下载与缓存配置，不影响日志设置。
     * @param isLogEnabled 是否打印 Logcat，默认关闭，不影响日志监听器。
     * @param logListener 全局日志监听器，默认 null；每次初始化均替换旧监听器。
     */
    @JvmStatic
    @JvmOverloads
    fun init(
        context: Context,
        downloadConfig: AnimationDownloadConfig = AnimationDownloadConfig(),
        isLogEnabled: Boolean = false,
        logListener: AnimationLogListener? = null,
    ) {
        AnimationLog.setLogcatEnabled(isLogEnabled)
        AnimationLog.setListener(logListener)
        AnimationResourceManager.init(context, downloadConfig)
    }
}
