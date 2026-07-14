package com.keke.giftplayer.gift.svga.utils.log

/**
 * SVGA logger configuration manager.
 **/
object SVGALogger {

    private var mLogger: ILogger? = DefaultLogCat()
    private var isLogEnabled = false

    /**
     * Injects an external logger implementation.
     */
    fun injectSVGALoggerImp(logImp: ILogger): SVGALogger {
        mLogger = logImp
        return this
    }

    /**
     * Sets whether logging is enabled.
     */
    fun setLogEnabled(isEnabled: Boolean): SVGALogger {
        isLogEnabled = isEnabled
        return this
    }

    /**
     * Returns the current ILogger implementation.
     */
    fun getSVGALogger(): ILogger? {
        return mLogger
    }

    /**
     * Returns whether logging is enabled.
     */
    fun isLogEnabled(): Boolean {
        return isLogEnabled
    }
}
