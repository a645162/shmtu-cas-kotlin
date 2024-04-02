package com.khm.shmtu.cas.auth

import com.khm.shmtu.cas.auth.common.CasAuth
import com.khm.shmtu.cas.captcha.Captcha
import okhttp3.OkHttpClient
import okhttp3.Request

class EpayAuth {

    private var savedCookie = ""
    private var htmlCode = ""

    private var loginUrl = ""
    private var loginCookie = ""

    fun getBill(
        pageNo: String = "1",
        tabNo: String = "1",
        cookie: String = ""
    ): Triple<Int, String, String> {
        val url =
            "https://ecard.shmtu.edu.cn/epay/consume/query" +
                    "?pageNo=$pageNo" +
                    "&tabNo=$tabNo"

        val client = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        val finalCookie = cookie.ifBlank {
            this.savedCookie
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Cookie", finalCookie)
            .get()
            .build()

        val response = client.newCall(request).execute()

        val responseCode = response.code

        return if (responseCode == 200) {
            this.htmlCode = (response.body?.string() ?: "").trim()

            Triple(responseCode, this.htmlCode, cookie)
        } else if (responseCode == 302) {
            val location =
                response.header("Location") ?: ""

            // Get all "Set-Cookie" Header
            val setCookieHeaders: List<String> =
                response.headers.values("Set-Cookie")

            var newCookie = cookie
            for (currentSetCookie in setCookieHeaders) {
                if (currentSetCookie.contains("JSESSIONID")) {
                    newCookie = currentSetCookie
                }
            }

            this.savedCookie = newCookie

            Triple(responseCode, location, newCookie)
        } else {
            Triple(responseCode, "", "")
        }
    }

    fun testLoginStatus(): Boolean {
        val resultBill =
            getBill(cookie = this.savedCookie)

        if (resultBill.first == 200) {
            // OK
            return true
        } else if (resultBill.first == 302) {
            this.loginUrl =
                resultBill.second
            this.savedCookie =
                resultBill.third

            return false
        } else {
            return false
        }
    }

    fun login(
        username: String,
        password: String
    ): Boolean {

        if (this.loginUrl.isBlank() || this.savedCookie.isBlank()) {
            if (testLoginStatus()) {
                return true
            }
        }

        val executionStr =
            CasAuth.getExecution(
                this.loginUrl,
                this.savedCookie
            )

        // 下载验证码
        val resultCaptcha =
            Captcha.getImageDataFromUrlUsingGet(
                cookie = this.savedCookie
            )

        // 检验下载的数据
        if (resultCaptcha == null) {
            println("获取验证码图片失败")
            return false
        }
        val imageData = resultCaptcha.first
        this.loginCookie = resultCaptcha.second
        if (imageData == null) {
            println("获取验证码失败")
            return false
        }

        // 调用远端识别接口
        val validateCode: String =
            Captcha.ocrByRemoteTcpServer(
                "127.0.0.1", 21601,
                imageData
            )
        val exprResult =
            Captcha.getExprResultByExprString(validateCode)

        val resultCas =
            CasAuth.casLogin(
                this.loginUrl,
                username,
                password,
                exprResult,
                executionStr,
                this.loginCookie
            )

        if (resultCas.first != 302) {
            println("程序出错，状态码：${resultCas.first}")
            return false
        }

        this.loginCookie = resultCas.third

        val resultRedirect =
            CasAuth.casRedirect(
                resultCas.second,
                this.savedCookie
            )

        if (resultRedirect.first != 302) {
            println("Login Ok,but cannot redirect to bill page.")
            println("Status code：${resultRedirect.first}")
            return false
        }

        val resultBill =
            getBill(cookie = this.savedCookie)

        return resultBill.first == 200
    }

}
