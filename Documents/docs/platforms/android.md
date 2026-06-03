---
title: Android 集成指南
---

# Android 集成指南

`cas_android_lib` 通过 `sourceSets.main.java.srcDirs += ['../cas_lib/src/main/kotlin']` **直接复用 `cas_lib` 的源码**，因此你在 Android 上能用到与 JVM 完全一致的 API（`cn.edu.shmtu.cas.*`）。

## 1. 添加依赖

本库通过 JitPack 同时发布 JVM `jar` 与 Android `aar`，坐标：

```
com.github.a645162.shmtu-cas-kotlin:shmtu-cas-jvm:<tag>     // 纯 JVM
com.github.a645162.shmtu-cas-kotlin:shmtu-cas-android:<tag> // Android AAR
```

> **`<tag>`** 可以是 Git tag、commit hash，或分支快照如 `master-SNAPSHOT`。

### Gradle Kotlin DSL

`settings.gradle.kts`：

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

`app/build.gradle.kts`：

```kotlin
dependencies {
    implementation("com.github.a645162.shmtu-cas-kotlin:shmtu-cas-android:<tag>")
}
```

### Gradle Groovy DSL

`settings.gradle`：

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

`app/build.gradle`：

```groovy
dependencies {
    implementation 'com.github.a645162.shmtu-cas-kotlin:shmtu-cas-android:<tag>'
}
```

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.a645162.shmtu-cas-kotlin</groupId>
        <artifactId>shmtu-cas-android</artifactId>
        <version>&lt;tag&gt;</version>
    </dependency>
</dependencies>
```

### 说明

- `shmtu-cas-jvm` 是纯 JVM `jar`，适合 Java/Kotlin 后端、桌面端
- `shmtu-cas-android` 是 Android `aar`，适合 Android App
- 两个库保持同一套 `cn.edu.shmtu.cas.*` 包名和主要 API
- Android 项目建议同时保留 `google()` 与 `mavenCentral()` 仓库配置

## 2. 最低配置

| 项 | 值 |
|----|----|
| `minSdk` | 21 |
| `compileSdk` | 37 |
| Kotlin | 2.2.10 |
| `coreLibraryDesugaring` | `com.android.tools:desugar_jdk_libs:2.1.5` |

`app/build.gradle.kts`：

```kotlin
android {
    compileOptions {
        coreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation("com.github.a645162.shmtu-cas-kotlin:shmtu-cas-android:<tag>")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
}
```

## 3. AndroidManifest 权限

库本身不强制要求权限，但 OkHttp 在 Android 上需要：

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

如果 OCR 服务器部署在局域网，需要允许明文 HTTP：

```xml
<!-- AndroidManifest.xml <application> 标签内 -->
android:usesCleartextTraffic="true"
```

或更安全的 network-security-config：

```xml
<!-- res/xml/network_security_config.xml -->
<network-security-config>
    <base-config cleartextTrafficPermitted="false" />
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">10.0.2.2</domain>
        <domain includeSubdomains="true">192.168.1.0</domain>
    </domain-config>
</network-security-config>
```

## 4. 选验证码方案

| 方案 | 实现 | 适用 |
|------|------|------|
| **系统弹窗** | `ManualCaptchaResolver` | 单账号、稳定用户，最省心 |
| **HTTP OCR** | `RemoteOcrHttpCaptchaResolver` | 多账号 / 后端统一 OCR 服务 |
| **TCP OCR** | `RemoteOcrCaptchaResolver` | 局域网直连 |
| **本地 ONNX** | `ExprCaptchaResolver` | 已训练自己的模型 |

### 4.1 系统弹窗（推荐入门）

```kotlin
val resolver = ManualCaptchaResolver { imageData ->
    // 1. 显示图片给用户（ImageView 加载 / DialogFragment）
    showCaptchaDialog(imageData)

    // 2. suspend 等待用户输入
    val userInput = captchaChannel.receive()   // suspend until user click "确定"

    CaptchaAnswer(userInput, CaptchaAnswerKind.ANSWER)
}
```

完整 DialogFragment 模板见下方 §7。

### 4.2 HTTP OCR（生产推荐）

```kotlin
val resolver = RemoteOcrHttpCaptchaResolver(
    baseUrl = "http://your-ocr-server:5000",
    retryTimes = 3
)
```

### 4.3 健康检查

```kotlin
lifecycleScope.launch {
    val ok = resolver.healthCheck()
    if (!ok) showSnackbar("OCR 服务不可达，请稍后重试")
}
```

## 5. 完整登录 + 同步示例（Kotlin + Coroutines）

```kotlin
class BillRepository(
    private val dao: BillDao,
    private val ocrUrl: String
) {
    private val resolver = RemoteOcrHttpCaptchaResolver(ocrUrl, retryTimes = 3)

    suspend fun syncOneAccount(username: String, password: String): Result<SyncResult> {
        val epay = EpayAuth(resolver)

        // 1. 尝试恢复已保存的会话
        savedSessionJson()?.let { epay.restoreSession(it) }
        if (epay.testLoginStatus().getOrNull() == true) {
            // 已登录 → 直接同步
        } else {
            // 2. 登录（内部已含 TGC 复用 + 自动重试）
            when (val r = epay.submitLogin(username, password).getOrThrow()) {
                is LoginSubmitResult.Success -> {}
                is LoginSubmitResult.PasswordError -> return Result.failure(Exception("密码错误"))
                is LoginSubmitResult.ValidateCodeError -> return Result.failure(Exception("验证码错误"))
                is LoginSubmitResult.Failure -> return Result.failure(Exception(r.message))
            }
            // 3. 保存会话
            saveSessionJson(epay.extractSession())
        }

        // 4. 同步账单到 Room
        return incrementalSync(epay, RoomBillStore(dao), SyncOptions(maxPages = 20))
    }

    private fun savedSessionJson(): String? =
        encryptedPrefs.getString("epay_cookies", null)

    private fun saveSessionJson(json: String) {
        encryptedPrefs.edit().putString("epay_cookies", json).apply()
    }
}

// BillStore 接到 Room
class RoomBillStore(private val dao: BillDao) : BillStore {
    override fun contains(transactionNo: String): Boolean = dao.exists(transactionNo)
    override fun merge(newBills: List<BillItem>) {
        dao.insertAll(newBills.map { it.toEntity() })
    }
}
```

在 ViewModel 中：

```kotlin
class SyncViewModel(private val repo: BillRepository) : ViewModel() {
    fun sync(username: String, password: String) = viewModelScope.launch {
        repo.syncOneAccount(username, password)
            .onSuccess { snackbar("新增 ${it.newCount} 条") }
            .onFailure { snackbar("同步失败：${it.message}") }
    }
}
```

## 6. 加密存储 Cookie（强烈建议）

把 `epay.extractSession()` 的 JSON 存进 `EncryptedSharedPreferences`：

```kotlin
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

val prefs = EncryptedSharedPreferences.create(
    context, "shmtu_cas_secrets", masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)
```

依赖：

```kotlin
implementation("androidx.security:security-crypto:1.1.0-alpha06")
```

## 7. 手动验证码弹窗模板

```kotlin
class CaptchaDialogFragment : DialogFragment() {

    private val _binding by lazy { DialogCaptchaBinding.inflate(layoutInflater) }
    private val pending: CaptchaDialogArgs by lazy { requireArguments() as CaptchaDialogArgs }
    private var done = false

    override fun onCreateView(...) = _binding.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding.ivCaptcha.setImageBitmap(pending.bitmap)
        _binding.btnSubmit.setOnClickListener {
            val text = _binding.etAnswer.text.toString().trim()
            if (text.isNotEmpty() && !done) {
                done = true
                pending.onAnswer(text)   // 回调续接登录协程
                dismiss()
            }
        }
    }
}

data class CaptchaDialogArgs(
    val bitmap: Bitmap,
    val onAnswer: (String) -> Unit
)

// 在 ViewModel 里把 suspend 转成回调
class CaptchaViewModel : ViewModel() {
    private val channel = Channel<String>(Channel.RENDEZVOUS)

    suspend fun awaitAnswer(): String = channel.receive()

    fun submit(text: String) = channel.trySend(text)
}
```

## 8. 网络配置细节

### 8.1 关闭 OkHttp 自动重定向

库内部已经为你处理，但你如果自定义了 `CasAuth.createClient()`，务必保留：

```kotlin
.followRedirects(false)
.followSslRedirects(false)
```

否则 `Set-Cookie` 和 `Location` 会被 OkHttp 吞掉，登录必然失败。

### 8.2 连接池

库内默认每次新建 client。生产环境可考虑为整个 App 共享一个 `OkHttpClient` 并注入（当前 API 暂未提供注入入口，会在后续版本提供）。

## 9. WorkManager 周期同步

把 `BillRepository.syncOneAccount` 放进 WorkManager：

```kotlin
class BillSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val account = inputData.getString("account") ?: return Result.failure()
        val password = inputData.getString("password") ?: return Result.failure()
        val ocrUrl = inputData.getString("ocr_url") ?: return Result.failure()
        return BillRepository(applicationContext, dao, ocrUrl)
            .syncOneAccount(account, password)
            .fold(
                onSuccess = { Result.success() },
                onFailure = { Result.failure() }
            )
    }
}
```

## 10. 常见问题

详见 [FAQ](/faq/) 的 Android 段，或：

- **Q: 必须在 UI 线程登录吗？**
  A: 不需要，所有 `suspend` 方法都应放在 `viewModelScope` / `lifecycleScope`。
- **Q: 想自定义 cookie 存储？**
  A: 调 `epay.restoreSession(json)` / `epay.extractSession()`，存哪都行。
- **Q: 多账号怎么同步？**
  A: 每个账号一个 `EpayAuth` 实例，串行调 `submitLogin` + `incrementalSync`，互不干扰。
- **Q: 验证码弹窗怎么与登录协程续接？**
  A: 用 `Channel` 或 `suspendCancellableCoroutine` 把回调转回 suspend（见 §7）。
