---
title: 错误码与重试
---

# 错误码与重试

## 登录相关

### `LoginSubmitResult`

```kotlin
sealed class LoginSubmitResult {
    data object Success : LoginSubmitResult()
    data object ValidateCodeError : LoginSubmitResult()
    data object PasswordError : LoginSubmitResult()
    data class Failure(val message: String) : LoginSubmitResult()
}
```

| 变体 | 重试策略 | 说明 |
|------|----------|------|
| `Success` | - | 已登录，可直接进入同步 |
| `ValidateCodeError` | **是**（自动） | 验证码错误，可重新 `prepareChallenge` + 重新识别 |
| `PasswordError` | **否** | 用户名/密码错误，需要用户介入 |
| `Failure(msg)` | 看情况 | 其它错误（网络、协议变更等） |

`submitLogin(user, pass, maxRetries = 5)` 一键登录已经帮你处理了 `ValidateCodeError` 的自动重试；手动模式需要自己循环：

```kotlin
for (attempt in 1..maxAttempts) {
    val challenge = epay.prepareChallenge().getOrThrow()
    val answer = resolver.resolve(challenge.captchaImage).getOrThrow().intoFinalAnswer().value
    when (val r = epay.submitLogin(user, pass, answer, challenge.execution).getOrThrow()) {
        is LoginSubmitResult.Success -> return r
        is LoginSubmitResult.ValidateCodeError -> continue
        is LoginSubmitResult.PasswordError -> return r
        is LoginSubmitResult.Failure -> {
            log.warn("登录失败：${r.message}")
            if (attempt == maxAttempts) return r
        }
    }
}
```

### `CasAuthStatus`（底层数值码）

| 状态 | code | 触发 |
|------|------|------|
| `SUCCESS` | 200 | `casLogin` 拿到 302 |
| `VALIDATE_CODE_ERROR` | -1 | `#loginErrorsPanel` 含 `reCAPTCHA` |
| `PASSWORD_ERROR` | -2 | `#loginErrorsPanel` 含 `account is not recognized` |
| `FAILURE` | 404 | 其它情况 |

`EpayAuth` / `WechatAuth` 内部把这两个状态映射到 `LoginSubmitResult`：

| `CasAuthStatus` | `LoginSubmitResult` |
|----------------|---------------------|
| `SUCCESS` | `Success` |
| `VALIDATE_CODE_ERROR` | `ValidateCodeError` |
| `PASSWORD_ERROR` | `PasswordError` |
| `FAILURE` | `Failure("...")` |

## 同步相关

### `incrementalSync` 返回值

```kotlin
data class SyncResult(
    val newCount: Int,
    val totalFetched: Int,
    val stoppedEarly: Boolean
)
```

| 字段 | 含义 |
|------|------|
| `newCount` | 本次新增的 `BillItem` 数 |
| `totalFetched` | 本次抓取的总条数 |
| `stoppedEarly` | 是否触发早停（连续 N 页无新增） |

**异常：**

- 网络失败 → `Result.failure(IOException)`
- 拉取中途被踢出（302）→ `Result.failure(Exception("未登录，需要重新登录"))`，需先 `submitLogin`

### 早停阈值

`SyncOptions.earlyStopThreshold` 控制早停灵敏度：

| 场景 | 推荐 |
|------|------|
| 增量同步 | `3` ~ `10` |
| 全量同步 | `Int.MAX_VALUE`（禁用） |

## 验证码相关

### `RemoteOcrHttpCaptchaResolver`

| 场景 | 行为 |
|------|------|
| `success=false` | `Result.failure(Exception(error))` |
| HTTP 非 200 | `Result.failure(Exception("HTTP $code"))` |
| 抛 `IOException` | `Result.failure`（内部会重试） |
| `retryTimes` 次全失败 | `Result.failure(lastException)` |

`healthCheck()` 失败仅返回 `false`，不抛异常。

### `RemoteOcrCaptchaResolver`

- 连接失败：包成 `Result.failure(ConnectException)`
- 超时：返回空字符串 → 视为失败
- `retryTimes` 次全失败：返回 `""`（注意：不是 `Result.failure`，因为底层是 `Captcha.ocrByRemoteTcpServerAutoRetry` 返回字符串）

### `ManualCaptchaResolver`

- handler 抛异常 → `Result.failure`
- handler 返回 `null` 或非法输入 → 由调用方决定如何处理

## 常见失败模式速查

| 现象 | 排查方向 |
|------|----------|
| `Result.failure("未登录，需要重新登录")` | Cookie 过期，调用 `submitLogin` |
| `LoginSubmitResult.ValidateCodeError` 连环出现 | OCR 服务不在线 / 模型缺失 / 验证码图片为空 |
| `LoginSubmitResult.PasswordError` | 用户输入错误，**不要**重试 |
| `Result.failure(ConnectException)` | OCR 地址/端口不通，防火墙？Docker 端口映射？ |
| `Result.failure(SocketTimeoutException)` | OCR 服务处理慢，调高 `retryTimes` 或扩容 |
| `incrementalSync` 第一页就空 | Cookie 没真正有效或 `BillType` 选错 |
| 持续 `HealthCheck=false` | baseUrl 写错、CORS、HTTPS 证书问题 |
