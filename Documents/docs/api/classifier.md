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

## BillClassifier

```kotlin
class BillClassifier(...) {
    fun classify(name: String, target: String): BillCategory
    companion object {
        fun fromToml(tomlStr: String): BillClassifier
        fun fromRulesToml(tomlStr: String): BillClassifier
    }
}
```

### TOML 规则

```toml
[type.deposit]
name = "充值"
match_field = "item_type"
match_names = ["中行云充值", "微信充值"]

[type.canteen]
name = "食堂"
match_field = "target_user"
match_targets = ["食堂", "餐厅"]
```

### 使用

```kotlin
val classifier = BillClassifier.fromToml("""
    [type.deposit]
    name = "充值"
    match_field = "item_type"
    match_names = ["中行云充值"]

    [type.canteen]
    name = "食堂"
    match_field = "target_user"
    match_targets = ["食堂"]
""".trimIndent())

classifier.classify("中行云充值", "某商户")      // DEPOSIT
classifier.classify("消费", "海馨楼食堂")         // CANTEEN
classifier.classify("消费", "未知")               // OTHER
```

匹配顺序：按 TOML 文件中的规则顺序遍历，命中即返回。

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
        fun fromToml(tomlStr: String): PositionTranslator
        fun fromRulesToml(tomlStr: String): PositionTranslator
    }
}
```

匹配策略：

1. 精确匹配（`keywords[targetUser.trim()]`）
2. 模糊匹配（`targetUser.contains(keyword)`）
3. 都不命中 → `null`

### TOML 规则

```toml
[position]
field = "target_user"

[position.keywords."A食堂1楼大餐厅"]
building = "海馨楼"
room = "海馨第1食堂"

[position.keywords."淋浴"]
building = "公共浴室"
room = "浴室"
```

### 使用

```kotlin
val translator = PositionTranslator.fromToml("""
    [position]
    field = "target_user"

    [position.keywords."A食堂1楼大餐厅"]
    building = "海馨楼"
    room = "海馨第1食堂"

    [position.keywords."淋浴"]
    building = "公共浴室"
    room = "浴室"
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
