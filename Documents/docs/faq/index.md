---
title: 常见问题
---

# 常见问题

## 通用问题

### 这个项目是做什么的？

SHMTU CAS Kotlin 是上海海事大学统一认证平台的 Kotlin 语言实现。它可以自动化完成 CAS 登录流程，包括验证码识别，并基于认证结果访问 Epay 一卡通账单和后勤热水信息等校园服务。

### 这个项目可以用于生产环境吗？

本项目为课程设计项目，仅用作学习用途。代码中的错误处理和安全性方面可能不够完善，不建议直接用于生产环境。

### 有其他语言的实现吗？

有。同系列项目包括：

- [Go 版本](https://github.com/a645162/shmtu-cas-go) - 为 Wails 桌面客户端准备
- Rust 版本 - 计划中，尚未实现

## 环境与配置

### 需要 Java 版本？

项目使用 Kotlin JVM Toolchain 21，需要 JDK 21 或更高版本。可以在 `build.gradle` 中查看配置：

```groovy
kotlin {
    jvmToolchain(21)
}
```

### OCR 服务器怎么部署？

OCR 推理服务器是独立项目 [shmtu-cas-ocr-server](https://github.com/a645162/shmtu-cas-ocr-server)，使用 C++ 编写，基于 Drogon 框架和 ncnn 推理引擎。支持 CPU 和 Vulkan GPU 两种模式，提供 Docker 部署方式。

默认监听端口为 21601，通信协议为 TCP。

### 如何修改 OCR 服务器地址？

三种方式：

1. **环境变量**（推荐）：
   ```bash
   export SHMTU_OCR_HOST=192.168.1.100
   export SHMTU_OCR_PORT=21601
   ```

2. **代码配置**：
   ```kotlin
   Captcha.setOcrServer("192.168.1.100", 21601)
   ```

3. **默认值**：`127.0.0.1:21601`

## 认证问题

### 登录失败，提示"用户名或密码错误"

- 确认学号和密码正确
- 注意密码前后不要有多余空格（代码中会自动 trim）
- 检查 CAS 平台是否可以正常网页登录

### 验证码识别总是失败

- 确认 OCR 服务器已启动且可访问
- 检查 `SHMTU_OCR_HOST` 和 `SHMTU_OCR_PORT` 配置是否正确
- 使用 `Captcha.testLocalTcpServerOcr()` 单独测试 OCR 识别
- 可以用 `Captcha.saveImageToFile()` 保存验证码图片查看内容

### 登录成功但查询数据返回 302

这说明 Cookie 过期或未正确保存。可能的原因：

- 登录后的重定向链没有完全跟踪
- Cookie 中的 `JSESSIONID` 或 `wengine_new_ticket` 缺失
- 会话已超时，需要重新登录

### 连接 OCR 服务器失败

`Captcha.ocrByRemoteTcpServer()` 在连接失败时会抛出 `ConnectException`，错误信息包含目标地址。检查：

- OCR 服务器是否已启动
- 防火墙是否放行了 21601 端口
- 网络是否可达

## 代码问题

### 为什么禁用 OkHttp 自动重定向？

CAS 认证流程中，每次 302 重定向都携带着重要的认证信息（`Set-Cookie`、`Location`）。如果让 OkHttp 自动跟随重定向，这些中间信息会丢失，导致认证失败。

```kotlin
val client = OkHttpClient.Builder()
    .followRedirects(false)
    .followSslRedirects(false)
    .build()
```

### 如何添加新的 CAS 认证服务？

参考 `EpayAuth` 或 `WechatAuth` 的实现模式：

1. 创建新的 Auth 类
2. 实现 `testLoginStatus()` - 检查登录状态
3. 实现 `login()` - CAS 登录流程
4. 实现数据查询方法
5. 如需解析 HTML，创建对应的 Parser 类

### Triple 返回值是什么含义？

代码中大量使用 `Triple<Int, String, String>` 作为返回值，约定如下：

| 位置 | HTTP 成功（200） | 重定向（302） | 失败 |
|------|-----------------|--------------|------|
| first | 200 | 302 | 状态码/错误码 |
| second | 响应体 | Location 地址 | 错误信息 |
| third | Cookie | Set-Cookie | 空 |
