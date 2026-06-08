package cn.edu.shmtu.cas.auth

import cn.edu.shmtu.cas.auth.common.CasAuth
import cn.edu.shmtu.cas.auth.common.CookieManager
import cn.edu.shmtu.cas.captcha.Captcha
import cn.edu.shmtu.cas.captcha.CaptchaResolver
import cn.edu.shmtu.cas.datatype.BillType
import cn.edu.shmtu.cas.session.LoginChallenge
import cn.edu.shmtu.cas.session.LoginSubmitResult
import cn.edu.shmtu.cas.session.SessionProbe
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.logging.Logger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * 一卡通充值平台认证
 *
 * 对齐 Rust 版本的 EpayAuth，三阶段设计 + Cookie 持久化 + TGC 复用
 */
class EpayAuth(
    private val captchaResolver: CaptchaResolver? = null
) {

    private companion object {
        val log = Logger.getLogger(EpayAuth::class.java.name)
        const val EPAY_BILL_URL = "https://ecard.shmtu.edu.cn/epay/consume/query"
        const val EPAY_PERSON_ACCOUNT_URL = "https://ecard.shmtu.edu.cn/epay/personaccount/index"
        const val VALIDATE_CODE_ERROR = 401
        const val PASSWORD_ERROR = 402
    }

    private val cookies = CookieManager()
    private val client: OkHttpClient = CasAuth.createClient()
    private var loginUrl: String? = null

    // ========== 会话持久化 ==========

    /**
     * 从 JSON 恢复 cookies（Android 端可对接 EncryptedSharedPreferences）
     */
    fun restoreSession(json: String): Result<Unit> = cookies.restore(json)

    /**
     * 导出当前 cookies 为 JSON
     */
    fun extractSession(): String = cookies.extract()

    fun getCookieString(): String = cookies.get()

    /**
     * 尝试复用 TGC（Ticket Granting Cookie）
     *
     * 对齐 Rust 版本的 try_reuse_tgc。
     */
    suspend fun tryReuseTgc(): Result<Boolean> = suspendCoroutine { cont ->
        val url = loginUrl ?: run {
            cont.resumeWithException(Exception("尚未探测登录状态，请先调用 probeLogin"))
            return@suspendCoroutine
        }

        try {
            val (execution, _) = CasAuth.getExecution(url, cookies.get())
            if (execution.isBlank()) {
                log.info("[EpayAuth] tryReuseTgc: TGC valid, CAS auto-authenticated")
                cont.resume(Result.success(true))
            } else {
                log.info("[EpayAuth] tryReuseTgc: TGC invalid, need manual login")
                cont.resume(Result.success(false))
            }
        } catch (e: Exception) {
            cont.resume(Result.failure(e))
        }
    }

    // ========== 探测 ==========

    suspend fun probeLogin(): Result<SessionProbe> = suspendCoroutine { cont ->
        val url = "$EPAY_BILL_URL?pageNo=1&tabNo=1"

        val request = Request.Builder()
            .url(url)
            .apply { if (!cookies.isEmpty()) addHeader("Cookie", cookies.get()) }
            .get().build()

        try {
            val response = client.newCall(request).execute()
            cookies.addAllFromSetCookieHeaders(response.headers("Set-Cookie"))

            when (response.code) {
                200 -> {
                    log.info("[EpayAuth] probeLogin: already logged in")
                    cont.resume(Result.success(SessionProbe.AlreadyLoggedIn))
                }
                302 -> {
                    val location = response.header("Location") ?: ""
                    if (location.isEmpty()) {
                        cont.resumeWithException(Exception("重定向URL为空"))
                    } else {
                        this.loginUrl = location
                        cont.resume(Result.success(SessionProbe.NeedLogin(location)))
                    }
                }
                else -> cont.resumeWithException(Exception("探测登录状态失败，状态码: ${response.code}"))
            }
        } catch (e: Exception) {
            cont.resume(Result.failure(e))
        }
    }

    // ========== Challenge ==========

    suspend fun prepareChallenge(): Result<LoginChallenge> = suspendCoroutine { cont ->
        val url = loginUrl ?: run {
            cont.resumeWithException(Exception("尚未探测登录状态，请先调用 probeLogin"))
            return@suspendCoroutine
        }

        val (execution, executionSessionId) = CasAuth.getExecution(url, cookies.get())
        if (execution.isBlank()) {
            cont.resumeWithException(Exception("获取 execution 失败"))
            return@suspendCoroutine
        }

        val captchaResult = Captcha.getImageDataFromUrlUsingGet(executionSessionId)
        if (captchaResult == null || captchaResult.first == null) {
            cont.resumeWithException(Exception("获取验证码图片失败"))
            return@suspendCoroutine
        }

        val imageData = captchaResult.first!!
        val captchaSessionId = captchaResult.second

        if (captchaSessionId.isNotBlank()) {
            cookies.addFromSetCookie(captchaSessionId)
        }

        log.info("[EpayAuth] prepareChallenge: execution=${execution.take(30)}..., imageSize=${imageData.size}")
        cont.resume(Result.success(LoginChallenge(execution, imageData)))
    }

    // ========== 提交登录（手动路径） ==========

    suspend fun submitLogin(
        username: String, password: String,
        validateCode: String, execution: String
    ): Result<LoginSubmitResult> = suspendCoroutine { cont ->
        val url = loginUrl ?: run {
            cont.resumeWithException(Exception("尚未探测登录状态，请先调用 probeLogin"))
            return@suspendCoroutine
        }

        val result = CasAuth.casLogin(url, username, password, validateCode, execution, cookies.get())

        when {
            result.first == 302 -> {
                if (result.third.isNotBlank()) cookies.restore(result.third)
                val redirectResult = CasAuth.casRedirect(result.second, cookies.get())
                if (redirectResult.first == 302) {
                    if (redirectResult.third.isNotBlank()) cookies.restore(redirectResult.third)
                    log.info("[EpayAuth] submitLogin: success")
                    cont.resume(Result.success(LoginSubmitResult.Success))
                } else {
                    cont.resume(Result.success(LoginSubmitResult.Failure("重定向失败")))
                }
            }
            result.first == VALIDATE_CODE_ERROR -> cont.resume(Result.success(LoginSubmitResult.ValidateCodeError))
            result.first == PASSWORD_ERROR -> cont.resume(Result.success(LoginSubmitResult.PasswordError))
            else -> cont.resume(Result.success(LoginSubmitResult.Failure(result.third)))
        }
    }

    // ========== 一键登录（自动路径） ==========

    suspend fun submitLogin(
        username: String, password: String, maxRetries: Int = 5
    ): Result<LoginSubmitResult> {
        val resolver = captchaResolver
            ?: return Result.failure(IllegalStateException("未设置 CaptchaResolver"))

        // 先尝试 TGC 复用
        val tgcResult = tryReuseTgc()
        if (tgcResult.isSuccess && tgcResult.getOrThrow()) {
            val testResult = testLoginStatus()
            if (testResult.isSuccess && testResult.getOrThrow()) {
                log.info("[EpayAuth] submitLogin: TGC reuse success")
                return Result.success(LoginSubmitResult.Success)
            }
        }

        var lastResult: Result<LoginSubmitResult>? = null

        for (attempt in 1..maxRetries) {
            log.info("[EpayAuth] submitLogin: attempt $attempt/$maxRetries")

            val challengeResult = prepareChallenge()
            if (challengeResult.isFailure) {
                lastResult = Result.failure(challengeResult.exceptionOrNull() ?: Exception("获取 challenge 失败"))
                continue
            }
            val challenge = challengeResult.getOrThrow()

            val resolveResult = resolver.resolve(challenge.captchaImage)
            if (resolveResult.isFailure) {
                lastResult = Result.failure(resolveResult.exceptionOrNull() ?: Exception("验证码解析失败"))
                continue
            }
            val finalAnswer = resolveResult.getOrThrow().intoFinalAnswer()

            val submitResult = submitLogin(username, password, finalAnswer.value, challenge.execution)
            if (submitResult.isFailure) { lastResult = submitResult; continue }

            when (val r = submitResult.getOrThrow()) {
                is LoginSubmitResult.Success -> return Result.success(r)
                is LoginSubmitResult.ValidateCodeError -> { lastResult = Result.success(r); continue }
                is LoginSubmitResult.PasswordError -> return Result.success(r)
                is LoginSubmitResult.Failure -> { lastResult = Result.success(r); continue }
            }
        }

        return lastResult ?: Result.failure(Exception("登录重试次数耗尽"))
    }

    // ========== 业务方法 ==========

    suspend fun testLoginStatus(): Result<Boolean> = suspendCoroutine { cont ->
        val request = Request.Builder()
            .url("$EPAY_BILL_URL?pageNo=1&tabNo=1")
            .apply { if (!cookies.isEmpty()) addHeader("Cookie", cookies.get()) }
            .get().build()

        try {
            val response = client.newCall(request).execute()
            cookies.addAllFromSetCookieHeaders(response.headers("Set-Cookie"))
            when (response.code) {
                200 -> cont.resume(Result.success(true))
                302 -> {
                    response.header("Location")?.let { this.loginUrl = it }
                    cont.resume(Result.success(false))
                }
                else -> cont.resumeWithException(Exception("测试登录状态失败，状态码: ${response.code}"))
            }
        } catch (e: Exception) { cont.resume(Result.failure(e)) }
    }

    /**
     * 获取账单页面 HTML（使用 BillType 枚举）
     */
    suspend fun getBill(pageNo: Int = 1, billType: BillType = BillType.ALL): Result<String> {
        return getBill(pageNo = pageNo, tabNo = billType.tabNo)
    }

    suspend fun getBill(pageNo: Int = 1, tabNo: String = "1"): Result<String> = suspendCoroutine { cont ->
        val request = Request.Builder()
            .url("$EPAY_BILL_URL?pageNo=$pageNo&tabNo=$tabNo")
            .apply { if (!cookies.isEmpty()) addHeader("Cookie", cookies.get()) }
            .get().build()

        try {
            val response = client.newCall(request).execute()
            cookies.addAllFromSetCookieHeaders(response.headers("Set-Cookie"))
            when (response.code) {
                200 -> cont.resume(Result.success(response.body.string()))
                302 -> cont.resumeWithException(Exception("未登录，需要重新登录"))
                else -> cont.resumeWithException(Exception("获取账单失败，状态码: ${response.code}"))
            }
        } catch (e: Exception) { cont.resume(Result.failure(e)) }
    }

    /**
     * 翻页获取全部账单 HTML
     */
    suspend fun getAllBills(
        billType: BillType = BillType.ALL,
        startPage: Int = 1,
        maxPages: Int = 50
    ): Result<List<String>> {
        val pages = mutableListOf<String>()
        for (page in startPage..(startPage + maxPages - 1)) {
            val result = getBill(pageNo = page, billType = billType)
            if (result.isFailure) {
                if (pages.isEmpty()) return result.map { listOf(it) }
                break
            }
            val html = result.getOrThrow()
            if (html.isBlank() || !html.contains("aazone")) break
            pages.add(html)
        }
        return Result.success(pages)
    }

    // ========== 个人账户页 ==========

    /**
     * 访问 `/epay/personaccount/index` 页面。
     *
     * 经验证,无需 Referer 也能正常获取完整页面内容;这里只发送 epay 的会话 Cookie
     * (依赖登录态)即可。需要已登录的 epay cookies。
     *
     * @return 成功: Result.success(html 字符串);失败: Result.failure(异常)
     */
    suspend fun getPersonAccountHtml(): Result<String> = suspendCoroutine { cont ->
        val request = Request.Builder()
            .url(EPAY_PERSON_ACCOUNT_URL)
            .apply { if (!cookies.isEmpty()) addHeader("Cookie", cookies.get()) }
            .get()
            .build()

        try {
            val response = client.newCall(request).execute()
            cookies.addAllFromSetCookieHeaders(response.headers("Set-Cookie"))
            when (response.code) {
                200 -> cont.resume(Result.success(response.body.string()))
                302 -> cont.resumeWithException(Exception("未登录或会话已过期，需要重新登录 (302 -> ${response.header("Location")})"))
                else -> cont.resumeWithException(Exception("获取个人账户页失败，状态码: ${response.code}"))
            }
        } catch (e: Exception) { cont.resume(Result.failure(e)) }
    }
}
