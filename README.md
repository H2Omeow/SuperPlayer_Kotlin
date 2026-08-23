# NekoPlayer

一个基于 Android 的现代化音乐播放器，支持网易云音乐曲库。

## 特性

### 播放功能
- 🎵 在线播放网易云音乐曲库
- 💾 智能缓存，节省流量
- 📥 本地下载，离线播放
- 🎚️ 多档音质选择（标准/较高/极高/无损）
- ⏭️ 播放模式切换（顺序/循环/随机/单曲循环）
- ⏩ 播放速度调节（0.5x - 2.0x）

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
- Gradle 8.0+

### 编译步骤

```bash
# 克隆仓库
git clone https://github.com/你的用户名/NekoPlayer.git
cd NekoPlayer

# 编译 Debug 版本
./gradlew assembleDebug

# 编译 Release 版本（需要配置签名）
./gradlew assembleRelease
```

### Release 签名配置

在项目根目录创建 `keystore.properties` 文件：

```properties
storeFile=/path/to/your/keystore.jks
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

## 下载

前往 [Releases](../../releases) 页面下载最新版本。

## 技术栈

- **语言**: Kotlin
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

- 本应用不收集任何用户数据
- 网易云 Cookie 仅存储在本地设备
- 播放历史与收藏仅在登录后同步到网易云服务器

## 免责声明

本项目仅供学习交流使用，音乐版权归网易云音乐及原作者所有。请支持正版音乐。

## 贡献

欢迎提交 Issue 和 Pull Request。

## 致谢

- [Material Design 3](https://m3.material.io/)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Media3](https://developer.android.com/guide/topics/media/media3)
- [Coil](https://coil-kt.github.io/coil/)
