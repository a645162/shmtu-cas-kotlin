---
title: BillSync
---

# BillSync

`cn.edu.shmtu.cas.sync` 是与宿主存储解耦的同步层。

## BillStore

```kotlin
interface BillStore {
    fun contains(transactionNo: String): Boolean
    fun merge(newBills: List<BillItem>)
}
```

实现示例（Room）：

```kotlin
class RoomBillStore(private val dao: BillDao) : BillStore {
    override fun contains(transactionNo: String) = dao.exists(transactionNo)
    override fun merge(newBills: List<BillItem>) = dao.insertAll(newBills.map { it.toEntity() })
}
```

## SyncOptions

```kotlin
data class SyncOptions(
    val startPage: Int = 1,
    val maxPages: Int = 50,
    val billType: BillType = BillType.ALL,
    val earlyStopThreshold: Int = 3
)
```

| 字段 | 默认 | 说明 |
|------|------|------|
| `startPage` | 1 | 起始页（断点续传） |
| `maxPages` | 50 | 最多翻多少页 |
| `billType` | `ALL` | 抓哪个 tabNo |
| `earlyStopThreshold` | 3 | 连续 N 页无新增则早停 |

## SyncResult

```kotlin
data class SyncResult(
    val newCount: Int,
    val totalFetched: Int,
    val stoppedEarly: Boolean
)
```

## SyncProgress

```kotlin
data class SyncProgress(val page: Int, val newCount: Int, val totalFetched: Int)
```

## incrementalSync

```kotlin
suspend fun incrementalSync(
    auth: EpayAuth,
    store: BillStore,
    options: SyncOptions = SyncOptions(),
    onProgress: (suspend (SyncProgress) -> Unit)? = null
): Result<SyncResult>
```

### 算法

```
for page in startPage..(startPage + maxPages - 1):
    html = auth.getBill(page, billType)
    items = BillParser().parseBillItems(html)
    if items.isEmpty():
        consecutiveEmptyPages++
        if consecutiveEmptyPages >= earlyStopThreshold: break
        continue
    totalFetched += items.size
    fresh = items.filter { !store.contains(it.transactionNo) }
    if fresh.isEmpty():
        consecutiveEmptyPages++
        if consecutiveEmptyPages >= earlyStopThreshold: return early-stop
    else:
        consecutiveEmptyPages = 0
        newCount += fresh.size
        store.merge(fresh)
    onProgress?.invoke(...)
```

### 使用示例

```kotlin
val store = object : BillStore {
    private val known = mutableSetOf<String>()
    override fun contains(transactionNo: String) = transactionNo in known
    override fun merge(newBills: List<BillItem>) = newBills.forEach { known += it.transactionNo }
}

val result = incrementalSync(
    auth = epay,
    store = store,
    options = SyncOptions(maxPages = 100, billType = BillType.ALL, earlyStopThreshold = 5),
    onProgress = { p -> println("page=${p.page} new=${p.newCount} total=${p.totalFetched}") }
).getOrThrow()

println("新增 ${result.newCount} 条, 早停=${result.stoppedEarly}")
```

## 早停 vs 全量

- 增量同步：`earlyStopThreshold = 3..10`
- 全量同步：`earlyStopThreshold = Int.MAX_VALUE`，让翻页跑到 `maxPages` 上限

## 异常

- `auth.getBill` 失败 → `Result.failure`，并把已抓数据丢弃（`break`）
- 拉取途中被踢出（302）→ `Result.failure`，需要重新 `submitLogin` 后重试

## 与 BillParser 的关系

`incrementalSync` 内部 new 一个 `BillParser` 实例。宿主不需要预先解析，只需要提供 `BillStore` 即可。
