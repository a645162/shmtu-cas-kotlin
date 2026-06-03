---
title: CasAuth API 参考
---

# CasAuth API 参考

`CasAuth` 是 CAS 认证的底层工具类，提供客户端创建、execution 提取、登录表单提交、重定向跟踪等协议细节。所有方法都是 `companion object` 方法，无需实例化。

`cn.edu.shmtu.cas.auth.common.CasAuth` — 对应包路径。

## createClient

```kotlin
fun createClient(): OkHttpClient
```

创建一个**不自动重定向**的 OkHttpClient。

| 配置 | 值 |
|------|----|
| `followRedirects` | `false` |
| `followSslRedirects` | `false` |
| `connectTimeout` | 10s |
| `readTimeout` | 30s |
| `writeTimeout` | 30s |

> ⚠️ 必须禁用自动重定向！CAS 流程的 `Set-Cookie` 与 `Location` 必须由代码自己接管。

## getExecution

```kotlin
fun getExecution(
    url: String = "https://cas.shmtu.edu.cn/cas/login",
    cookie: String = ""
): Pair<String, String>
```

获取 CAS 登录页 `execution` 隐藏字段值，并返回服务器下发的 `JSESSIONID`。

返回 `Pair(execution, jSessionId)`。

| 出错情况 | 返回 |
|----------|------|
| HTTP 非 200 | `Pair("", "")` |
| HTML 无 `input[name=execution]` | `Pair("", "")` |
| `Set-Cookie` 无 `JSESSIONID` | `Pair(value, cookie)` |

底层使用 Jsoup 解析。

## casLogin

```kotlin
fun casLogin(
    url: String,
    username: String,
    password: String,
    validateCode: String,
    execution: String,
    cookie: String
): Triple<Int, String, String>
```

提交 CAS 登录表单。返回 `Triple(code, locationOrHtml, setCookieOrError)`。

### 表单字段

| 字段 | 值 |
|------|----|
| `username` | `username.trim()` |
| `password` | `password.trim()` |
| `validateCode` | `validateCode.trim()` |
| `execution` | `execution.trim()` |
| `_eventId` | `submit` |
| `geolocation` | 空串 |

### 自定义请求头

| 头 | 值 |
|----|----|
| `Host` | `cas.shmtu.edu.cn` |
| `Content-Type` | `application/x-www-form-urlencoded` |
| `Connection` | `keep-alive` |
| `Accept-Encoding` | `gzip, deflate, br` |
| `Accept` | `*/*` |
| `Cookie` | `cookie.trim()` |

### 返回值含义

| 状态码 | 含义 | 来源 |
|--------|------|------|
| `302` | 登录成功，`second` 为重定向 URL | `Location` 头 |
| `CasAuthStatus.VALIDATE_CODE_ERROR.code` (`-1`) | 验证码错误 | `#loginErrorsPanel` 含 `reCAPTCHA` |
| `CasAuthStatus.PASSWORD_ERROR.code` (`-2`) | 用户名/密码错误 | `#loginErrorsPanel` 含 `account is not recognized` |
| 其它 | 其它错误 | 原始状态码 + 错误文本 |

## casRedirect

```kotlin
fun casRedirect(url: String, cookie: String): Triple<Int, String, String>
```

跟踪一次重定向，返回 `Triple(code, nextLocation, mergedCookie)`。

- `code == 302` → 继续跟踪
- `code == 200` → 视为已落到目标服务

## mergeCookies

```kotlin
fun mergeCookies(existingCookie: String, setCookieHeaders: List<String>): String
```

把 `Set-Cookie` 头列表合并进已有 cookie 字符串。返回 `"name=value; name=value"` 格式。

## 使用示例

```kotlin
val (execution, jsession) = CasAuth.getExecution(
    url = "https://cas.shmtu.edu.cn/cas/login?service=...",
    cookie = ""
)

val challenge = epay.prepareChallenge().getOrThrow()
// challenge.execution, challenge.captchaImage

val (code, location, cookie) = CasAuth.casLogin(
    url = loginUrl,
    username = "学号",
    password = "密码",
    validateCode = "8",
    execution = challenge.execution,
    cookie = jsession
)
if (code == 302) {
    val (c2, next, cookie2) = CasAuth.casRedirect(location, cookie)
    // 继续 follow 302 链直到 200
}
```
