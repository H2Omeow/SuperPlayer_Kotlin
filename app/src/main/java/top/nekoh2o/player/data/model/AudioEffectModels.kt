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
    val loudnessGain: Int = 0,
    val masteringPresetId: Int = 0,
    val masteringMix: Int = 100
) {
    fun normalized() = copy(
        eqBands = List(10) { index ->
            eqBands.getOrNull(index)?.takeIf { it.isFinite() }?.coerceIn(-15f, 15f) ?: 0f
        },
        bassBoost = bassBoost.coerceIn(0, 100),
        virtualizer = virtualizer.coerceIn(0, 100),
        reverbWet = reverbWet.coerceIn(0, 100),
        reverbRoomSize = reverbRoomSize.coerceIn(0, 100),
        reverbDamping = reverbDamping.coerceIn(0, 100),
        loudnessGain = loudnessGain.coerceIn(0, 100),
        masteringPresetId = masteringPresetId.takeIf { it in 0..34 } ?: 0,
        masteringMix = masteringMix.coerceIn(0, 100)
    )
}

object EQPresets {
    val presets = mapOf(
        "流行" to listOf(0f, 2f, 3f, 4f, 3f, 2f, 1f, 0f, 0f, 0f),
        "摇滚" to listOf(5f, 4f, 2f, 0f, -1f, -1f, 0f, 2f, 4f, 5f),
        "古典" to listOf(0f, 0f, 0f, 0f, 0f, 0f, -2f, -3f, -4f, -5f),
        "人声" to listOf(0f, -2f, -1f, 1f, 3f, 3f, 2f, 1f, 0f, 0f),
        "低音炮" to listOf(8f, 6f, 3f, 1f, -1f, -1f, 0f, 1f, 2f, 3f),
        "次元回响" to listOf(3f, 2f, 1f, 0f, -1f, -1f, 0f, 1f, 2f, 3f),
        "星海幻梦" to listOf(2f, 3f, 2f, 1f, 0f, 0f, 1f, 2f, 3f, 2f),
        "初音絮语" to listOf(4f, 3f, 2f, 1f, 0f, -1f, 0f, 1f, 2f, 3f),
        "樱瓣环绕" to listOf(4f, 3f, 2f, 0f, -2f, -2f, 0f, 2f, 4f, 5f),
        "神谕调音" to listOf(2f, 4f, 2f, -1f, -2f, 0f, 2f, 4f, 5f, 4f),
        "次元穿透" to listOf(5f, 4f, 2f, -1f, -3f, -3f, -1f, 2f, 4f, 6f),
        "幻灵近耳" to listOf(5f, 4f, 2f, 1f, 0f, -2f, 1f, 3f, 5f, 4f),
        "月华音乐厅" to listOf(1f, 2f, 3f, 3f, 2f, 2f, 1f, 1f, 0f, -1f),
        "琉璃现场" to listOf(0f, 1f, 2f, 3f, 3f, 2f, 1f, 1f, 0f, -1f),
        "深海低语" to listOf(10f, 8f, 4f, 2f, 0f, -2f, -2f, -1f, 0f, 1f),
        "心跳节拍" to listOf(8f, 6f, 3f, 1f, -1f, -1f, 1f, 3f, 6f, 8f),
        "次元共鸣" to listOf(3f, 4f, 5f, 4f, 2f, 1f, 0f, 2f, 4f, 5f),
        "雷电脉冲" to listOf(9f, 7f, 4f, 1f, -1f, -1f, 0f, 2f, 5f, 7f),
        "月光守护" to listOf(-5f, -3f, -1f, 0f, 1f, 2f, 1f, 0f, -1f, -2f),
        "龙吟低炮" to listOf(12f, 10f, 6f, 3f, 1f, -1f, -2f, -2f, 0f, 2f),
        "AI调音大师" to listOf(2f, 3f, 1f, -1f, -1f, 0f, 1f, 2f, 3f, 2f)
    )

    val frequencies = listOf(32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
}
