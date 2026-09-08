# SuperPlayer / NekoPlayer 音效排查规格与进度

## 来源与授权

- 会话：`~/.claude/projects/-home-h2o/b86b8583-d2ac-40c6-9c20-d7da61fad7e9.jsonl`。
- 原规格：2026-09-09 00:52（北京时间）；用户于 00:54 明确授权，并于 01:18 要求继续。
- 任务：修复专业音效无法播放，核对文档预设，按实际引擎能力显示控制项。
- 2026-09-09 接续：历史最后停在 JNI / DSP / 服务 / UI 未完成接线的状态。
- 用户确认目前只有参数文档，会另行索取原始 C++ 源码。
- 不创建子 Agent；不包含离线导出、真实 AI 推理。
- 后续用户明确授权：完成验证后提交并推送 GitHub，覆盖 `v1.0.8-pre`，更新 README 与指定项目致谢。

## 已完成的实现

- [x] PCM 输入被消费、输出可读，使用 Media3 BaseAudioProcessor 管理输出交接与 EOS。
- [x] 常驻处理器在关闭/系统模式下旁路；切换引擎不重建播放器，保留播放队列与状态。
- [x] 每个处理器独享 Native 实例；JNI 操作与释放在播放线程进行。
- [x] JNI 新接口对齐，检查格式、直接缓冲区容量、完整声道帧和 EQ 数组。
- [x] 初始化/处理失败恢复原 PCM 并公布错误；切换设置或 flush 后可以重试。
- [x] 修正 RBJ Peaking EQ 反馈系数符号，增加独立的滤波、混响、签名链和参数模块。
- [x] 单/双声道处理，按采样率初始化延迟线，接入混响空间与阻尼。
- [x] 系统效果独立初始化，检查控制权与启用返回值；按实际成功项展示界面。
- [x] 系统 EQ 按设备中心频率插值；响度接入 LoudnessEnhancer；混响接入 aux send。
- [x] 声明 session 0 辅助混响所需的普通权限 MODIFY_AUDIO_SETTINGS。
- [x] 预设/混合比例持久化与 Intent 接线，非法旧设置归一化，一次性重置全部参数。
- [x] 补充文档 16 个 EQ 预设，保留原有 5 个通用预设，共 21 个。
- [x] 实现文档第 6 章签名系列 20 组：惜 5、次元 10、跨界 5。

## 预设能力与限制

| 范围 | 当前状态 | 后续 |
|---|---|---|
| EQ 21 组 | 已接入；系统需实际支持均衡器 | 真机频段映射回归 |
| 签名母带 20 组，应用 ID 15..34 | 按文档参数与描述实现完整串联链，可选 | 获取源码后逐项核对公式与听感 |
| 基础母带 6 组，应用 ID 1..6 | 仅保留目录定义，不向用户提供可选入口 | 接入原始独立处理链 |
| 声境母带 8 组，应用 ID 7..14 | 仅保留目录定义，不向用户提供可选入口 | 接入原始共享链与参数 |

签名链顺序：输入增益、DC 阻断、高/低通、三段 EQ、含可选侧链高通的压缩、饱和、M/S 宽度、限幅。
遵从文档对原实现简化的说明：仅使用 midWidth；不声称实现声明性的 4x/8x 过采样；48 dB/oct 低通仍为两级。
当前限制器是增益平滑加采样峰值保护，不等于过采样真峰值保证；文档没有给出 autoRelease 的具体算法，当前使用表内固定释放时间。
文档的 TUBE 多项式在极端输入下会快速增长，当前在饱和前后增加数值边界保护。
混响只处理输入期间的尾音，不在曲末追加额外时长。
当前 PCM16 实时通路不提供 24-bit/192kHz 升格、AI 模型或杜比多声道解码。

不能以名称或近似 EQ 替代剩余 14 组链路。待核对的关键定义包括：

- LoudnessNormalizer 的 200ms 窗口与固定 8192 样本之间的关系、静音门限和状态重置。
- TapeSaturation 的完整偏置/包络跟踪与高频压缩实现。
- AI 动态均衡的 targetEnergyDb 定义和逐帧/逐样本更新规则。
- 前视限幅的完整延迟、排空与声道联动契约。
- 高频恢复与空间渲染的完整滤波器/延迟线状态更新。

## 需索取的源码

以下文件必须来自同一版本的 `app/src/main/cpp/`：

```text
CMakeLists.txt
audio-dsp.h
signature-master-dsp.h
shengjing-master-dsp.h
native-audio.cpp
signature-master-1.cpp
signature-master-2.cpp
signature-master-3.cpp
signature-master-4.cpp
shengjing-master-liuguang.cpp
shengjing-master-poxiao.cpp
shengjing-master-guizhen.cpp
shengjing-master-xinghai.cpp
shengjing-master-qingquan.cpp
shengjing-master-qiongding.cpp
shengjing-master-yuanhe.cpp
shengjing-master-wuxia.cpp
```

同时需要 `player/CiyuanxiNativeAudioEffectController.kt`，以及上述文件实际依赖的其他本地头文件/DSP 实现。
文档不足以穷举传递依赖，提供完整的音频 DSP 源码目录可避免遗漏。
当前不需要离线导出源文件、历史 `.bak` 文件或签名协议中的凭据。

## 验证进度

- [x] C++ ASan/UBSan：20 组预设 × 6 个采样率 × 单/双声道；输出非空/有界/互异、分块一致、重置、全部 PCM16 数值零参数直通。
- [x] JNI 主机测试：真实动态库符号、容量边界、单/双声道与释放；另用 javap 核对实际 Kotlin native 方法描述符。
- [x] JVM 回归 9 项全部通过：缓冲区位置、EOS、flush/reset、格式切换、引擎旁路、JNI 失败恢复、旧设置归一化。
- [x] 最终 debug 构建与 lint 通过；lint 仍有仓库其他模块的警告。
- [x] Release 构建与 JNI 保留规则核验；首次 daemon 意外退出后，以单 worker、1GB JVM 堆重试成功。
- [ ] 真机：当前无连接设备，尚未确认专业音效实际播放恢复。

## 下一步

收到源码后先核对签名链，再接入基础/声境链；处理前视延迟与 EOS 后才开放相应预设。
真机验收覆盖：启动时选择不同引擎、播放中反复切换、seek/切歌/自然结束、蓝牙与耳机切换、设置恢复、系统不支持项隐藏。

## 复现验证

Android/JVM 构建：

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleRelease --console=plain
```

Linux 主机 DSP 检查（GCC，启用 ASan/UBSan）：

```sh
g++ -std=c++17 -O1 -g -fsanitize=address,undefined -fno-omit-frame-pointer -Iapp/src/main/cpp app/src/test/cpp/audio-engine-test.cpp -o /tmp/neko-audio-engine-test
/tmp/neko-audio-engine-test
```

Linux 主机 JNI 检查（将 JDK include 路径替换为本机安装位置）：

```sh
g++ -std=c++17 -O2 -shared -fPIC -I/usr/lib/jvm/zulu-21-amd64/include -I/usr/lib/jvm/zulu-21-amd64/include/linux -Iapp/src/main/cpp app/src/main/cpp/native-audio-effects.cpp -o /tmp/libneko-jni-test.so
javac -d /tmp/neko-jni-tests app/src/test/cpp/NativeAudioEffectsController.java
java -cp /tmp/neko-jni-tests top.nekoh2o.player.audio.NativeAudioEffectsController /tmp/libneko-jni-test.so
```
