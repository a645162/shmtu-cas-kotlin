package cn.edu.shmtu.cas.auth.common

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

/**
 * CAS 认证底层操作
 *
 * 对齐 Rust 版本的 cas/mod.rs，提供：
 * - createClient: 构建不自动跟随重定向的 OkHttpClient
 * - getExecution: 从 CAS 登录页获取 execution token + JSESSIONID
 * - casLogin: 提交登录表单
 * - casRedirect: 跟随重定向
 * - mergeCookies: 合并 Set-Cookie 头到已有 cookie
 */
class CasAuth {

    companion object {
        private val log = Logger.getLogger(CasAuth::class.java.name)

        fun createClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }

        private fun extractCookieNameValue(setCookieValue: String): String {
            val semiIdx = setCookieValue.indexOf(';')
            return if (semiIdx > 0) setCookieValue.substring(0, semiIdx).trim() else setCookieValue.trim()
        }

        fun mergeCookies(existingCookie: String, setCookieHeaders: List<String>): String {
            if (setCookieHeaders.isEmpty()) return existingCookie

            val cookieMap = linkedMapOf<String, String>()

            existingCookie.split(";").map { it.trim() }.filter { it.contains("=") }.forEach {
                val name = it.substringBefore("=").trim()
                cookieMap[name] = it.trim()
            }

            for (setCookie in setCookieHeaders) {
                val name = setCookie.substringBefore("=").trim()
                if (name.isNotEmpty()) {
                    cookieMap[name] = extractCookieNameValue(setCookie)
                }
            }

            return cookieMap.values.joinToString("; ")
        }

        /**
         * 获取 execution token 和 JSESSIONID
         *
         * @return Pair(execution, jSessionId)
         */
        fun getExecution(
            url: String = "https://cas.shmtu.edu.cn/cas/login",
            cookie: String = ""
        ): Pair<String, String> {
            val client = createClient()

            val request = Request.Builder()
                .url(url)
                .addHeader("Cookie", cookie)
                .get()
                .build()

            val response = client.newCall(request).execute()

            return if (response.code == 200) {
                val htmlCode = response.body?.string() ?: ""
                val document: Document = Jsoup.parse(htmlCode)
                val element: Element? = document.selectFirst("input[name=execution]")
                val value: String = element?.attr("value") ?: ""

                val jSessionId = response.headers("Set-Cookie")
                    .firstOrNull { it.contains("JSESSIONID") }
                    ?: cookie

                log.info("[CasAuth] getExecution: execution=${value.take(40)}...")
                Pair(value.trim(), jSessionId)
            } else {
                log.warning("[CasAuth] getExecution: failed, code=${response.code}")
                Pair("", "")
            }
        }

        fun casLogin(
            url: String,
            username: String,
            password: String,
            validateCode: String,
            execution: String,
            cookie: String
        ): Triple<Int, String, String> {
            val client = createClient()

            val formBody = FormBody.Builder()
                .add("username", username.trim())
                .add("password", password.trim())
                .add("validateCode", validateCode.trim())
                .add("execution", execution.trim())
                .add("_eventId", "submit")
                .add("geolocation", "")
                .build()

            val request = Request.Builder()
                .url(url)
                .addHeader("Host", "cas.shmtu.edu.cn")
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("Connection", "keep-alive")
                .addHeader("Accept-Encoding", "gzip, deflate, br")
                .addHeader("Accept", "*/*")
                .addHeader("Cookie", cookie.trim())
                .post(formBody)
                .build()

            val response = client.newCall(request).execute()
            val responseCode = response.code

            return if (responseCode == 302) {
                val location = response.header("Location") ?: ""
                val newCookie = mergeCookies(cookie, response.headers("Set-Cookie"))
                log.info("[CasAuth] casLogin: success (302), location=${location.take(60)}...")
                Triple(responseCode, location, newCookie)
            } else {
                val htmlCode = response.body?.string() ?: ""
                val document: Document = Jsoup.parse(htmlCode)
                val element: Element? = document.selectFirst("#loginErrorsPanel")
                val errorText = element?.text() ?: ""
                log.warning("[CasAuth] casLogin: failed, code=$responseCode, error=$errorText")

                if (errorText.contains("account is not recognized")) {
                    Triple(CasAuthStatus.PASSWORD_ERROR.code, htmlCode, "")
                } else if (errorText.contains("reCAPTCHA")) {
                    Triple(CasAuthStatus.VALIDATE_CODE_ERROR.code, htmlCode, "")
                } else {
                    Triple(responseCode, htmlCode, errorText)
                }
            }
        }

        fun casRedirect(url: String, cookie: String): Triple<Int, String, String> {
            val client = createClient()
            val request = Request.Builder()
                .url(url)
                .addHeader("Cookie", cookie)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseCode = response.code

            return if (responseCode == 302) {
                val location = response.header("Location") ?: ""
                val newCookie = mergeCookies(cookie, response.headers("Set-Cookie"))
                Triple(responseCode, location, newCookie)
            } else {
                log.warning("[CasAuth] casRedirect: failed, code=$responseCode")
                Triple(responseCode, "", "")
            }
        }
    }
}
