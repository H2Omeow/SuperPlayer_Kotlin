package top.nekoh2o.player.data.model

enum class AudioEffectEngine(val value: Int, val label: String) {
    NONE(0, "关闭音效"),
    SYSTEM(1, "系统音效（兼容性好）"),
    NATIVE_CPP(2, "专业音效（高音质）")
}

data class AudioEffectSettings(
    val engine: AudioEffectEngine = AudioEffectEngine.NONE,
    val eqBands: List<Float> = List(10) { 0f },
    val eqPresetName: String = "",
    val bassBoost: Int = 0,
    val virtualizer: Int = 0,
    val reverbWet: Int = 0,
    val reverbRoomSize: Int = 50,
    val reverbDamping: Int = 30,
    val loudnessGain: Int = 0
)

object EQPresets {
    val presets = mapOf(
        "流行" to listOf(0f, 2f, 3f, 4f, 3f, 2f, 1f, 0f, 0f, 0f),
        "摇滚" to listOf(5f, 4f, 2f, 0f, -1f, -1f, 0f, 2f, 4f, 5f),
        "古典" to listOf(0f, 0f, 0f, 0f, 0f, 0f, -2f, -3f, -4f, -5f),
        "人声" to listOf(0f, -2f, -1f, 1f, 3f, 3f, 2f, 1f, 0f, 0f),
        "低音炮" to listOf(8f, 6f, 3f, 1f, -1f, -1f, 0f, 1f, 2f, 3f),
        "次元回响" to listOf(3f, 2f, 1f, 0f, -1f, -1f, 0f, 1f, 2f, 3f),
        "星海幻梦" to listOf(2f, 3f, 2f, 1f, 0f, 0f, 1f, 2f, 3f, 2f),
        "初音絮语" to listOf(4f, 3f, 2f, 1f, 0f, -1f, 0f, 1f, 2f, 3f)
    )

    val frequencies = listOf(32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
}
