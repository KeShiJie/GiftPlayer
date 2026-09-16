package com.keke.giftplayer

import android.app.Application
import com.keke.giftplayer.pag.PagPlayerPlugin
import com.keke.giftplayer.vap.VapPlayerPlugin

/**
 * Created by keke on 2026/09/16.
 * Desc: Demo 应用的进程级初始化入口。
 */
class DemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        GiftPlayer.initialize(
            context = this,
            config = GiftPlayerConfig(logcatEnabled = true),
            plugins = listOf(
                PagPlayerPlugin(),
                VapPlayerPlugin(),
            ),
        )
    }
}
