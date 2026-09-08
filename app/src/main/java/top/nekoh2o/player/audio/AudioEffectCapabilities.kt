package top.nekoh2o.player.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** 能力来自当前音频会话的实际初始化结果，而不是设备声明的效果列表。 */
data class SystemEffectCapabilities(
    val equalizer: Boolean = false,
    val bassBoost: Boolean = false,
    val virtualizer: Boolean = false,
    val loudness: Boolean = false,
    val reverb: Boolean = false
) {
    val any: Boolean get() = equalizer || bassBoost || virtualizer || loudness || reverb
}

data class AudioEffectStatusState(
    val system: SystemEffectCapabilities = SystemEffectCapabilities(),
    val systemInitialized: Boolean = false,
    val nativeError: String? = null
)

object AudioEffectStatus {
    val state = MutableStateFlow(AudioEffectStatusState())

    fun publishSystem(capabilities: SystemEffectCapabilities, initialized: Boolean) {
        state.update { it.copy(system = capabilities, systemInitialized = initialized) }
    }
}
