---
title: CAS认证流程概览
---

# CAS 认证流程概览

## 什么是 CAS

CAS（Central Authentication Service）是上海海事大学使用的统一认证平台，地址为 `https://cas.shmtu.edu.cn/cas/login`。校园内的多个子系统（Epay 一卡通、微信后勤平台等）均通过 CAS 实现单点登录（SSO）。

## 认证流程

整个 CAS 认证流程可以分为以下几个步骤：

```
1. 访问目标服务 → 302 重定向到 CAS 登录页
2. 获取登录页面 → 提取 execution 参数
3. 下载验证码图片 → 发送到 OCR 服务器识别
4. 提交登录表单 → 302 重定向表示成功
5. 跟随重定向 → 回到目标服务并获取认证 Cookie
```

### 第一步：访问目标服务

当未登录用户访问受 CAS 保护的服务时，服务会返回 302 重定向，将用户引导至 CAS 登录页面。例如：

- Epay：`https://ecard.shmtu.edu.cn/epay/consume/query` → 302 → CAS 登录页
- 后勤平台：`http://hqzx.shmtu.edu.cn/cellphone/getHotWater` → 302 → CAS 登录页

在代码中，我们使用 `OkHttpClient` 并禁用自动重定向，手动跟踪每次 302 响应：

```kotlin
val client = OkHttpClient.Builder()
    .followRedirects(false)
    .followSslRedirects(false)
    .build()
```

### 第二步：获取 execution 参数

CAS 登录页面包含一个隐藏的 `execution` 字段，每次请求的值都不同，用于防止 CSRF 攻击。我们使用 Jsoup 解析 HTML 提取该值：

```kotlin
val document: Document = Jsoup.parse(htmlCode)
val element: Element? = document.selectFirst("input[name=execution]")
val execution: String = element?.attr("value") ?: ""
```

### 第三步：下载验证码并识别

CAS 登录需要输入验证码（数学表达式计算），验证码图片从 `https://cas.shmtu.edu.cn/cas/captcha` 获取：

```kotlin
val resultCaptcha = Captcha.getImageDataFromUrlUsingGet(cookie = loginCookie)
val imageData = resultCaptcha.first       // 图片二进制数据
val jSessionId = resultCaptcha.second     // 服务器返回的 JSESSIONID
```

获取到验证码图片后，通过 TCP 协议发送给远程 OCR 服务器识别：

```kotlin
val validateCode = Captcha.ocrByRemoteTcpServer(host, port, imageData)
val exprResult = Captcha.getExprResultByExprString(validateCode)
```

### 第四步：提交登录表单

使用提取的参数构造 POST 请求提交登录：

```kotlin
val formBody = FormBody.Builder()
    .add("username", username)
    .add("password", password)
    .add("validateCode", exprResult)
    .add("execution", execution)
    .add("_eventId", "submit")
    .add("geolocation", "")
    .build()
```

登录成功的标志是服务器返回 302 状态码，`Location` 头包含回调地址。

### 第五步：跟随重定向回到目标服务

CAS 认证成功后，需要跟随重定向链回到目标服务。每次重定向都会通过 `Set-Cookie` 头传递认证凭证（如 `JSESSIONID`、`wengine_new_ticket` 等）。

```kotlin
val resultRedirect = CasAuth.casRedirect(location, cookie)
```

## 认证状态

`CasAuthStatus` 枚举定义了认证可能的结果：

| 状态 | 代码 | 说明 |
|------|------|------|
| SUCCESS | 200 | 认证成功 |
| VALIDATE_CODE_ERROR | -1 | 验证码错误 |
| PASSWORD_ERROR | -2 | 用户名或密码错误 |
| FAILURE | 404 | 其他认证失败 |

## 注意事项

- CAS 登录过程中必须正确维护 Cookie，特别是 `JSESSIONID`，它在获取验证码时由服务器设置
- `execution` 参数每次请求都不同，必须在提交登录表单前重新获取
- 验证码为数学表达式（如 `3+5=8`），需要计算等号后面的结果
- OkHttp 客户端必须禁用自动重定向，以便手动处理 302 响应和 Cookie
