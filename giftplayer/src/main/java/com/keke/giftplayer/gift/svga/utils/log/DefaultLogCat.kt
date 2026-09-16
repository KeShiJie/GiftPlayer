package com.keke.giftplayer.gift.svga.utils.log

import android.util.Log
import com.keke.giftplayer.internal.GiftPlayerLog

/**
 * Default internal ILogger implementation.
 */
class DefaultLogCat : ILogger {
    override fun verbose(tag: String, msg: String) {
        GiftPlayerLog.log(Log.VERBOSE, tag, msg)
    }

    override fun info(tag: String, msg: String) {
        GiftPlayerLog.log(Log.INFO, tag, msg)
    }

    override fun debug(tag: String, msg: String) {
        GiftPlayerLog.log(Log.DEBUG, tag, msg)
    }

    override fun warn(tag: String, msg: String) {
        GiftPlayerLog.log(Log.WARN, tag, msg)
    }

    override fun error(tag: String, msg: String?, error: Throwable?) {
        GiftPlayerLog.log(Log.ERROR, tag, msg.orEmpty(), error)
    }
}
