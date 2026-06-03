---
title: CAS 认证原理
---

# CAS 认证原理

## 什么是 CAS

CAS（Central Authentication Service）是上海海事大学使用的统一认证平台，地址为 `https://cas.shmtu.edu.cn/cas/login`。校园内的多个子系统（Epay 一卡通、微信后勤平台等）均通过 CAS 实现单点登录（SSO）。

## 总体流程

```
1. 访问目标服务 → 302 重定向到 CAS 登录页
2. 获取登录页面 → 提取 execution 参数
3. 下载验证码图片 → 解析出算式答案
4. 提交登录表单 → 302 表示成功
5. 跟随重定向 → 回到目标服务并获得认证 Cookie（含 TGC）
6. 后续请求复用 TGC → 直到 TGC 失效
```

## 第一步：访问目标服务

未登录时，受 CAS 保护的服务会返回 `302`，把浏览器引导到 CAS 登录页。本库用 `OkHttpClient` 关闭自动重定向：

```kotlin
val client = OkHttpClient.Builder()
    .followRedirects(false)
    .followSslRedirects(false)
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()
```

每次 `302` 都必须由代码自己跟踪，因为 `Set-Cookie` 和 `Location` 都携带着关键认证信息。

## 第二步：execution 提取

CAS 登录页含有一个隐藏的 `execution` 字段，每次请求值都不同，用于防止 CSRF 重放：

```kotlin
val (execution, jSessionId) = CasAuth.getExecution(
    url = "https://cas.shmtu.edu.cn/cas/login?service=...",
    cookie = ""
)
```

底层使用 Jsoup：

```kotlin
val element = Jsoup.parse(htmlCode).selectFirst("input[name=execution]")
val execution = element?.attr("value") ?: ""
```

服务器在返回登录页的同时通过 `Set-Cookie: JSESSIONID=...` 设置会话 ID，必须在后续请求中带上。

## 第三步：验证码

CAS 登录要求输入数学表达式（如 `3+5=8`）的计算结果。本库把「图片字节 → 答案」抽成 `CaptchaResolver`：

```kotlin
interface CaptchaResolver {
    suspend fun resolve(imageData: ByteArray): Result<CaptchaAnswer>
}
```

内置四种实现（详见 [CaptchaResolver](/api/captcha-resolver)）：

- `ManualCaptchaResolver` — 弹窗让用户输入
- `RemoteOcrCaptchaResolver` — TCP 远程 OCR
- `RemoteOcrHttpCaptchaResolver` — HTTP 远程 OCR
- `ExprCaptchaResolver` — 调用方直接给算式

无论哪种实现，宿主最终都通过 `CaptchaAnswer.intoFinalAnswer()` 得到 `ANSWER` 形态的字符串。

## 第四步：提交登录

```kotlin
val (code, location, setCookie) = CasAuth.casLogin(
    url = casLoginUrl,
    username = "学号",
    password = "密码",
    validateCode = exprResult,        // 例如 "8"
    execution = execution,
    cookie = currentCookie
)
```

表单字段：

| 字段 | 值 |
|------|----|
| `username` | `username.trim()` |
| `password` | `password.trim()` |
| `validateCode` | `validateCode.trim()` |
| `execution` | `execution.trim()` |
| `_eventId` | `submit` |
| `geolocation` | 空串 |

返回结果：

| 状态码 | 含义 | 来源 |
|--------|------|------|
| `302` | 成功，`location` 为重定向目标 | `Location` 头 |
| `-1` (`VALIDATE_CODE_ERROR`) | 验证码错误 | `#loginErrorsPanel` 含 `reCAPTCHA` |
| `-2` (`PASSWORD_ERROR`) | 用户名/密码错误 | `#loginErrorsPanel` 含 `account is not recognized` |
| 其它 | 其它错误 | 解析错误面板 |

## 第五步：跟随重定向

登录成功后 CAS 会返回 `302 → Location` 链。每一个跳板都可能下发新的 `Set-Cookie`：

```kotlin
val (code, nextLocation, mergedCookie) = CasAuth.casRedirect(location, cookie)
```

`mergeCookies` 把当前 cookie 与新 `Set-Cookie` 合并：

```kotlin
fun mergeCookies(existing: String, setCookieHeaders: List<String>): String
```

合并后 `TGC`（Ticket Granting Cookie）就持有在宿主手里。

## 第六步：TGC 复用

TGC 失效前，再访问目标服务能直接跳到子系统。`EpayAuth.tryReuseTgc()` 会再次请求 `getExecution`：

- 返回空 execution → TGC 仍有效，CAS 服务器会直接放行
- 返回非空 execution → TGC 已失效，必须重新走 challenge → login

实际登录流程是三阶段设计（[EpayAuth](/api/epay-auth)）：

```
probeLogin   → 探测当前会话
prepareChallenge → 准备 execution + 验证码图片
submitLogin  → 提交登录（含 TGC 复用 + 自动重试）
```

## 认证状态枚举

```kotlin
sealed class SessionProbe {
    data object AlreadyLoggedIn : SessionProbe()
    data class NeedLogin(val loginUrl: String) : SessionProbe()
}

sealed class LoginSubmitResult {
    data object Success : LoginSubmitResult()
    data object ValidateCodeError : LoginSubmitResult()
    data object PasswordError : LoginSubmitResult()
    data class Failure(val message: String) : LoginSubmitResult()
}
```

`CasAuthStatus` 枚举保留了底层数值码：

| 状态 | code |
|------|------|
| `SUCCESS` | `200` |
| `VALIDATE_CODE_ERROR` | `-1` |
| `PASSWORD_ERROR` | `-2` |
| `FAILURE` | `404` |

## 注意事项

- `execution` 每次请求都不一样，提交前必须重新拿
- `JSESSIONID` 在验证码下载时下发，后续 `casLogin` 必须带上
- 关闭 OkHttp 的自动重定向，否则会丢失 `Set-Cookie`
- TGC 跨服务不通用，但 `cas.shmtu.edu.cn` 域内复用
- `from` 参数在微信后勤平台必须保留原始请求 URL
