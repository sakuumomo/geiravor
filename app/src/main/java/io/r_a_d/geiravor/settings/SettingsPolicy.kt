package io.r_a_d.geiravor.settings

import android.content.res.Configuration

object SettingsPolicy {
    const val AUTO_START_DEFAULT = false
    const val AUTO_START_VEHICLE_DEFAULT = false
    const val ABOUT_NAME = "Geiravor"
    const val ABOUT_LINE = "Based on r/a/dio's Valkyrie"

    fun shouldStartOnPlug(
        enabled: Boolean,
        pluggedIn: Boolean,
        isInitialSticky: Boolean = false,
    ): Boolean = enabled && pluggedIn && !isInitialSticky

    fun shouldStartInVehicle(
        enabled: Boolean,
        enteredCar: Boolean,
        isInitialSticky: Boolean = false,
    ): Boolean = enabled && enteredCar && !isInitialSticky

    fun isHeadsetPlugged(state: Int): Boolean = state == 1

    fun isCarUiMode(uiModeType: Int): Boolean =
        uiModeType == Configuration.UI_MODE_TYPE_CAR
}
