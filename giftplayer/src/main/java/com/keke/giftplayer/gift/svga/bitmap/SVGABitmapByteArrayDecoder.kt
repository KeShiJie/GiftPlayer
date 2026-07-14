package com.keke.giftplayer.gift.svga.bitmap

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.keke.giftplayer.gift.svga.bitmap.SVGABitmapDecoder

/**
 * Decodes a Bitmap from bytes.
 *
 * Created by im_dsd on 2020/7/7 17:50.
 */
internal object SVGABitmapByteArrayDecoder : SVGABitmapDecoder<ByteArray>() {

    override fun onDecode(data: ByteArray, ops: BitmapFactory.Options): Bitmap? {
        return BitmapFactory.decodeByteArray(data, 0, data.count(), ops)
    }
}
