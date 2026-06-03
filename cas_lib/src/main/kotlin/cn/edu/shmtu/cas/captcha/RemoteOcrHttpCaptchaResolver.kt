package cn.edu.shmtu.cas.captcha

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URI
import java.util.Base64

/**
 * 远程 HTTP OCR 验证码解析器
 *
 * 对齐 Rust 版本的 OcrHttpCaptchaResolver：
 * - POST {base_url}/api/ocr  Body: {"imageBase64": "<base64>"}
 * - Response: {"success": bool, "expression": "12+34=", "result": 46, "error": "..."}
 */
class RemoteOcrHttpCaptchaResolver(
    private val baseUrl: String,
    private val retryTimes: Int = 3
) : CaptchaResolver {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class OcrRequest(val imageBase64: String)

    override suspend fun resolve(imageData: ByteArray): Result<CaptchaAnswer> {
        var lastException: Exception? = null
        repeat(retryTimes) { attempt ->
            try {
                val result = doResolve(imageData)
                if (result.isSuccess) return result
                lastException = Exception(result.exceptionOrNull()?.message ?: "OCR 识别失败")
            } catch (e: Exception) {
                lastException = e
            }
        }
        return Result.failure(lastException ?: Exception("OCR 识别失败"))
    }

    private suspend fun doResolve(imageData: ByteArray): Result<CaptchaAnswer> =
        withContext(Dispatchers.IO) {
            try {
                val url = URI("$baseUrl/api/ocr").toURL()
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.doOutput = true

                val base64Image = Base64.getEncoder().encodeToString(imageData)
                val requestBody = json.encodeToString(OcrRequest(base64Image))
                connection.outputStream.use { os ->
                    os.write(requestBody.toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode
                if (responseCode != 200) {
                    return@withContext Result.failure(Exception("HTTP $responseCode"))
                }

                val response = connection.inputStream.bufferedReader().readText()
                val responseJson = json.parseToJsonElement(response).jsonObject

                if (responseJson["success"]?.jsonPrimitive?.booleanOrNull != true) {
                    val error = responseJson["error"]?.jsonPrimitive?.contentOrNull ?: "OCR 识别失败"
                    return@withContext Result.failure(Exception(error))
                }

                val expression = responseJson["expression"]?.jsonPrimitive?.contentOrNull ?: ""
                if (expression.isNotEmpty()) {
                    Result.success(CaptchaAnswer(expression, CaptchaAnswerKind.EXPRESSION))
                } else {
                    Result.failure(Exception("OCR 返回空表达式"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URI("$baseUrl/api/health").toURL()
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.responseCode == 200
        } catch (_: Exception) {
            false
        }
    }
}
