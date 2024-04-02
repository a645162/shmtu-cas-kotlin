package com.khm.shmtu.cas

import com.khm.shmtu.cas.demo.HotWaterDemo
import com.khm.shmtu.cas.demo.BillDemo
import com.khm.shmtu.cas.captcha.Captcha

fun main() {
    // 热水数据获取测试
    HotWaterDemo.testHotWater()

    // 账单数据获取测试
    BillDemo.testBill()

    // 验证码识别测试
    Captcha.testLocalTcpServerOcrMultiThread(1)
}
