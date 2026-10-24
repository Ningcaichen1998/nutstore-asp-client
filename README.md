# 坚果云第三方应用管理（非官方）

一个用于管理坚果云 WebDAV 第三方应用密码的 **Android 客户端**，支持：账号密码登录（网页窗口人工滑块验证）、自动保存 Cookie、创建新应用密码、查看已创建应用的名称 / 授权日期 / 应用密码、删除应用密码。

## 下载

在 [GitHub Releases](https://github.com/Ningcaichen1998/nutstore-asp-client/releases) 下载最新版 APK。

## 功能

| 功能 | 说明 |
| --- | --- |
| 账号密码登录 | 输入服务器地址、账号、密码 |
| 网页窗口人工验证 | 内置 WebView 加载坚果云登录页，自动填入账号密码，用户手动完成腾讯滑块验证 |
| 自动保存 Cookie | 登录成功后自动提取会话 Cookie 保存到本机，下次免验证 |
| 我的 / 管理 底栏 | 「我的」页展示 WebDAV 地址、账号、密码、应用数量、最近创建；「管理」页创建与查看应用 |
| 应用密码列表 | 名称、授权日期、应用密码（可显示/复制，支持点击复制、默认明文等设置） |
| 创建 / 删除应用密码 | 输入名称一键生成；删除前二次确认 |

## 实现原理

坚果云未公开应用密码管理 API。本客户端使用其移动端网页接口（与官方 Windows/安卓客户端的「第三方应用管理」同源）：

- 列表：`GET /d/mobile_asp` → 从 HTML 中提取 `userAspsStr` JSON
- 创建：`POST /d/ajax/userop/generateAsp`，参数 `asp_name`
- 删除：`POST /d/ajax/userop/revokeAsp`，参数 `asp_name`

凭证仅保存在本机私有目录，不上传任何服务器。

## 源码结构

```
nutstore-asp-client/
└── android/               # Android 工程（纯 Java，无 Gradle）
    ├── app/src/           # Java 源码
    ├── app/res/           # 布局、资源、图标
    ├── AndroidManifest.xml
    ├── build.sh           # aapt2+javac+d8+apksigner 手动构建脚本
    └── gen_icon.py        # 图标生成器
```

## 构建

```bash
cd android
bash build.sh
```

构建链：`aapt2` + `javac` + `d8` + `zipalign` + `apksigner`，无需 Gradle。签名密钥为构建时自动生成。

## 风险提示

- 非官方实现，坚果云接口变更可能导致客户端失效。
- 首次安装需在系统设置中允许「安装未知来源应用」。
- 请勿将 Cookie 提供给不信任的程序；本客户端代码开源可审计。

## 背景图来源

APP 背景图来自微博用户 [@钟诗曼_](https://weibo.com/u/6034212582)。
