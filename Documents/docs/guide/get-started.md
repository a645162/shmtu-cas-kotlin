---
title: 快速开始
---

# 快速开始

## 环境要求

- **JDK 17+**（建议 21）：Kotlin 2.2 + Gradle 8+，已开启 JVM toolchain 17
- **Gradle**：仓库自带 `gradlew`，无需单独安装
- **OCR 推理服务器**（可选）：如需验证码自动识别，可部署 [shmtu-cas-ocr-server](https://github.com/a645162/shmtu-cas-ocr-server)，TCP 模式默认端口 `21601`、HTTP 模式默认 `21600`

## 克隆与构建

```bash
git clone https://github.com/a645162/shmtu-cas-kotlin.git
cd shmtu-cas-kotlin
./gradlew build
```

主要依赖：

| 依赖 | 版本 | 用途 |
|------|------|------|
| Kotlin | 2.2.10 | 编程语言 |
| OkHttp | 5.3.2 | HTTP 客户端 |
| Jsoup | 1.22.2 | HTML 解析 |
| kotlinx-coroutines-core | 1.11.0 | suspend 协程 |
| kotlinx-serialization-json | 1.11.0 | Cookie JSON 序列化 |

## 子项目

```
shmtu-cas-kotlin/
├── cas_lib/            # 纯 JVM 库 cn.edu.shmtu.cas.*
├── cas_android_lib/    # Android 库（复用 cas_lib 源码）
├── cas_cli/            # 命令行工具
└── Documents/docs/     # VitePress 文档
```

详细结构见 [模块与子项目](/guide/modules)。

## 最简登录 + 同步

```kotlin
import cn.edu.shmtu.cas.auth.EpayAuth
import cn.edu.shmtu.cas.captcha.RemoteOcrHttpCaptchaResolver
import cn.edu.shmtu.cas.datatype.BillItem
import cn.edu.shmtu.cas.sync.BillStore
import cn.edu.shmtu.cas.sync.SyncOptions
import cn.edu.shmtu.cas.sync.incrementalSync
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val resolver = RemoteOcrHttpCaptchaResolver("http://127.0.0.1:21600")
    val epay = EpayAuth(resolver)

    when (val r = epay.submitLogin("学号", "密码")) {
        is cn.edu.shmtu.cas.session.LoginSubmitResult.Success -> println("登录成功")
        is cn.edu.shmtu.cas.session.LoginSubmitResult.PasswordError -> error("密码错误")
        is cn.edu.shmtu.cas.session.LoginSubmitResult.ValidateCodeError -> error("验证码错误")
        is cn.edu.shmtu.cas.session.LoginSubmitResult.Failure -> error(r.message)
    }

    val store = object : BillStore {
        override fun contains(transactionNo: String) = false
        override fun merge(newBills: List<BillItem>) = newBills.forEach(::println)
    }

    incrementalSync(epay, store, SyncOptions(maxPages = 20)) { p ->
        println("page=${p.page} new=${p.newCount} total=${p.totalFetched}")
    }
}
```

## 三阶段登录（手动 challenge）

```kotlin
val epay = EpayAuth()

// 1. 探测
when (val probe = epay.probeLogin().getOrThrow()) {
    is SessionProbe.AlreadyLoggedIn -> {} // 已登录
    is SessionProbe.NeedLogin -> {}       // 需要登录
}

// 2. 准备 challenge（含 execution + 验证码图片）
val challenge = epay.prepareChallenge().getOrThrow()

// 3. 解析验证码（由你决定：弹窗、OCR、手动...）
val answer = myResolver.resolve(challenge.captchaImage).getOrThrow().intoFinalAnswer().value

// 4. 提交
val result = epay.submitLogin("学号", "密码", answer, challenge.execution).getOrThrow()
```

## OCR 服务器配置

| 场景 | 解析器 | 默认地址 |
|------|--------|----------|
| TCP（局域网/无 Docker） | `RemoteOcrCaptchaResolver` | `127.0.0.1:21601` |
| HTTP（容器化/可观测） | `RemoteOcrHttpCaptchaResolver` | `http://127.0.0.1:21600` |
| 人工（UI 弹窗） | `ManualCaptchaResolver` | - |
| 已有 ONNX 模型 | `ExprCaptchaResolver` | - |

环境变量：

```bash
export SHMTU_OCR_HOST=192.168.1.100
export SHMTU_OCR_PORT=21601
export SHMTU_OCR_HTTP_URL=http://192.168.1.100:21600
```

## 平台选择

| 目标 | 入口 | 文档 |
|------|------|------|
| Android App | `cas_android_lib` | [Android 集成](/platforms/android) |
| JVM / 桌面端 | `cas_lib` | [JVM 集成](/platforms/jvm) |
| 命令行调试 | `cas_cli` | [CLI 工具](/platforms/cli) |

## 下一步

- [整体架构](/guide/architecture)
- [API 总览](/api/overview)
- [Android 集成指南](/platforms/android)
