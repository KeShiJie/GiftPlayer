package com.keke.giftplayer.animation.download

import java.io.File

/**
 * Created by keke on 2026/06/23.
 * Desc: Animation resource download callback.
 */
interface AnimationDownloadCallback {
    /** 主线程下载进度；totalBytes <= 0 表示总大小未知，缓存命中可能直接回调成功。 */
    fun onProgress(resource: AnimationResource, downloadedBytes: Long, totalBytes: Long) {}

    /** Called when the resource hits cache or downloads successfully. */
    fun onSuccess(resource: AnimationResource, file: File)

    /** Called when the resource fails to download or validate. */
    fun onError(resource: AnimationResource, error: Throwable?)
}
