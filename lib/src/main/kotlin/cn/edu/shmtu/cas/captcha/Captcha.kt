package cn.edu.shmtu.cas.captcha

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.net.Socket
import java.net.URI
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO

import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException

class Captcha {

    companion object {

        var ocrHost: String = "127.0.0.1"
        var ocrPort: Int = 21601

        fun setOcrServer(host: String, port: Int = 21601) {
            ocrHost = host
            ocrPort = port
        }

        fun readImageFromFile(fileName: String): ByteArray {
            // Read image from file
            val imageFile = File(fileName)
            val image = ImageIO.read(imageFile)

            // Convert image to byte array
            val baos = ByteArrayOutputStream()
            ImageIO.write(image, "png", baos)
            val imageBytes = baos.toByteArray()
            return imageBytes
        }

        fun saveImageToFile(imageData: ByteArray, directoryPath: String = ".") {
            val currentDateTime = LocalDateTime.now()
            val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            val fileName = "captcha_${currentDateTime.format(formatter)}.png"
            val filePath = Paths.get(directoryPath, fileName).toString()
            java.io.FileOutputStream(filePath).use { fos ->
                fos.write(imageData)
            }
            println("Image saved to file: $fileName")
        }

        fun validateIPAddress(ip: String): Boolean {
            val ipAddressPattern = Regex(
                "^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
                        "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
                        "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
                        "([01]?\\d\\d?|2[0-4]\\d|25[0-5])$"
            )
            return ipAddressPattern.matches(ip)
        }

        fun validatePort(port: String): Boolean {
            val integerPort = port.toIntOrNull()
            return integerPort != null && validatePort(integerPort)
        }

        fun validatePort(port: Int): Boolean {
            return port in 0..65535
        }

        fun getImageDataFromUrl(
            imageUrl: String = "https://cas.shmtu.edu.cn/cas/captcha"
        ): ByteArray {
            val url = URI(imageUrl).toURL()
            val inputStream = BufferedInputStream(url.openStream())
            val outputStream = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            return outputStream.toByteArray()
        }

        fun getImageDataFromUrlUsingGet(
            cookie: String? = null
        ): Pair<ByteArray?, String>? {
            val imageUrl = "https://cas.shmtu.edu.cn/cas/captcha"

            val client = OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()

            val requestBuilder = Request.Builder()
                .url(imageUrl)
                .get()

            if (!cookie.isNullOrBlank()) {
                requestBuilder.addHeader("Cookie", cookie)
            }

            val request = requestBuilder.build()

            try {
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    println("请求失败，状态码：${response.code}")
                    return null
                }

                val returnCookie =
                    response.headers["Set-Cookie"] ?: (cookie ?: "")

                return Pair(response.body?.bytes(), returnCookie)
            } catch (e: IOException) {
                println("请求失败：${e.message}")
                return null
            }
        }

        fun ocrByRemoteTcpServer(
            host: String, port: Int,
            imageData: ByteArray
        ): String {
            try {
                Socket(host, port).use { socket ->
                    // 设置超时时间为 5 秒
                    // socket.setSoTimeout(5000)

                    val outputStream = socket.getOutputStream()
                    val dataOutputStream = DataOutputStream(outputStream)

                    // 发送图像数据
                    dataOutputStream.write(imageData)
                    dataOutputStream.flush()

                    // 发送特殊标记，表示图像数据发送完毕
                    val endMarker = "<END>".toByteArray(Charsets.UTF_8)
                    outputStream.write(endMarker)
                    outputStream.flush()

                    try {
                        val inputStream = socket.getInputStream()
                        val response = inputStream.readBytes().toString(Charsets.UTF_8)
                        return response
                    } catch (e: SocketTimeoutException) {
                        // 超时，返回空字符串
                        return ""
                    }
                }
            } catch (e: ConnectException) {
                println("[Captcha OCR] 连接远程验证码识别服务器失败！")
                println("[Captcha OCR] 目标地址: $host:$port")
                println("[Captcha OCR] 错误信息: ${e.message}")
                throw e
            }
        }

        fun ocrByRemoteTcpServerAutoRetry(
            host: String, port: Int,
            imageData: ByteArray,
            retryTimes: Int = 3
        ): String {
            var result: String = ""

            for (i in 1..retryTimes) {

                try {
                    result = ocrByRemoteTcpServer(host, port, imageData)
                } catch (e: Exception) {
                    println("第${i + 1}次尝试远程识别验证码失败")
                    println("错误信息：${e.message}")
                    continue
                }

                if (result.isNotEmpty()) {
                    break
                }
            }

            return result
        }

        fun getExprResultByExprString(expr: String): String {
            val index = expr.indexOf("=")
            if (index != -1) {
                val result = expr.substring(index + 1).trim()
                return result
            }
            return ""
        }

        fun testLocalTcpServerOcr(
            ip: String = ocrHost,
            port: Int = ocrPort,
        ) {
            println("识别验证码 Test")
            val resultCaptcha =
                getImageDataFromUrlUsingGet()

            if (resultCaptcha == null) {
                println("获取验证码失败")
                return
            }

            val imageData = resultCaptcha.first
            println(resultCaptcha.second)

            if (imageData == null) {
                println("获取验证码失败")
                return
            }

            val startTime = System.currentTimeMillis()
            val validateCode =
                ocrByRemoteTcpServerAutoRetry(
                    ip, port,
                    imageData
                )
            // 计算代码执行时间
            val endTime = System.currentTimeMillis()
            val executionTime = endTime - startTime
            println("OCR执行时间: $executionTime 毫秒")

            val exprResult =
                getExprResultByExprString(validateCode)
            println(validateCode)
            println(exprResult)

            saveImageToFile(imageData)
        }

        fun testLocalTcpServerOcrMultiThread(times: Int = 10) {
            val threads = List(times) {
                Thread {
                    testLocalTcpServerOcr()
                }
            }

            threads.forEach { it.start() } // 启动所有线程

            // 等待所有线程执行完毕
            threads.forEach { it.join() }

            println("All threads have finished execution.")
        }

    }

}
