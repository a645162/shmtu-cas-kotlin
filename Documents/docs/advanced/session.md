---
title: Cookie 与会话持久化
---

# Cookie 与会话持久化

TGC（Ticket Granting Cookie）是 CAS 颁发的跨子系统凭证，库把它统一存在 `CookieManager` 里。

## CookieManager

`cn.edu.shmtu.cas.auth.common.CookieManager` 是线程不安全、面向单次会话的小工具：

| 方法 | 签名 | 说明 |
|------|------|------|
| `restore(jsonOrCookieString)` | `Result<Unit>` | 恢复 cookie（兼容 JSON 与 `key=v; key=v` 字符串） |
| `restoreFromCookieString(s)` | `Result<Unit>` | 同上，专用于 cookie 字符串 |
| `extract()` | `String` | 导出 JSON 格式 |
| `addFromSetCookie(header)` | `Unit` | 添加单个 `Set-Cookie` 头 |
| `addAllFromSetCookieHeaders(headers)` | `Unit` | 批量 |
| `get()` | `String` | 取当前 cookie 字符串 |
| `isEmpty()` | `Boolean` | 是否有 cookie |
| `clear()` | `Unit` | 清空 |

JSON 形态：

```json
{
  "JSESSIONID": { "value": "A1B2C3D4..." },
  "TGC": { "value": "TGT-..." }
}
```

普通字符串形态：

```
JSESSIONID=A1B2C3D4...; TGC=TGT-...
```

`restore` 优先尝试 JSON，失败则回退到字符串解析，因此两种格式都接受。

## 在 EpayAuth / WechatAuth 中

构造时不传 cookie，登录成功后由 `casLogin` / `casRedirect` 的 `Set-Cookie` 自动填入：

```kotlin
val epay = EpayAuth(resolver)
epay.submitLogin("user", "pass")
    .getOrThrow()
    .let { require(it is LoginSubmitResult.Success) }

// 导出
val json: String = epay.extractSession()

// 重新构造并恢复
val epay2 = EpayAuth(resolver)
epay2.restoreSession(json)
if (epay2.testLoginStatus().getOrNull() == true) {
    // 会话有效，直接续接
}
```

## Android 加密存储

```kotlin
val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
val prefs = EncryptedSharedPreferences.create(
    context, "shmtu_cas", masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)

// 保存
prefs.edit().putString("epay_${accountId}", epay.extractSession()).apply()

// 读取
val json = prefs.getString("epay_${accountId}", null)
json?.let { epay.restoreSession(it) }
```

依赖：

```kotlin
implementation("androidx.security:security-crypto:1.1.0-alpha06")
```

## JVM 文件存储

```kotlin
val sessionsDir = File("sessions").apply { mkdirs() }

// 保存
File(sessionsDir, "$accountId.json").writeText(epay.extractSession())

// 恢复
val saved = File(sessionsDir, "$accountId.json")
if (saved.exists()) epay.restoreSession(saved.readText())
```

## TGC 复用原理

`EpayAuth.tryReuseTgc()` 内部会再次访问 `casLoginUrl` 拿 `execution`：

- 返回空 execution → CAS 服务器认得当前 TGC，已经放行 → 视为登录成功
- 返回非空 execution → TGC 过期 → 必须重新走 challenge → login

`submitLogin(user, pass, maxRetries)` 的一键登录就是先 `tryReuseTgc`，命中就直接返回；未命中再走 challenge 循环。

## 注意事项

- TGC 跨进程/跨设备不通用，每个 `EpayAuth` 实例独立持有
- 不要把 `CookieManager` 共享给多个 `EpayAuth` 实例，会出现 cookie 串台
- 服务器可能在重定向中途下发多个 `Set-Cookie`，必须用 `addAllFromSetCookieHeaders` 而非覆盖
- 恢复后建议立即 `testLoginStatus()` 确认 TGC 仍然有效
