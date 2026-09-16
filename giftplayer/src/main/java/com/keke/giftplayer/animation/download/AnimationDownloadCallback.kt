package com.keke.giftplayer.animation.download

import java.io.File

/**
 * Created by keke on 2026/06/23.
 */
interface AnimationDownloadCallback {
    /** 主线程下载进度；totalBytes <= 0 表示总大小未知，缓存命中同样异步回调成功。 */
    fun onProgress(resource: AnimationResource, downloadedBytes: Long, totalBytes: Long) {}

    fun onSuccess(resource: AnimationResource, file: File)

    fun onError(resource: AnimationResource, error: Throwable?)
}
