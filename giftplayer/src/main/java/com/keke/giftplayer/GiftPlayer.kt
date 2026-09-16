package com.keke.giftplayer

import android.content.Context
import com.keke.giftplayer.animation.download.AnimationDownloadCallback
import com.keke.giftplayer.animation.download.AnimationDownloadTask
import com.keke.giftplayer.animation.download.AnimationResource
import com.keke.giftplayer.animation.download.AnimationResourceManager
import com.keke.giftplayer.internal.GiftPlayerLog
import java.io.File

/**
 * Created by keke on 2026/09/16.
 * Desc: GiftPlayer 对外统一入口。
 */
object GiftPlayer {
    @Volatile
    private var initialized = false

    /** 建议在 Application.onCreate() 中调用一次，重复调用会被忽略。 */
    @JvmStatic
    @JvmOverloads
    @Synchronized
    fun initialize(context: Context, config: GiftPlayerConfig = GiftPlayerConfig()) {
        if (initialized) {
            GiftPlayerLog.w("GiftPlayer is already initialized; the new configuration was ignored.")
            return
        }
        AnimationResourceManager.initialize(context.applicationContext, config)
        GiftPlayerLog.setLogcatEnabled(config.logcatEnabled)
        GiftPlayerLog.setLogger(config.logger)
        initialized = true
    }

    @JvmStatic
    fun isInitialized(): Boolean = initialized

    /** 运行期间仅调整 Logcat 输出，不修改其他配置。 */
    @JvmStatic
    fun setLogcatEnabled(enabled: Boolean) {
        GiftPlayerLog.setLogcatEnabled(enabled)
    }

    /** 运行期间替换日志接收器，传 null 移除。 */
    @JvmStatic
    fun setLogger(logger: GiftPlayerLogger?) {
        GiftPlayerLog.setLogger(logger)
    }

    @JvmStatic
    fun preload(
        resources: List<AnimationResource>,
        callback: AnimationDownloadCallback,
    ): List<AnimationDownloadTask> {
        ensureInitialized()
        return AnimationResourceManager.preload(resources, callback)
    }

    @JvmStatic
    fun preload(resources: List<AnimationResource>): List<AnimationDownloadTask> {
        ensureInitialized()
        return AnimationResourceManager.preload(resources)
    }

    @JvmStatic
    fun download(resource: AnimationResource, callback: AnimationDownloadCallback): AnimationDownloadTask {
        ensureInitialized()
        return AnimationResourceManager.download(resource, callback)
    }

    @JvmStatic
    fun getCachedFile(resource: AnimationResource): File? {
        ensureInitialized()
        return AnimationResourceManager.getCachedFile(resource)
    }

    @JvmStatic
    fun isCached(resource: AnimationResource): Boolean {
        ensureInitialized()
        return AnimationResourceManager.isCached(resource)
    }

    @JvmStatic
    fun getCacheSize(): Long {
        ensureInitialized()
        return AnimationResourceManager.getCacheSize()
    }

    @JvmStatic
    fun clearExpiredCache() {
        ensureInitialized()
        AnimationResourceManager.clearExpired()
    }

    @JvmStatic
    fun clearCache() {
        ensureInitialized()
        AnimationResourceManager.clearAll()
    }

    private fun ensureInitialized() {
        check(initialized) { "GiftPlayer.initialize(context) must be called first." }
    }
}
