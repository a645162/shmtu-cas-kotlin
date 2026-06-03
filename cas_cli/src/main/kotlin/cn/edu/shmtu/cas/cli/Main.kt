package cn.edu.shmtu.cas.cli

import cn.edu.shmtu.cas.auth.EpayAuth
import cn.edu.shmtu.cas.auth.WechatAuth
import cn.edu.shmtu.cas.captcha.Captcha
import cn.edu.shmtu.cas.captcha.CaptchaAnswer
import cn.edu.shmtu.cas.captcha.CaptchaAnswerKind
import cn.edu.shmtu.cas.captcha.CaptchaResolver
import cn.edu.shmtu.cas.captcha.ManualCaptchaResolver
import cn.edu.shmtu.cas.captcha.RemoteOcrCaptchaResolver
import cn.edu.shmtu.cas.captcha.RemoteOcrHttpCaptchaResolver
import cn.edu.shmtu.cas.parser.BillParser
import cn.edu.shmtu.cas.parser.HotWaterParser
import cn.edu.shmtu.cas.session.LoginSubmitResult
import cn.edu.shmtu.cas.session.SessionProbe
import kotlinx.coroutines.runBlocking

/**
 * 上海海事大学 CAS 登录与账单查询工具
 *
 * 对齐 Rust 版本的 shmtu-cas-cli (clap 子命令)
 *
 * 用法:
 *   shmtu-cas bill -u <学号> -p <密码> [--captcha ocr|manual] [--ocr-host X] [--ocr-port N]
 *   shmtu-cas hot-water -u <学号> -p <密码> [--captcha ocr|manual]
 *   shmtu-cas captcha-test [--ocr-host X] [--ocr-port N]
 *   shmtu-cas parse -i <html文件>
 *   shmtu-cas help
 */

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        printUsage()
        return
    }

    when (args[0]) {
        "bill" -> cmdBill(args.drop(1))
        "hot-water" -> cmdHotWater(args.drop(1))
        "captcha-test" -> cmdCaptchaTest(args.drop(1))
        "parse" -> cmdParse(args.drop(1))
        "help", "--help", "-h" -> printUsage()
        else -> {
            println("Unknown command: ${args[0]}")
            printUsage()
        }
    }
}

// ========== 参数解析 ==========

private class CommonOpts(
    val username: String,
    val password: String,
    val captchaMode: String,  // "ocr" or "manual"
    val ocrHost: String,
    val ocrPort: Int,
    val ocrServerType: String, // "tcp" or "http"
    val ocrHttpUrl: String,
)

private fun parseCommonOpts(args: List<String>): CommonOpts {
    var username = System.getenv("SHMTU_USER_ID") ?: System.getenv("SHMTU_USERNAME") ?: ""
    var password = System.getenv("SHMTU_PASSWORD") ?: ""
    var captchaMode = "ocr"
    var ocrHost = System.getenv("SHMTU_OCR_HOST") ?: "127.0.0.1"
    var ocrPort = System.getenv("SHMTU_OCR_PORT")?.toIntOrNull() ?: 21601
    var ocrServerType = "tcp"
    var ocrHttpUrl = System.getenv("SHMTU_OCR_HTTP_URL") ?: RemoteOcrHttpCaptchaResolver.DEFAULT_BASE_URL

    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "-u", "--username" -> { username = args.getOrElse(++i) { "" } }
            "-p", "--password" -> { password = args.getOrElse(++i) { "" } }
            "-c", "--captcha" -> { captchaMode = args.getOrElse(++i) { "ocr" } }
            "--ocr-host" -> { ocrHost = args.getOrElse(++i) { ocrHost } }
            "--ocr-port" -> { ocrPort = args.getOrElse(++i) { ocrPort.toString() }.toIntOrNull() ?: ocrPort }
            "--ocr-server-type" -> { ocrServerType = args.getOrElse(++i) { "tcp" } }
            "--ocr-http-url" -> { ocrHttpUrl = args.getOrElse(++i) { ocrHttpUrl } }
        }
        i++
    }

    return CommonOpts(username, password, captchaMode, ocrHost, ocrPort, ocrServerType, ocrHttpUrl)
}

private fun buildResolver(opts: CommonOpts): CaptchaResolver {
    return when (opts.captchaMode) {
        "manual" -> ManualCaptchaResolver { imageData ->
            Captcha.saveImageToFile(imageData)
            println("验证码已保存到文件，请查看后输入答案")
            print("请输入验证码答案: ")
            val input = readlnOrNull()?.trim() ?: ""
            CaptchaAnswer(input, CaptchaAnswerKind.ANSWER)
        }
        else -> when (opts.ocrServerType) {
            "http" -> RemoteOcrHttpCaptchaResolver(opts.ocrHttpUrl)
            else -> RemoteOcrCaptchaResolver(opts.ocrHost, opts.ocrPort)
        }
    }
}

// ========== 子命令 ==========

private fun cmdBill(args: List<String>) {
    val opts = parseCommonOpts(args)
    if (opts.username.isBlank() || opts.password.isBlank()) {
        println("Error: --username and --password are required (or set SHMTU_USER_ID / SHMTU_PASSWORD)")
        return
    }

    runBlocking {
        val auth = EpayAuth(buildResolver(opts))
        println("正在探测登录状态...")

        val probe = auth.probeLogin()
        if (probe.isFailure) {
            println("探测登录状态失败: ${probe.exceptionOrNull()?.message}")
            return@runBlocking
        }
        if (probe.getOrThrow() is SessionProbe.AlreadyLoggedIn) {
            println("已经登录")
        } else {
            val result = auth.submitLogin(opts.username, opts.password)
            if (result.isFailure) {
                println("登录异常: ${result.exceptionOrNull()?.message}")
                return@runBlocking
            }
            if (result.getOrThrow() !is LoginSubmitResult.Success) {
                println("登录失败: ${result.getOrThrow()}")
                return@runBlocking
            }
            println("登录成功")
        }

        println("正在获取账单...")
        val billResult = auth.getBill(pageNo = 1, billType = cn.edu.shmtu.cas.datatype.BillType.ALL)
        if (billResult.isFailure) {
            println("获取账单失败: ${billResult.exceptionOrNull()?.message}")
            return@runBlocking
        }
        val bills = BillParser().getBillList(billResult.getOrThrow())
        println("共 ${bills.size} 条账单记录")
        for (bill in bills) {
            println("${bill["dateTimeStrFormat"]} | ${bill["type"]} | ${bill["targetUser"]} | ${bill["money"]} | ${bill["status"]}")
        }
    }
}

private fun cmdHotWater(args: List<String>) {
    val opts = parseCommonOpts(args)
    if (opts.username.isBlank() || opts.password.isBlank()) {
        println("Error: --username and --password are required (or set SHMTU_USER_ID / SHMTU_PASSWORD)")
        return
    }

    runBlocking {
        val auth = WechatAuth(buildResolver(opts))
        println("正在探测登录状态...")

        val probe = auth.probeLogin()
        if (probe.isFailure) {
            println("探测登录状态失败: ${probe.exceptionOrNull()?.message}")
            return@runBlocking
        }
        if (probe.getOrThrow() is SessionProbe.AlreadyLoggedIn) {
            println("已经登录")
        } else {
            val result = auth.submitLogin(opts.username, opts.password)
            if (result.isFailure) {
                println("登录异常: ${result.exceptionOrNull()?.message}")
                return@runBlocking
            }
            if (result.getOrThrow() !is LoginSubmitResult.Success) {
                println("登录失败: ${result.getOrThrow()}")
                return@runBlocking
            }
            println("登录成功")
        }

        println("正在获取热水信息...")
        val hotWaterResult = auth.getHotWater()
        if (hotWaterResult.isFailure) {
            println("获取热水信息失败: ${hotWaterResult.exceptionOrNull()?.message}")
            return@runBlocking
        }
        val list = HotWaterParser(hotWaterResult.getOrThrow()).getHotWaterList()
        println("共 ${list.size} 栋楼")
        for (info in list) {
            println("${info.third}号楼: 温度 ${info.first}℃, 水位 ${info.second}%")
        }
    }
}

private fun cmdCaptchaTest(args: List<String>) {
    var ocrHost = System.getenv("SHMTU_OCR_HOST") ?: "127.0.0.1"
    var ocrPort = System.getenv("SHMTU_OCR_PORT")?.toIntOrNull() ?: 21601
    var ocrServerType = "tcp"
    var ocrHttpUrl = System.getenv("SHMTU_OCR_HTTP_URL") ?: RemoteOcrHttpCaptchaResolver.DEFAULT_BASE_URL

    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--ocr-host" -> { ocrHost = args.getOrElse(++i) { ocrHost } }
            "--ocr-port" -> { ocrPort = args.getOrElse(++i) { ocrPort.toString() }.toIntOrNull() ?: ocrPort }
            "--ocr-server-type" -> { ocrServerType = args.getOrElse(++i) { "tcp" } }
            "--ocr-http-url" -> { ocrHttpUrl = args.getOrElse(++i) { ocrHttpUrl } }
        }
        i++
    }

    when (ocrServerType) {
        "http" -> runBlocking {
            val resultCaptcha = Captcha.getImageDataFromUrlUsingGet()
            if (resultCaptcha == null) {
                println("获取验证码失败")
                return@runBlocking
            }

            val imageData = resultCaptcha.first
            if (imageData == null) {
                println("获取验证码失败")
                return@runBlocking
            }

            val resolver = RemoteOcrHttpCaptchaResolver(ocrHttpUrl)
            val ok = resolver.healthCheck()
            if (!ok) {
                println("HTTP OCR 服务不可达: $ocrHttpUrl")
                return@runBlocking
            }

            val startTime = System.currentTimeMillis()
            val result = resolver.resolve(imageData)
            val executionTime = System.currentTimeMillis() - startTime
            if (result.isFailure) {
                println("HTTP OCR 识别失败: ${result.exceptionOrNull()?.message}")
                return@runBlocking
            }

            val answer = result.getOrThrow()
            println("OCR执行时间: $executionTime 毫秒")
            println(answer.value)
            println(answer.intoFinalAnswer().value)
            Captcha.saveImageToFile(imageData)
        }
        else -> {
            Captcha.ocrHost = ocrHost
            Captcha.ocrPort = ocrPort
            Captcha.testLocalTcpServerOcr(ocrHost, ocrPort)
        }
    }
}

private fun cmdParse(args: List<String>) {
    var inputPath: String? = null

    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "-i", "--input" -> { inputPath = args.getOrElse(++i) { null } }
        }
        i++
    }

    if (inputPath == null) {
        println("Error: --input is required")
        return
    }

    val html = java.io.File(inputPath).readText()
    val bills = BillParser().getBillList(html)
    if (bills.isEmpty()) {
        println("没有找到账单记录")
        return
    }
    println("找到 ${bills.size} 条账单记录")
    for (bill in bills) {
        println("${bill["dateTimeStrFormat"]} | ${bill["type"]} | ${bill["targetUser"]} | ${bill["money"]} | ${bill["status"]}")
    }
}

// ========== 帮助 ==========

private fun printUsage() {
    println("""
        |shmtu-cas — 上海海事大学CAS登录与账单查询工具
        |
        |用法:
        |  shmtu-cas <command> [options]
        |
        |命令:
        |  bill          登录CAS并获取账单
        |  hot-water     登录微信平台并获取宿舍热水状态
        |  captcha-test  测试验证码OCR
        |  parse         解析本地HTML账单文件
        |  help          显示帮助信息
        |
        |bill 选项:
        |  -u, --username <学号>        用户名 (env: SHMTU_USER_ID)
        |  -p, --password <密码>        密码 (env: SHMTU_PASSWORD)
        |  -c, --captcha <ocr|manual>   验证码模式 (默认: ocr)
        |  --ocr-host <host>            OCR服务器地址 (默认: 127.0.0.1, env: SHMTU_OCR_HOST)
        |  --ocr-port <port>            OCR服务器端口 (默认: 21601, env: SHMTU_OCR_PORT)
        |  --ocr-server-type <tcp|http> OCR服务器协议 (默认: tcp)
        |  --ocr-http-url <url>         HTTP OCR服务器地址 (默认: ${RemoteOcrHttpCaptchaResolver.DEFAULT_BASE_URL})
        |
        |hot-water 选项:
        |  同 bill
        |
        |captcha-test 选项:
        |  --ocr-host, --ocr-port, --ocr-server-type, --ocr-http-url
        |
        |parse 选项:
        |  -i, --input <file>           HTML文件路径
    """.trimMargin())
}
