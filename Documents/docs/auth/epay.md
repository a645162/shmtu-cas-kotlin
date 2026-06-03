---
title: Epay 一卡通
---

# Epay 一卡通

## 概述

Epay 是上海海事大学一卡通消费查询平台，地址 `https://ecard.shmtu.edu.cn/epay`。通过 CAS 认证后可以分页查询消费记录。

## 三阶段流程

```kotlin
val epay = EpayAuth(remoteOcrResolver)

// 1. 探测登录状态
val probe: SessionProbe = epay.probeLogin().getOrThrow()
// AlreadyLoggedIn → 直接 getBill
// NeedLogin(loginUrl) → 继续

// 2. 准备 challenge
val challenge: LoginChallenge = epay.prepareChallenge().getOrThrow()

// 3. 提交登录（手动传 validateCode + execution）
val result: LoginSubmitResult = epay.submitLogin(
    username = "学号",
    password = "密码",
    validateCode = "8",
    execution = challenge.execution
).getOrThrow()

// 4. 拉账单
val html: String = epay.getBill(pageNo = 1, billType = BillType.ALL).getOrThrow()
```

## 一键登录

如果 `EpayAuth` 构造时注入了 `CaptchaResolver`，可走 `submitLogin(user, pass, maxRetries = 5)` 一步到位：

```kotlin
val epay = EpayAuth(RemoteOcrHttpCaptchaResolver("http://127.0.0.1:5000"))
val r = epay.submitLogin("学号", "密码")
```

内部步骤：

1. `tryReuseTgc()` 试探 TGC 是否仍有效
2. 失败后进入循环：`prepareChallenge` → `resolver.resolve` → 底层 `casLogin`
3. 遇 `ValidateCodeError` 自动重试（最多 `maxRetries` 次）
4. 遇 `PasswordError` 立即返回

## 关键方法

| 方法 | 签名 | 说明 |
|------|------|------|
| `probeLogin` | `suspend () -> Result<SessionProbe>` | 探测会话（200/302） |
| `prepareChallenge` | `suspend () -> Result<LoginChallenge>` | 拉取 execution + 验证码 |
| `submitLogin` | `suspend (user, pass, code, exec) -> Result<LoginSubmitResult>` | 手动登录 |
| `submitLogin` | `suspend (user, pass, maxRetries) -> Result<LoginSubmitResult>` | 一键登录 |
| `tryReuseTgc` | `suspend () -> Result<Boolean>` | TGC 复用 |
| `testLoginStatus` | `suspend () -> Result<Boolean>` | 当前是否已登录 |
| `getBill(pageNo, billType)` | `suspend () -> Result<String>` | 拉单页 HTML |
| `getBill(pageNo, tabNo)` | `suspend () -> Result<String>` | 拉单页 HTML（原始 tabNo） |
| `getAllBills(billType, startPage, maxPages)` | `suspend () -> Result<List<String>>` | 翻页抓全部 |
| `restoreSession(json)` | `(String) -> Result<Unit>` | 恢复 Cookie |
| `extractSession()` | `() -> String` | 导出 Cookie JSON |
| `getCookieString()` | `() -> String` | 导出 Cookie 字符串 |

## BillType 枚举

```kotlin
enum class BillType(val tabNo: String, val label: String) {
    ALL("0", "全部"),
    NOT_PAID("1", "未支付"),
    SUCCESS("2", "成功"),
    FAILURE("3", "失败")
}
```

`tabNo` 直接传给 `getBill` 的 URL 参数。

## 解析账单

```kotlin
val html = epay.getBill(pageNo = 1, billType = BillType.ALL).getOrThrow()
val parser = BillParser()
val items: List<BillItem> = parser.parseBillItems(html)
val totalPages: Int = parser.getPageCount(html)
```

详见 [BillParser API](/api/bill-parser)。

## 增量同步

```kotlin
val store = object : BillStore {
    override fun contains(transactionNo: String) = db.exists(transactionNo)
    override fun merge(newBills: List<BillItem>) = db.insertAll(newBills)
}
val result: SyncResult = incrementalSync(
    epay, store,
    SyncOptions(startPage = 1, maxPages = 50, billType = BillType.ALL, earlyStopThreshold = 3),
    onProgress = { p -> println("page=${p.page} new=${p.newCount}") }
)
```

详见 [BillSync](/api/bill-sync)。

## Cookie 管理

`EpayAuth` 内部使用 `CookieManager`：

- 构造时无 Cookie
- `casLogin` / `casRedirect` 返回的 `Set-Cookie` 自动合并
- `restoreSession(json)` 支持 JSON 或 `key=value; key=value` 字符串
- `extractSession()` 输出 JSON（key → `{value}`）

完整接口见 [Cookie 与会话持久化](/advanced/session)。

## 完整示例

```kotlin
val resolver = RemoteOcrHttpCaptchaResolver("http://127.0.0.1:5000")
val epay = EpayAuth(resolver)

if (epay.submitLogin("学号", "密码").getOrThrow() is LoginSubmitResult.Success) {
    val bills = incrementalSync(epay, myStore, SyncOptions(maxPages = 20))
    println("new=${bills.getOrThrow().newCount}")
    prefs.edit().putString("cookies", epay.extractSession()).apply()
}
```
