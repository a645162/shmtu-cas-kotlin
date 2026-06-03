---
title: BillParser
---

# BillParser

`BillParser` 把 Epay 账单页 HTML 解析为强类型 `BillItem` 列表。

`cn.edu.shmtu.cas.parser.BillParser`

## BillParseResult

```kotlin
data class BillParseResult(
    val bills: List<BillItem>,
    val totalPages: Int
)
```

## 核心方法

### parseBillPage

```kotlin
fun parseBillPage(htmlCode: String): BillParseResult
```

一次性解析整页：账单 + 总页数。

### parseBillItems

```kotlin
fun parseBillItems(htmlCode: String): List<BillItem>   // 解析 HTML
fun parseBillItems(): List<BillItem>                    // 解析已加载的 tr
```

返回强类型 `List<BillItem>`，含 `timestamp` 方便排序。

### getBillList (HashMap 形态，向后兼容)

```kotlin
fun getBillList(htmlCode: String): MutableList<HashMap<String, String>>
fun getBillList(): MutableList<HashMap<String, String>>
```

| 键 | 含义 |
|----|------|
| `dateStr` | 原始日期 |
| `timeStr` | 原始时间 |
| `timeStrFormat` | `HH:mm:ss` |
| `dateTimeStrFormat` | `yyyy.MM.dd HH:mm:ss` |
| `type` | 交易类型 |
| `number` | 交易号 |
| `targetUser` | 对方账户 |
| `money` | 金额字符串 |
| `method` | 支付方式 |
| `status` | 状态枚举名 |

### getBillTr

```kotlin
fun getBillTr(htmlCode: String): BillParser
```

链式：`BillParser().getBillTr(html).getBillList()`

### getPageCount

```kotlin
fun getPageCount(htmlCode: String): Int
```

从分页控件提取总页数，解析失败返回 `1`。

## BillItem 字段

详见 [数据类型 → BillItem](/api/datatype#billitem)。

## CSV 导出

### CsvExporter

```kotlin
class CsvExporter(
    private val headers: List<String> = DEFAULT_HEADERS,
    private val fields: List<String> = DEFAULT_FIELDS
)
```

默认表头：

| 表头 | 字段 |
|------|------|
| 日期 | `date_str` |
| 时间 | `time_str` |
| 时间(格式化) | `time_str_formatted` |
| 日期时间 | `date_time_formatted` |
| 时间戳 | `timestamp` |
| 交易名称 | `item_type` |
| 交易号 | `number` |
| 对方 | `target_user` |
| 金额 | `money_str` |
| 付款方式 | `method` |
| 状态 | `status` |

方法：

```kotlin
fun export(path: String, bills: List<BillItem>)          // 写文件
fun toCsvString(bills: List<BillItem>): String            // 返回字符串
```

支持自定义表头与字段：

```kotlin
CsvExporter()
    .let { it.export("simple.csv", bills) }   // 默认

val custom = CsvExporter(
    headers = listOf("日期", "金额", "商户"),
    fields = listOf("date_time_formatted", "money_str", "target_user")
)
custom.export("simple.csv", bills)
```

## 使用示例

```kotlin
val html = epay.getBill(pageNo = 1, billType = BillType.ALL).getOrThrow()
val parser = BillParser()
val items = parser.parseBillItems(html)
val totalPages = parser.getPageCount(html)

println("总页数: $totalPages, 本页 ${items.size} 条")
items.forEach { println(it) }

// 导出 CSV
CsvExporter().export("bills_${System.currentTimeMillis()}.csv", items)
```
