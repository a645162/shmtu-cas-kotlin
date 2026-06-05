---
title: 整体架构
---

# 整体架构

`shmtu-cas-kotlin` 的设计重点不是「做一个完整应用」，而是「把容易变的外层和可复用的内层拆开」。

## 分层视角

可以把 `cas_lib` 的代码分成四层：

1. 协议与抓取层
2. 验证码抽象层
3. 会话与状态层
4. 解析、同步与扩展层

## 1. 协议与抓取层

对应 `cn.edu.shmtu.cas.auth.common.CasAuth`。

职责：

- 创建不复用重定向的 `OkHttpClient`（CAS 流程的 `Set-Cookie` / `Location` 都必须手动接管）
- 解析 CAS 登录页 `execution`
- 提交账号、密码、验证码
- 跟随重定向链
- 合并 `Set-Cookie` 头到宿主 cookie 字符串

边界：

- 不做业务持久化
- 不做 UI 弹窗
- 不关心宿主如何保存 cookies（字符串、加密、JSON 都行）

## 2. 验证码抽象层

对应 `cn.edu.shmtu.cas.captcha` 包。

核心是 `CaptchaResolver` 接口：

```kotlin
interface CaptchaResolver {
    suspend fun resolve(imageData: ByteArray): Result<CaptchaAnswer>
}
```

四种内置实现覆盖了最常见的部署形态：

| 实现 | 触发方式 | 适合 |
|------|----------|------|
| `ManualCaptchaResolver` | 弹窗/终端读取用户输入 | UI 程序、调试、OCR 不可用时回退 |
| `RemoteOcrCaptchaResolver` | TCP 发送图片 + `<END>`，读取算式 | 局域网 / shmtu-cas-ocr-server TCP 模式 |
| `RemoteOcrHttpCaptchaResolver` | HTTP POST base64 | Docker 部署、可观测可重试 |
| `ExprCaptchaResolver` | 调用方直接给出算式字符串 | 已有 ONNX / 第三方模型 |

边界：

- 不内置模型
- 不强制协议（TCP / HTTP / 人工都可）
- 只把「图片字节 → 答案」这一件事抽干净

## 3. 会话与状态层

对应 `cn.edu.shmtu.cas.auth.{EpayAuth, WechatAuth}` 与 `cn.edu.shmtu.cas.auth.common.CookieManager`。

`EpayAuth` / `WechatAuth` 内部持有：

- `CookieManager` — 维护当前会话的所有 cookie，支持 `restore(json)` / `extract()` 双向序列化
- `OkHttpClient` — 复用 `CasAuth.createClient()` 行为
- 上次探测到的 `loginUrl`（用于 TGC 复用 / 重新登录）
- 可选的 `CaptchaResolver`（一键登录自动模式才用）

业务流程是三阶段：

```
probeLogin  →  prepareChallenge  →  submitLogin
   ↑                                     ↓
   └── restoreSession (TGC 复用) ───────┘
```

边界：

- 不知道宿主用什么数据库
- 不知道宿主的 UI 怎么提示用户
- 只暴露 `Result<*>` / sealed class 结果，由宿主决定下一步

## 4. 解析、同步与扩展层

对应 `cn.edu.shmtu.cas.parser`、`cn.edu.shmtu.cas.sync`、`cn.edu.shmtu.cas.classifier`。

- `BillParser` / `HotWaterParser` / `CsvExporter` — 单一职责的 HTML / CSV 处理
- `SyncOptions` + `incrementalSync` — 核心同步算法，逐页抓取 → `BillStore.contains` 去重 → 连续 N 个空页早停
- `BillClassifier` / `PositionTranslator` — 关键词规则驱动的语义增强，宿主通过 TOML 注入

边界：

- `BillStore` 接口只问 `contains` / `merge` 两个问题，宿主可对接 Room / SQLite / 内存 / 文件
- 同步层不知道什么是「账号」「身份」，所有 host 概念都让宿主承担

## 数据流总览

一次典型的「自动登录 + 增量同步」：

1. 宿主创建 `EpayAuth(remoteOcrResolver)`
2. 调用 `probeLogin()` 探测当前会话（200 / 302）
3. 已登录 → 直接进入同步；未登录 → 继续
4. 调用 `submitLogin(user, pass)` 一键登录：
   - 先 `tryReuseTgc()` 试探 TGC 是否仍然有效
   - 失败后进入 `prepareChallenge()` 循环
   - 调用 `resolver.resolve(image)` 取答案
   - 调用底层 `casLogin`，遇 `ValidateCodeError` 自动重试
5. 同步层 `incrementalSync(epay, store, options)`：
   - 翻页抓 HTML
   - 调 `BillParser` 解析
   - 通过 `BillStore.contains` 去重
   - 新条目统一交给 `BillStore.merge`
   - 连续若干页无新增则早停
6. 通过 `extractSession()` 持久化 Cookie，下次启动 `restoreSession()` 复用

## 设计取舍

### 为什么把验证码抽成接口而不是直接调 OCR

因为验证码求解手段在部署现场差异极大。解耦后，库不会强迫宿主接受某种固定部署方式；同一份同步代码既能跑在调试模式（人工输入），也能跑在生产模式（HTTP OCR）。

### 为什么 Cookie 暴露成 JSON 提取/恢复

因为宿主可能：

- 保存到 Android `EncryptedSharedPreferences`
- 保存到桌面端文件
- 保存在密文存储中

JSON 是最容易跨层传递的格式，`CookieManager.restore` 同时兼容 JSON 与 `key=value; key=value` 字符串。

### 为什么同步只暴露增量接口

因为「全量同步」本质上只是不同参数组合（`maxPages=Int.MAX_VALUE`、`earlyStopThreshold=Int.MAX_VALUE`），并不需要在库里重复一套主流程。宿主完全可以通过调整：

- `maxPages`
- `earlyStopThreshold`
- `startPage`

构造自己的全量 / 断点续传策略。

### 为什么 TGC 复用放在 `submitLogin` 内部

因为 TGC 是否有效只有 CAS 服务器知道，把它包在登录入口里，调用方就不用再写「如果 cookie 没失效就跳过登录」这种样板代码。
