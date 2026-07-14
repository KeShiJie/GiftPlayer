package com.keke.giftplayer.gift.svga.utils

import android.graphics.Bitmap
import android.text.TextUtils
import android.util.Log
import com.keke.giftplayer.animation.core.AnimationLog
import com.keke.giftplayer.gift.svga.bitmap.SVGABitmapByteArrayDecoder
import com.keke.giftplayer.gift.svga.bitmap.SVGABitmapFileDecoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Created by HHJ on 2023/3/7 12:30
 * Desc: SVGA bitmap cache helper. Synchronized methods should be used carefully on the main thread.
 */
object BitmapCacheHelper {

    /**
     * Bitmap cache table.
     */
    private val caches: ConcurrentHashMap<String, BitmapCache> = ConcurrentHashMap()

    /**
     * Relation table used for release.
     */
    private val relatedList: ConcurrentHashMap<String, CopyOnWriteArraySet<String>> = ConcurrentHashMap()

    /**
     * Creates a bitmap.
     */
    @Synchronized
    fun createBitmap(relatedKey: String, filePath: String, frameWidth: Int, frameHeight: Int): Bitmap? {
        return getBitmapCache(relatedKey, filePath) {
            return@getBitmapCache SVGABitmapFileDecoder.decodeBitmapFrom(filePath, frameWidth, frameHeight)
        }
    }

    /**
     * Creates a bitmap.
     */
    @Synchronized
    fun createBitmap(relatedKey: String, byteArray: ByteArray, frameWidth: Int, frameHeight: Int): Bitmap? {
        return getBitmapCache(relatedKey, md5(byteArray)) {
            return@getBitmapCache SVGABitmapByteArrayDecoder.decodeBitmapFrom(byteArray, frameWidth, frameHeight)
        }
    }

    /**
     * Creates a bitmap.
     */
    @Synchronized
    fun createBitmap(relatedKey: String, filePath: String, sampleSize: Int): Bitmap? {
        return getBitmapCache(relatedKey, filePath) {
            return@getBitmapCache SVGABitmapFileDecoder.decodeBitmapFrom(filePath, sampleSize)
        }
    }

    /**
     * Creates a bitmap.
     */
    @Synchronized
    fun createBitmap(relatedKey: String, byteArray: ByteArray, sampleSize:Int): Bitmap? {
        return getBitmapCache(relatedKey, md5(byteArray)) {
            return@getBitmapCache SVGABitmapByteArrayDecoder.decodeBitmapFrom(byteArray, sampleSize)
        }
    }

    /**
     * Gets bitmap cache.
     */
    private fun getBitmapCache(relatedKey: String, key: String, callback: () -> Bitmap?): Bitmap? {
        // Get cache.
        val cache = caches[key]
        // Cache is empty.
        if (cache == null) {
            // Create cache object.
            val bitmapCache = BitmapCache()
            // Create bitmap.
            bitmapCache.bitmap = callback.invoke()
            // Add to cache table.
            caches[key] = bitmapCache
            // Record reference.
            recordQuote(relatedKey, key, bitmapCache)
            return bitmapCache.bitmap
        }

        // Bitmap is null or recycled.
        if (cache.bitmap == null || cache.bitmap.isRecycled) {
            // Refresh cache.
            cache.bitmap = callback.invoke()
            // Record reference.
            recordQuote(relatedKey, key, cache)
            return cache.bitmap
        }

        // Record reference.
        recordQuote(relatedKey, key, cache)
        return cache.bitmap
    }

    /**
     * Records a reference.
     */
    private fun recordQuote(relatedKey: String, key: String, cache: BitmapCache) {
        // Add reference.
        cache.addQuote(relatedKey)
        // Add to relation table.
        relatedBitmap(relatedKey, key)
    }

    /**
     * Relates bitmap to a key.
     */
    @Synchronized
    private fun relatedBitmap(relatedKey: String, key: String) {
        if (!TextUtils.isEmpty(relatedKey) && !TextUtils.isEmpty(key)) {
            if (!relatedList.containsKey(relatedKey)) {
                val keys: CopyOnWriteArraySet<String> = CopyOnWriteArraySet()
                keys.add(key)
                relatedList[relatedKey] = keys
            } else {
                relatedList[relatedKey]?.add(key)
            }
        }
    }

    /**
     * Removes relation.
     */
    @Synchronized
    fun removeRelated(relatedKey: String) {
        relatedList[relatedKey]?.forEach { key ->
            val cache = caches[key]
            cache?.apply {
                // Remove reference from cache.
                removeQuote(relatedKey)
                // Remove cache when it has no references.
                if (isEmpty) {
                    caches.remove(key)
                }
            }
        }
        relatedList.remove(relatedKey)
        if (AnimationLog.isEnabled()) {
            Log.i("SVGA-clear", "release, remaining cache relations: " + relatedList.size)
        }
    }

    /**
     * Returns cache list for inspection.
     */
    fun getTestCachesList(): MutableList<BitmapCache> {
        val list: MutableList<BitmapCache> = ArrayList()
        for (value in caches.values) {
            list.add(value)
        }
        return list
    }

    /**
     * Returns MD5.
     */
    fun md5(byteArray: ByteArray): String {
        return String(MessageDigest.getInstance("md5").digest(byteArray))
    }

    /**
     * Releases all cache. Avoid calling unless necessary.
     */
    @Synchronized
    fun release() {
        caches.clear()
        relatedList.clear()
    }
}
