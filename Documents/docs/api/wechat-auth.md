---
title: WechatAuth
---

# WechatAuth

`WechatAuth` 封装了微信后勤平台（热水系统）的认证与查询。

`cn.edu.shmtu.cas.auth.WechatAuth`

## 构造函数

```kotlin
class WechatAuth(
    private val captchaResolver: CaptchaResolver? = null
)
```

## 三阶段方法

### probeLogin

```kotlin
suspend fun probeLogin(): Result<SessionProbe>
```

探测会话。`302` 时从 `Location` 提取微信认证 URL 并保存到 `loginWUrl`。

### prepareChallenge

```kotlin
suspend fun prepareChallenge(): Result<LoginChallenge>
```

内部步骤：

1. 访问 `loginWUrl` → 拿到 `wengine_new_ticket` Cookie + 302 到 CAS
2. 用新 Cookie 调 `CasAuth.getExecution` 拿 `execution`
3. 下载验证码图片

### submitLogin（手动）

```kotlin
suspend fun submitLogin(
    username: String, password: String,
    validateCode: String, execution: String
): Result<LoginSubmitResult>
```

内部会带 `from=http://hqzx.shmtu.edu.cn/cellphone/getHotWater` 跟随重定向。

### submitLogin（一键）

```kotlin
suspend fun submitLogin(
    username: String, password: String,
    maxRetries: Int = 5
): Result<LoginSubmitResult>
```

需要 `captchaResolver != null`。

### testLoginStatus

```kotlin
suspend fun testLoginStatus(): Result<Boolean>
```

通过访问热水接口判断当前是否已登录。

## 业务方法

### getHotWater

```kotlin
suspend fun getHotWater(): Result<String>
```

请求地址：`http://hqzx.shmtu.edu.cn/cellphone/getHotWater`

## 会话管理

```kotlin
fun restoreSession(json: String): Result<Unit>
fun extractSession(): String
fun getCookieString(): String
```

## 内部常量

```kotlin
const val HOT_WATER_URL = "http://hqzx.shmtu.edu.cn/cellphone/getHotWater"
const val VALIDATE_CODE_ERROR = 401
const val PASSWORD_ERROR = 402
```

## 完整示例

```kotlin
val wechat = WechatAuth(RemoteOcrHttpCaptchaResolver("http://127.0.0.1:21600"))
val r = wechat.submitLogin("学号", "密码").getOrThrow()
if (r is LoginSubmitResult.Success) {
    val html = wechat.getHotWater().getOrThrow()
    HotWaterParser(html).getHotWaterList().forEach { (t, w, b) ->
        println("$b 号楼: $t ℃ / $w %")
    }
}
```

## 与 EpayAuth 的关键差异

| 项 | EpayAuth | WechatAuth |
|----|----------|------------|
| 入口 URL | `ecard.shmtu.edu.cn/epay/...` | `hqzx.shmtu.edu.cn/cellphone/getHotWater` |
| 跳转链 | 302 → CAS | 302 → 微信 → CAS |
| 业务方法 | `getBill` | `getHotWater` |
| 同步算法 | `incrementalSync` 支持 | 暂不内置 |
| `LoginChallenge` | execution + image | execution + image（内部多拿 wengine ticket） |
