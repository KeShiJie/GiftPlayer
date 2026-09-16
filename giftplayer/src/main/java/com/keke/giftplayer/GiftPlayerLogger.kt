package com.keke.giftplayer

/**
 * Created by keke on 2026/09/16.
 * Desc: GiftPlayer 日志接收接口。
 */
fun interface GiftPlayerLogger {
    /**
     * 在产生日志的线程同步回调，可能来自多个线程。
     * 请只转交给进程级日志组件，耗时上传应异步执行。
     */
    fun log(level: GiftPlayerLogLevel, tag: String, message: String, throwable: Throwable?)
}

/** GiftPlayer 对外稳定的日志级别，不依赖 Android Log 的整数常量。 */
enum class GiftPlayerLogLevel {
    Verbose,
    Debug,
    Info,
    Warn,
    Error,
}
