package io.r_a_d.geiravor.radio

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import io.r_a_d.geiravor.playback.FavePolicy
import uniffi.geiravor_core.Status
import uniffi.geiravor_core.StatusListener

data class RadioUiState(
    val status: Status? = null,
    val streamDown: Boolean = false,
    val canRequest: Boolean? = null,
    val heartFilled: Boolean = false,
    val faveNotice: String? = null,
    val faveListRevision: Int = 0,
)

object RadioStore : StatusListener {
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(RadioUiState())
    val state: StateFlow<RadioUiState> = _state.asStateFlow()

    override fun onUpdate(status: Status, streamDown: Boolean) {
        main.post {
            val prev = _state.value
            _state.value = prev.copy(
                status = status,
                streamDown = streamDown,
                faveNotice = if (FavePolicy.keepFaveNotice(prev.status?.np, status.np)) {
                    prev.faveNotice
                } else {
                    null
                },
            )
        }
    }

    fun setHeart(
        filled: Boolean,
        notice: String? = null,
        replaceNotice: Boolean = false,
        bumpList: Boolean = false,
    ) {
        val apply = {
            val prev = _state.value
            _state.value = prev.copy(
                heartFilled = filled,
                faveNotice = if (replaceNotice) notice else prev.faveNotice,
                faveListRevision = if (bumpList) prev.faveListRevision + 1 else prev.faveListRevision,
            )
        }
        if (Looper.myLooper() == main.looper) {
            apply()
        } else {
            main.post(apply)
        }
    }

    fun setCanRequest(canRequest: Boolean?) {
        val apply = { _state.value = _state.value.copy(canRequest = canRequest) }
        if (Looper.myLooper() == main.looper) {
            apply()
        } else {
            main.post(apply)
        }
    }
}
