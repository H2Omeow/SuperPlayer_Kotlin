package top.nekoh2o.player.audio

import android.media.audiofx.Equalizer
import android.media.audiofx.BassBoost
import android.media.audiofx.Virtualizer
import android.media.audiofx.PresetReverb
import android.util.Log
import top.nekoh2o.player.data.model.AudioEffectSettings

class SystemAudioEffectsManager(private val audioSessionId: Int) {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var reverb: PresetReverb? = null

    private var isInitialized = false

    fun initialize(): Boolean {
        return try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
            }

            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = true
            }

            virtualizer = Virtualizer(0, audioSessionId).apply {
                enabled = true
            }

            reverb = PresetReverb(0, audioSessionId).apply {
                enabled = true
                preset = PresetReverb.PRESET_NONE
            }

            isInitialized = true
            Log.i(TAG, "音效引擎初始化成功")
            true
        } catch (e: Exception) {
            Log.e(TAG, "音效引擎初始化失败：${e.message}")
            false
        }
    }

    fun applySettings(settings: AudioEffectSettings) {
        if (!isInitialized) return

        try {
            equalizer?.let { eq ->
                val numBands = eq.numberOfBands.toInt()
                settings.eqBands.take(numBands).forEachIndexed { index, gain ->
                    val bandLevel = (gain * 100).toInt().toShort()
                    eq.setBandLevel(index.toShort(), bandLevel.coerceIn(
                        eq.bandLevelRange[0],
                        eq.bandLevelRange[1]
                    ))
                }
            }

            bassBoost?.setStrength((settings.bassBoost * 10).toShort())

            virtualizer?.setStrength((settings.virtualizer * 10).toShort())

            reverb?.preset = when {
                settings.reverbWet == 0 -> PresetReverb.PRESET_NONE
                settings.reverbRoomSize > 70 -> PresetReverb.PRESET_LARGEHALL
                settings.reverbRoomSize > 40 -> PresetReverb.PRESET_MEDIUMHALL
                else -> PresetReverb.PRESET_SMALLROOM
            }

        } catch (e: Exception) {
            Log.e(TAG, "应用音效设置失败：${e.message}")
        }
    }

    fun release() {
        equalizer?.release()
        bassBoost?.release()
        virtualizer?.release()
        reverb?.release()
        isInitialized = false
    }

    companion object {
        private const val TAG = "SystemAudioFX"
    }
}
