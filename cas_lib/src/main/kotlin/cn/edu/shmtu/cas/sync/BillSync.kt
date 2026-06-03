package cn.edu.shmtu.cas.sync

import cn.edu.shmtu.cas.auth.EpayAuth
import cn.edu.shmtu.cas.datatype.BillItem
import cn.edu.shmtu.cas.datatype.BillType
import cn.edu.shmtu.cas.parser.BillParser
import java.util.logging.Logger

/**
 * 账单存储后端接口
 *
 * Android 端实现此接口对接 Room DAO：
 * ```kotlin
 * class RoomBillStore(private val dao: BillDao) : BillStore {
 *     override fun contains(transactionNo: String) = dao.exists(transactionNo)
 *     override fun merge(newBills: List<BillItem>) = dao.insertAll(newBills.map { it.toEntity() })
 * }
 * ```
 */
interface BillStore {
    fun contains(transactionNo: String): Boolean
    fun merge(newBills: List<BillItem>)
}

/**
 * 同步选项
 */
data class SyncOptions(
    val startPage: Int = 1,
    val maxPages: Int = 50,
    val billType: BillType = BillType.ALL,
    val earlyStopThreshold: Int = 3
)

/**
 * 同步结果
 */
data class SyncResult(
    val newCount: Int,
    val totalFetched: Int,
    val stoppedEarly: Boolean
)

/**
 * 同步进度回调（Android UI 进度条）
 */
data class SyncProgress(val page: Int, val newCount: Int, val totalFetched: Int)

/**
 * 增量同步账单
 *
 * 逐页抓取，通过 [BillStore.contains] 去重，
 * 连续 [SyncOptions.earlyStopThreshold] 页无新增时早停。
 *
 * 对齐 Rust 版本的 incremental_sync + incremental_sync_with_progress。
 */
suspend fun incrementalSync(
    auth: EpayAuth,
    store: BillStore,
    options: SyncOptions = SyncOptions(),
    onProgress: (suspend (SyncProgress) -> Unit)? = null
): Result<SyncResult> {
    val parser = BillParser()
    var newCount = 0
    var totalFetched = 0
    var consecutiveEmptyPages = 0

    for (page in options.startPage..(options.startPage + options.maxPages - 1)) {
        val billResult = auth.getBill(pageNo = page, billType = options.billType)
        if (billResult.isFailure) {
            log.warning("[BillSync] getBill page $page failed: ${billResult.exceptionOrNull()?.message}")
            break
        }

        val items = parser.parseBillItems(billResult.getOrThrow())
        if (items.isEmpty()) {
            // 第一页就空 = 没数据
            if (page == options.startPage) {
                onProgress?.invoke(SyncProgress(page, newCount, totalFetched))
                break
            }
            consecutiveEmptyPages++
            if (consecutiveEmptyPages >= options.earlyStopThreshold) {
                onProgress?.invoke(SyncProgress(page, newCount, totalFetched))
                break
            }
            onProgress?.invoke(SyncProgress(page, newCount, totalFetched))
            continue
        }

        totalFetched += items.size

        val freshItems = items.filter { !store.contains(it.transactionNo) }
        if (freshItems.isEmpty()) {
            consecutiveEmptyPages++
            if (consecutiveEmptyPages >= options.earlyStopThreshold) {
                log.info("[BillSync] early stop at page $page")
                onProgress?.invoke(SyncProgress(page, newCount, totalFetched))
                return Result.success(SyncResult(newCount, totalFetched, stoppedEarly = true))
            }
        } else {
            consecutiveEmptyPages = 0
            newCount += freshItems.size
            store.merge(freshItems)
        }

        onProgress?.invoke(SyncProgress(page, newCount, totalFetched))
    }

    return Result.success(SyncResult(newCount, totalFetched, stoppedEarly = false))
}

private val log = Logger.getLogger("cn.edu.shmtu.cas.sync")
