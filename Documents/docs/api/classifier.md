---
title: BillClassifier / PositionTranslator
---

# BillClassifier / PositionTranslator

`cn.edu.shmtu.cas.classifier` 提供基于关键词规则的语义增强层。

## BillCategory

```kotlin
enum class BillCategory {
    DEPOSIT, ELECTRICITY, BATH, HOT_WATER, CAKE, CANTEEN,
    LIBRARY, HOSPITAL, SHOP, LAUNDRY, NETWORK, TRANSPORT, OTHER
}
```

提供 `fromString(s)`，大小写不敏感，未知值返回 `OTHER`。

## CategoryRule

```kotlin
@Serializable
data class CategoryRule(
    val name: List<String> = emptyList(),
    val target: List<String> = emptyList()
)
```

- `name` — 匹配 `BillItem.billType`（交易名称）
- `target` — 匹配 `BillItem.targetUser`（对方账户）

## BillClassifier

```kotlin
class BillClassifier(private val categories: Map<String, CategoryRule>) {
    fun classify(name: String, target: String): BillCategory
    companion object {
        fun fromJson(jsonStr: String): BillClassifier
    }
}
```

### JSON 规则

```json
{
  "deposit":    { "name": ["中行云充值", "微信充值"] },
  "electricity":{ "name": ["电费"] },
  "bath":       { "target": ["淋浴", "热水"] },
  "canteen":    { "target": ["食堂", "餐厅"] }
}
```

### 使用

```kotlin
val classifier = BillClassifier.fromJson("""
    {
      "deposit": { "name": ["中行云充值"] },
      "canteen": { "target": ["食堂"] }
    }
""".trimIndent())

classifier.classify("中行云充值", "某商户")      // DEPOSIT
classifier.classify("消费", "海馨楼食堂")         // CANTEEN
classifier.classify("消费", "未知")               // OTHER
```

匹配顺序：先 `name`，后 `target`，命中即返回。

## PositionTranslator

把 `targetUser` 翻译为「楼栋/房间」结构。

### PositionInfo

```kotlin
@Serializable
data class PositionInfo(val position: String, val room: String)
```

### PositionTranslator

```kotlin
class PositionTranslator private constructor(private val keywords: Map<String, PositionInfo>) {
    fun translate(targetUser: String): PositionInfo?
    fun getAllKeywords(): Map<String, PositionInfo>
    companion object {
        fun fromJson(jsonStr: String): PositionTranslator
    }
}
```

匹配策略：

1. 精确匹配（`keywords[targetUser.trim()]`）
2. 模糊匹配（`targetUser.contains(keyword)`）
3. 都不命中 → `null`

### JSON 规则

```json
{
  "field": "target",
  "keywords": {
    "A食堂1楼大餐厅": { "position": "海馨楼",   "room": "海馨第1食堂" },
    "淋浴":          { "position": "公共浴室", "room": "浴室" }
  }
}
```

### 使用

```kotlin
val translator = PositionTranslator.fromJson("""
    {
      "field": "target",
      "keywords": {
        "A食堂1楼大餐厅": { "position": "海馨楼", "room": "海馨第1食堂" },
        "淋浴": { "position": "公共浴室", "room": "浴室" }
      }
    }
""".trimIndent())

val info = translator.translate("A食堂1楼大餐厅")
println("${info?.position} / ${info?.room}")  // 海馨楼 / 海馨第1食堂

// 模糊匹配
val (pos, room) = translator.translate("淋浴-北区浴室")!!
```

## 与 Room 持久化

`BillItem` 不含 `position` / `room` 字段。`PositionTranslator` 通常在写数据库时把 `targetUser` 转成 `position` / `room` 一起存：

```kotlin
class RoomBillStore(
    private val dao: BillDao,
    private val translator: PositionTranslator
) : BillStore {
    override fun contains(transactionNo: String) = dao.exists(transactionNo)
    override fun merge(newBills: List<BillItem>) {
        val entities = newBills.map { bill ->
            val info = translator.translate(bill.targetUser)
            BillEntity(
                transactionNo = bill.transactionNo,
                dateTime = bill.dateTimeFormat,
                billType = bill.billType,
                targetUser = bill.targetUser,
                amount = bill.amount,
                status = bill.status.name,
                position = info?.position ?: "",
                room = info?.room ?: ""
            )
        }
        dao.insertAll(entities)
    }
}
```
