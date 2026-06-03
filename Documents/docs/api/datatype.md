---
title: 数据类型与枚举
---

# 数据类型与枚举

## BillItem

`cn.edu.shmtu.cas.datatype.BillItem` — 一条账单的强类型表示。

```kotlin
data class BillItem(
    // === 时间 ===
    val dateStr: String,            // "2026.04.29"
    val timeStr: String,            // "143025"
    val timeStrFormat: String,      // "14:30:25"
    val dateTimeFormat: String,     // "2026.04.29 14:30:25"
    val timestamp: Long,            // Unix 秒

    // === 交易信息 ===
    val billType: String,           // "水控消费"
    val transactionNo: String,      // "20260429143025123456"
    val targetUser: String,         // "A食堂1楼大餐厅"

    // === 金额 ===
    val amount: String,             // "12.50"
    val money: Float,               // 12.5f

    // === 其他 ===
    val paymentMethod: String,      // "刷卡"
    val status: BillItemStatus      // 枚举
)
```

### 方法

| 方法 | 签名 | 说明 |
|------|------|------|
| `toString()` | 字符串 | `"日期时间 \| 类型 \| 对方 \| 金额 \| 状态"` |
| `getField(name)` | `String` | 按 `name` 取字段（用于 CSV 导出） |

`getField` 支持的字段名：

| name | 返回 |
|------|------|
| `date_str` | `dateStr` |
| `time_str` | `timeStr` |
| `time_str_formatted` | `timeStrFormat` |
| `date_time_formatted` | `dateTimeFormat` |
| `timestamp` | `timestamp.toString()` |
| `item_type` | `billType` |
| `number` | `transactionNo` |
| `target_user` | `targetUser` |
| `money_str` | `amount` |
| `money` | `"%.2f".format(money)` |
| `method` | `paymentMethod` |
| `status` / `status_str` | `status.name` |
| 其它 | 空串 |

## BillType

`cn.edu.shmtu.cas.datatype.BillType`

```kotlin
enum class BillType(val tabNo: String, val label: String) {
    ALL("0", "全部"),
    NOT_PAID("1", "未支付"),
    SUCCESS("2", "成功"),
    FAILURE("3", "失败")
}
```

`tabNo` 直接用于 `epay.getBill(pageNo, tabNo = billType.tabNo)`。

## BillItemStatus

`cn.edu.shmtu.cas.datatype.BillItemStatus`

```kotlin
enum class BillItemStatus {
    SUCCESS, FAILURE, NOT_PAID, UNKNOWN;
    companion object {
        fun fromString(text: String): BillItemStatus
    }
}
```

`fromString` 规则：

| 文本包含 | 枚举 |
|---------|------|
| `成功` | `SUCCESS` |
| `失败` | `FAILURE` |
| `未支付` / `待支付` | `NOT_PAID` |
| 其它 | `UNKNOWN` |

## SessionProbe

`cn.edu.shmtu.cas.session.SessionProbe`

```kotlin
sealed class SessionProbe {
    data object AlreadyLoggedIn : SessionProbe()
    data class NeedLogin(val loginUrl: String) : SessionProbe()
}
```

## LoginSubmitResult

```kotlin
sealed class LoginSubmitResult {
    data object Success : LoginSubmitResult()
    data object ValidateCodeError : LoginSubmitResult()
    data object PasswordError : LoginSubmitResult()
    data class Failure(val message: String) : LoginSubmitResult()
}
```

## LoginChallenge

```kotlin
data class LoginChallenge(
    val execution: String,
    val captchaImage: ByteArray
)
```

`captchaImage` 是 PNG 原始字节。

## CasAuthStatus（底层数值码）

```kotlin
enum class CasAuthStatus(val code: Int) {
    SUCCESS(200),
    VALIDATE_CODE_ERROR(-1),
    PASSWORD_ERROR(-2),
    FAILURE(404)
}
```

`EpayAuth` / `WechatAuth` 内部把 `CasAuthStatus` 映射成 `LoginSubmitResult`，宿主一般直接消费 sealed class。

## CaptchaAnswer / CaptchaAnswerKind

见 [CaptchaResolver](/api/captcha-resolver#captchaanswer)。
