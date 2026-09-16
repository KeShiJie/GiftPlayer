package com.keke.giftplayer.internal

import android.util.Log
import com.keke.giftplayer.GiftPlayerLogLevel
import com.keke.giftplayer.GiftPlayerLogger
import com.keke.giftplayer.animation.core.AnimationError
import com.keke.giftplayer.animation.core.AnimationFillMode
import com.keke.giftplayer.animation.core.AnimationFormat
import com.keke.giftplayer.animation.core.AnimationRequest
import com.keke.giftplayer.animation.core.AnimationScaleType
import com.keke.giftplayer.animation.core.AnimationSource
import com.keke.giftplayer.animation.download.AnimationResource
import com.keke.giftplayer.gift.svga.utils.log.SVGALogger
import com.keke.giftplayer.animation.loader.ResolvedAnimationSource
import java.io.File

/**
 * Created by keke on 2026/4/16.
 */
internal object GiftPlayerLog {
    const val TAG = "GiftPlayerLog"
    @Volatile
    private var logcatEnabled = false

    @Volatile
    private var logger: GiftPlayerLogger? = null

    private val dispatching = ThreadLocal<Boolean>()

    /** 设置全局日志监听器；传 null 移除。监听器异常不会中断播放或下载。 */
    @JvmStatic
    fun setLogger(value: GiftPlayerLogger?) {
        logger = value
    }

    /** 控制 Logcat 输出，默认关闭，始终不影响监听器。 */
    @JvmStatic
    fun setLogcatEnabled(value: Boolean) {
        logcatEnabled = value
        SVGALogger.setLogEnabled(value)
    }

    fun hasLogger(): Boolean = logger != null

    fun info(module: String, event: String, details: String = "") {
        dispatch(Log.INFO, TAG, eventMessage(module, event, details))
    }

    fun warn(module: String, event: String, details: String = "") {
        dispatch(Log.WARN, TAG, eventMessage(module, event, details))
    }

    fun error(module: String, event: String, details: String = "", throwable: Throwable? = null) {
        dispatch(Log.ERROR, TAG, eventMessage(module, event, details), throwable)
    }

    /** 供内置 SVGA 实现保留原始 Tag 的兼容日志入口。 */
    @JvmStatic
    @JvmOverloads
    fun log(level: Int, tag: String, message: String, throwable: Throwable? = null) {
        dispatch(level, tag, message, throwable)
    }

    /** 统一转发日志。 */
    private fun dispatch(level: Int, tag: String, message: String, throwable: Throwable? = null) {
        if (dispatching.get() == true) return
        if (logcatEnabled) {
            val text = if (throwable == null) message else "$message\n${Log.getStackTraceString(throwable)}"
            Log.println(level, tag, text)
        }
        val currentLogger = logger ?: return
        // 避免业务日志桥接回动画库时产生递归；线程之间互不影响。
        dispatching.set(true)
        try {
            currentLogger.log(level.toPublicLevel(), tag, message, throwable)
        } catch (_: Exception) {
            // 日志接收失败不影响动画流程，也不再次调用监听器报告错误。
        } finally {
            dispatching.remove()
        }
    }

    private fun Int.toPublicLevel(): GiftPlayerLogLevel {
        return when (this) {
            Log.VERBOSE -> GiftPlayerLogLevel.Verbose
            Log.DEBUG -> GiftPlayerLogLevel.Debug
            Log.INFO -> GiftPlayerLogLevel.Info
            Log.WARN -> GiftPlayerLogLevel.Warn
            else -> GiftPlayerLogLevel.Error
        }
    }

    fun sourceType(source: AnimationSource): String {
        return when (source) {
            is AnimationSource.Asset -> "Asset"
            is AnimationSource.FilePath -> "File"
            is AnimationSource.Url -> "URL"
        }
    }

    fun resolvedSourceType(source: ResolvedAnimationSource): String {
        return when (source) {
            is ResolvedAnimationSource.Asset -> "Asset"
            is ResolvedAnimationSource.FilePath -> "File"
        }
    }

    fun requestSummary(request: AnimationRequest): String {
        return "${traceSummary(request)} | ${sourceSummary(request.source)} | Format=${formatName(request.format)} | Loop Count=${request.loopCount} | " +
            "Auto Play=${request.autoPlay} | Scale Type=${scaleTypeName(request.scaleType)} | " +
            "Fill Mode=${fillModeName(request.fillMode)} | Audio Enabled=${request.enableAudio} | " +
            "Pause When Invisible=${request.pauseWhenInvisible}"
    }

    fun resolvedSourceSummary(source: ResolvedAnimationSource): String {
        return when (source) {
            is ResolvedAnimationSource.Asset -> "Source=Asset | Asset Name=[${source.name}]"
            is ResolvedAnimationSource.FilePath -> "Source=File | File Path=[${source.path}]"
        }
    }

    fun downloadSummary(
        resource: AnimationResource,
        file: File? = null,
        downloadPriority: Int = resource.downloadPriority,
        traceIds: Collection<String> = listOf(resource.traceId),
    ): String {
        val optionalFields = buildList {
            resource.id?.let { add("Resource ID=[$it]") }
            resource.version?.let { add("Resource Version=[$it]") }
            resource.category?.let { add("Resource Category=[$it]") }
            file?.let { add("Cache File=[${it.name}]") }
        }
        return buildList {
            add("Trace ID=[${traceIds.joinToString()}]")
            add("URL=[${resource.url}]")
            add("Download Priority=$downloadPriority")
            add("Format=${formatName(resource.format)}")
            addAll(optionalFields)
        }.joinToString(" | ")
    }

    fun queueSummary(queueId: Long, playbackPriority: Int, request: AnimationRequest, file: File? = null): String {
        val fileDetail = file?.let { " | Cache File=[${it.name}]" }.orEmpty()
        return "Queue ID=$queueId | Playback Priority=$playbackPriority | ${requestSummary(request)}$fileDetail"
    }

    fun traceSummary(request: AnimationRequest): String = "Trace ID=[${request.traceId}]"

    fun errorSummary(error: AnimationError): String {
        return when (error) {
            is AnimationError.InvalidSource -> "Error Type=Invalid Source | Reason=${error.reason}"
            is AnimationError.UnsupportedFormat -> "Error Type=Unsupported Format | ${sourceSummary(error.source)}"
            is AnimationError.PlayerPluginMissing -> "Error Type=Missing Player Plugin | Format=${formatName(error.format)}"
            is AnimationError.DownloadFailed -> "Error Type=Download Failed | Reason=${error.cause?.message.orEmpty()}"
            is AnimationError.FileNotFound -> "Error Type=File Not Found | File Path=[${error.path}]"
            is AnimationError.DecodeFailed -> "Error Type=Decode Failed | Format=${formatName(error.format)} | Reason=${error.cause?.message.orEmpty()}"
            is AnimationError.RenderFailed -> "Error Type=Render Failed | Format=${formatName(error.format)} | Reason=${error.cause?.message.orEmpty()}"
            is AnimationError.Cancelled -> "Error Type=Cancelled | Reason=${error.reason}"
        }
    }

    private fun eventMessage(module: String, event: String, details: String): String {
        val traceMatch = TRACE_ID_PATTERN.find(details)
        val tracePrefix = traceMatch?.value?.let { "【$it】" }.orEmpty()
        val remainingDetails = traceMatch?.let {
            details.split('|')
                .map(String::trim)
                .filter { field -> field.isNotEmpty() && field != it.value }
                .joinToString(" | ")
        } ?: details
        return "$tracePrefix【$module】$event" + remainingDetails.takeIf { it.isNotBlank() }?.let { " | $it" }.orEmpty()
    }

    private fun sourceSummary(source: AnimationSource): String {
        return when (source) {
            is AnimationSource.Asset -> "Source=Asset | Asset Name=[${source.name}]"
            is AnimationSource.FilePath -> "Source=File | File Path=[${source.path}]"
            is AnimationSource.Url -> "Source=URL | URL=[${source.url}] | Download Priority=${source.downloadPriority}"
        }
    }

    fun formatName(format: AnimationFormat): String {
        return when (format) {
            AnimationFormat.Auto -> "Auto"
            AnimationFormat.Svga -> "SVGA"
            AnimationFormat.Pag -> "PAG"
            AnimationFormat.Vap -> "VAP"
        }
    }

    private fun scaleTypeName(scaleType: AnimationScaleType): String {
        return when (scaleType) {
            AnimationScaleType.FitCenter -> "Fit Center"
            AnimationScaleType.CenterCrop -> "Center Crop"
            AnimationScaleType.FitXY -> "Fit XY"
        }
    }

    private fun fillModeName(fillMode: AnimationFillMode): String {
        return when (fillMode) {
            AnimationFillMode.Backward -> "Backward"
            AnimationFillMode.Forward -> "Forward"
            AnimationFillMode.Clear -> "Clear"
        }
    }

    private val TRACE_ID_PATTERN = Regex("Trace ID=\\[[^]]*]")
}
