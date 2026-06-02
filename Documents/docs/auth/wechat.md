---
title: 微信平台认证与热水查询
---

# 微信平台认证与热水查询

## 概述

上海海事大学后勤服务平台（`http://hqzx.shmtu.edu.cn`）提供热水信息查询等功能，通过微信平台认证（wengine_new_ticket）实现访问控制。该平台同样依赖 CAS 统一认证。

## 认证流程

微信平台认证流程与 Epay 类似，但多了一个 `wengine_new_ticket` 环节：

```
1. 访问后勤平台热水接口 → 302 重定向
2. 获取 wengine_new_ticket → 302 重定向到 CAS 登录页
3. CAS 登录（含验证码识别）→ 302 重定向
4. 跟随重定向获取认证 Cookie（含 wengine_new_ticket）
5. 使用认证 Cookie 访问热水数据
```

## wengine_new_ticket

微信平台认证的关键区别在于需要先获取 `wengine_new_ticket`。当首次访问后勤平台时，302 重定向的 URL 指向微信认证服务，需要再请求一次该 URL 才能获取到 CAS 登录地址：

```kotlin
private fun getWEngineNewTicket(url: String): Triple<Int, String, String> {
    // 请求微信认证服务 URL
    // 返回 302 重定向到 CAS 登录页
    // 同时通过 Set-Cookie 返回 wengine_new_ticket
}
```

## 使用方法

### 登录

```kotlin
val wechatAuth = WechatAuth()
val isSuccess = wechatAuth.login(userId, password)
```

`login` 方法内部执行以下操作：

1. 调用 `testLoginStatus()` 检查是否已登录
2. 如果未登录，获取 302 重定向地址
3. 请求 `wengine_new_ticket` 获取 CAS 登录 URL
4. 提取 `execution` 参数
5. 下载验证码并通过 OCR 识别
6. 提交 CAS 登录表单
7. 跟随重定向（附带来源 URL `from=http://hqzx.shmtu.edu.cn/cellphone/getHotWater`）
8. 保存认证 Cookie

### 查询热水信息

登录成功后，可以查询热水信息：

```kotlin
val hotWaterResult = wechatAuth.getHotWater()

val statusCode = hotWaterResult.first     // HTTP 状态码
val htmlContent = hotWaterResult.second   // 热水信息 HTML
val cookie = hotWaterResult.third         // 更新后的 Cookie
```

### 检查登录状态

```kotlin
val isLoggedIn = wechatAuth.testLoginStatus()
```

## 热水数据解析

获取到热水信息页面的 HTML 后，可以使用 `HotWaterParser` 解析：

```kotlin
val parser = HotWaterParser(htmlContent)
val hotWaterList = parser.getHotWaterList()

for (item in hotWaterList) {
    val temperature = item.first   // 温度（摄氏度）
    val waterLevel = item.second   // 水位百分比
    val building = item.third      // 楼号
    println("${building}号楼 - 温度: ${temperature}℃ - 水位: ${waterLevel}%")
}
```

返回数据为 `MutableList<Triple<Float, Float, Int>>`，每个三元组表示：

| 位置 | 类型 | 说明 |
|------|------|------|
| first | Float | 热水温度（摄氏度） |
| second | Float | 水位百分比（0-100） |
| third | Int | 楼号 |

## Cookie 管理

微信平台认证中，`savedCookie` 存储了包含 `wengine_new_ticket` 的完整认证 Cookie。该 Cookie 在登录成功后由 `casRedirect` 的响应中获取。

注意事项：

- 微信平台的 Cookie 有效期有限，过期后需要重新登录
- `from` 参数在 CAS 重定向时需要指定原始请求 URL

## 完整示例

```kotlin
val wechatAuth = WechatAuth()

// 登录
if (!wechatAuth.login(userId, password)) {
    println("登录失败")
    return
}

// 查询热水信息
val result = wechatAuth.getHotWater()
if (result.first == 200) {
    val parser = HotWaterParser(result.second)
    val hotWaterList = parser.getHotWaterList()

    for (item in hotWaterList) {
        println("${item.third}号楼 - ${item.first}℃ - 水位${item.second}%")
    }
}
```
