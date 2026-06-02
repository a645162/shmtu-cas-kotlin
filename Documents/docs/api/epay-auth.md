---
title: EpayAuth API 参考
---

# EpayAuth API 参考

`EpayAuth` 提供 Epay 一卡通平台的认证和账单查询功能。位于 `cn.edu.shmtu.cas.auth` 包。

::: info
`EpayAuth` 需要实例化使用，内部自动管理 Cookie 和登录状态。
:::

## 构造函数

```kotlin
class EpayAuth()
```

无参数构造。实例化后内部状态为空，需要调用 `login` 方法完成认证。

---

## login

执行 Epay 平台登录（基于 CAS 认证）。

```kotlin
fun login(
    username: String,
    password: String
): Boolean
```

### 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| username | String | 学号 |
| password | String | 密码 |

### 返回值

`Boolean` - 登录是否成功。

### 内部流程

1. 检查登录状态（如已登录则直接返回 `true`）
2. 获取 302 重定向地址，提取 CAS 登录 URL
3. 调用 `CasAuth.getExecution()` 获取 execution 参数
4. 调用 `Captcha.getImageDataFromUrlUsingGet()` 下载验证码
5. 调用 `Captcha.ocrByRemoteTcpServer()` 识别验证码
6. 调用 `CasAuth.casLogin()` 提交登录
7. 调用 `CasAuth.casRedirect()` 跟随重定向回 Epay
8. 验证是否成功访问账单页面

---

## getBill

查询一卡通消费账单。

```kotlin
fun getBill(
    pageNo: String = "1",
    tabNo: String = "1",
    cookie: String = ""
): Triple<Int, String, String>
```

### 参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| pageNo | String | `"1"` | 页码 |
| tabNo | String | `"1"` | 标签页编号 |
| cookie | String | `""` | 自定义 Cookie，为空使用内部 Cookie |

### 返回值

`Triple<Int, String, String>`

| 位置 | 说明 |
|------|------|
| first | HTTP 状态码。200 表示成功，302 表示需要重新认证 |
| second | 200 时为账单 HTML；302 时为重定向地址 |
| third | Cookie 信息 |

---

## testLoginStatus

检查当前登录状态。

```kotlin
fun testLoginStatus(): Boolean
```

### 返回值

`Boolean` - 是否已登录。通过尝试访问账单页面判断：

- 返回 200 → 已登录
- 返回 302 → 未登录，同时更新内部登录 URL 和 Cookie

---

## 内部状态

| 属性 | 类型 | 说明 |
|------|------|------|
| _epayCookie | String | Epay 平台认证 Cookie |
| _htmlCode | String | 最近一次响应的 HTML |
| _loginUrl | String | CAS 登录 URL |
| _loginCookie | String | 登录过程使用的 Cookie |

---

## 使用示例

```kotlin
val epayAuth = EpayAuth()

// 登录
if (epayAuth.login("学号", "密码")) {
    // 查询账单
    val result = epayAuth.getBill(pageNo = "1")
    if (result.first == 200) {
        println("账单数据获取成功")
        // 使用 BillParser 解析 HTML
    }
}
```
