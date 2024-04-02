package com.khm.shmtu.cas.demo

import com.khm.shmtu.cas.auth.EpayAuth

class BillDemo {

    companion object {

        fun testBill() {
            val epayAuth = EpayAuth()
            val isSuccess =
                epayAuth.login("", "")
            println(isSuccess)
            if (!isSuccess) {
                println("Login failed!")
                return
            }
            val billResult =
                epayAuth.getBill(pageNo = "1")
            println(billResult.first)
            println(billResult.second)
        }

    }

}