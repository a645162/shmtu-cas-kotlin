package cn.edu.shmtu.cas

import cn.edu.shmtu.cas.demo.HotWaterDemo
import cn.edu.shmtu.cas.demo.BillDemo
import cn.edu.shmtu.cas.captcha.Captcha

fun main() {

    val userId = System.getenv("SHMTU_USER_ID")
    val password = System.getenv("SHMTU_PASSWORD")
    val maskedPassword = password?.let { "*".repeat(it.length) } ?: ""

    println("===== Environment Variables =====")
    println("SHMTU_USER_ID: $userId")
    println("SHMTU_PASSWORD: $maskedPassword")
    println("SHMTU_OCR_HOST: ${System.getenv("SHMTU_OCR_HOST")}")
    println("SHMTU_OCR_PORT: ${System.getenv("SHMTU_OCR_PORT")}")
    println("=================================")
    println()

    System.getenv("SHMTU_OCR_HOST")?.let {
        Captcha.ocrHost = it
    }
    System.getenv("SHMTU_OCR_PORT")?.toIntOrNull()?.let {
        Captcha.ocrPort = it
    }

    // Get From Environment


    // 热水数据获取测试
//    HotWaterDemo.testHotWater()

    // 账单数据获取测试
    BillDemo.testBill(userId, password)

    // 验证码识别测试
//    Captcha.testLocalTcpServerOcrMultiThread(1)
}
