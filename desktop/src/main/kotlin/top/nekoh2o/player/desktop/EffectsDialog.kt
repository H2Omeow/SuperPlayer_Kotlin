package top.nekoh2o.player.desktop

import top.nekoh2o.player.data.model.*
import java.awt.*
import javax.swing.*

internal object DesktopEffects {
    fun load(): AudioEffectSettings {
        val p = DesktopPaths.preferences
        fun number(key: String, fallback: Int) = p.getString("fx." + key, null)?.toIntOrNull() ?: fallback
        return AudioEffectSettings(engine = if (p.getBoolean("fx.enabled", false)) AudioEffectEngine.NATIVE_CPP else AudioEffectEngine.NONE,
            eqBands = List(10) { p.getString("fx.eq" + it, "0")?.toFloatOrNull() ?: 0f },
            bassBoost = number("bass",0), virtualizer = number("width",0), reverbWet = number("wet",0), reverbRoomSize = number("room",50),
            reverbDamping = number("damping",30), loudnessGain = number("gain",0), masteringPresetId = number("master",0), masteringMix = number("mix",100)).normalized()
    }
    fun save(s: AudioEffectSettings) {
        val edit = DesktopPaths.preferences.edit().putBoolean("fx.enabled", s.engine == AudioEffectEngine.NATIVE_CPP)
        s.eqBands.forEachIndexed { i, value -> edit.putString("fx.eq" + i, value.toString()) }
        mapOf("bass" to s.bassBoost, "width" to s.virtualizer, "wet" to s.reverbWet, "room" to s.reverbRoomSize, "damping" to s.reverbDamping,
            "gain" to s.loudnessGain, "master" to s.masteringPresetId, "mix" to s.masteringMix).forEach { (key,value) -> edit.putString("fx." + key, value.toString()) }
        edit.apply()
    }
}

internal class EffectsDialog(owner: JFrame, player: DesktopPlayer) : JDialog(owner, "专业音效与母带", false) {
    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE; size = Dimension(780,630); setLocationRelativeTo(owner)
        val initial = player.effects
        val root = JPanel(BorderLayout(10,10)).apply { border = BorderFactory.createEmptyBorder(16,16,16,16) }; contentPane = root
        val enabled = JCheckBox("启用专业音效（C++ DSP）", initial.engine == AudioEffectEngine.NATIVE_CPP)
        val preset = JComboBox((listOf("自定义") + EQPresets.presets.keys).toTypedArray())
        val masters = listOf(MasteringPreset(0,"关闭母带","","")) + MasteringPresets.available
        val master = JComboBox(masters.map { it.name }.toTypedArray()).apply { selectedIndex = masters.indexOfFirst { it.id == initial.masteringPresetId }.coerceAtLeast(0) }
        root.add(row(enabled, JLabel("均衡器"), preset), BorderLayout.NORTH)
        val center = JPanel(BorderLayout())
        val eqPanel = JPanel(GridLayout(1,10,4,4))
        val bands = initial.eqBands.mapIndexed { i, value ->
            JSlider(SwingConstants.VERTICAL, -15,15,value.toInt()).apply {
                majorTickSpacing = 5; paintTicks = true; toolTipText = EQPresets.frequencies[i].toString() + " Hz"
                val slider = this
                eqPanel.add(JPanel(BorderLayout()).apply { add(slider); add(JLabel(EQPresets.frequencies[i].toString(),SwingConstants.CENTER),BorderLayout.SOUTH) })
            }
        }
        center.add(eqPanel); root.add(center)
        val controls = JPanel(GridLayout(0,2,8,4))
        fun slider(label: String, value: Int): JSlider = JSlider(0,100,value).also { controls.add(JLabel(label)); controls.add(it) }
        val bass = slider("低音增强", initial.bassBoost); val width = slider("立体声宽度",initial.virtualizer)
        val wet = slider("混响",initial.reverbWet); val room = slider("空间大小",initial.reverbRoomSize); val damping = slider("混响阻尼",initial.reverbDamping)
        val gain = slider("响度增益",initial.loudnessGain); controls.add(JLabel("母带渲染")); controls.add(master)
        val mix = slider("母带混合", initial.masteringMix)
        fun update() {
            player.effects = AudioEffectSettings(if (enabled.isSelected) AudioEffectEngine.NATIVE_CPP else AudioEffectEngine.NONE,
                bands.map { it.value.toFloat() }, eqPresetName = preset.selectedItem.toString(), bassBoost = bass.value, virtualizer = width.value,
                reverbWet = wet.value, reverbRoomSize = room.value, reverbDamping = damping.value, loudnessGain = gain.value,
                masteringPresetId = masters[master.selectedIndex].id, masteringMix = mix.value).normalized()
        }
        bands.forEach { it.addChangeListener { update() } }; listOf(bass,width,wet,room,damping,gain,mix).forEach { it.addChangeListener { update() } }
        enabled.addActionListener { update() }; master.addActionListener { update() }
        preset.addActionListener { EQPresets.presets[preset.selectedItem]?.let { values -> bands.forEachIndexed { i, slider -> slider.value = values[i].toInt() } }; update() }
        val lower = JPanel(BorderLayout(8,8)); lower.add(controls)
        lower.add(row(button("恢复平直") { bands.forEach { it.value = 0 }; bass.value = 0; width.value = 0; wet.value = 0; gain.value = 0; master.selectedIndex = 0; preset.selectedIndex = 0; update() },
            button("保存并关闭") { update(); DesktopEffects.save(player.effects); dispose() }), BorderLayout.SOUTH)
        root.add(lower, BorderLayout.SOUTH)
    }
}
