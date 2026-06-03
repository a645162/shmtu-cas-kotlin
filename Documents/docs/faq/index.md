---
title: 常见问题
---

# 常见问题

## 通用问题

### 这个项目是做什么的？

`shmtu-cas-kotlin` 是上海海事大学统一认证平台 + 一卡通账单 + 后勤热水的 Kotlin 多端库。同一套源码既可发布为 JVM jar、CLI 工具，也可发布为 Android AAR，方便 Kotlin 全栈接入。

### 与 Rust / Go 版本的关系？

| 语言 | 仓库 | 备注 |
|------|------|------|
| Kotlin | 本仓库 | JVM + Android + CLI |
| Rust | [shmtu-cas-rs](https://github.com/a645162/shmtu-cas-rs) | 已被 shmtu-terminal-tauri 集成 |
| Go | [shmtu-cas-go](https://github.com/a645162/shmtu-cas-go) | Wails 桌面客户端 |

API 风格对齐 Rust 版本，命名从 snake_case 改成 Kotlin 习惯的 camelCase。

### 这个项目可以用于生产环境吗？

本项目为课程设计项目，仅用作学习用途。错误处理和安全性方面可能不够完善，不建议直接用于生产环境。

## 环境与配置

### 需要 Java 版本？

Kotlin 2.2 + Gradle 8 + JVM toolchain 17。运行 Android 库需要 minSdk 21+。

### OCR 服务器怎么部署？

[shmtu-cas-ocr-server](https://github.com/a645162/shmtu-cas-ocr-server) 是独立项目，提供：

- TCP 模式：默认端口 `21601`
- HTTP 模式：默认端口 `5000`
- 支持 CPU 和 Vulkan GPU 两种构建目标

### 如何修改 OCR 服务器地址？

| 方式 | 适用 |
|------|------|
| `RemoteOcrCaptchaResolver(host, port)` | TCP |
| `RemoteOcrHttpCaptchaResolver(baseUrl)` | HTTP |
| 环境变量 `SHMTU_OCR_HOST` / `SHMTU_OCR_PORT` | CLI |

## 认证问题

### 登录失败，提示密码错误

- 确认学号/密码正确
- `CasAuth.casLogin` 会自动 `trim`，但中间空格不会去掉
- 检查 CAS 平台是否可以正常网页登录

### 验证码识别总是失败

- 确认 OCR 服务器已启动且可访问
- 用 `RemoteOcrHttpCaptchaResolver.healthCheck()` 验证连通
- TCP 模式确认防火墙放行 `21601` 端口
- 用 `Captcha.saveImageToFile()` 抓张图肉眼比对

### 登录成功但查询数据返回 `Result.failure("未登录，需要重新登录")`

- Cookie 没真正有效，可能是 `restoreSession` 的 JSON 不完整
- 在 `restoreSession` 后调 `testLoginStatus()` 校验
- 必要时重新走 `submitLogin`

### TGC 复用失败

`tryReuseTgc()` 返回 `false` 表示 TGC 已过期，库会自动走 challenge → submitLogin，无需干预。如果你手动拆开了三阶段，记得在 submitLogin 失败后重试。

## Android 专项

### 安装到 Android 11+ 报 `CLEARTEXT communication ... not permitted`

- `AndroidManifest.xml` 的 `<application>` 加 `android:usesCleartextTraffic="true"`
- 或用 `res/xml/network_security_config.xml` 精确放行局域网 IP

### 弹窗验证码怎么与 suspend 续接？

用 `Channel` 或 `suspendCancellableCoroutine` 把 UI 回调转回 suspend，参考 [Android 集成 §7](/platforms/android#7-手动验证码弹窗模板)。

### 怎么在 WorkManager 里同步？

参考 [Android 集成 §9](/platforms/android#9-workmanager-周期同步)：把 `BillRepository.syncOneAccount` 包到 `CoroutineWorker.doWork()`，根据 `Result` 决定 `Result.success() / retry() / failure()`。

### 多账号同步怎么写？

每个账号 `new EpayAuth(resolver)` 一次（Cookie 互不共享），串行调 `submitLogin` + `incrementalSync`。

## JVM 端

### 启动报 `NoClassDefFoundError: kotlinx/coroutines/...`？

显式依赖 `kotlinx-coroutines-core`。

### 怎么把 `suspend` API 包成阻塞？

用 `runBlocking { ... }`，CLI 入口（`cas_cli`）就是参考实现。

## 同步问题

### 同步结果 `newCount == 0` 但 `totalFetched > 0`

正常情况。`incrementalSync` 抓的条目都已在宿主 `BillStore.contains` 里，全部是旧数据。

### 想全量同步

```kotlin
SyncOptions(
    maxPages = 1000,
    earlyStopThreshold = Int.MAX_VALUE
)
```

### 同步中断（302 / 网络失败）

`incrementalSync` 返回 `Result.failure`。已抓的页可能没 merge，需要根据 `BillStore.merge` 的实现判断事务性。

## 设计与扩展

### 为什么不内置 ONNX 模型？

为了避免 `dylib` 体积与跨平台兼容性问题。OCR 走 `CaptchaResolver` 接口，宿主可自由接本地 ONNX / 远程服务 / UI 弹窗。

### 怎么实现自己的 `CaptchaResolver`？

实现 `suspend fun resolve(imageData: ByteArray): Result<CaptchaAnswer>`，把识别结果包装成 `CaptchaAnswer` 即可。

### 怎么实现自己的 `BillStore`？

实现 `contains(transactionNo: String): Boolean` 与 `merge(newBills: List<BillItem>)` 两个方法，对接你的 Room DAO / SQLiteOpenHelper / 文件存储。
