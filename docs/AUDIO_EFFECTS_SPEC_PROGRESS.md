# SuperPlayer 音效技术规格与验证状态

## 目标与范围

- 修复专业音效播放链，核对预设参数，按实际引擎能力显示控制项。
- 实现范围为实时音效，不包含离线导出或真实 AI 推理。
- 已实现链路与待接入链路分别列出；缺少完整算法定义的预设暂不开放。

## 已完成的实现

- [x] PCM 输入被消费、输出可读，使用 Media3 BaseAudioProcessor 管理输出交接与 EOS。
- [x] 常驻处理器在关闭/系统模式下旁路；切换引擎不重建播放器，保留播放队列与状态。
- [x] 每个处理器独享 Native 实例；JNI 操作与释放在播放线程进行。
- [x] JNI 新接口对齐，检查格式、直接缓冲区容量、完整声道帧和 EQ 数组。
- [x] 初始化/处理失败恢复原 PCM 并公布错误；切换设置或 flush 后可以重试。
- [x] 修正 RBJ Peaking EQ 反馈系数符号，增加独立的滤波、混响、签名链和参数模块。
- [x] 将高输入下发散的着色多项式替换为有界平滑饱和；母带及最终输出使用瞬时压峰、平滑释放的声道联动保护。
- [x] 单/双声道处理，按采样率初始化延迟线，接入混响空间与阻尼。
- [x] 系统效果独立初始化，检查控制权与启用返回值；按实际成功项展示界面。
- [x] 系统 EQ 按设备中心频率插值；响度接入 LoudnessEnhancer；混响接入 aux send。
- [x] 声明 session 0 辅助混响所需的普通权限 MODIFY_AUDIO_SETTINGS。
- [x] 预设/混合比例持久化与 Intent 接线，非法旧设置归一化，一次性重置全部参数。
- [x] 补充文档 16 个 EQ 预设，保留原有 5 个通用预设，共 21 个。
- [x] 按参考参数表接入签名系列 20 组：惜 5、次元 10、跨界 5；原实现一致性仍待核对。

## 实现入口

| 文件 | 职责 |
|---|---|
| `app/src/main/java/top/nekoh2o/player/audio/NativeAudioProcessor.kt` | Media3 PCM 缓冲区与处理器生命周期 |
| `app/src/main/java/top/nekoh2o/player/audio/NativeAudioEffectsController.kt` | JNI 接口与参数传递 |
| `app/src/main/cpp/native-audio-effects.cpp` | Native 实例创建、输入校验与释放 |
| `app/src/main/cpp/audio-engine.h` | 实时 DSP 调度与混合 |
| `app/src/main/cpp/audio-dsp.h` | 基础滤波器 |
| `app/src/main/cpp/reverb.h` | 混响延迟线 |
| `app/src/main/cpp/signature-chain.h` | 签名母带处理链 |
| `app/src/main/cpp/signature-presets.h` | 20 组签名母带参数 |

JNI 使用 `nativeCreate`、`nativeConfigure`、`nativeProcess`、`nativeReset`、`nativeRelease`。
调用以实例句柄为边界；PCM 通过直接 ByteBuffer 传入，状态仅由播放线程访问。

## 预设能力与限制

| 范围 | 当前状态 | 后续 |
|---|---|---|
| EQ 21 组 | 已接入；系统需实际支持均衡器 | 真机频段映射回归 |
| 签名母带 20 组，应用 ID 15..34 | 已接入滤波、压缩、饱和、声场与采样峰值保护，可选 | 获取源码后逐项核对公式与听感 |
| 基础母带 6 组，应用 ID 1..6 | 仅保留目录定义，不向用户提供可选入口 | 接入原始独立处理链 |
| 声境母带 8 组，应用 ID 7..14 | 仅保留目录定义，不向用户提供可选入口 | 接入原始共享链与参数 |

签名链顺序：输入增益、DC 阻断、高/低通、三段 EQ、含可选侧链高通的压缩、饱和、M/S 宽度、限幅。
遵从文档对原实现简化的说明：仅使用 midWidth；不声称实现声明性的 4x/8x 过采样；48 dB/oct 低通仍为两级。
当前限制器在没有前视缓冲的条件下使用表内攻击/释放时间平滑增益，并以指数软拐点处理残余超峰；它是采样峰值保护，不等于过采样真峰值保证。
原着色多项式在高输入下会快速增长，当前使用奇对称、单调且全范围有界的平滑饱和曲线，避免内部数值膨胀后进入硬裁剪。
混响只处理输入期间的尾音，不在曲末追加额外时长。
当前 PCM16 实时通路不提供 24-bit/192kHz 升格、AI 模型或杜比多声道解码。

不能以名称或近似 EQ 替代剩余 14 组链路。待核对的关键定义包括：

- LoudnessNormalizer 的 200ms 窗口与固定 8192 样本之间的关系、静音门限和状态重置。
- TapeSaturation 的完整偏置/包络跟踪与高频压缩实现。
- AI 动态均衡的 targetEnergyDb 定义和逐帧/逐样本更新规则。
- 前视限幅的完整延迟、排空与声道联动契约。
- 高频恢复与空间渲染的完整滤波器/延迟线状态更新。

## 待补充的参考源码

以下是用于核对预设和接入剩余 14 组母带的外部参考源码，**不是编译当前项目所缺的文件**。
文件应来自同一版本的参考实现 `app/src/main/cpp/`：

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
参考参数表不足以穷举传递依赖，需同时包含所引用的本地 DSP 文件。
离线导出源文件与历史 `.bak` 文件不属于实时处理范围。

## 验证进度

- [x] C++ ASan/UBSan：20 组预设 × 6 个采样率 × 单/双声道；覆盖热信号与复合极端增益，检查输出有界/互异、分块一致、重置、饱和曲线和全部 PCM16 数值零参数直通。
- [x] JNI 主机测试：真实动态库符号、容量边界、单/双声道与释放；另用 javap 核对实际 Kotlin native 方法描述符。
- [x] JVM 回归 9 项全部通过：缓冲区位置、EOS、flush/reset、格式切换、引擎旁路、JNI 失败恢复、旧设置归一化。
- [x] 最终 debug 构建与 lint 通过；lint 仍有仓库其他模块的警告。
- [x] Release 构建与 JNI 保留规则核验通过。
- [ ] 真机播放与设备兼容性验收。

## 下一步

后续实现依赖原始源码：先核对签名链，再接入基础/声境链；处理前视延迟与 EOS 后才开放相应预设。
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

Linux 主机 JNI 检查（JAVA_HOME 指向本机 JDK 安装目录）：

```sh
g++ -std=c++17 -O2 -shared -fPIC -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" -Iapp/src/main/cpp app/src/main/cpp/native-audio-effects.cpp -o /tmp/libneko-jni-test.so
javac -d /tmp/neko-jni-tests app/src/test/cpp/NativeAudioEffectsController.java
java -cp /tmp/neko-jni-tests top.nekoh2o.player.audio.NativeAudioEffectsController /tmp/libneko-jni-test.so
```
