---
title: EpayAuth
---

# EpayAuth

`EpayAuth` 封装了一卡通（Epay）侧的 CAS 登录 + 账单抓取。

`cn.edu.shmtu.cas.auth.EpayAuth`

## 构造函数

```kotlin
class EpayAuth(
    private val captchaResolver: CaptchaResolver? = null
)
```

| 参数 | 说明 |
|------|------|
| `captchaResolver` | 可选；提供后 `submitLogin(user, pass, maxRetries)` 走一键登录 |

## 三阶段方法

### probeLogin

```kotlin
suspend fun probeLogin(): Result<SessionProbe>
```

探测当前会话。

- `200` → `SessionProbe.AlreadyLoggedIn`
- `302` → `SessionProbe.NeedLogin(loginUrl)`，内部保存 loginUrl
- 其它 → `Result.failure`

### prepareChallenge

```kotlin
suspend fun prepareChallenge(): Result<LoginChallenge>
```

准备 `execution` + 验证码图片。

```kotlin
data class LoginChallenge(
    val execution: String,
    val captchaImage: ByteArray
)
```

必须先 `probeLogin` 拿到 loginUrl。

### submitLogin（手动）

```kotlin
suspend fun submitLogin(
    username: String, password: String,
    validateCode: String, execution: String
): Result<LoginSubmitResult>
```

手动提交登录。返回 `LoginSubmitResult`：

| 变体 | 含义 |
|------|------|
| `Success` | 已登录 |
| `ValidateCodeError` | 验证码错误 |
| `PasswordError` | 用户名/密码错误 |
| `Failure(msg)` | 其它错误 |

### submitLogin（一键）

```kotlin
suspend fun submitLogin(
    username: String, password: String,
    maxRetries: Int = 5
): Result<LoginSubmitResult>
```

需要 `captchaResolver != null`。内部：

1. `tryReuseTgc()` 试探 TGC
2. 循环 `prepareChallenge` → `resolver.resolve` → 底层 `casLogin`
3. `ValidateCodeError` 自动重试，`PasswordError` 立即返回

### tryReuseTgc

```kotlin
suspend fun tryReuseTgc(): Result<Boolean>
```

试探 TGC 是否仍有效。

### testLoginStatus

```kotlin
suspend fun testLoginStatus(): Result<Boolean>
```

通过访问账单接口判断当前是否已登录。

## 业务方法

### getBill (BillType)

```kotlin
suspend fun getBill(
    pageNo: Int = 1,
    billType: BillType = BillType.ALL
): Result<String>
```

### getBill (raw tabNo)

```kotlin
suspend fun getBill(
    pageNo: Int = 1,
    tabNo: String = "1"
): Result<String>
```

请求地址：`https://ecard.shmtu.edu.cn/epay/consume/query?pageNo={pageNo}&tabNo={tabNo}`

### getAllBills

```kotlin
suspend fun getAllBills(
    billType: BillType = BillType.ALL,
    startPage: Int = 1,
    maxPages: Int = 50
): Result<List<String>>
```

翻页抓全部，遇到 `aazone` 标记缺失或空页即停。

## 会话管理

```kotlin
fun restoreSession(json: String): Result<Unit>    // 恢复 Cookie
fun extractSession(): String                      // 导出 JSON
fun getCookieString(): String                     // 导出字符串
```

## 内部常量

```kotlin
const val EPAY_BILL_URL = "https://ecard.shmtu.edu.cn/epay/consume/query"
const val VALIDATE_CODE_ERROR = 401
const val PASSWORD_ERROR = 402
```

## 完整示例

```kotlin
val epay = EpayAuth(RemoteOcrHttpCaptchaResolver("http://127.0.0.1:21600"))

// 一键登录
val result = epay.submitLogin("学号", "密码").getOrThrow()
if (result is LoginSubmitResult.Success) {
    val html = epay.getBill(pageNo = 1, billType = BillType.ALL).getOrThrow()
    val items = BillParser().parseBillItems(html)
    println("共 ${items.size} 条")
}
```
