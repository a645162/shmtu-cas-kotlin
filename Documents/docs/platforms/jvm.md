---
title: JVM / 桌面端集成
---

# JVM / 桌面端集成

`cas_lib` 是纯 Kotlin/JVM 库，可直接在 Java 桌面端、Kotlin 后端、Spring Boot、命令行工具中使用。

## 添加依赖

```kotlin
dependencies {
    implementation("com.github.a645162.shmtu-cas-kotlin:shmtu-cas-jvm:<tag>")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
}
```

## 环境要求

- JDK 17+（Kotlin 2.2 已开启 `jvmToolchain(17)`）
- 纯 JVM，无 Android 依赖

## 最小示例

```kotlin
import cn.edu.shmtu.cas.auth.EpayAuth
import cn.edu.shmtu.cas.captcha.RemoteOcrHttpCaptchaResolver
import cn.edu.shmtu.cas.parser.BillParser
import cn.edu.shmtu.cas.sync.BillStore
import cn.edu.shmtu.cas.sync.SyncOptions
import cn.edu.shmtu.cas.sync.incrementalSync
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val epay = EpayAuth(RemoteOcrHttpCaptchaResolver("http://127.0.0.1:5000"))
    val r = epay.submitLogin("学号", "密码")
    check(r.getOrThrow() is cn.edu.shmtu.cas.session.LoginSubmitResult.Success)

    val html = epay.getBill(pageNo = 1).getOrThrow()
    BillParser().parseBillItems(html).forEach(::println)

    // 同步到内存 store
    val store = object : BillStore {
        private val known = mutableSetOf<String>()
        private val all = mutableListOf<cn.edu.shmtu.cas.datatype.BillItem>()
        override fun contains(transactionNo: String) = transactionNo in known
        override fun merge(newBills: List<cn.edu.shmtu.cas.datatype.BillItem>) {
            newBills.forEach { known += it.transactionNo; all += it }
        }
    }
    val sync = incrementalSync(epay, store, SyncOptions(maxPages = 50)).getOrThrow()
    println("new=${sync.newCount} total=${sync.totalFetched} earlyStop=${sync.stoppedEarly}")
}
```

## 用作 Java 调用

由于 API 大量使用 `suspend`，Java 调用需包一层 `runBlocking`：

```java
import cn.edu.shmtu.cas.auth.EpayAuth;
import cn.edu.shmtu.cas.captcha.RemoteOcrHttpCaptchaResolver;
import cn.edu.shmtu.cas.session.LoginSubmitResult;
import kotlinx.coroutines.runBlocking;

public class CasJavaBridge {
    public static void main(String[] args) {
        runBlocking(() -> {
            EpayAuth epay = new EpayAuth(new RemoteOcrHttpCaptchaResolver("http://127.0.0.1:5000"));
            LoginSubmitResult r = epay.submitLogin(args[0], args[1]).getOrThrow();
            // ...
        });
    }
}
```

## 与 Spring / Ktor 后端

`incrementalSync` 是 suspend 函数，可以直接在 controller 中：

```kotlin
@RestController
class SyncController(private val store: BillStore) {
    @PostMapping("/sync")
    suspend fun sync(@RequestBody req: SyncRequest): SyncResult {
        val epay = EpayAuth(RemoteOcrHttpCaptchaResolver(req.ocrUrl))
        epay.submitLogin(req.username, req.password)
            .getOrThrow()
            .let { require(it is LoginSubmitResult.Success) { "登录失败" } }
        return incrementalSync(epay, store, SyncOptions(maxPages = req.maxPages)).getOrThrow()
    }
}
```

## 与桌面 GUI（Tauri / Swing / JavaFX）

- Tauri：参考 [shmtu-cas-go](https://github.com/a645162/shmtu-cas-go) 的集成模式，用 Kotlin 写后端、React 写前端
- Swing / JavaFX：在 `SwingWorker` / `Task` 里调 `runBlocking { ... }`
- 验证码弹窗：`ManualCaptchaResolver` 配 Swing `JDialog`

## Cookie 持久化

JVM 端没有 `EncryptedSharedPreferences`，最简单是存 JSON 文件：

```kotlin
val cookiesFile = File("sessions/${accountId}.json")
epay.restoreSession(cookiesFile.readText())
// ...
cookiesFile.writeText(epay.extractSession())
```

## 常见问题

- **Q: 启动报 `NoClassDefFoundError: kotlinx/coroutines/...`？**
  A: 必须显式依赖 `kotlinx-coroutines-core`。
- **Q: 想用 Java 11？**
  A: 目前 toolchain 是 17，强行切到 11 会在 desugaring 上失败。
- **Q: 怎么禁用网络？**
  A: 库本身没提供 mock 拦截；测试时建议直接注入 `ManualCaptchaResolver` 模拟识别。
