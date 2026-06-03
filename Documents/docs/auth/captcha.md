---
title: 验证码抽象
---

# 验证码抽象

验证码求解是整个登录流程里最不稳定的环节：本库把它抽成 `CaptchaResolver` 接口，宿主可自由切换识别策略。

## 核心类型

### `CaptchaAnswerKind`

```kotlin
enum class CaptchaAnswerKind {
    /** 算式如 "12+34="，调用方还需要计算 */
    EXPRESSION,
    /** 已经是最终答案 */
    ANSWER
}
```

| 变体 | 含义 | 使用场景 |
|------|------|----------|
| `EXPRESSION` | OCR 返回原始算式 | 远程 OCR 服务通常返回 `"3+5=8"` |
| `ANSWER` | 已计算出最终答案 | 手动输入或已解析完毕 |

### `CaptchaAnswer`

```kotlin
data class CaptchaAnswer(
    val value: String,
    val kind: CaptchaAnswerKind = CaptchaAnswerKind.EXPRESSION
)
```

`intoFinalAnswer()` 内部逻辑：

- `ANSWER` → 直接返回 `value`
- `EXPRESSION` → 调 `Captcha.getExprResultByExprString(value)` 提取 `=` 右侧数字

```kotlin
val a = CaptchaAnswer("3+5=8", CaptchaAnswerKind.EXPRESSION)
assert(a.intoFinalAnswer().value == "8")

val b = CaptchaAnswer("8", CaptchaAnswerKind.ANSWER)
assert(b.intoFinalAnswer().value == "8")
```

## `CaptchaResolver` 接口

```kotlin
interface CaptchaResolver {
    suspend fun resolve(imageData: ByteArray): Result<CaptchaAnswer>
}
```

要点：

- 输入是图片字节（`PNG` / `JPEG`），不关心图片来源
- 输出是 `CaptchaAnswer`，保留 EXPRESSION/ANSWER 两种语义
- `suspend` 支持协程
- 实现者必须包成 `Result`，不要抛异常

## 四种内置实现

### 1. `ManualCaptchaResolver`

由调用方提供 handler 回调，常用于 UI 弹窗或终端输入。

```kotlin
val resolver = ManualCaptchaResolver { imageData ->
    Captcha.saveImageToFile(imageData)
    print("请输入验证码答案: ")
    val input = readLine()?.trim().orEmpty()
    CaptchaAnswer(input, CaptchaAnswerKind.ANSWER)
}
```

适合：UI 程序、首次调试、OCR 不稳定时的回退路径。

### 2. `RemoteOcrCaptchaResolver`

通过远端 TCP OCR 服务识别验证码。

```kotlin
val resolver = RemoteOcrCaptchaResolver(host = "127.0.0.1", port = 21601, retryTimes = 3)
val answer = resolver.resolve(imageData).getOrThrow()
```

协议：

1. 连 `host:port`
2. 发送图片字节
3. 发送 `<END>` 标记
4. 读回响应字符串（数学表达式如 `3+5=8`）

返回 `kind = EXPRESSION`，需调 `intoFinalAnswer()`。

### 3. `RemoteOcrHttpCaptchaResolver`

RESTful HTTP OCR。

```kotlin
val resolver = RemoteOcrHttpCaptchaResolver("http://127.0.0.1:5000", retryTimes = 3)
```

请求格式：

```
POST {baseUrl}/api/ocr
Content-Type: application/json

{"imageBase64": "<base64>"}
```

响应：

```json
{
  "success": true,
  "expression": "3+5=8",
  "result": 8,
  "error": null
}
```

还提供 `suspend fun healthCheck(): Boolean`，方便启动前检测。

### 4. `ExprCaptchaResolver`

调用方直接给出算式字符串，常见于已部署自有 ONNX / 第三方模型。

```kotlin
val resolver = ExprCaptchaResolver { _ -> "3+5=8" }
```

内部依然走 `getExprResultByExprString` 规约。

## 选型建议

| 场景 | 推荐 | 理由 |
|------|------|------|
| Android UI | `ManualCaptchaResolver` | 系统弹窗最稳 |
| Docker 部署 | `RemoteOcrHttpCaptchaResolver` | 易观测、易重试、易负载均衡 |
| 局域网 / 直连 | `RemoteOcrCaptchaResolver` | 协议简单、延迟低 |
| 已有 ONNX | `ExprCaptchaResolver` | 不耦合协议 |

## 完整示例

```kotlin
val resolver = RemoteOcrHttpCaptchaResolver("http://127.0.0.1:5000")
val epay = EpayAuth(resolver)

val challenge = epay.prepareChallenge().getOrThrow()
val finalAnswer = resolver.resolve(challenge.captchaImage)
    .getOrThrow()
    .intoFinalAnswer()
    .value

epay.submitLogin("学号", "密码", finalAnswer, challenge.execution)
```

## OCR 服务器部署

- 项目地址：[shmtu-cas-ocr-server](https://github.com/a645162/shmtu-cas-ocr-server)
- 模型训练：[shmtu-cas-ocr-model](https://github.com/a645162/shmtu-cas-ocr-model)
- 默认端口：TCP `21601`、HTTP `5000`
- 支持 CPU 与 Vulkan GPU
