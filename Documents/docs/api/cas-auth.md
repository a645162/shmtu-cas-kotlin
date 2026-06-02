---
title: CasAuth API 参考
---

# CasAuth API 参考

`CasAuth` 是 CAS 认证的核心工具类，提供登录页面解析、表单提交和重定向跟踪功能。位于 `cn.edu.shmtu.cas.auth.common` 包。

::: info
`CasAuth` 的所有方法均为伴生对象（companion object）方法，可以直接通过类名调用，无需实例化。
:::

## getExecution

获取 CAS 登录页面的 `execution` 隐藏字段值。

```kotlin
fun getExecution(
    url: String = "https://cas.shmtu.edu.cn/cas/login",
    cookie: String = ""
): String
```

### 参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| url | String | `https://cas.shmtu.edu.cn/cas/login` | CAS 登录页面 URL |
| cookie | String | `""` | 请求携带的 Cookie |

### 返回值

`String` - `execution` 字段的值。获取失败时返回空字符串。

### 说明

- 使用 Jsoup 解析 HTML，查找 `input[name=execution]` 元素
- `execution` 值每次请求都不同，用于防止 CSRF 攻击
- 必须在提交登录表单前获取最新的 `execution` 值

### 示例

```kotlin
val execution = CasAuth.getExecution(
    url = "https://cas.shmtu.edu.cn/cas/login?service=...",
    cookie = jSessionId
)
```

---

## casLogin

提交 CAS 登录表单。

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

### 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| url | String | CAS 登录提交地址 |
| username | String | 学号/用户名 |
| password | String | 密码 |
| validateCode | String | 验证码计算结果 |
| execution | String | 从登录页面提取的 execution 值 |
| cookie | String | JSESSIONID Cookie |

### 返回值

`Triple<Int, String, String>`

| 位置 | 说明 |
|------|------|
| first | 状态码。302 表示登录成功，其他表示失败 |
| second | 302 时为重定向地址；其他情况为响应 HTML |
| third | 302 时为 `Set-Cookie` 值；其他情况为错误信息 |

### 错误处理

当登录失败时，方法会解析 HTML 中的 `#loginErrorsPanel` 元素获取错误信息：

- 包含 `account is not recognized` → 返回 `CasAuthStatus.PASSWORD_ERROR.code`（-2）
- 包含 `reCAPTCHA` → 返回 `CasAuthStatus.VALIDATE_CODE_ERROR.code`（-1）
- 其他错误 → 返回原始 HTTP 状态码

---

## casRedirect

CAS 认证成功后跟踪重定向。

```kotlin
fun casRedirect(
    url: String,
    cookie: String
): Triple<Int, String, String>
```

### 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| url | String | 重定向目标地址 |
| cookie | String | 当前 Cookie |

### 返回值

`Triple<Int, String, String>`

| 位置 | 说明 |
|------|------|
| first | 状态码。302 表示需要继续重定向 |
| second | 重定向地址 |
| third | 新的 Cookie（来自 `Set-Cookie` 头） |

### 说明

- CAS 认证成功后可能需要多次重定向才能到达目标服务
- 每次重定向都可能更新 Cookie
- 调用方需要根据返回的状态码决定是否继续跟踪重定向

### 示例

```kotlin
val result = CasAuth.casRedirect(location, cookie)
if (result.first == 302) {
    // 继续跟踪重定向
    val nextResult = CasAuth.casRedirect(result.second, result.third)
}
```
