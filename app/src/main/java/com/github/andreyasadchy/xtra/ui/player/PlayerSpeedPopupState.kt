package com.github.andreyasadchy.xtra.ui.player

object PlayerSpeedPopupState {

    fun initialSpeed(currentSpeed: Float?, savedSpeed: Float): Float {
        return currentSpeed ?: savedSpeed
    }
}
