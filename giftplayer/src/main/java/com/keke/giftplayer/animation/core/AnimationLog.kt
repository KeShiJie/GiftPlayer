package com.keke.giftplayer.animation.core

import android.util.Log
import com.keke.giftplayer.gift.svga.utils.log.SVGALogger
import com.keke.giftplayer.animation.loader.ResolvedAnimationSource

/**
 * Created by keke on 2026/4/16.
 */
object AnimationLog {
    const val TAG = "AnimationPlayer"
    @Volatile
    private var logcatEnabled = false

    @Volatile
    private var listener: AnimationLogListener? = null

    private val dispatching = ThreadLocal<Boolean>()

    /** 设置全局日志监听器；传 null 移除。监听器异常不会中断播放或下载。 */
    @JvmStatic
    fun setListener(value: AnimationLogListener?) {
        listener = value
    }

    /** 控制 Logcat 输出，默认关闭，始终不影响监听器。 */
    @JvmStatic
    fun setLogcatEnabled(value: Boolean) {
        logcatEnabled = value
        SVGALogger.setLogEnabled(value)
    }

    /** 控制 Logcat 输出；初始化时使用 isLogEnabled 配置此值，不影响监听器。 */
    @JvmStatic
    fun setEnabled(value: Boolean) {
        setLogcatEnabled(value)
    }

    internal fun hasListener(): Boolean = listener != null

    fun i(message: String) {
        log(Log.INFO, TAG, message)
    }

    fun w(message: String) {
        log(Log.WARN, TAG, message)
    }

    fun e(message: String) {
        log(Log.ERROR, TAG, message)
    }

    fun e(message: String, throwable: Throwable?) {
        log(Log.ERROR, TAG, message, throwable)
    }

    /** 统一转发日志ç */
    @JvmStatic
    @JvmOverloads
    fun log(level: Int, tag: String, message: String, throwable: Throwable? = null) {
        if (dispatching.get() == true) return
        if (logcatEnabled) {
            val text = if (throwable == null) message else "$message\n${Log.getStackTraceString(throwable)}"
            Log.println(level, tag, text)
        }
        val currentListener = listener ?: return
        // 避免业务日志桥接回动画库时产生递归；线程之间互不影响。
        dispatching.set(true)
        try {
            currentListener.onLog(level, tag, message, throwable)
        } catch (_: Exception) {
            // 日志接收失败不影响动画流程，也不再次调用监听器报告错误。
        } finally {
            dispatching.remove()
        }
    }

    fun sourceType(source: AnimationSource): String {
        return when (source) {
            is AnimationSource.Asset -> "asset"
            is AnimationSource.FilePath -> "file"
            is AnimationSource.Url -> "url"
        }
    }

    fun resolvedSourceType(source: ResolvedAnimationSource): String {
        return when (source) {
            is ResolvedAnimationSource.Asset -> "asset"
            is ResolvedAnimationSource.FilePath -> "file"
        }
    }

    fun requestSummary(request: AnimationRequest): String {
        return "source=${sourceType(request.source)}, format=${request.format}, loop=${request.loopCount}, " +
            "scale=${request.scaleType}, fill=${request.fillMode}, audio=${request.enableAudio}, " +
            "pauseWhenInvisible=${request.pauseWhenInvisible}"
    }

    fun errorSummary(error: AnimationError): String {
        return when (error) {
            is AnimationError.InvalidSource -> "InvalidSource(${error.reason})"
            is AnimationError.UnsupportedFormat -> "UnsupportedFormat(source=${sourceType(error.source)})"
            is AnimationError.DownloadFailed -> "DownloadFailed(${error.cause?.message.orEmpty()})"
            is AnimationError.FileNotFound -> "FileNotFound"
            is AnimationError.DecodeFailed -> "DecodeFailed(format=${error.format}, cause=${error.cause?.message.orEmpty()})"
            is AnimationError.RenderFailed -> "RenderFailed(format=${error.format}, cause=${error.cause?.message.orEmpty()})"
            is AnimationError.Cancelled -> "Cancelled(${error.reason})"
        }
    }
}
