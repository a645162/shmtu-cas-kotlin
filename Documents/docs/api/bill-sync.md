---
title: BillSync
---

# BillSync

`cn.edu.shmtu.cas.sync` 是与宿主存储解耦的同步层。

当前同步层提供三种入口：

- `incrementalSync(...)`：单账号增量同步
- `fullSync(...)`：单账号全量同步
- `syncAccount(...)`：单账号完整状态机包装器，负责探测登录、验证码、登录、翻页与持久化

## BillStore

```kotlin
interface BillStore {
    fun contains(transactionNo: String): Boolean
    fun merge(newBills: List<BillItem>)
    fun clear() {}
    fun onBeforeMerge(items: List<BillItem>): List<BillItem> = items
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
    val earlyStopThreshold: Int = 3,
    val sinceTimestamp: Long? = null,
    val clearBeforeMerge: Boolean = false,
)
```

| 字段 | 默认 | 说明 |
|------|------|------|
| `startPage` | 1 | 起始页（断点续传） |
| `maxPages` | 50 | 最多翻多少页 |
| `billType` | `ALL` | 抓哪个 tabNo |
| `earlyStopThreshold` | 3 | 连续 N 页无新增则早停 |
| `sinceTimestamp` | `null` | 只保留该时间戳之后的账单；`null` 表示不限制 |
| `clearBeforeMerge` | `false` | 全量模式合并前先清空宿主旧数据 |

### SyncRangePreset

```kotlin
enum class SyncRangePreset {
    Week, HalfMonth, Month, HalfYear, Year, All
}
```

推荐通过工厂方法构造选项，而不是手写 `sinceTimestamp`：

```kotlin
val inc = SyncOptions.incremental(SyncRangePreset.Month)
val full = SyncOptions.full(SyncRangePreset.All)
```

## SyncResult

```kotlin
data class SyncResult(
    val newCount: Int,
    val totalFetched: Int,
    val pagesFetched: Int,
    val stoppedEarly: Boolean
)
```

## SyncProgress

```kotlin
data class SyncProgress(
    val accountId: String,
    val currentAccount: String,
    val accountIndex: Int,
    val totalAccounts: Int,
    val newCount: Int,
    val pagesFetched: Int,
    val totalNewCount: Int,
    val status: SyncStatus,
)
```

## incrementalSync

```kotlin
suspend fun incrementalSync(
    auth: EpayAuth,
    store: BillStore,
    options: SyncOptions = SyncOptions.incremental(SyncRangePreset.Month),
    onProgress: (SyncProgress) -> Unit = {},
): Result<SyncResult>
```

## fullSync

```kotlin
suspend fun fullSync(
    auth: EpayAuth,
    store: BillStore,
    options: SyncOptions = SyncOptions.full(SyncRangePreset.All),
    onProgress: (SyncProgress) -> Unit = {},
): Result<SyncResult>
```

## syncAccount

```kotlin
suspend fun syncAccount(
    auth: EpayAuth,
    store: BillStore,
    context: AccountContext,
    resolver: CaptchaResolver?,
    username: String = "",
    password: String = "",
    options: SyncOptions = SyncOptions.incremental(SyncRangePreset.Month),
    fullSync: Boolean = false,
    onProgress: (SyncProgress) -> Unit = {},
): Result<SyncResult>
```

`syncAccount(...)` 是 Android / 桌面端最常用的包装入口：

- 已登录时直接进入翻页同步
- 未登录且提供 `resolver` 时：自动识别验证码并提交登录
- 未登录且 `resolver == null` 时：抛 `ManualCaptchaRequiredException`
- `fullSync = true` 时按全量规则运行，并配合 `SyncOptions.full(...)`

注意：如果你走自动登录路径，**必须显式传入 `username/password`**。否则 challenge 提交时会因为缺少凭据而失败。

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

### 使用示例：单账号增量

```kotlin
val store = object : BillStore {
    private val known = mutableSetOf<String>()
    override fun contains(transactionNo: String) = transactionNo in known
    override fun merge(newBills: List<BillItem>) = newBills.forEach { known += it.transactionNo }
}

val result = incrementalSync(
    auth = epay,
    store = store,
    options = SyncOptions.incremental(SyncRangePreset.Month).copy(
        maxPages = 100,
        billType = BillType.ALL,
        earlyStopThreshold = 5
    ),
    onProgress = { p -> println("status=${p.status} pageFetched=${p.pagesFetched} totalNew=${p.totalNewCount}") }
).getOrThrow()

println("新增 ${result.newCount} 条, 早停=${result.stoppedEarly}")
```

### 使用示例：单账号完整状态机

```kotlin
val result = syncAccount(
    auth = epay,
    store = store,
    context = AccountContext(accountId = "10001", accountLabel = "本科"),
    resolver = myResolver, // 手动验证码场景可传 null
    username = "学号",
    password = "密码",
    options = SyncOptions.full(SyncRangePreset.HalfYear),
    fullSync = true,
    onProgress = { p -> println(p.toMessage()) }
).getOrThrow()
```

## 早停 vs 全量

- 增量同步：`earlyStopThreshold = 3..10`
- 全量同步：`SyncOptions.full(...)` 会把 `earlyStopThreshold` 拉到极大值，并开启 `clearBeforeMerge`

## 异常

- `auth.getBill` 失败 → `Result.failure`，并把已抓数据丢弃（`break`）
- 拉取途中被踢出（302）→ `Result.failure`，需要重新 `submitLogin` 后重试
- `resolver == null` 且需要重新登录 → 抛 `ManualCaptchaRequiredException`
- 手动验证码续传时，调用方必须复用**当前 challenge 对应的 `execution`**，不要重新取 challenge 后再提交旧验证码

## 与 BillParser 的关系

`incrementalSync` 内部 new 一个 `BillParser` 实例。宿主不需要预先解析，只需要提供 `BillStore` 即可。
