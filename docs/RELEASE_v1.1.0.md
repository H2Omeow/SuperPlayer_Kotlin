# NekoPlayer 1.1.0

- Android 与桌面焕新界面，统一导航、音乐库与播放页布局。
- 桌面正式切换为 Flutter，移除 Java 运行时依赖；提供 Windows / Linux 的 x64、ARM64 原生构建，不提供 32 位桌面版。
- 新增音源插件管理，支持外部订阅、JSON / JavaScript 导入，以及 MusicFree、Baka、LX 核心音源接口；AnimeMusic 作为内置订阅，无需旧音乐代理服务器。
- 集成动态歌词引擎、共享 JavaScript 插件运行时及原生 DSP 音效，完善下载缓存、播放恢复、歌单导入与云同步。
- 加强插件校验、请求隔离与 TLS 校验，Linux 内置中文字体并支持系统媒体控制。

## 安装

- Android：下载 APK 覆盖安装，保持原签名；包含 ARMv7、ARM64、x86、x64。
- Windows：按架构选择 `windows-x64-setup.exe` 或 `windows-arm64-setup.exe`，无需另装 Java。安装包未配置 Windows 代码签名，系统可能提示未知发布者。
- Linux：选择对应架构的 DEB / RPM，或解压 tar.gz 运行 `NekoPlayer`。需要 glibc 2.35+、GTK3、libmpv、libsecret、libepoxy、GnuTLS；DEB / RPM 声明了运行依赖。便携包在 Ubuntu / Debian 上可先安装 `libgtk-3-0 libmpv2 libsecret-1-0 libepoxy0 libgnutls30`（Ubuntu 22.04 使用 `libmpv1`）。

## 兼容范围

外部插件目前兼容核心搜索、歌曲解析、歌词等音源接口；不同插件的 MV、评论、扩展歌单接口不保证全部可用。音源内容与可用性取决于订阅提供方。升级前建议备份歌单；旧 Compose 桌面版与 Flutter 版的数据目录不同，不会自动迁移全部旧版数据。

下载后可通过 `SHA256SUMS` 校验文件完整性。
