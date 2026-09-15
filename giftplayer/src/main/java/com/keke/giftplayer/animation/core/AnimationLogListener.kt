package com.keke.giftplayer.animation.core

/**
 * Created by keke on 2026/09/15.
 * Desc: 动画库日志接收接口。
 *
 * 设置后独立接收日志，不受 isLogEnabled 或 Logcat 打印开关影响。
 * 回调在产生日志的线程同步执行，可能来自多个线程，请将耗时上传交给业务异步处理。
 * 监听器由全局日志入口持有，请勿捕获 Activity 或 View；不再使用时通过 setListener(null) 移除。
 */
fun interface AnimationLogListener {
    /** level 使用 android.util.Log 的级别常量，throwable 在没有异常时为 null。 */
    fun onLog(level: Int, tag: String, message: String, throwable: Throwable?)
}
