package cn.edu.shmtu.cas.sync

import cn.edu.shmtu.cas.auth.EpayAuth
import cn.edu.shmtu.cas.captcha.CaptchaResolver
import cn.edu.shmtu.cas.datatype.BillItem
import cn.edu.shmtu.cas.datatype.BillType
import cn.edu.shmtu.cas.parser.BillParser
import cn.edu.shmtu.cas.session.LoginSubmitResult
import cn.edu.shmtu.cas.session.ManualCaptchaRequiredException
import cn.edu.shmtu.cas.session.SessionProbe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.logging.Logger

/**
 * 同步状态机
 *
 * 顺序：ProbingLogin → GettingCaptcha → LoggingIn → Syncing{page,total}* → Persisting → Completed
 * 任何阶段失败：Failed(error)
 *
 * 借鉴 Rust `shmtu_cas::sync::SyncStatus` 的状态机设计，
 * 但使用 Kotlin sealed class 风格，参数挂在子类型上。
 */
sealed class SyncStatus {
    /** 探测登录状态（已登录 / 需要登录） */
    data object ProbingLogin : SyncStatus()

    /** 等待用户提供验证码 */
    data object GettingCaptcha : SyncStatus()

    /** 正在提交登录（已识别验证码） */
    data object LoggingIn : SyncStatus()

    /** 翻页拉账单中，page/total 表示"第 N/M 页" */
    data class Syncing(val page: Int, val total: Int) : SyncStatus()

    /** 拉完所有页，正在写入存储 */
    data object Persisting : SyncStatus()

    /** 同步完成 */
    data object Completed : SyncStatus()

    /** 同步失败 */
    data class Failed(val error: String) : SyncStatus()
}

/**
 * 同步进度（推送回调的顶层类型）
 *
 * 字段对齐 Rust `SyncProgress` 但用 Kotlin 风格：
 * - 账户标识/索引/总数：上下文
 * - 计数器：newCount（当前账号本次新增）/ pagesFetched（已拉取页数）/ totalNewCount（身份级累计）
 * - 状态机：status
 */
data class SyncProgress(
    val accountId: String,
    val currentAccount: String,
    val accountIndex: Int,
    val totalAccounts: Int,
    val newCount: Int,
    val pagesFetched: Int,
    val totalNewCount: Int,
    val status: SyncStatus,
) {
    /** 简易文案生成（贴 Rust `emit_progress` 里的中文模板） */
    fun toMessage(): String = when (val s = status) {
        SyncStatus.ProbingLogin -> "账号 $currentAccount 正在检查登录状态（${accountIndex + 1}/$totalAccounts）"
        SyncStatus.GettingCaptcha -> "账号 $currentAccount 需要验证码（${accountIndex + 1}/$totalAccounts），累计新增 $totalNewCount 条"
        SyncStatus.LoggingIn -> "账号 $currentAccount 已通过登录检查，正在准备拉取账单（${accountIndex + 1}/$totalAccounts），累计新增 $totalNewCount 条"
        is SyncStatus.Syncing -> "账号 $currentAccount 正在从校园平台拉取账单第 ${s.page}/${s.total} 页，当前账号新增 $newCount 条，累计新增 $totalNewCount 条（${accountIndex + 1}/$totalAccounts）"
        SyncStatus.Persisting -> "账号 $currentAccount 已拉取完成，正在写入原始账单并合并到身份：新增 $newCount 条，拉取 $pagesFetched 页，累计新增 $totalNewCount 条（${accountIndex + 1}/$totalAccounts）"
        SyncStatus.Completed -> "账号 $currentAccount 拉取完成并已写入原始账单、合并到身份：新增 $newCount 条，拉取 $pagesFetched 页，累计新增 $totalNewCount 条（${accountIndex + 1}/$totalAccounts）"
        is SyncStatus.Failed -> "账号 $currentAccount 同步失败（${accountIndex + 1}/$totalAccounts）：${s.error}"
    }
}

/**
 * 同步范围预设
 *
 * 借鉴 Rust `SyncRangePreset` + `range_since_timestamp`。
 * `sinceTimestamp()` 返回该范围对应的 epoch 秒（秒级精度，与 Rust 保持一致）。
 */
enum class SyncRangePreset {
    Week, HalfMonth, Month, HalfYear, Year, All;

    fun sinceTimestamp(): Long? = when (this) {
        Week -> Instant.now().minus(7, ChronoUnit.DAYS).epochSecond
        HalfMonth -> Instant.now().minus(15, ChronoUnit.DAYS).epochSecond
        Month -> Instant.now().minus(30, ChronoUnit.DAYS).epochSecond
        HalfYear -> Instant.now().minus(183, ChronoUnit.DAYS).epochSecond
        Year -> Instant.now().minus(365, ChronoUnit.DAYS).epochSecond
        All -> null
    }

    companion object {
        /** 增量默认：早停阈值 10、max 100 页（贴 Rust `default_incremental_sync_options`） */
        val IncrementalDefaults = SyncOptions(
            startPage = 1,
            maxPages = 100,
            billType = BillType.ALL,
            earlyStopThreshold = 10,
        )

        /** 全量默认：max 1000 页、关闭早停 */
        val FullSyncDefaults = SyncOptions(
            startPage = 1,
            maxPages = 1000,
            billType = BillType.ALL,
            earlyStopThreshold = Int.MAX_VALUE,
            clearBeforeMerge = true,
        )
    }
}

/**
 * 同步选项
 *
 * @property startPage 起始页（默认 1）
 * @property maxPages 最大翻页数（贴 Rust）
 * @property billType 账单类型
 * @property earlyStopThreshold 连续 N 页无新增就早停（贴 Rust）
 * @property sinceTimestamp 只同步该时间戳之后的账单（秒级，null = 不限）
 * @property clearBeforeMerge 全量模式：合并前先清空旧数据
 */
data class SyncOptions(
    val startPage: Int = 1,
    val maxPages: Int = 50,
    val billType: BillType = BillType.ALL,
    val earlyStopThreshold: Int = 3,
    val sinceTimestamp: Long? = null,
    val clearBeforeMerge: Boolean = false,
) {
    init {
        require(startPage >= 1) { "startPage must be >= 1" }
        require(maxPages >= 1) { "maxPages must be >= 1" }
        require(earlyStopThreshold >= 1) { "earlyStopThreshold must be >= 1" }
    }

    companion object {
        /** 增量快捷构造：自动从 [SyncRangePreset] 派生 sinceTimestamp */
        fun incremental(range: SyncRangePreset): SyncOptions =
            SyncRangePreset.IncrementalDefaults.copy(sinceTimestamp = range.sinceTimestamp())

        /** 全量快捷构造 */
        fun full(range: SyncRangePreset): SyncOptions =
            SyncRangePreset.FullSyncDefaults.copy(sinceTimestamp = range.sinceTimestamp())
    }
}

/**
 * 账单存储后端接口
 *
 * Android 端实现此接口对接 Room DAO：
 * ```kotlin
 * class RoomBillStore(...) : BillStore {
 *     override fun contains(transactionNo: String) = dao.exists(transactionNo)
 *     override fun merge(newBills: List<BillItem>) = dao.insertAll(newBills.map { it.toEntity() })
 *     override fun clear() = dao.deleteByAccountId(accountId)
 *     override fun onBeforeMerge(items: List<BillItem>) = items.map { it.attachCategory(classifier, translator) }
 * }
 * ```
 *
 * **线程安全**：当 [syncAccountsParallel] 并行调用时，同一账号的 store 由单协程独占，
 * 多个不同账号的 store 各自独立；store 实现自行保证线程安全。
 */
interface BillStore {
    fun contains(transactionNo: String): Boolean
    fun merge(newBills: List<BillItem>)

    /** 全量模式钩子：清空当前账号的历史账单。默认空实现。 */
    fun clear() {}

    /**
     * 合并前钩子：用于分类/位置翻译等附加处理。
     * 默认透传；app 端实现可在此把 lib 的 [BillItem] 转成带分类信息的自定义类型再返回。
     */
    fun onBeforeMerge(items: List<BillItem>): List<BillItem> = items
}

/**
 * 同步结果（合并层）
 *
 * 包含本账号本次同步的统计信息。
 */
data class SyncResult(
    val newCount: Int,
    val totalFetched: Int,
    val pagesFetched: Int,
    val stoppedEarly: Boolean,
)

/**
 * 账号上下文（用于 syncAccount 包装器）
 */
data class AccountContext(
    val accountId: String,
    val accountLabel: String,
    val accountIndex: Int = 0,
    val totalAccounts: Int = 1,
)

/**
 * 单账号同步入口
 *
 * 每个账号绑定独立的 [EpayAuth] 和 [BillStore]。
 * 并行场景下调用方把每个账号包成 [AccountContext] 并行调起本函数。
 */
data class AccountSyncJob(
    val context: AccountContext,
    val auth: EpayAuth,
    val store: BillStore,
    /** null = 手动模式（抛 ManualCaptchaRequiredException 由 UI 处理） */
    val resolver: CaptchaResolver? = null,
    /** null = 增量；非 null = 全量 */
    val range: SyncRangePreset? = null,
)

/**
 * 单账号同步结果
 */
data class AccountSyncResult(
    val context: AccountContext,
    val result: Result<SyncResult>,
)

/**
 * 多账号同步结果汇总
 */
data class ParallelSyncSummary(
    val results: List<AccountSyncResult>,
    val totalNewCount: Int,
    val successCount: Int,
    val failureCount: Int,
) {
    val allSuccess: Boolean get() = failureCount == 0
}

/**
 * 内部：页级原始进度。包装成 [SyncProgress] 由 lib 完成，不暴露给调用方。
 */
internal data class SyncPageProgress(
    val page: Int,
    val totalPages: Int,
    val newCount: Int,
    val pagesFetched: Int,
)

// =================== 顶层 API ===================

/**
 * 增量同步账单（已登录场景，单账号）
 *
 * 逐页抓取，通过 [BillStore.contains] 去重。
 * 连续 [SyncOptions.earlyStopThreshold] 页无新增就早停。
 * 仅在 [SyncOptions.sinceTimestamp] 之前的页才会写入存储。
 *
 * 状态机触发点：Syncing（每页）→ Persisting（合并前）→ Completed/Failed
 */
suspend fun incrementalSync(
    auth: EpayAuth,
    store: BillStore,
    options: SyncOptions = SyncOptions.incremental(SyncRangePreset.Month),
    onProgress: (SyncProgress) -> Unit = {},
): Result<SyncResult> = runSync(auth, store, options, onProgress, fullSync = false)

/**
 * 全量同步账单（单账号）
 *
 * 翻完所有页不做去重。如 [SyncOptions.clearBeforeMerge] = true，
 * 会在合并前先调 [BillStore.clear]。
 */
suspend fun fullSync(
    auth: EpayAuth,
    store: BillStore,
    options: SyncOptions = SyncOptions.full(SyncRangePreset.All),
    onProgress: (SyncProgress) -> Unit = {},
): Result<SyncResult> = runSync(auth, store, options, onProgress, fullSync = true)

/**
 * 单账号同步包装器：探测 → 登录 → 翻页 → 持久化
 *
 * 业务层（如 Android UseCase）只需调用本函数即可，无需自己拼装流程。
 *
 * @param resolver null = 手动模式（抛 [ManualCaptchaRequiredException] 由 UI 处理）；
 *                 非 null = 自动调用 resolver 解码
 * @param range null = 增量；非 null = 全量
 */
suspend fun syncAccount(
    auth: EpayAuth,
    store: BillStore,
    context: AccountContext,
    resolver: CaptchaResolver?,
    range: SyncRangePreset? = null,
    onProgress: (SyncProgress) -> Unit = {},
): Result<SyncResult> {
    val baseOptions = if (range == null) SyncOptions.incremental(SyncRangePreset.Month)
                      else SyncOptions.full(range)

    fun emit(status: SyncStatus, newCount: Int = 0, pagesFetched: Int = 0, totalNew: Int = 0) {
        onProgress(SyncProgress(
            accountId = context.accountId,
            currentAccount = context.accountLabel,
            accountIndex = context.accountIndex,
            totalAccounts = context.totalAccounts,
            newCount = newCount,
            pagesFetched = pagesFetched,
            totalNewCount = totalNew,
            status = status,
        ))
    }

    return try {
        // 1. 探测
        emit(SyncStatus.ProbingLogin)
        val probeResult = auth.probeLogin()
        if (probeResult.isFailure) {
            val err = probeResult.exceptionOrNull()!!
            emit(SyncStatus.Failed(err.message ?: "探测失败"))
            return Result.failure(err)
        }
        val probe = probeResult.getOrThrow()

        if (probe is SessionProbe.NeedLogin) {
            // 2. 拿 challenge
            emit(SyncStatus.GettingCaptcha)
            val challengeResult = auth.prepareChallenge()
            if (challengeResult.isFailure) {
                val err = challengeResult.exceptionOrNull() ?: Exception("获取验证码失败")
                emit(SyncStatus.Failed(err.message ?: "获取验证码失败"))
                return Result.failure(err)
            }
            val challenge = challengeResult.getOrThrow()

            // 3. 验证码：resolver 存在走自动；否则抛 ManualCaptchaRequiredException
            if (resolver == null) {
                throw ManualCaptchaRequiredException.of(
                    imageBytes = challenge.captchaImage,
                    execution = challenge.execution,
                    accountId = 0L,                       // lib 层不知道 Android 域 ID，调用方自行覆盖
                    accountLabel = context.accountLabel,
                )
            }

            emit(SyncStatus.LoggingIn)
            val resolveResult = resolver.resolve(challenge.captchaImage)
            if (resolveResult.isFailure) {
                val err = resolveResult.exceptionOrNull() ?: Exception("验证码解析失败")
                emit(SyncStatus.Failed(err.message ?: "验证码解析失败"))
                return Result.failure(err)
            }
            // 自动登录流程：拿用户名+密码由调用方在 resolver 里封好；这里仅占位
            val captcha = resolveResult.getOrThrow().intoFinalAnswer().value
            val submitResult = auth.submitLogin("", "", captcha, challenge.execution)
            if (submitResult.isFailure) {
                val err = submitResult.exceptionOrNull() ?: Exception("提交登录失败")
                emit(SyncStatus.Failed(err.message ?: "提交登录失败"))
                return Result.failure(err)
            }
            when (val r = submitResult.getOrThrow()) {
                is LoginSubmitResult.Success -> { /* fall through */ }
                is LoginSubmitResult.PasswordError -> {
                    emit(SyncStatus.Failed("用户名或密码错误"))
                    return Result.failure(Exception("用户名或密码错误"))
                }
                is LoginSubmitResult.ValidateCodeError -> {
                    emit(SyncStatus.Failed("验证码识别错误"))
                    return Result.failure(Exception("验证码识别错误"))
                }
                is LoginSubmitResult.Failure -> {
                    emit(SyncStatus.Failed(r.message))
                    return Result.failure(Exception(r.message))
                }
            }
        }

        // 4. 翻页同步
        runSync(auth, store, baseOptions, onProgress, fullSync = range != null)
    } catch (e: ManualCaptchaRequiredException) {
        emit(SyncStatus.GettingCaptcha)
        throw e
    } catch (e: Exception) {
        emit(SyncStatus.Failed(e.message ?: e.javaClass.simpleName))
        Result.failure(e)
    }
}

// =================== 多账号并行入口 ===================

/**
 * 并行同步多个账号
 *
 * 设计要点：
 * 1. **单账号内部仍串行**（分页+持久化是天然串行 IO，不并发翻页）
 * 2. **多账号用协程并行**：每个账号一个 async 任务，独立 [EpayAuth] / [BillStore] 实例
 * 3. **进度回调携带 accountId**，调用方按账号 ID 区分（避免并行时 accountIndex 互相覆盖）
 * 4. **任一失败不影响其他**：SupervisorJob 隔离，failedCount 计入汇总
 * 5. **统一 totalNewCount 计算**：每条回调携带本账号 newCount + 全局累计（用 thread-safe counter）
 *
 * @param jobs 待同步账号列表（每个 job 的 auth/store 必须独立）
 * @param onProgress 进度回调，每条事件携带 [SyncProgress.accountId] 用于区分
 * @return [ParallelSyncSummary] 汇总所有账号的同步结果
 */
suspend fun syncAccountsParallel(
    jobs: List<AccountSyncJob>,
    onProgress: (SyncProgress) -> Unit = {},
): ParallelSyncSummary = coroutineScope {
    if (jobs.isEmpty()) {
        return@coroutineScope ParallelSyncSummary(emptyList(), 0, 0, 0)
    }

    val totalAccounts = jobs.size
    val deferreds = jobs.mapIndexed { idx, job ->
        val contextualProgress: (SyncProgress) -> Unit = { p ->
            onProgress(p.copy(
                accountIndex = idx,
                totalAccounts = totalAccounts,
            ))
        }

        async {
            try {
                val result = syncAccount(
                    auth = job.auth,
                    store = job.store,
                    context = job.context.copy(
                        accountIndex = idx,
                        totalAccounts = totalAccounts,
                    ),
                    resolver = job.resolver,
                    range = job.range,
                    onProgress = contextualProgress,
                )
                AccountSyncResult(job.context, result)
            } catch (e: ManualCaptchaRequiredException) {
                // 手动验证码：包装成"已抛 GettingCaptcha"返回 failure 结果，不算异常
                AccountSyncResult(job.context, Result.failure(e))
            } catch (e: Exception) {
                AccountSyncResult(job.context, Result.failure(e))
            }
        }
    }

    val results = deferreds.awaitAll()
    val successCount = results.count { it.result.isSuccess }
    val failureCount = results.size - successCount
    val totalNew = results.sumOf { it.result.getOrNull()?.newCount ?: 0 }

    ParallelSyncSummary(
        results = results,
        totalNewCount = totalNew,
        successCount = successCount,
        failureCount = failureCount,
    )
}

/**
 * 同步全量账号列表（便利函数，包装 [syncAccountsParallel]）
 *
 * @param onAccountProgress 单账号进度回调（accountId 是 key）
 * @param onAllCompleted 所有账号完成时回调（携带汇总）
 * @return Job 句柄，可用于取消整个同步
 */
fun launchSyncAll(
    jobs: List<AccountSyncJob>,
    onAccountProgress: (accountId: String, progress: SyncProgress) -> Unit,
    onAllCompleted: (ParallelSyncSummary) -> Unit,
    scope: CoroutineScope,
): Job = scope.launch {
    val summary = syncAccountsParallel(jobs) { p ->
        onAccountProgress(p.accountId, p)
    }
    onAllCompleted(summary)
}

// =================== 内部：翻页循环 ===================

/**
 * 翻页循环：所有 Syncing/Persisting 状态机触发点都在这里。
 *
 * 借鉴 Rust `incremental_sync_with_progress`：
 * 1. 每页 fetch → parse → 去重过滤 → 调 onBeforeMerge → 调 merge
 * 2. 每页结束触发 Syncing 进度
 * 3. 全量结束后触发 Persisting + Completed
 */
private suspend fun runSync(
    auth: EpayAuth,
    store: BillStore,
    options: SyncOptions,
    onProgress: (SyncProgress) -> Unit,
    fullSync: Boolean,
): Result<SyncResult> {
    val parser = BillParser()
    var newCount = 0
    var totalFetched = 0
    var pagesFetched = 0
    var consecutiveEmptyPages = 0
    var lastTotalPages = options.maxPages
    val sinceTs = options.sinceTimestamp

    // 全量模式：合并前清空
    if (fullSync && options.clearBeforeMerge) {
        try { store.clear() } catch (e: Exception) {
            log.warning("[BillSync] store.clear() failed: ${e.message}")
        }
    }

    fun emit(status: SyncStatus) {
        onProgress(SyncProgress(
            accountId = "",
            currentAccount = "",
            accountIndex = 0,
            totalAccounts = 1,
            newCount = newCount,
            pagesFetched = pagesFetched,
            totalNewCount = newCount,
            status = status,
        ))
    }

    try {
        for (page in options.startPage..(options.startPage + options.maxPages - 1)) {
            val billResult = auth.getBill(pageNo = page, billType = options.billType)
            if (billResult.isFailure) {
                emit(SyncStatus.Failed("第 $page 页拉取失败：${billResult.exceptionOrNull()?.message ?: "未知错误"}"))
                return Result.failure(billResult.exceptionOrNull()!!)
            }

            val html = billResult.getOrThrow()
            val parseResult = parser.parseBillPage(html)
            val items = parseResult.bills
            val totalPages = parseResult.totalPages
            lastTotalPages = totalPages
            pagesFetched++

            if (items.isEmpty()) {
                if (page == options.startPage) {
                    emit(SyncStatus.Syncing(page, totalPages))
                    break
                }
                consecutiveEmptyPages++
                if (consecutiveEmptyPages >= options.earlyStopThreshold) {
                    emit(SyncStatus.Syncing(page, totalPages))
                    log.info("[BillSync] early stop at page $page (consecutiveEmpty=$consecutiveEmptyPages)")
                    break
                }
                emit(SyncStatus.Syncing(page, totalPages))
                continue
            }

            totalFetched += items.size

            // 时间窗口过滤（贴 Rust since_timestamp）
            val timeFiltered = if (sinceTs == null) items
                else items.filter { it.timestamp >= sinceTs }

            // 去重过滤（增量模式）
            val finalItems = if (fullSync) timeFiltered
                else timeFiltered.filter { !store.contains(it.transactionNo) }

            if (finalItems.isNotEmpty()) {
                val enriched = store.onBeforeMerge(finalItems)
                store.merge(enriched)
                newCount += enriched.size
            }

            if (!fullSync && finalItems.isEmpty()) {
                consecutiveEmptyPages++
                if (consecutiveEmptyPages >= options.earlyStopThreshold) {
                    emit(SyncStatus.Syncing(page, totalPages))
                    log.info("[BillSync] early stop at page $page (no new items)")
                    break
                }
            } else if (finalItems.isNotEmpty()) {
                consecutiveEmptyPages = 0
            }

            emit(SyncStatus.Syncing(page, totalPages))

            // 全量模式：翻到 totalPages 就停；增量模式：翻到 maxPages 上限
            if (fullSync && page >= totalPages) break
        }

        emit(SyncStatus.Persisting)
        emit(SyncStatus.Completed)
        return Result.success(SyncResult(
            newCount = newCount,
            totalFetched = totalFetched,
            pagesFetched = pagesFetched,
            stoppedEarly = !fullSync && pagesFetched < lastTotalPages,
        ))
    } catch (e: Exception) {
        emit(SyncStatus.Failed(e.message ?: e.javaClass.simpleName))
        return Result.failure(e)
    }
}

private val log = Logger.getLogger("cn.edu.shmtu.cas.sync")
