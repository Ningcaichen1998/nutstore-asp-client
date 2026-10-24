# 坚果云第三方应用管理 - 安卓版

原生 Android 客户端（纯 Java，无第三方依赖），用于管理坚果云 WebDAV 第三方应用密码。

## 成品

- APK：通过 [GitHub Releases](https://github.com/Ningcaichen1998/nutstore-asp-client/releases) 下载（已签名，Android 7.0+，minSdk 24）
- 包名：`com.workbuddy.nutstoreasp`
- 当前版本：1.3

## 功能

- **账号密码登录**：输入服务器地址、账号、密码
- **网页窗口人工验证**：点击登录后打开内置 WebView 加载坚果云登录页，自动填入账号密码，由用户手动完成腾讯滑块验证
- **自动提取并保存 Cookie**：登录成功（跳转到 /d/home）后自动提取会话 Cookie 保存到本机 SharedPreferences，下次免验证
- **应用密码列表**：展示名称、授权日期、应用密码（默认打码，可显示/复制）
- **创建应用密码**：输入名称一键生成，弹窗展示完整密码（可复制）
- **删除应用密码**：二次确认后删除

## 技术说明

- 登录：WebView 加载 `{server}/d/login`，注入 JS 填充账号密码，用户手动过滑块；成功后通过 `CookieManager.getCookie()` 提取 Cookie
- 接口（与官方客户端「第三方应用管理」同源）：
  - 列表 `GET /d/mobile_asp`
  - 创建 `POST /d/ajax/userop/generateAsp`（`asp_name`）
  - 删除 `POST /d/ajax/userop/revokeAsp`（`asp_name`）
- 凭证仅保存在本机私有目录，不上传任何服务器

## 重新构建

```bash
cd android
bash build.sh
```

构建链：`aapt2` + `javac` + `d8` + `zipalign` + `apksigner`，无需 Gradle。签名密钥为首次构建时自动生成并保存在 `android/release.keystore`（口令 `nutstoreasp123`），**跨版本复用同一密钥**，因此可直接覆盖安装更新，无需卸载。

## 安装提示

首次安装需在系统设置中允许「安装未知来源应用」。签名密钥已固定（`android/release.keystore`），后续版本可直接覆盖安装更新。
