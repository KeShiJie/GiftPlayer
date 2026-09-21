package com.keke.giftplayer

import android.content.Context
import androidx.annotation.WorkerThread
import com.keke.giftplayer.animation.download.AnimationDownloadCallback
import com.keke.giftplayer.animation.download.AnimationDownloadTask
import com.keke.giftplayer.animation.download.AnimationResource
import com.keke.giftplayer.animation.download.AnimationResourceManager
import com.keke.giftplayer.animation.plugin.AnimationPlayerPlugin
import com.keke.giftplayer.internal.AnimationPlayerPluginRegistry
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
    fun initialize(
        context: Context,
        config: GiftPlayerConfig = GiftPlayerConfig(),
        plugins: List<AnimationPlayerPlugin> = emptyList(),
    ) {
        if (initialized) {
            GiftPlayerLog.warn("Initialization", "Duplicate Initialization", "New configuration was ignored")
            return
        }
        AnimationPlayerPluginRegistry.replaceExternalPlugins(plugins)
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

    /**
     * 批量预加载，适用常用礼物预先准备
     */
    @JvmStatic
    fun preload(
        resources: List<AnimationResource>,
        callback: AnimationDownloadCallback ? =null,
    ): List<AnimationDownloadTask> {
        ensureInitialized()
        return AnimationResourceManager.preload(resources, callback)
    }


    @JvmStatic
    fun download(resource: AnimationResource, callback: AnimationDownloadCallback): AnimationDownloadTask {
        ensureInitialized()
        return AnimationResourceManager.download(resource, callback)
    }


    /** 异步查询有效缓存文件 */
    @JvmStatic
    fun getCachedFileAsync(resource: AnimationResource, callback: (File?) -> Unit) {
        ensureInitialized()
        AnimationResourceManager.getCachedFileAsync(resource, callback)
    }


    /** 异步判断资源是否存在有效缓存 */
    @JvmStatic
    fun isCachedAsync(resource: AnimationResource, callback: (Boolean) -> Unit) {
        ensureInitialized()
        AnimationResourceManager.isCachedAsync(resource, callback)
    }


    /** 异步统计缓存大小 */
    @JvmStatic
    fun getCacheSizeAsync(callback: (Long) -> Unit) {
        ensureInitialized()
        AnimationResourceManager.getCacheSizeAsync(callback)
    }


    /** 异步清理过期缓存 */
    @JvmStatic
    fun clearExpiredCacheAsync(callback: () -> Unit) {
        ensureInitialized()
        AnimationResourceManager.clearExpiredAsync(callback)
    }

    /** 异步清理缓存 */
    @JvmStatic
    fun clearCacheAsync(callback: () -> Unit) {
        ensureInitialized()
        AnimationResourceManager.clearAllAsync(callback)
    }

    private fun ensureInitialized() {
        check(initialized) { "GiftPlayer.initialize(context) must be called first." }
    }
}
