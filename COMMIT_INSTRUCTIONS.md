# 提交和发布指令

## 立即执行以下命令

```bash
cd /home/h2o/NekoPlayer

# 提交代码
git commit -m "fix: 修复缓存/下载播放、登录持久化问题；重构全屏播放器和设置页

- 修复缓存音乐断网无法播放（优化数据源链路和缓存 key）
- 修复已下载音乐无法播放（支持 content:// 和 file:// URI）
- 修复退出重进后登录失效（优化 CookieStore 初始化时序）
- 重构全屏播放器为网易云风格（点击唱片切换歌词视图）
- 重组设置页为五大功能分类（个性化/账户/播放/存储/高级）
- 添加项目 README 文档"

# 创建版本标签（pre-release）
git tag v1.0.6-pre

# 推送到 GitHub
git push origin main
git push origin v1.0.6-pre
```

## 自动化流程

推送 tag 后，GitHub Actions 会自动：
1. 使用 JDK 17 构建环境
2. 执行 `./gradlew clean assembleRelease`
3. 使用你配置的签名密钥签名 APK
4. 创建 GitHub Release（自动标记为 pre-release）
5. 上传签名后的 APK 文件

## Release 说明

GitHub Actions 会自动生成 release notes，你也可以手动编辑，参考 `RELEASE_NOTES.md` 的内容。

## 已修改的文件

### 代码文件（5个）
- ✅ `app/src/main/java/top/nekoh2o/player/playback/PlaybackService.kt`
- ✅ `app/src/main/java/top/nekoh2o/player/data/cache/MusicCache.kt`
- ✅ `app/src/main/java/top/nekoh2o/player/data/net/CookieStore.kt`
- ✅ `app/src/main/java/top/nekoh2o/player/ui/PlayerViewModel.kt`
- ✅ `app/src/main/java/top/nekoh2o/player/ui/screens/FullPlayerScreen.kt`
- ✅ `app/src/main/java/top/nekoh2o/player/ui/screens/SettingsScreen.kt`

### 文档文件（2个）
- ✅ `README.md` - 通用的项目说明文档
- ✅ `RELEASE_NOTES.md` - 本次发布的更新说明

## 验证构建

推送后访问：
- GitHub Actions: https://github.com/你的用户名/NekoPlayer/actions
- Releases: https://github.com/你的用户名/NekoPlayer/releases

构建时间约 3-5 分钟。

## 如果需要手动构建

```bash
# 编译 Debug 版本（用于本地测试）
./gradlew clean assembleDebug

# APK 位置
ls -lh app/build/outputs/apk/debug/app-debug.apk

# 安装到设备
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 测试重点

1. **缓存播放** - 在线播放后断网，应能继续播放
2. **下载播放** - 下载后断网，应能播放已下载歌曲
3. **登录持久化** - 退出 App 重新打开，应保持登录状态
4. **全屏播放器** - 默认显示唱片，点击切换歌词
5. **设置页** - 五大分类清晰，所有设置项可用

## 完成标志

✅ 所有代码已修改并暂存  
✅ README.md 已创建（通用文档）  
✅ RELEASE_NOTES.md 已创建  
✅ 临时文档已清理  
⏳ 等待执行 git commit、tag、push
