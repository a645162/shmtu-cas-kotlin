---
title: Captcha API 参考
---

# Captcha API 参考

`Captcha` 提供验证码图片下载、OCR 识别和相关工具方法。位于 `cn.edu.shmtu.cas.captcha` 包。

::: info
`Captcha` 的所有方法均为伴生对象方法，可直接通过类名调用。
:::

## 配置属性

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| ocrHost | String | `"127.0.0.1"` | OCR 服务器地址 |
| ocrPort | Int | `21601` | OCR 服务器端口 |

## setOcrServer

设置 OCR 服务器地址和端口。

```kotlin
fun setOcrServer(host: String, port: Int = 21601)
```

---

## getImageDataFromUrlUsingGet

通过 OkHttp GET 请求下载验证码图片（推荐方法）。

```kotlin
fun getImageDataFromUrlUsingGet(
    cookie: String? = null
): Pair<ByteArray?, String>?
```

### 参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| cookie | String? | `null` | 请求携带的 Cookie |

### 返回值

`Pair<ByteArray?, String>?` - 成功时返回图片数据和 Cookie，失败返回 `null`。

| 位置 | 说明 |
|------|------|
| first | 验证码图片二进制数据 |
| second | 服务器通过 `Set-Cookie` 返回的 JSESSIONID |

---

## getImageDataFromUrl

通过 URL 直接下载验证码图片。

```kotlin
fun getImageDataFromUrl(
    imageUrl: String = "https://cas.shmtu.edu.cn/cas/captcha"
): ByteArray
```

### 参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| imageUrl | String | CAS 验证码 URL | 验证码图片地址 |

### 返回值

`ByteArray` - 验证码图片二进制数据。

---

## ocrByRemoteTcpServer

通过 TCP 协议发送验证码图片到 OCR 服务器识别。

```kotlin
fun ocrByRemoteTcpServer(
    host: String,
    port: Int,
    imageData: ByteArray
): String
```

### 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| host | String | OCR 服务器地址 |
| port | Int | OCR 服务器端口 |
| imageData | ByteArray | 验证码图片二进制数据 |

### 返回值

`String` - OCR 识别结果（数学表达式，如 `"3+5=8"`）。连接失败时抛出 `ConnectException`。

### 通信协议

1. 建立 TCP 连接
2. 发送图片二进制数据
3. 发送 `<END>` 标记
4. 接收识别结果字符串

---

## ocrByRemoteTcpServerAutoRetry

带自动重试的 OCR 识别。

```kotlin
fun ocrByRemoteTcpServerAutoRetry(
    host: String,
    port: Int,
    imageData: ByteArray,
    retryTimes: Int = 3
): String
```

### 参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| host | String | - | OCR 服务器地址 |
| port | Int | - | OCR 服务器端口 |
| imageData | ByteArray | - | 验证码图片二进制数据 |
| retryTimes | Int | `3` | 最大重试次数 |

### 返回值

`String` - OCR 识别结果。所有重试均失败时返回空字符串。

---

## getExprResultByExprString

从数学表达式中提取计算结果。

```kotlin
fun getExprResultByExprString(expr: String): String
```

### 示例

```kotlin
Captcha.getExprResultByExprString("3+5=8")  // 返回 "8"
Captcha.getExprResultByExprString("9-2=7")  // 返回 "7"
```

---

## 工具方法

### saveImageToFile

将验证码图片保存到文件。

```kotlin
fun saveImageToFile(imageData: ByteArray, directoryPath: String = ".")
```

文件名格式：`captcha_YYYYMMDDHHmmss.png`

### readImageFromFile

从文件读取图片数据。

```kotlin
fun readImageFromFile(fileName: String): ByteArray
```

### validateIPAddress

验证 IP 地址格式。

```kotlin
fun validateIPAddress(ip: String): Boolean
```

### validatePort

验证端口号。

```kotlin
fun validatePort(port: Int): Boolean   // 0-65535
fun validatePort(port: String): Boolean
```

---

## 测试方法

### testLocalTcpServerOcr

单次验证码识别测试。

```kotlin
fun testLocalTcpServerOcr(
    ip: String = ocrHost,
    port: Int = ocrPort
)
```

### testLocalTcpServerOcrMultiThread

多线程并发验证码识别测试。

```kotlin
fun testLocalTcpServerOcrMultiThread(times: Int = 10)
```
