package com.keke.giftplayer.gift.svga

/**
 * Created by cuiminghui on 2017/3/30.
 */
interface SVGACallback {

    fun onPause()
    fun onFinished()
    fun onRepeat()
    fun onStep(frame: Int, percentage: Double)

}

abstract class SVGACallbackAdapter : SVGACallback {

    override fun onPause() {
    }

    override fun onFinished() {
    }

    override fun onRepeat() {
    }

    override fun onStep(frame: Int, percentage: Double) {
    }

}