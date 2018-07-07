package com.vnp.vision.carslist.views

import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider


fun View.applyRoundOutline() {
    this.outlineProvider = RoundButtonOutlineProvider()
}

/**
 * Outline provider for round buttons
 */
class RoundButtonOutlineProvider : ViewOutlineProvider() {
    override fun getOutline(view: View?, outline: Outline?) {
        view?.apply {
            outline?.setOval(0, 0, view.width, view.height)
        }
    }
}