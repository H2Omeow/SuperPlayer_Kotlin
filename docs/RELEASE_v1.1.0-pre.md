# v1.1.0-pre

## 音源

- 新增独立的“音源”分组，可在分组内切换酷狗、酷我、网易、QQ、咪咕和 B 站。
- 聚合搜索结果保留实际平台标识，歌曲卡片显示来源。
- 客户端不保存上游音源地址；登录本站后通过 `https://nekoh2o.top/api/music-source/` 代理调用。
- 音源请求使用本站 JWT、短期会话密钥、时间戳、随机数和 HMAC-SHA256 签名，并在服务端校验防重放。
- 全站共享每日 10,000 次音乐链接额度；歌词和 MV 不扣额度。统计页：`https://nekoh2o.top/source/`。

## MV

- 网易云、酷狗和音源支持 MV 搜索与播放。
- MV 请求同样需要本站登录和签名校验，但不消耗音乐链接额度。

## 桌面版

- 桌面 UI 改为 Compose Desktop，横屏播放页与 Android 结构保持一致。
- Windows 发布内置 Java 运行时的 MSI/EXE 安装包，安装后创建桌面和开始菜单快捷方式。
- Linux 发布 x64、ARM64 的 DEB/RPM 安装包和内置 Java 的便携 tar.gz；其他 glibc 系统可解压后运行 `NekoPlayer` 启动器。Release 不上传 ZIP 桌面包。
- 低资源构建限制为单 CPU、单 Gradle worker 和约 2GB 内存。

## 验证

- `./deploy/build-desktop-low-resource.sh -p desktop compileKotlin` 通过。
- `node --test deploy/music-source.test.mjs` 通过。
- 线上健康检查、音源搜索、歌词、统计和酷狗 MV 搜索已验证。

## 限制

- 音源每日额度由本站服务端统一控制，达到 10,000 次后所有用户的音乐链接请求返回 429，次日北京时间零点恢复。
- Windows ARM64、Windows/Linux 32 位和 ARMv7 暂不列入 Compose Desktop 安装包发布矩阵。
