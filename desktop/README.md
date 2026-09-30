# NekoPlayer 桌面版

Kotlin/JVM、Compose Desktop 桌面应用，复用 Android 的业务 API、音质选择、评论和 C++ DSP；横屏布局与 Android 播放页保持一致。

## 安装

Windows 发布 MSI/EXE 安装包，安装时会创建桌面和开始菜单快捷方式，安装包内置 Java 运行时，安装后可直接启动。Linux 发布 DEB/RPM 包；开发调试才使用可运行目录，不要求用户手动解压。

| 系统 | 架构 | 启动 |
| --- | --- | --- |
| Windows 10/11 | x64 | 安装 MSI 或 EXE 后从桌面/开始菜单启动 |
| Linux glibc 2.35+ | x64、ARM64 | 安装 DEB/RPM 后从应用菜单启动 |

Linux 需要桌面会话和 X11/ALSA/字体库；Wayland 需要 XWayland。Debian/Ubuntu 可安装 `libasound2`（新版本为 `libasound2t64`）、`libx11-6`、`libxext6`、`libxi6`、`libxrender1`、`libxtst6`、`libfreetype6`、`fontconfig`、`fonts-noto-cjk`。不支持 Alpine/musl。Windows 便携包未使用商业代码签名证书。

## 功能与边界

- 网易云/酷狗搜索、推荐、云歌单，本地音乐、播放队列、循环/随机、进度与歌词。
- Android 与桌面版均提供独立的“音源”分组，可在其中切换酷狗、酷我、网易、QQ、咪咕和 B 站。
- 网易云扫码/Cookie 登录；酷狗短信/扫码，原版和概念版独立 Cookie 管理。
- 本站浏览器登录使用本机回调并验证身份；若账号中心拒绝本机回调，可导入令牌或回调链接。
- 本站自动双向同步本地收藏、历史和歌单；空白设备登录本站后恢复网易云 Cookie、酷狗原版/概念版完整 Cookie 与当前酷狗平台。
- 桌面端后续登录、导入、退出、清空音乐平台账号或切换酷狗平台时，会立即把凭据变更回写云端。
- 播放前查询可用音质：默认音质优先，否则最高可用。下载只展示验证通过的音质并保留原文件。
- 网易云/酷狗/本站评论、回复；网易云/本站点赞和删除本人评论。酷狗点赞和删除仍按已验证协议开放。
- 网易云、酷狗和音源均支持 MV 搜索与播放；MV 和歌词请求不消耗音源每日音乐链接额度。
- 10 段 EQ、低音、声场、混响、响度、母带；复用既有 DSP 和预设 ID。

播放先缓存完整音频，再解码为 **48 kHz、16-bit 双声道 PCM**；初次播放大文件需要等待缓冲。下载不经过重采样或 DSP。没有 Android 系统音效模式，不保证声卡独占或位完美输出。不绕过付费、试听或下载权限。

“音源”分组不在客户端保存上游地址，必须登录本站账号后经 `https://nekoh2o.top/api/music-source/` 代理调用。请求使用短期会话密钥、时间戳、随机数和 HMAC 签名；全站共用每日 10,000 次音乐链接额度，统计页位于 `https://nekoh2o.top/source/`。

支持 MP3、FLAC、AAC/M4A/ALAC、Ogg/Vorbis/Opus、WAV、APE、WMA。单文件及自动管理的缓存上限为 512 MiB。

## 数据

Linux：`$XDG_CONFIG_HOME/NekoPlayer` 或 `~/.config/NekoPlayer`；Windows：`%APPDATA%/NekoPlayer`。JVM 参数 `-Dnekoplayer.dataDir=...` 可指定独立目录。凭据保存在本机文件中，POSIX 系统限制为所有者读写；请勿共享这些文件。

退出会取消请求、停止播放并关闭解码进程。清理播放缓存可在退出后删除数据目录的 `cache` 子目录，保留账号和收藏文件。

## 开发与构建

需要 JDK 17+、CMake、C++17 编译器和 PATH 中的 FFmpeg，不需要 Android SDK。发布包通过 Compose Desktop 生成 MSI、EXE、DEB 和 RPM，并内置 Java 运行时。

```sh
cmake -S desktop/native -B desktop/build/native -DCMAKE_BUILD_TYPE=Release
cmake --build desktop/build/native
./gradlew -p desktop test installDist
JAVA_OPTS="-Djava.library.path=$PWD/desktop/build/native" desktop/build/install/NekoPlayer-desktop/bin/NekoPlayer-desktop
```

Gradle 同步可移植业务源码，平台适配独立维护。发布 CI 当前构建 Linux x64、Linux ARM64 和 Windows x64；Compose Desktop 官方安装器不再承诺 Windows ARM64、32 位或 ARMv7。低资源构建脚本 `deploy/build-desktop-low-resource.sh` 将 Gradle 限制为单 worker、单 CPU 和约 2GB 总内存。

## 开源组件

FFmpeg 8.1.2 使用 LGPL 配置，禁用网络及 GPL/nonfree 组件。Release 提供构建所用完整 `ffmpeg-8.1.2.tar.xz` 源码，配置命令位于包内 `build-info.json`；独立解码器可在 `native/` 替换。许可证在 `licenses/`，Zulu OpenJDK 运行时保留原有 legal 文件。Windows JNI 平台头来自 OpenJDK 21u，遵循 GPLv2 with Classpath Exception。

感谢 [Binaryify/NeteaseCloudMusicApi](https://github.com/Binaryify/NeteaseCloudMusicApi)、[MakcRe/KuGouMusicApi](https://github.com/MakcRe/KuGouMusicApi)、FFmpeg、OpenJDK、Azul Zulu、FlatLaf、LLVM-MinGW。
