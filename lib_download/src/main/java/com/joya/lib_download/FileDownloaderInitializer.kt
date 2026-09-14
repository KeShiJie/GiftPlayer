package com.joya.lib_download

import android.app.Application
import android.content.Context
import com.liulishuo.filedownloader.FileDownloader
import com.liulishuo.filedownloader.connection.FileDownloadUrlConnection
import com.liulishuo.filedownloader.util.FileDownloadHelper

/**
 * Created by keke on 2026/06/18.
 * Desc: FileDownloader 初始化器
 */
object FileDownloaderInitializer {

    private var initialized = false

    @Volatile
    private var defaultCreator = defaultCreator(20_000, 30_000)

    @Volatile
    private var customCreator: FileDownloadHelper.ConnectionCreator? = null

    private val connectionFactory = FileDownloadHelper.ConnectionCreator { url ->
        (customCreator ?: defaultCreator).create(url)
    }

    @JvmStatic
    fun init(context: Context) {
        init(context, null)
    }

    @JvmStatic
    fun init(
        context: Context,
        connectionCreator: FileDownloadHelper.ConnectionCreator?,
    ) {
        init(context, connectionCreator, 20_000, 30_000)
    }

    /**
     * Updates future connections in this process; existing connections keep their original timeouts.
     * A supplied custom creator remains authoritative across init calls and owns its timeout policy.
     *
     * The bundled AAR defaults to a separate service process. Hosts must either set
     * `process.non-separate=true` in assets/filedownloader.properties or initialize here from
     * Application.onCreate in the download process too. Runtime updates are process-local.
     */
    @JvmStatic
    @Synchronized
    fun init(
        context: Context,
        connectionCreator: FileDownloadHelper.ConnectionCreator?,
        connectTimeoutMillis: Int,
        readTimeoutMillis: Int,
    ) {
        require(connectTimeoutMillis > 0) { "connectTimeoutMillis must be positive." }
        require(readTimeoutMillis > 0) { "readTimeoutMillis must be positive." }
        val appContext = context.applicationContext
        val application = appContext as? Application ?: return
        defaultCreator = defaultCreator(connectTimeoutMillis, readTimeoutMillis)
        if (connectionCreator != null) customCreator = connectionCreator
        if (initialized) return

        val setup = FileDownloader.setupOnApplicationOnCreate(application)
        setup.connectionCreator(connectionFactory)
        setup.commit()
        FileDownloader.setup(appContext)
        initialized = true
    }

    private fun defaultCreator(connectTimeoutMillis: Int, readTimeoutMillis: Int) =
        FileDownloadUrlConnection.Creator(
            FileDownloadUrlConnection.Configuration()
                .connectTimeout(connectTimeoutMillis)
                .readTimeout(readTimeoutMillis),
        )
}
