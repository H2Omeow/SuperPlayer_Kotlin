# 酷狗 API 修复清单

根据官方文档 https://kugoumusicapi-docs.4everland.app/ 发现的问题：

## 1. 搜索接口必须携带认证信息
- **问题**：所有搜索接口（search、search/suggest、search/hot）必须携带 cookie，否则返回 error_code: 152
- **文档原文**：⚠️ 注意：因接口问题，获取搜索结果需要在 url 后添加`cookie`认证信息或者`Set-cookie`，否则会返回 `error_code: 152`
- **修复**：在所有搜索相关接口添加 cookie 参数

## 2. 推荐歌曲接口路径错误
- **当前路径**：`/kgapi/recommend/songs`
- **正确路径**：`/kgapi/personal/fm`（私人 FM，对应手机和 PC 端的猜你喜欢）
- **文档说明**：私人 FM(对应手机和 pc 端的猜你喜欢)

## 3. 需要 dfid（设备指纹）
- **接口**：`/kgapi/register/dev` -> `/kgapi/dfid`
- **作用**：获取设备指纹，很多接口都需要
- **文档说明**：⚠️ 注意：因接口问题，目前获取 url 接口数据需要先调用 `/register/dev` 接口获取 dfid

## 4. 支持 QQ 登录
- **QQ 扫码登录**：
  - `/kgapi/login/qq/qr/create` - 生成二维码
  - `/kgapi/login/qq/qr/check` - 检测扫码状态
- **QQ 授权登录**：
  - `/kgapi/login/qq` - 通过 openid + access_token 登录

## 5. Cookie 构建格式
- **格式**：`token=xxx;userid=xxx;dfid=xxx`
- **必需性**：搜索、推荐等接口都需要完整的 cookie 字符串

## 修复优先级
1. ✅ 添加 dfid 获取接口
2. ✅ 修复搜索接口 - 添加 cookie 参数
3. ✅ 修复推荐接口路径
4. ✅ 在 CookieStore 初始化时自动获取 dfid
5. ✅ 添加 QQ 登录支持
6. ✅ 更新 Repository 调用方式
