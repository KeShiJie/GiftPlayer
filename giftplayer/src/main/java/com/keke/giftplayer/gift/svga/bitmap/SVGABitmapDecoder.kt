package com.keke.giftplayer.gift.svga.bitmap

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Bitmap decoder.
 *
 * <T> data type to load.
 *
 * Created by im_dsd on 2020/7/7 17:39.
 */
internal abstract class SVGABitmapDecoder<T> {

    fun decodeBitmapFrom(data: T, reqWidth: Int, reqHeight: Int): Bitmap? {
        return BitmapFactory.Options().run {
            // Enable bounds-only mode when requested dimensions are valid.
            inJustDecodeBounds = (reqWidth > 0 && reqHeight > 0)
            inPreferredConfig = Bitmap.Config.RGB_565

            val bitmap = onDecode(data, this)
            if (!inJustDecodeBounds) {
                return bitmap
            }

            // Calculate inSampleSize
            inSampleSize = BitmapSampleSizeCalculator.calculate(this, reqWidth, reqHeight)
            // Decode bitmap with inSampleSize set
            inJustDecodeBounds = false
            onDecode(data, this)
        }
    }

    fun decodeBitmapFrom(data: T, sampleSize:Int): Bitmap? = BitmapFactory.Options().run {
        // Decode with explicit sample size.
        inPreferredConfig = Bitmap.Config.RGB_565
        inJustDecodeBounds = false
        // Calculate inSampleSize
        inSampleSize = sampleSize
        return onDecode(data, this)
    }

    abstract fun onDecode(data: T, ops: BitmapFactory.Options): Bitmap?
}
