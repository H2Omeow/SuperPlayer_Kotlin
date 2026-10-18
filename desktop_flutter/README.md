# NekoPlayer Flutter 桌面端

迁移开发中，不替代原 `desktop/`。目标：Windows / Linux 的 x86_64、ARM64，不支持 32 位。

已接入本地音乐库、平台账号与系统密钥环、账号音乐库、分类搜索、多音源解析、翻译歌词、评论、MV、下载、音频/歌词离线缓存、手动云同步和旧版备份导入；另有独立播放器背景、原版 C++ DSP、系统媒体控制、桌面歌词与可选托盘。Linux 托盘优先使用系统 Ayatana/AppIndicator，缺少可用托盘时不隐藏主窗口。完整实网播放仍需有效账号权益及设备验证，Windows 和 ARM64 尚未实机验收。配置与原版隔离。

使用 Flutter 3.47.7。在仓库根目录运行，`FLUTTER_BIN` 可指定 SDK 路径：

```sh
./deploy/flutter-low-resource.sh pub get
./deploy/flutter-low-resource.sh analyze
./deploy/flutter-low-resource.sh test
./deploy/flutter-low-resource.sh run -d linux
```

Linux 需要 GTK 3、Clang、CMake、Ninja、pkg-config，以及同架构 libmpv、libsecret、libepoxy 的运行库和开发包；账号存储需要可用且已解锁的 Secret Service 密钥环。Windows 需要 Visual Studio C++ 工具链、CMake 3.31+、7-Zip；Windows 音视频插件固定到官方含 ARM64 支持的 Git 提交，勿直接删除覆盖。专业 DSP 另需同架构 FFmpeg / ffprobe；miniaudio 头文件由 CMake 按固定版本和 SHA256 获取。四个原生目标仍需构建/播放验收，不自动上传或发布。

Windows ARM64 还必须设置 `NEKO_WINDOWS_ARM64_ANGLE_DIR`，提供同架构 ANGLE 的 `include/`、`lib/` 导入库与根目录 DLL。已核实上游默认 ANGLE 包仍为 x64，构建会明确阻止混用；缺少 ARM64 依赖时不能宣称支持完成。可用 `python3 desktop_flutter/tool/check_artifact.py <目标> <可执行文件及全部动态库>` 检查实际产物。
