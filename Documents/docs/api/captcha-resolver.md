---
title: CaptchaResolver
---

# CaptchaResolver

`CaptchaResolver` 是验证码识别的统一抽象接口。`EpayAuth` / `WechatAuth` 的「一键登录」依赖此接口。

`cn.edu.shmtu.cas.captcha.CaptchaResolver`

## 接口定义

```kotlin
interface CaptchaResolver {
    suspend fun resolve(imageData: ByteArray): Result<CaptchaAnswer>
}
```

- 输入是验证码图片字节（`PNG` / `JPEG`）
- 输出是 `Result<CaptchaAnswer>`，不要抛异常
- `suspend` 允许远程 IO

## CaptchaAnswer

```kotlin
data class CaptchaAnswer(
    val value: String,
    val kind: CaptchaAnswerKind = CaptchaAnswerKind.EXPRESSION
)
```

`CaptchaAnswerKind`：

| 变体 | 含义 |
|------|------|
| `EXPRESSION` | 算式，如 `"3+5=8"` |
| `ANSWER` | 最终答案，如 `"8"` |

`intoFinalAnswer(): CaptchaAnswer`：

- `ANSWER` → 直接返回自身
- `EXPRESSION` → 调 `Captcha.getExprResultByExprString(value)`，得到 `ANSWER` 形态

## 四种内置实现

### ManualCaptchaResolver

```kotlin
class ManualCaptchaResolver(
    private val handler: suspend (ByteArray) -> CaptchaAnswer
) : CaptchaResolver
```

由调用方提供 handler，常用于 UI 弹窗或终端输入。

```kotlin
val resolver = ManualCaptchaResolver { imageData ->
    showCaptchaDialog(imageData)
    val input = channel.receive()
    CaptchaAnswer(input, CaptchaAnswerKind.ANSWER)
}
```

### RemoteOcrCaptchaResolver

```kotlin
class RemoteOcrCaptchaResolver(
    private val host: String,
    private val port: Int,
    private val retryTimes: Int = 3
) : CaptchaResolver
```

通过 TCP 调用远端 OCR 服务。内部用 `Captcha.ocrByRemoteTcpServerAutoRetry`。

```kotlin
val resolver = RemoteOcrCaptchaResolver("127.0.0.1", 21601, retryTimes = 5)
val answer = resolver.resolve(imageData).getOrThrow()
```

### RemoteOcrHttpCaptchaResolver

```kotlin
class RemoteOcrHttpCaptchaResolver(
    private val baseUrl: String,
    private val retryTimes: Int = 3
) : CaptchaResolver
```

RESTful HTTP OCR。请求 `POST {baseUrl}/api/ocr`，body `{"imageBase64": "..."}`。

```kotlin
val resolver = RemoteOcrHttpCaptchaResolver("http://127.0.0.1:5000", retryTimes = 3)
val ok = resolver.healthCheck()   // 可选
val answer = resolver.resolve(imageData).getOrThrow()
```

响应 JSON：

```json
{"success": true, "expression": "3+5=8", "result": 8, "error": null}
```

### ExprCaptchaResolver

```kotlin
class ExprCaptchaResolver(
    private val exprProvider: (ByteArray) -> String
) : CaptchaResolver
```

调用方直接提供算式字符串，常见于已部署自有 ONNX / 第三方模型。

```kotlin
val resolver = ExprCaptchaResolver { _ -> onnxModel.predict(it) }
```

## 自定义实现

任何识别后端都可以实现 `CaptchaResolver`：

```kotlin
class OnDeviceTfliteResolver(
    private val interpreter: Interpreter
) : CaptchaResolver {
    override suspend fun resolve(imageData: ByteArray): Result<CaptchaAnswer> = runCatching {
        val expr = interpreter.predict(imageData)
        CaptchaAnswer(Captcha.getExprResultByExprString(expr), CaptchaAnswerKind.ANSWER)
    }
}
```

## 选型速查

| 场景 | 推荐 |
|------|------|
| Android 单账号 | `ManualCaptchaResolver` |
| 多账号 / 后端 OCR | `RemoteOcrHttpCaptchaResolver` |
| 局域网直连 | `RemoteOcrCaptchaResolver` |
| 已有 ONNX | `ExprCaptchaResolver` |
| 自定义识别后端 | 实现 `CaptchaResolver` |
