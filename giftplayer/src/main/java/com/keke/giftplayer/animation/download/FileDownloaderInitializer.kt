package com.keke.giftplayer.animation.download

import android.app.Application
import android.content.Context
import com.liulishuo.filedownloader.FileDownloader
import com.liulishuo.filedownloader.util.FileDownloadHelper
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Created by keke on 2026/06/18.
 * Desc: FileDownloader initializer.
 */
object FileDownloaderInitializer {

    // Guards initialization so setup runs only once.
    private val initialized = AtomicBoolean(false)

    @JvmStatic
    fun init(context: Context) {
        init(context, null)
    }

    @JvmStatic
    fun init(
        context: Context,
        connectionCreator: FileDownloadHelper.ConnectionCreator?,
    ) {
        val appContext = context.applicationContext
        val application = appContext as? Application ?: return
        if (!initialized.compareAndSet(false, true)) return

        val setup = FileDownloader.setupOnApplicationOnCreate(application)
        if (connectionCreator != null) {
            setup.connectionCreator(connectionCreator)
        }
        setup.commit()
        FileDownloader.setup(appContext)
    }
}
