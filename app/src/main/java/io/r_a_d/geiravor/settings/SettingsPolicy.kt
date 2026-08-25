package io.r_a_d.geiravor.settings

object SettingsPolicy {
    const val AUTO_START_DEFAULT = false

    fun shouldStartOnPlug(
        enabled: Boolean,
        pluggedIn: Boolean,
        isInitialSticky: Boolean = false,
    ): Boolean = enabled && pluggedIn && !isInitialSticky

    fun isHeadsetPlugged(state: Int): Boolean = state == 1
}
