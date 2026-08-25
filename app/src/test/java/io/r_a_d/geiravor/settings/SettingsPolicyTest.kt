package io.r_a_d.geiravor.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPolicyTest {
    @Test
    fun autoStartOnPlugDefaultsOff() {
        assertFalse(SettingsPolicy.AUTO_START_DEFAULT)
    }

    @Test
    fun startsOnlyWhenEnabledAndPluggedIn() {
        assertFalse(SettingsPolicy.shouldStartOnPlug(enabled = false, pluggedIn = true))
        assertFalse(SettingsPolicy.shouldStartOnPlug(enabled = true, pluggedIn = false))
        assertTrue(SettingsPolicy.shouldStartOnPlug(enabled = true, pluggedIn = true))
        assertFalse(
            SettingsPolicy.shouldStartOnPlug(
                enabled = true,
                pluggedIn = true,
                isInitialSticky = true,
            ),
        )
    }

    @Test
    fun headsetPlugStateOneIsInserted() {
        assertTrue(SettingsPolicy.isHeadsetPlugged(state = 1))
        assertFalse(SettingsPolicy.isHeadsetPlugged(state = 0))
        assertFalse(SettingsPolicy.isHeadsetPlugged(state = -1))
    }

    @Test
    fun autoStartInVehicleDefaultsOffAndIsIndependentOfPlug() {
        assertFalse(SettingsPolicy.AUTO_START_VEHICLE_DEFAULT)
        assertTrue(
            SettingsPolicy.shouldStartInVehicle(enabled = true, enteredCar = true),
        )
        assertFalse(
            SettingsPolicy.shouldStartInVehicle(enabled = false, enteredCar = true),
        )
        assertFalse(
            SettingsPolicy.shouldStartInVehicle(
                enabled = true,
                enteredCar = true,
                isInitialSticky = true,
            ),
        )
        assertTrue(
            SettingsPolicy.shouldStartOnPlug(enabled = true, pluggedIn = true),
        )
    }

    @Test
    fun carUiModeIsVehicle() {
        assertTrue(
            SettingsPolicy.isCarUiMode(
                uiModeType = android.content.res.Configuration.UI_MODE_TYPE_CAR,
            ),
        )
        assertFalse(
            SettingsPolicy.isCarUiMode(
                uiModeType = android.content.res.Configuration.UI_MODE_TYPE_NORMAL,
            ),
        )
    }
}
