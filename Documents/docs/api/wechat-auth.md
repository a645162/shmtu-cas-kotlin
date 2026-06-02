---
title: WechatAuth API 参考
---

# WechatAuth API 参考

`WechatAuth` 提供微信平台（后勤服务平台）的认证和数据查询功能。位于 `cn.edu.shmtu.cas.auth` 包。

::: info
`WechatAuth` 需要实例化使用，内部自动管理 Cookie 和登录状态。
:::

## 构造函数

```kotlin
class WechatAuth()
```

无参数构造。实例化后内部状态为空，需要调用 `login` 方法完成认证。

---

## login

执行微信平台登录（基于 CAS 认证 + wengine_new_ticket）。

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

1. 检查用户名密码是否为空
2. 检查登录状态（如已登录则直接返回 `true`）
3. 获取 302 重定向地址
4. 调用 `getWEngineNewTicket()` 获取微信认证凭证
5. 调用 `CasAuth.getExecution()` 获取 execution 参数
6. 调用 `Captcha.getImageDataFromUrlUsingGet()` 下载验证码
7. 调用 `Captcha.ocrByRemoteTcpServer()` 识别验证码
8. 调用 `CasAuth.casLogin()` 提交登录
9. 调用 `CasAuth.casRedirect()` 跟随重定向（附带 `from` 参数指向热水查询 URL）
10. 保存认证 Cookie
11. 验证登录状态

---

## getHotWater

查询热水信息。

```kotlin
fun getHotWater(
    cookie: String = ""
): Triple<Int, String, String>
```

### 参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| cookie | String | `""` | 自定义 Cookie，为空使用内部 Cookie |

### 返回值

`Triple<Int, String, String>`

| 位置 | 说明 |
|------|------|
| first | HTTP 状态码。200 表示成功，302 表示需要重新认证 |
| second | 200 时为热水信息 HTML；302 时为重定向地址 |
| third | Cookie 信息 |

---

## testLoginStatus

检查当前登录状态。

```kotlin
fun testLoginStatus(): Boolean
```

### 返回值

`Boolean` - 是否已登录。通过尝试访问热水查询接口判断：

- 返回 200 → 已登录
- 返回 302 → 未登录，同时更新内部登录 URL

---

## getWEngineNewTicket（私有方法）

请求微信认证服务获取 wengine_new_ticket。

```kotlin
private fun getWEngineNewTicket(
    url: String
): Triple<Int, String, String>
```

### 说明

微信平台认证的关键环节。访问重定向地址后，微信认证服务会：

1. 返回 302 重定向到 CAS 登录页
2. 通过 `Set-Cookie` 设置 `wengine_new_ticket`

---

## 内部状态

| 属性 | 类型 | 说明 |
|------|------|------|
| savedCookie | String | 微信平台认证 Cookie（含 wengine_new_ticket） |
| loginWUrl | String | 微信认证重定向地址 |

---

## 使用示例

```kotlin
val wechatAuth = WechatAuth()

// 登录
if (wechatAuth.login("学号", "密码")) {
    // 查询热水信息
    val result = wechatAuth.getHotWater()
    if (result.first == 200) {
        val parser = HotWaterParser(result.second)
        val list = parser.getHotWaterList()
        for (item in list) {
            println("${item.third}号楼 - ${item.first}℃ - 水位${item.second}%")
        }
    }
}
```
