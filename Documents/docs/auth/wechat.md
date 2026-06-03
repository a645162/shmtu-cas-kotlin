---
title: 微信平台/热水
---

# 微信平台/热水

## 概述

上海海事大学后勤服务平台（`http://hqzx.shmtu.edu.cn`）通过微信平台认证（`wengine_new_ticket`）实现访问控制。登录后可以查询各楼栋热水温度与水位。

## 与 Epay 的区别

| 项 | EpayAuth | WechatAuth |
|----|----------|------------|
| 目标服务 | `https://ecard.shmtu.edu.cn/epay/...` | `http://hqzx.shmtu.edu.cn/cellphone/getHotWater` |
| 跳转链 | 直接 302 → CAS | 先 302 → 微信认证 → CAS |
| Cookie 关键字段 | `JSESSIONID` + `TGC` | `wengine_new_ticket` + `JSESSIONID` + `TGC` |
| 业务方法 | `getBill` | `getHotWater` |
| 同步算法 | `incrementalSync` 支持 | 暂不内置（一次性拉取即可） |

## 三阶段流程

```kotlin
val wechat = WechatAuth(remoteOcrResolver)

// 1. 探测
val probe = wechat.probeLogin().getOrThrow()
// AlreadyLoggedIn → 直接 getHotWater
// NeedLogin(loginWUrl) → 继续

// 2. 准备 challenge（内部会先访问 wengine_new_ticket）
val challenge = wechat.prepareChallenge().getOrThrow()

// 3. 提交登录
val result = wechat.submitLogin("学号", "密码", "8", challenge.execution).getOrThrow()
```

## 关键方法

| 方法 | 签名 | 说明 |
|------|------|------|
| `probeLogin` | `suspend () -> Result<SessionProbe>` | 探测会话 |
| `prepareChallenge` | `suspend () -> Result<LoginChallenge>` | 拉取 wengine_ticket → execution → 验证码 |
| `submitLogin` | `suspend (user, pass, code, exec) -> Result<LoginSubmitResult>` | 手动登录 |
| `submitLogin` | `suspend (user, pass, maxRetries) -> Result<LoginSubmitResult>` | 一键登录 |
| `testLoginStatus` | `suspend () -> Result<Boolean>` | 当前是否已登录 |
| `getHotWater` | `suspend () -> Result<String>` | 拉取热水 HTML |
| `restoreSession(json)` | `(String) -> Result<Unit>` | 恢复 Cookie |
| `extractSession()` | `() -> String` | 导出 Cookie JSON |

## wengine_new_ticket 流程

```kotlin
// probeLogin 拿到 loginWUrl（指向微信认证）
// prepareChallenge 内部：
//   1. 先请求 loginWUrl → 拿到 Set-Cookie: wengine_new_ticket + 302 到 CAS
//   2. 用新 Cookie 调 CasAuth.getExecution → execution
//   3. 下载验证码图片
```

完整实现见 `WechatAuth.prepareChallenge()`。

## 解析热水

```kotlin
val html = wechat.getHotWater().getOrThrow()
val list: List<Triple<Float, Float, Int>> = HotWaterParser(html).getHotWaterList()

for ((temperature, waterLevel, building) in list) {
    println("$building 号楼: $temperature ℃, 水位 $waterLevel %")
}
```

`Triple` 字段顺序：

| 位置 | 类型 | 说明 |
|------|------|------|
| first | `Float` | 温度（℃） |
| second | `Float` | 水位百分比 |
| third | `Int` | 楼号 |

详见 [HotWaterParser API](/api/hotwater-parser)。

## Cookie 持久化

```kotlin
// 保存
val json = wechat.extractSession()
sharedPrefs.edit().putString("wechat_cookies", json).apply()

// 恢复
val saved = sharedPrefs.getString("wechat_cookies", null)
if (saved != null) {
    val wechat = WechatAuth(resolver)
    wechat.restoreSession(saved)
    if (wechat.testLoginStatus().getOrThrow() == true) {
        // 直接 getHotWater
    }
}
```

## 完整示例

```kotlin
val resolver = RemoteOcrHttpCaptchaResolver("http://127.0.0.1:21600")
val wechat = WechatAuth(resolver)

when (val r = wechat.submitLogin("学号", "密码").getOrThrow()) {
    is LoginSubmitResult.Success -> {
        val html = wechat.getHotWater().getOrThrow()
        HotWaterParser(html).getHotWaterList().forEach { (t, w, b) ->
            println("$b 号楼: $t ℃ / $w %")
        }
    }
    is LoginSubmitResult.PasswordError -> error("密码错误")
    is LoginSubmitResult.ValidateCodeError -> error("验证码错误，请重试")
    is LoginSubmitResult.Failure -> error(r.message)
}
```
