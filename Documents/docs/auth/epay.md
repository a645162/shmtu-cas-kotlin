---
title: Epay认证与账单查询
---

# Epay 认证与账单查询

## 概述

Epay 是上海海事大学一卡通消费查询平台，地址为 `https://ecard.shmtu.edu.cn/epay`。通过 CAS 认证后可以查询一卡通的消费记录。

## 认证流程

Epay 认证基于 CAS 单点登录，完整流程如下：

```
1. 访问 Epay 账单页面 → 302 重定向到 CAS 登录页
2. CAS 登录（含验证码识别）→ 302 重定向回 Epay
3. 跟随重定向获取 Epay 认证 Cookie
4. 使用认证 Cookie 访问账单数据
```

## 使用方法

### 登录

```kotlin
val epayAuth = EpayAuth()
val isSuccess = epayAuth.login(userId, password)
```

`login` 方法内部执行以下操作：

1. 调用 `testLoginStatus()` 检查是否已登录
2. 如果未登录，从 302 重定向获取 CAS 登录 URL
3. 提取 `execution` 参数
4. 下载验证码并通过 OCR 识别
5. 提交 CAS 登录表单
6. 跟随重定向回到 Epay 平台

### 查询账单

登录成功后，可以查询消费记录：

```kotlin
val billResult = epayAuth.getBill(
    pageNo = "1",   // 页码
    tabNo = "1"     // 标签页编号
)

val statusCode = billResult.first    // HTTP 状态码
val htmlContent = billResult.second  // 账单页面 HTML
val cookie = billResult.third       // 更新后的 Cookie
```

参数说明：

| 参数 | 默认值 | 说明 |
|------|--------|------|
| pageNo | "1" | 页码，从 1 开始 |
| tabNo | "1" | 标签页编号 |
| cookie | "" | 自定义 Cookie，为空则使用内部存储的 Cookie |

### 检查登录状态

```kotlin
val isLoggedIn = epayAuth.testLoginStatus()
```

该方法通过尝试访问账单页面来判断当前是否已登录。如果返回 200 则已登录，返回 302 则需要重新登录。

## 账单数据解析

获取到账单页面的 HTML 后，可以使用 `BillParser` 解析消费记录：

```kotlin
val billParser = BillParser()
billParser.getBillTr(htmlContent)
val billList = billParser.getBillList()

for (bill in billList) {
    println("日期: ${bill["dateTimeStrFormat"]}")
    println("类型: ${bill["type"]}")
    println("金额: ${bill["money"]}")
    println("方式: ${bill["method"]}")
    println("状态: ${bill["status"]}")
    println("---")
}
```

### 账单字段说明

| 字段 | 说明 |
|------|------|
| dateStr | 日期（原始格式） |
| timeStr | 时间（原始格式） |
| timeStrFormat | 时间（格式化后，如 14:30:25） |
| dateTimeStrFormat | 日期+时间（格式化后） |
| type | 交易类型 |
| number | 交易号 |
| targetUser | 目标用户 |
| money | 金额 |
| method | 交易方式 |
| status | 交易状态 |

### 获取总页数

```kotlin
val totalPages = billParser.getPageCount(htmlContent)
```

## Cookie 管理

Epay 认证过程中的 Cookie 管理非常重要：

- 首次访问 Epay 时，服务器会通过 302 响应的 `Set-Cookie` 返回 `JSESSIONID`
- CAS 登录成功后，重定向回 Epay 时会再次设置认证 Cookie
- 后续请求必须携带完整的 Cookie，否则会重新触发 CAS 认证

`EpayAuth` 类内部自动管理 Cookie，无需手动处理。

## 完整示例

```kotlin
val epayAuth = EpayAuth()

// 登录
if (!epayAuth.login(userId, password)) {
    println("登录失败")
    return
}

// 查询第一页账单
val result = epayAuth.getBill(pageNo = "1")
if (result.first == 200) {
    val parser = BillParser()
    parser.getBillTr(result.second)
    val bills = parser.getBillList()

    println("共获取 ${bills.size} 条消费记录")
    for (bill in bills) {
        println("${bill["dateTimeStrFormat"]} | ${bill["type"]} | ${bill["money"]}元")
    }
}
```
