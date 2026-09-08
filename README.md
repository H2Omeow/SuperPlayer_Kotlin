# NekoPlayer

一个基于 Android 的现代化音乐播放器，支持网易云音乐曲库。

## 特性

### 播放功能
- 🎵 在线播放网易云音乐曲库
- 💾 智能缓存，节省流量
- 📥 本地下载，离线播放
- 🎚️ 多档音质选择（标准/较高/极高/无损/Hi-Res/臻音全景声/沉浸环绕声/超清母带/Audio Vivid），根据网易云会员等级自动开放
- ⏭️ 播放模式切换（顺序/循环/随机/单曲循环）
- ⏩ 播放速度调节（0.5x - 2.0x）

### 音效（v1.0.8-pre）
- **系统音效**：按当前设备音频会话的实际能力显示均衡器、低音增强、环绕、混响和响度控制。
- **专业音效**：本地 C++ DSP，支持单/双声道 PCM16、10 段 EQ、混响空间与阻尼调节。
- **预设**：21 组 EQ 预设，以及按参数文档实现的 20 组签名母带（惜、次元、跨界系列）和母带混合比例。
- 播放中切换引擎保持播放器与队列；专业引擎初始化或处理失败时提示错误并原样播放。
- 另外 14 组基础/声境母带等待原始源码接入，暂不开放；不提供真实 AI 推理、杜比解码或 24-bit 升格。

缓冲区、JNI 与 DSP 自动化测试已通过；**真机播放和不同设备系统音效仍需验收**。详见[技术规格与验证状态](docs/AUDIO_EFFECTS_SPEC_PROGRESS.md)。

### 界面设计
- 🎨 Material Design 3 设计语言
- 🌃 全局壁纸与动态模糊背景
- 💿 唱片旋转动画与歌词视图切换
- 🎭 可调节的控件透明度
- ♿ 完整的无障碍支持（TalkBack）

### 歌词功能
- 📜 滚动歌词显示
- 🎤 逐字高亮（卡拉OK模式）
- 🌐 翻译歌词支持
- 🎈 悬浮歌词窗口
- 📱 双行/单行显示切换

### 个性化
- 🖼️ 自定义全局背景壁纸
- 🎨 封面/壁纸双背景源
- 🌫️ 可调节模糊强度与遮罩透明度
- 🎛️ 细粒度的界面控件透明度设置

### 账户功能
- 👤 网易云账户登录
- ☁️ 云端收藏与歌单同步
- 🔐 Cookie 管理
- 📜 播放历史记录

### 高级功能
- ⏰ 定时关闭
- 🔋 电池优化豁免申请
- 📁 自定义下载目录（支持 MediaStore 与 SAF）
- 🗂️ 缓存与下载管理

## 系统要求

- Android 7.0 (API 24) 或更高版本
- 建议 Android 10+ 以获得最佳存储体验

## 构建

### 前置要求
- JDK 17
- Android SDK
- Android NDK 与 CMake 3.22.1
- 使用仓库提供的 Gradle Wrapper

### 编译步骤

```bash
# 克隆仓库
git clone https://github.com/H2Omeow/SuperPlayer_Kotlin.git
cd SuperPlayer_Kotlin

# 编译 Debug 版本
./gradlew assembleDebug

# 编译 Release 版本（需要配置签名）
./gradlew assembleRelease
```

### Release 签名配置

正式签名通过以下环境变量注入：

```properties
KEYSTORE_FILE=/path/to/your/keystore.jks
KEYSTORE_PASSWORD=your_store_password
KEY_ALIAS=your_key_alias
KEY_PASSWORD=your_key_password
```

未配置时本地 release 使用 debug 签名。GitHub Actions 从仓库 Secrets 读取正式签名，推送 `v*` 标签自动构建并更新 Release。

## 下载

前往 [Releases](../../releases) 页面下载最新版本。

## 技术栈

- **语言**: Kotlin + C++（JNI 音效处理）
- **UI 框架**: Jetpack Compose
- **播放器**: Media3 (ExoPlayer)
- **网络**: Retrofit + OkHttp
- **图片加载**: Coil
- **异步**: Coroutines + Flow
- **依赖注入**: 手动依赖注入
- **持久化**: DataStore + SharedPreferences

## 架构

- **MVVM 架构模式**
- **单 Activity 多 Composable 设计**
- **Repository 层数据抽象**
- **Media3 MediaSession 后台播放**

## 许可证

本项目采用 [许可证名称] 许可证 - 详见 [LICENSE](LICENSE) 文件。

## 隐私说明

- 本应用在用户通过 SSO 账户中心登录后，会将用户的播放记录、收藏、歌单等数据保存到本站服务器
- **网易云 Cookie 会同步保存到云端**，用于多端同步和服务端代理请求（获取音质、歌单等需要会员凭据的接口）
- 本站不保证数据绝对安全（如服务器遭到入侵等不可控因素）
- 使用本站的云端同步功能即默认接受上述风险
- 如不希望数据上传至服务器，请勿登录账户或使用云端同步功能

## 免责声明

本项目仅供学习交流使用，音乐版权归网易云音乐及原作者所有。请支持正版音乐。

## 贡献

欢迎提交 Issue 和 Pull Request。

## 致谢

- [Binaryify/NeteaseCloudMusicApi](https://github.com/Binaryify/NeteaseCloudMusicApi)
- [MakcRe/KuGouMusicApi](https://github.com/MakcRe/KuGouMusicApi)
- [Material Design 3](https://m3.material.io/)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Media3](https://developer.android.com/guide/topics/media/media3)
- [Coil](https://coil-kt.github.io/coil/)
