package com.keke.giftplayer.gift.svga.utils.log

import android.util.Log
import com.keke.giftplayer.animation.core.AnimationLog

/**
 * Default internal ILogger implementation.
 */
class DefaultLogCat : ILogger {
    override fun verbose(tag: String, msg: String) {
        AnimationLog.log(Log.VERBOSE, tag, msg)
    }

    override fun info(tag: String, msg: String) {
        AnimationLog.log(Log.INFO, tag, msg)
    }

    override fun debug(tag: String, msg: String) {
        AnimationLog.log(Log.DEBUG, tag, msg)
    }

    override fun warn(tag: String, msg: String) {
        AnimationLog.log(Log.WARN, tag, msg)
    }

    override fun error(tag: String, msg: String?, error: Throwable?) {
        AnimationLog.log(Log.ERROR, tag, msg.orEmpty(), error)
    }
}
