---
title: Captcha (底层)
---

# Captcha (底层)

`Captcha` 是验证码相关的底层工具类（companion object）。新代码建议优先使用 [`CaptchaResolver`](/api/captcha-resolver) 接口；`Captcha` 主要服务于内部实现与 CLI 调试。

`cn.edu.shmtu.cas.captcha.Captcha`

## 配置属性

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `ocrHost` | `String` | `"127.0.0.1"` | TCP OCR 服务器地址 |
| `ocrPort` | `Int` | `21601` | TCP OCR 服务器端口 |

## setOcrServer

```kotlin
fun setOcrServer(host: String, port: Int = 21601)
```

## getImageDataFromUrlUsingGet

```kotlin
fun getImageDataFromUrlUsingGet(cookie: String? = null): Pair<ByteArray?, String>?
```

通过 OkHttp 下载验证码图片。

返回 `Pair(imageBytes, mergedCookie)`，失败时返回 `null`。

请求地址：`https://cas.shmtu.edu.cn/cas/captcha`

## getImageDataFromUrl

```kotlin
fun getImageDataFromUrl(
    imageUrl: String = "https://cas.shmtu.edu.cn/cas/captcha"
): ByteArray
```

直接通过 `java.net.URL` 下载图片，不携带 Cookie（一般不用）。

## ocrByRemoteTcpServer

```kotlin
fun ocrByRemoteTcpServer(
    host: String, port: Int, imageData: ByteArray
): String
```

通过 TCP 把图片发给 OCR 服务，读取算式字符串。

协议：

1. 连 `host:port`
2. 发送图片字节
3. 发送 `<END>` 标记
4. 读回响应

连接失败抛 `ConnectException`。

## ocrByRemoteTcpServerAutoRetry

```kotlin
fun ocrByRemoteTcpServerAutoRetry(
    host: String, port: Int,
    imageData: ByteArray,
    retryTimes: Int = 3
): String
```

自动重试版本。所有重试均失败返回 `""`。

## getExprResultByExprString

```kotlin
fun getExprResultByExprString(expr: String): String
```

从算式中提取 `=` 右侧。无 `=` 返回空串。

```kotlin
assert(Captcha.getExprResultByExprString("3+5=8") == "8")
assert(Captcha.getExprResultByExprString("42") == "")
```

## 工具方法

| 方法 | 签名 | 说明 |
|------|------|------|
| `saveImageToFile` | `(ByteArray, dir: String = ".")` | 保存到 `captcha_YYYYMMDDHHmmss.png` |
| `readImageFromFile` | `(String) -> ByteArray` | 读本地图片 |
| `validateIPAddress` | `(String) -> Boolean` | IP 格式校验 |
| `validatePort` | `(Int / String) -> Boolean` | 端口 0-65535 |

## 测试方法

| 方法 | 签名 | 说明 |
|------|------|------|
| `testLocalTcpServerOcr` | `(ip, port) -> Unit` | 单次下载+识别，打印耗时 |
| `testLocalTcpServerOcrMultiThread` | `(times: Int = 10) -> Unit` | 多线程并发测试 |

## 旧 API 弃用说明

> 旧版 `Main.kt` 直接使用 `getImageDataFromUrlUsingGet()` + `getExprResultByExprString()` 的同步模式，与新版三阶段登录（`CaptchaResolver`）相比灵活性差。新代码请改用：
>
> ```kotlin
> val resolver = RemoteOcrCaptchaResolver(host, port)
> val answer = resolver.resolve(imageData).getOrThrow()
> ```
