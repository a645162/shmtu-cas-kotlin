package cn.edu.shmtu.cas.auth

import cn.edu.shmtu.cas.auth.common.CasAuth
import cn.edu.shmtu.cas.auth.common.CookieManager
import cn.edu.shmtu.cas.captcha.Captcha
import cn.edu.shmtu.cas.captcha.CaptchaResolver
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
 * 微信认证（热水平台）
 *
 * 对齐 Rust 版本的 WechatAuth，三阶段设计 + Cookie 持久化
 */
class WechatAuth(
    private val captchaResolver: CaptchaResolver? = null
) {

    private companion object {
        val log = Logger.getLogger(WechatAuth::class.java.name)
        const val HOT_WATER_URL = "http://hqzx.shmtu.edu.cn/cellphone/getHotWater"
        const val VALIDATE_CODE_ERROR = 401
        const val PASSWORD_ERROR = 402
    }

    private val cookies = CookieManager()
    private var loginWUrl: String? = null
    private val client: OkHttpClient = CasAuth.createClient()

    // ========== 会话持久化 ==========

    fun restoreSession(json: String): Result<Unit> = cookies.restore(json)

    fun extractSession(): String = cookies.extract()

    fun getCookieString(): String = cookies.get()

    // ========== 探测 ==========

    suspend fun probeLogin(): Result<SessionProbe> = suspendCoroutine { cont ->
        val request = Request.Builder()
            .url(HOT_WATER_URL)
            .apply { if (!cookies.isEmpty()) addHeader("Cookie", cookies.get()) }
            .get().build()

        try {
            val response = client.newCall(request).execute()
            cookies.addAllFromSetCookieHeaders(response.headers("Set-Cookie"))

            when (response.code) {
                200 -> cont.resume(Result.success(SessionProbe.AlreadyLoggedIn))
                302 -> {
                    val location = response.header("Location") ?: ""
                    if (location.isNotBlank()) {
                        this.loginWUrl = location
                        cont.resume(Result.success(SessionProbe.NeedLogin(location)))
                    } else {
                        cont.resumeWithException(Exception("重定向URL为空"))
                    }
                }
                else -> cont.resumeWithException(Exception("探测登录状态失败，状态码: ${response.code}"))
            }
        } catch (e: Exception) {
            cont.resume(Result.failure(e))
        }
    }

    private fun getWEngineNewTicketSync(): Triple<Int, String, String> {
        val url = loginWUrl ?: return Triple(0, "", "")
        val request = Request.Builder().url(url).get().build()
        val response = client.newCall(request).execute()
        cookies.addAllFromSetCookieHeaders(response.headers("Set-Cookie"))

        return if (response.code == 302) {
            Triple(response.code, response.header("Location") ?: "", cookies.get())
        } else {
            Triple(response.code, "", "")
        }
    }

    // ========== Challenge ==========

    suspend fun prepareChallenge(): Result<LoginChallenge> = suspendCoroutine { cont ->
        val url = loginWUrl ?: run {
            cont.resumeWithException(Exception("尚未探测登录状态"))
            return@suspendCoroutine
        }

        val ticketResult = getWEngineNewTicketSync()
        if (ticketResult.first != 302) {
            cont.resumeWithException(Exception("获取 wengine ticket 失败"))
            return@suspendCoroutine
        }

        val casLoginUrl = ticketResult.second
        val cookie = ticketResult.third

        val (execution, executionSessionId) = CasAuth.getExecution(casLoginUrl, cookie)
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

        cont.resume(Result.success(LoginChallenge(execution, imageData)))
    }

    // ========== 提交登录（手动路径） ==========

    suspend fun submitLogin(
        username: String, password: String,
        validateCode: String, execution: String
    ): Result<LoginSubmitResult> = suspendCoroutine { cont ->
        loginWUrl ?: run {
            cont.resumeWithException(Exception("尚未探测登录状态"))
            return@suspendCoroutine
        }

        val ticketResult = getWEngineNewTicketSync()
        if (ticketResult.first != 302) {
            cont.resumeWithException(Exception("获取 wengine ticket 失败"))
            return@suspendCoroutine
        }

        val loginResult = CasAuth.casLogin(ticketResult.second, username, password, validateCode, execution, ticketResult.third)

        when {
            loginResult.first == 302 -> {
                if (loginResult.third.isNotBlank()) cookies.restore(loginResult.third)
                val wechatRedirectUrl = "${loginResult.second}&from=$HOT_WATER_URL"
                val redirectResponse = CasAuth.casRedirect(wechatRedirectUrl, cookies.get())
                if (redirectResponse.first == 302) {
                    if (redirectResponse.third.isNotBlank()) cookies.restore(redirectResponse.third)
                    cont.resume(Result.success(LoginSubmitResult.Success))
                } else {
                    cont.resume(Result.success(LoginSubmitResult.Failure("重定向失败")))
                }
            }
            loginResult.first == VALIDATE_CODE_ERROR -> cont.resume(Result.success(LoginSubmitResult.ValidateCodeError))
            loginResult.first == PASSWORD_ERROR -> cont.resume(Result.success(LoginSubmitResult.PasswordError))
            else -> cont.resume(Result.success(LoginSubmitResult.Failure(loginResult.third)))
        }
    }

    // ========== 一键登录（自动路径） ==========

    suspend fun submitLogin(
        username: String, password: String,
        maxRetries: Int = 5
    ): Result<LoginSubmitResult> {
        val resolver = captchaResolver
            ?: return Result.failure(IllegalStateException("未设置 CaptchaResolver"))

        var lastResult: Result<LoginSubmitResult>? = null

        for (attempt in 1..maxRetries) {
            log.info("[WechatAuth] submitLogin: attempt $attempt/$maxRetries")

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
            .url(HOT_WATER_URL)
            .apply { if (!cookies.isEmpty()) addHeader("Cookie", cookies.get()) }
            .get().build()

        try {
            val response = client.newCall(request).execute()
            cookies.addAllFromSetCookieHeaders(response.headers("Set-Cookie"))
            when (response.code) {
                200 -> cont.resume(Result.success(true))
                302 -> {
                    response.header("Location")?.let { this.loginWUrl = it }
                    cont.resume(Result.success(false))
                }
                else -> cont.resumeWithException(Exception("测试登录状态失败，状态码: ${response.code}"))
            }
        } catch (e: Exception) { cont.resume(Result.failure(e)) }
    }

    suspend fun getHotWater(): Result<String> = suspendCoroutine { cont ->
        val request = Request.Builder()
            .url(HOT_WATER_URL)
            .apply { if (!cookies.isEmpty()) addHeader("Cookie", cookies.get()) }
            .get().build()

        try {
            val response = client.newCall(request).execute()
            cookies.addAllFromSetCookieHeaders(response.headers("Set-Cookie"))
            when (response.code) {
                200 -> cont.resume(Result.success(response.body.string()))
                302 -> {
                    response.header("Location")?.let { this.loginWUrl = it }
                    cont.resumeWithException(Exception("未登录，需要重新登录"))
                }
                else -> cont.resumeWithException(Exception("获取热水数据失败，状态码: ${response.code}"))
            }
        } catch (e: Exception) { cont.resume(Result.failure(e)) }
    }
}
