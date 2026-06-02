package cn.edu.shmtu.cas.auth

import cn.edu.shmtu.cas.auth.common.CasAuth
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
 * 对齐 Rust 版本的 WechatAuth，三阶段设计：
 * 1. probeLogin() - 探测热水平台登录状态
 * 2. prepareChallenge() - 获取 wengine ticket + execution + 验证码图片
 * 3. submitLogin(user, pwd, code, execution) - 提交登录（手动路径）
 * 4. submitLogin(user, pwd) - 一键登录（自动路径）
 *
 * @param captchaResolver 验证码解析策略（可选）
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

    private var savedCookie: String = ""
    private var loginWUrl: String? = null
    private val client: OkHttpClient = CasAuth.createClient()

    suspend fun probeLogin(): Result<SessionProbe> = suspendCoroutine { cont ->
        val request = Request.Builder()
            .url(HOT_WATER_URL)
            .apply {
                if (savedCookie.isNotBlank()) addHeader("Cookie", savedCookie)
            }
            .get()
            .build()

        try {
            val response = client.newCall(request).execute()
            savedCookie = CasAuth.mergeCookies(savedCookie, response.headers("Set-Cookie"))

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
        savedCookie = CasAuth.mergeCookies(savedCookie, response.headers("Set-Cookie"))

        return if (response.code == 302) {
            Triple(response.code, response.header("Location") ?: "", savedCookie)
        } else {
            Triple(response.code, "", "")
        }
    }

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
            savedCookie = CasAuth.mergeCookies(savedCookie, listOf(captchaSessionId))
        }

        cont.resume(Result.success(LoginChallenge(execution, imageData)))
    }

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
                if (loginResult.third.isNotBlank()) savedCookie = loginResult.third
                val wechatRedirectUrl = "${loginResult.second}&from=$HOT_WATER_URL"
                val redirectResponse = CasAuth.casRedirect(wechatRedirectUrl, savedCookie)
                if (redirectResponse.first == 302) {
                    if (redirectResponse.third.isNotBlank()) savedCookie = redirectResponse.third
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

    suspend fun submitLogin(
        username: String, password: String,
        maxRetries: Int = 5
    ): Result<LoginSubmitResult> {
        val resolver = captchaResolver
            ?: return Result.failure(IllegalStateException("未设置 CaptchaResolver"))

        var lastResult: Result<LoginSubmitResult>? = null

        for (attempt in 1..maxRetries) {
            val challengeResult = prepareChallenge()
            if (challengeResult.isFailure) { lastResult = Result.failure(challengeResult.exceptionOrNull()!!); continue }
            val challenge = challengeResult.getOrThrow()

            val resolveResult = resolver.resolve(challenge.captchaImage)
            if (resolveResult.isFailure) { lastResult = Result.failure(resolveResult.exceptionOrNull()!!); continue }

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

    suspend fun testLoginStatus(): Result<Boolean> = suspendCoroutine { cont ->
        val request = Request.Builder()
            .url(HOT_WATER_URL)
            .apply { if (savedCookie.isNotBlank()) addHeader("Cookie", savedCookie) }
            .get()
            .build()

        try {
            val response = client.newCall(request).execute()
            savedCookie = CasAuth.mergeCookies(savedCookie, response.headers("Set-Cookie"))
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
            .apply { if (savedCookie.isNotBlank()) addHeader("Cookie", savedCookie) }
            .get()
            .build()

        try {
            val response = client.newCall(request).execute()
            savedCookie = CasAuth.mergeCookies(savedCookie, response.headers("Set-Cookie"))
            when (response.code) {
                200 -> cont.resume(Result.success(response.body?.string() ?: ""))
                302 -> {
                    response.header("Location")?.let { this.loginWUrl = it }
                    cont.resumeWithException(Exception("未登录，需要重新登录"))
                }
                else -> cont.resumeWithException(Exception("获取热水数据失败，状态码: ${response.code}"))
            }
        } catch (e: Exception) { cont.resume(Result.failure(e)) }
    }

    fun getCookie(): String = savedCookie
    fun setCookie(cookie: String) { savedCookie = cookie }
}
