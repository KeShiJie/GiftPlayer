package com.keke.giftplayer.gift.svga.utils.log

import com.keke.giftplayer.animation.core.AnimationLog

/**
 * Log output helper.
 */
internal object LogUtils {
    private const val TAG = "${AnimationLog.TAG}_SVGALog"

    fun verbose(tag: String = TAG, msg: String) {
        if (!SVGALogger.isLogEnabled() && !AnimationLog.hasListener()) {
            return
        }
        SVGALogger.getSVGALogger()?.verbose(tag, msg)
    }

    fun info(tag: String = TAG, msg: String) {
        if (!SVGALogger.isLogEnabled() && !AnimationLog.hasListener()) {
            return
        }
        SVGALogger.getSVGALogger()?.info(tag, msg)
    }

    fun debug(tag: String = TAG, msg: String) {
        if (!SVGALogger.isLogEnabled() && !AnimationLog.hasListener()) {
            return
        }
        SVGALogger.getSVGALogger()?.debug(tag, msg)
    }

    fun warn(tag: String = TAG, msg: String) {
        if (!SVGALogger.isLogEnabled() && !AnimationLog.hasListener()) {
            return
        }
        SVGALogger.getSVGALogger()?.warn(tag, msg)
    }

    fun error(tag: String = TAG, msg: String) {
        if (!SVGALogger.isLogEnabled() && !AnimationLog.hasListener()) {
            return
        }
        SVGALogger.getSVGALogger()?.error(tag, msg, null)
    }

    fun error(tag: String, error: Throwable) {
        if (!SVGALogger.isLogEnabled() && !AnimationLog.hasListener()) {
            return
        }
        SVGALogger.getSVGALogger()?.error(tag, error.message, error)
    }

    fun error(tag: String = TAG, msg: String, error: Throwable) {
        if (!SVGALogger.isLogEnabled() && !AnimationLog.hasListener()) {
            return
        }
        SVGALogger.getSVGALogger()?.error(tag, msg, error)
    }
}
