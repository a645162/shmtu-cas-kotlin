---
title: BillParser API 参考
---

# BillParser API 参考

`BillParser` 用于解析 Epay 一卡通消费账单页面的 HTML，提取结构化的消费记录。位于 `cn.edu.shmtu.cas.parser` 包。

## 构造函数

```kotlin
class BillParser()
```

默认构造函数创建空的解析器，后续需要调用 `getBillTr()` 传入 HTML 数据。

---

## getBillTr

解析账单页面 HTML，提取账单表格行元素。

```kotlin
fun getBillTr(htmlCode: String): BillParser
```

### 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| htmlCode | String | 账单页面 HTML 内容 |

### 返回值

`BillParser` - 返回自身，支持链式调用。

### 说明

- 使用 Jsoup 解析 HTML
- 查找 `#aazone.zone_show_box_1 > table > tbody` 下的所有 `tr` 元素
- 每行必须包含 7 个子元素（列），否则会被跳过

---

## getBillList

获取解析后的账单列表。

```kotlin
fun getBillList(): MutableList<HashMap<String, String>>
```

### 返回值

`MutableList<HashMap<String, String>>` - 消费记录列表，每条记录为一个 HashMap。

### 字段说明

| 键 | 说明 | 示例 |
|------|------|------|
| dateStr | 日期（原始格式） | `"2026-04-29"` |
| timeStr | 时间（原始格式） | `"143025"` |
| timeStrFormat | 时间（格式化） | `"14:30:25"` |
| dateTimeStrFormat | 日期+时间 | `"2026-04-29 14:30:25"` |
| type | 交易类型 | `"消费"` |
| number | 交易号 | `"1234567890"` |
| targetUser | 目标用户 | `"食堂一楼"` |
| money | 金额 | `"12.50"` |
| method | 交易方式 | `"刷卡"` |
| status | 交易状态 | `"成功"` |

---

## getPageCount

获取账单总页数。

```kotlin
fun getPageCount(htmlCode: String): Int
```

### 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| htmlCode | String | 账单页面 HTML 内容 |

### 返回值

`Int` - 总页数。解析失败返回 -1。

### 说明

从分页控件中解析总页数，查找格式如 `"X/Y页 首页"` 的文本。

---

## 使用示例

```kotlin
val parser = BillParser()

// 链式调用
val billList = parser
    .getBillTr(htmlContent)
    .getBillList()

// 遍历账单
for (bill in billList) {
    println("${bill["dateTimeStrFormat"]} | ${bill["type"]} | ${bill["money"]}元")
}

// 获取总页数
val totalPages = parser.getPageCount(htmlContent)
println("总页数: $totalPages")
```
