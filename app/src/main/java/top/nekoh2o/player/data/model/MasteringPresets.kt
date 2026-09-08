package top.nekoh2o.player.data.model

/** ID 是持久化/JNI 协议，不能通过列表顺序动态生成或重排。 */
data class MasteringPreset(val id: Int, val name: String, val group: String, val description: String)

object MasteringPresets {
    // Keep catalog IDs stable; expose only chains with a native implementation.
    val available: List<MasteringPreset> get() = all.filter { it.id in 15..34 }
    val all = listOf(
        MasteringPreset(1, "次惜母带", "基础", "去浑浊 EQ、谐波饱和、压缩与三段声场"),
        MasteringPreset(2, "臻母带", "基础", "三段多频带压缩与磁带染色"),
        MasteringPreset(3, "全景母带", "基础", "多频带压缩、加宽与短延迟交叉馈送"),
        MasteringPreset(4, "AI 母带", "基础", "三段动态频谱调节；纯 DSP，不使用 AI 模型"),
        MasteringPreset(5, "独家 AI 母带", "基础", "瞬态感知压缩；纯 DSP，不使用 AI 模型"),
        MasteringPreset(6, "蝰蛇母带", "基础", "次惜处理链，独立响度与峰值参数"),
        MasteringPreset(7, "声境·流光", "声境", "明亮高频与泛音增强"),
        MasteringPreset(8, "声境·破晓", "声境", "电子管染色、人声与高频提升"),
        MasteringPreset(9, "声境·归真", "声境", "暖厚低频与磁带染色"),
        MasteringPreset(10, "声境·星海", "声境", "空气感与宽阔高频"),
        MasteringPreset(11, "声境·清泉", "声境", "不使用饱和，温和压缩"),
        MasteringPreset(12, "声境·穹顶", "声境", "立体声空间模拟与高度反射"),
        MasteringPreset(13, "声境·源核", "声境", "电子管染色与立体声空间模拟"),
        MasteringPreset(14, "声境·无瑕", "声境", "高频增强与平面环绕模拟"),
        MasteringPreset(15, "惜·流年", "惜", "温和磁带染色与收拢声场"),
        MasteringPreset(16, "惜·微醺", "惜", "暖低频、磁带偏置与柔和压缩"),
        MasteringPreset(17, "惜·故里", "惜", "中频人声与电子管染色"),
        MasteringPreset(18, "惜·澄澈", "惜", "无饱和、慢压缩与平直 EQ"),
        MasteringPreset(19, "惜·絮语", "惜", "收低频，突出 2.5kHz 人声"),
        MasteringPreset(20, "次元·爆裂", "次元", "快速并行压缩与晶体管削波"),
        MasteringPreset(21, "次元·轰鸣", "次元", "40Hz 提升与低频保护侧链"),
        MasteringPreset(22, "次元·裂空", "次元", "5kHz 焦点与快速压缩"),
        MasteringPreset(23, "次元·熔毁", "次元", "中频密度与强晶体管染色"),
        MasteringPreset(24, "次元·璀璨", "次元", "16kHz 高频架与电子管染色"),
        MasteringPreset(25, "次元·深空", "次元", "慢压缩与宽阔声场"),
        MasteringPreset(26, "次元·极夜", "次元", "增强低频、收敛高频"),
        MasteringPreset(27, "次元·羽化", "次元", "无饱和、削减低频与高频提升"),
        MasteringPreset(28, "次元·全景", "次元", "轻微晶体管染色与声场处理"),
        MasteringPreset(29, "次元·虚数", "次元", "强压缩与明显泵感"),
        MasteringPreset(30, "跨界·卡带", "跨界", "6kHz 低通与重磁带染色"),
        MasteringPreset(31, "跨界·霓虹", "跨界", "并行压缩与电子管染色"),
        MasteringPreset(32, "跨界·留声", "跨界", "窄频宽、中频突出与收拢声场"),
        MasteringPreset(33, "跨界·碎梦", "跨界", "强削波与极限压缩；刻意失真风格"),
        MasteringPreset(34, "跨界·战歌", "跨界", "快速压缩与战鼓保护侧链")
    )
}
