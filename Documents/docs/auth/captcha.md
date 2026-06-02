---
title: 验证码识别
---

# 验证码识别

## 概述

上海海事大学 CAS 登录页面使用数学表达式验证码（如 `3+5=8`），需要下载验证码图片并识别出计算结果。本项目通过远程 OCR 推理服务器实现验证码自动识别。

## 验证码类型

CAS 系统使用的验证码为数学表达式图片，格式为 `A+B=C` 或 `A-B=C`。识别时需要：

1. 下载验证码图片
2. 将图片发送给 OCR 服务器识别表达式
3. 提取等号后面的计算结果

## 验证码下载

### 通过 URL 直接下载

```kotlin
val imageData = Captcha.getImageDataFromUrl()
```

此方法直接通过 `java.net.URL` 下载验证码图片，不携带 Cookie，适用于简单场景。

### 通过 OkHttp GET 请求下载（推荐）

```kotlin
val result = Captcha.getImageDataFromUrlUsingGet(cookie = loginCookie)
if (result != null) {
    val imageData = result.first       // ByteArray 图片数据
    val sessionId = result.second      // 服务器返回的 Cookie（JSESSIONID）
}
```

推荐使用此方法，因为：

- 服务器在返回验证码图片时会通过 `Set-Cookie` 头设置 `JSESSIONID`
- 该 `JSESSIONID` 必须在后续登录请求中携带，否则验证码校验会失败
- 方法会自动处理 Cookie 的更新和返回

### 验证码图片保存

调试时可以将验证码图片保存到本地查看：

```kotlin
Captcha.saveImageToFile(imageData, directoryPath = ".")
// 文件名格式：captcha_20260429005239.png
```

## OCR 识别

### 远程 TCP 服务器识别

本项目的验证码识别通过 TCP 协议与远程推理服务器通信：

```kotlin
val validateCode = Captcha.ocrByRemoteTcpServer(
    host = "127.0.0.1",
    port = 21601,
    imageData = imageData
)
```

通信协议：

1. 客户端建立 TCP 连接到 OCR 服务器
2. 发送验证码图片的二进制数据
3. 发送 `<END>` 标记表示数据传输结束
4. 接收服务器返回的识别结果字符串

### 自动重试

OCR 识别可能因网络波动而失败，可使用自动重试方法：

```kotlin
val validateCode = Captcha.ocrByRemoteTcpServerAutoRetry(
    host = "127.0.0.1",
    port = 21601,
    imageData = imageData,
    retryTimes = 3  // 默认重试 3 次
)
```

### 提取计算结果

OCR 服务器返回的识别结果格式为数学表达式（如 `3+5=8`），需要提取等号后面的结果：

```kotlin
val exprResult = Captcha.getExprResultByExprString(validateCode)
// 输入: "3+5=8"
// 输出: "8"
```

## OCR 服务器配置

### 默认配置

默认连接本地 OCR 服务器：

```kotlin
Captcha.ocrHost  // "127.0.0.1"
Captcha.ocrPort  // 21601
```

### 环境变量配置

通过环境变量设置 OCR 服务器地址（在 `Main.kt` 中读取）：

```bash
export SHMTU_OCR_HOST=192.168.1.100
export SHMTU_OCR_PORT=21601
```

### 代码动态配置

```kotlin
Captcha.setOcrServer("192.168.1.100", 21601)
```

### 部署 OCR 服务器

验证码 OCR 推理服务器是一个独立项目，使用 C++ Drogon 框架 + ncnn 推理引擎，支持 CPU 和 Vulkan GPU：

- 项目地址：[shmtu-cas-ocr-server](https://github.com/a645162/shmtu-cas-ocr-server)
- 模型训练：[shmtu-cas-ocr-model](https://github.com/a645162/shmtu-cas-ocr-model)
- 默认端口：21601

## 多线程识别

支持多线程并发识别验证码，用于压力测试：

```kotlin
Captcha.testLocalTcpServerOcrMultiThread(times = 10)
```

此方法会启动 10 个线程同时请求验证码并识别，可用于测试 OCR 服务器的并发处理能力。

## 工具方法

| 方法 | 说明 |
|------|------|
| `validateIPAddress(ip)` | 验证 IP 地址格式 |
| `validatePort(port)` | 验证端口号范围（0-65535） |
| `readImageFromFile(fileName)` | 从文件读取图片 |
| `saveImageToFile(imageData, dir)` | 保存图片到文件 |
