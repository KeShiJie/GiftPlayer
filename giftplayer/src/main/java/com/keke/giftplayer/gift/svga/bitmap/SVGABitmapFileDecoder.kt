package com.keke.giftplayer.gift.svga.bitmap

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Decodes a Bitmap from a file.
 *
 * Created by im_dsd on 2020/7/7 17:50.
 */
internal object SVGABitmapFileDecoder : SVGABitmapDecoder<String>() {

    override fun onDecode(data: String, ops: BitmapFactory.Options): Bitmap? {
        return BitmapFactory.decodeFile(data, ops)
    }
}
