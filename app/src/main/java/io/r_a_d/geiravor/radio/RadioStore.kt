package io.r_a_d.geiravor.radio

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uniffi.geiravor_core.Status
import uniffi.geiravor_core.StatusListener

data class RadioUiState(
    val status: Status? = null,
    val streamDown: Boolean = false,
    val canRequest: Boolean? = null,
)

object RadioStore : StatusListener {
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(RadioUiState())
    val state: StateFlow<RadioUiState> = _state.asStateFlow()

    override fun onUpdate(status: Status, streamDown: Boolean) {
        main.post {
            _state.value = _state.value.copy(status = status, streamDown = streamDown)
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
