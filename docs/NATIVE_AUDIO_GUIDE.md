# Native 音效引擎启用指南

## 当前状态

✅ **方案 A（系统音效）**：已完全实现并可用  
⏳ **方案 B（Native C++ 音效）**：代码已准备，等待 NDK 环境

## 为什么需要 Native 音效？

方案 A 使用 Android 系统 API，存在以下限制：
1. 不同设备效果差异大（华为、小米、OPPO 等 ROM 实现不同）
2. 部分设备不支持或效果很差
3. 无法精细控制算法参数（如混响只能选预设）
4. 无法扩展更高级的音效（压缩器、限幅器、母带处理）

方案 B 的优势：
- ✅ 全设备一致的专业音质
- ✅ 完全自定义的 DSP 算法
- ✅ 可扩展到 34 种母带处理链
- ✅ 适合音乐发烧友和专业用户

## 已准备的代码

### 1. Native 层（C++）
```
app/src/main/cpp/
├── CMakeLists.txt              # CMake 构建配置
├── audio-dsp.h                 # Biquad 滤波器算法库
└── native-audio-effects.cpp    # JNI 实现
```

**实现的算法**：
- 10 段参数均衡器（RBJ Cookbook Peaking EQ）
- 低音增强（Low Shelf Filter）
- 3D 环绕（M/S 立体声加宽）
- Schroeder 混响（4 个 comb + 2 个 allpass）
- 响度增益（带防爆音保护）

### 2. JNI 桥接层（Kotlin）
```
app/src/main/java/top/nekoh2o/player/audio/
└── NativeAudioEffectsController.kt
```

**JNI 方法**：
- `nativeInit(sampleRate: Int): Boolean` - 初始化引擎
- `nativeSetEqBands(bands: FloatArray)` - 设置 10 段 EQ
- `nativeSetBassBoost(strength: Int)` - 设置低音增强
- `nativeSetVirtualizer(strength: Int)` - 设置 3D 环绕
- `nativeSetReverbWet(wet: Int)` - 设置混响湿度
- `nativeSetLoudnessGain(gain: Int)` - 设置响度增益
- `nativeProcessSamples(samples: ShortArray)` - 实时处理音频
- `nativeRelease()` - 释放资源

## 启用步骤

### 第 1 步：安装 Android NDK

**方法 1：使用 Android Studio**
1. 打开 Android Studio
2. Tools → SDK Manager
3. SDK Tools 标签页
4. 勾选 "NDK (Side by side)" 版本 26.1.10909125
5. 点击 Apply 下载安装（约 1GB）

**方法 2：使用命令行（推荐）**
```bash
cd ~/android-sdk/cmdline-tools/latest/bin
./sdkmanager "ndk;26.1.10909125"
```

**方法 3：手动下载**
1. 访问 https://developer.android.com/ndk/downloads
2. 下载 NDK r26c (26.1.10909125)
3. 解压到 `~/android-sdk/ndk/26.1.10909125/`
4. 确保 `source.properties` 文件存在

### 第 2 步：启用 NDK 配置

编辑 `app/build.gradle.kts`：

```kotlin
android {
    // ... 现有配置 ...

    defaultConfig {
        applicationId = "top.nekoh2o.player"
        minSdk = 24
        targetSdk = 34
        versionCode = buildVersionCode
        versionName = buildVersionName

        // 添加这部分
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    // ... 其他配置 ...

    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

    // 添加这部分
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}
```

### 第 3 步：编译验证

```bash
cd /home/h2o/NekoPlayer
./gradlew :app:assembleDebug
```

**预期输出**：
```
> Task :app:externalNativeBuildDebug
Build nekoplayer_audio_effects arm64-v8a
...
BUILD SUCCESSFUL in Xs
```

**检查 .so 文件**：
```bash
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep libnekoplayer
```

应该看到：
```
lib/arm64-v8a/libnekoplayer_audio_effects.so
lib/armeabi-v7a/libnekoplayer_audio_effects.so
lib/x86/libnekoplayer_audio_effects.so
lib/x86_64/libnekoplayer_audio_effects.so
```

### 第 4 步：集成到播放器（需要补充）

**当前状态**：PlaybackService.kt 中方案 B 分支为空

**需要创建**：`NativeAudioProcessor.kt`
```kotlin
package top.nekoh2o.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

@UnstableApi
class NativeAudioProcessor : AudioProcessor {
    private var inputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var outputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var isActive = false
    private var pendingInputBuffer = AudioProcessor.EMPTY_BUFFER

    init {
        NativeAudioEffectsController.nativeInit(44100)
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        this.inputAudioFormat = inputAudioFormat

        // 仅支持 16-bit 立体声
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT &&
            inputAudioFormat.channelCount == 2) {

            NativeAudioEffectsController.nativeInit(inputAudioFormat.sampleRate)
            isActive = true
            outputAudioFormat = inputAudioFormat
        } else {
            isActive = false
            outputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
        }

        return outputAudioFormat
    }

    override fun isActive(): Boolean = isActive

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive) return

        // 转换 ByteBuffer → ShortArray → 处理 → ByteBuffer
        val samples = ShortArray(inputBuffer.remaining() / 2)
        inputBuffer.asShortBuffer().get(samples)

        NativeAudioEffectsController.nativeProcessSamples(samples)

        val outputBuffer = replaceOutputBuffer(samples.size * 2)
        outputBuffer.asShortBuffer().put(samples)
        outputBuffer.flip()
    }

    override fun getOutput(): ByteBuffer = pendingInputBuffer

    override fun queueEndOfStream() {
        // Native 处理无缓冲延迟，直接标记结束
    }

    override fun isEnded(): Boolean = pendingInputBuffer === AudioProcessor.EMPTY_BUFFER

    override fun flush() {
        pendingInputBuffer = AudioProcessor.EMPTY_BUFFER
    }

    override fun reset() {
        flush()
        inputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
        outputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
        isActive = false
        NativeAudioEffectsController.nativeRelease()
    }

    private fun replaceOutputBuffer(size: Int): ByteBuffer {
        if (pendingInputBuffer.capacity() < size) {
            pendingInputBuffer = ByteBuffer.allocateDirect(size)
                .order(ByteOrder.nativeOrder())
        } else {
            pendingInputBuffer.clear()
        }
        return pendingInputBuffer
    }
}
```

**修改 PlaybackService.kt**：
```kotlin
private fun updateAudioEffects(settings: AudioEffectSettings) {
    when (settings.engine) {
        AudioEffectEngine.NONE -> {
            audioEffectsManager?.release()
            audioEffectsManager = null
        }
        AudioEffectEngine.SYSTEM -> {
            // ... 现有代码 ...
        }
        AudioEffectEngine.NATIVE_CPP -> {
            audioEffectsManager?.release()
            audioEffectsManager = null
            
            // 初始化 Native 引擎
            if (NativeAudioEffectsController.nativeInit(44100)) {
                NativeAudioEffectsController.nativeSetEqBands(settings.eqBands.toFloatArray())
                NativeAudioEffectsController.nativeSetBassBoost(settings.bassBoost)
                NativeAudioEffectsController.nativeSetVirtualizer(settings.virtualizer)
                NativeAudioEffectsController.nativeSetReverbWet(settings.reverbWet)
                NativeAudioEffectsController.nativeSetLoudnessGain(settings.loudnessGain)
                
                // TODO: 需要重新创建 ExoPlayer 并注入 NativeAudioProcessor
                // 这需要更复杂的架构调整
            }
        }
    }
}
```

### 第 5 步：测试

1. **初始化测试**
   ```kotlin
   // 在 PlaybackService.onCreate() 中测试
   val success = NativeAudioEffectsController.nativeInit(44100)
   Log.i("PlaybackService", "Native 音效初始化: $success")
   ```

2. **处理测试**
   ```kotlin
   // 创建测试音频数据
   val testSamples = ShortArray(8192) { (Math.random() * 32767).toInt().toShort() }
   NativeAudioEffectsController.nativeProcessSamples(testSamples)
   // 检查是否崩溃
   ```

3. **性能测试**
   ```kotlin
   val iterations = 1000
   val startTime = System.currentTimeMillis()
   repeat(iterations) {
       NativeAudioEffectsController.nativeProcessSamples(testSamples)
   }
   val elapsed = System.currentTimeMillis() - startTime
   Log.i("Performance", "处理 $iterations 次耗时 ${elapsed}ms，平均 ${elapsed.toFloat()/iterations}ms")
   ```

## 技术挑战

### 1. ExoPlayer AudioProcessor 集成
**问题**：ExoPlayer 的 AudioProcessor 需要通过 RenderersFactory 注入

**解决方案**：
```kotlin
val player = ExoPlayer.Builder(this)
    .setRenderersFactory(
        DefaultRenderersFactory(this).apply {
            setEnableAudioTrackPlaybackParams(false)
            // 需要更深入的 RenderersFactory 自定义
        }
    )
    .build()
```

### 2. 实时处理延迟
**要求**：处理延迟 < 50ms（避免音画不同步）

**优化方向**：
- 使用 ARM NEON SIMD 指令加速滤波器计算
- 减少 comb 滤波器数量（4 → 2）
- 降低混响质量（用户可选）

### 3. CPU 占用
**目标**：< 8%（单核）

**监控方法**：
```bash
adb shell top -d 1 | grep nekoh2o.player
```

## APK 体积影响

**方案 A（仅系统音效）**：
- 增量：~50KB（纯 Kotlin 代码）

**方案 B（包含 Native）**：
- 增量：~500KB
  - arm64-v8a: ~150KB
  - armeabi-v7a: ~130KB
  - x86_64: ~120KB
  - x86: ~100KB

## 疑难排查

### 问题 1：UnsatisfiedLinkError
```
java.lang.UnsatisfiedLinkError: dlopen failed: library "libnekoplayer_audio_effects.so" not found
```

**原因**：.so 文件未正确打包到 APK

**解决**：
```bash
# 检查 .so 是否存在
find app/build/intermediates/cxx -name "*.so"

# 检查 APK 内容
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep .so
```

### 问题 2：Native 崩溃
```
A/libc: Fatal signal 11 (SIGSEGV)
```

**调试方法**：
```bash
# 获取崩溃日志
adb logcat -d > crash.log

# 查看 tombstone
adb shell ls /data/tombstones/
adb pull /data/tombstones/tombstone_XX .

# 使用 ndk-stack 解析
~/android-sdk/ndk/26.1.10909125/ndk-stack -sym app/build/intermediates/cxx/Debug -dump crash.log
```

### 问题 3：音质失真
**检查点**：
- 防爆音裁剪是否正常（clampf）
- 增益值是否过大（> 15dB）
- 混响反馈系数是否稳定（< 1.0）

## 性能基准

**目标指标**（基于文档规划）：
- CPU 占用：< 8%（单核）
- 内存占用：< 15MB 增量
- 处理延迟：< 50ms
- 长时间播放：2 小时无崩溃、无内存泄漏

**实际测试**（待测量）：
- [ ] CPU 占用测试
- [ ] 内存泄漏测试
- [ ] 延迟测试（示波器）
- [ ] 稳定性测试（2 小时+）

## 下一步行动

1. ✅ **已完成**：方案 A 系统音效完全可用
2. ⏳ **等待**：安装 NDK 环境（需要约 1GB 空间 + 下载时间）
3. ⏳ **待实现**：NativeAudioProcessor.kt 集成到 ExoPlayer
4. ⏳ **待测试**：性能测试、稳定性测试、音质对比
5. ⏳ **待优化**：CPU 占用优化、NEON 指令加速

## 时间估算

- **NDK 下载安装**：30 分钟（取决于网络速度）
- **编译验证**：10 分钟
- **AudioProcessor 集成**：2-3 小时（技术难点）
- **测试调优**：2-3 小时
- **总计**：约 1 天工作量

---

**建议**：先让用户体验方案 A（系统音效），收集反馈后再决定是否投入精力实现方案 B。如果大部分用户的设备系统音效效果良好，方案 B 可作为高级选项提供给发烧友。
