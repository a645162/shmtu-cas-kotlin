# 上海海事大学统一认证平台登录流程(Kotlin)

## 本系列项目

### 客户端

* Go Wails版
  [https://github.com/a645162/SHMTU-Terminal-Wails](https://github.com/a645162/SHMTU-Terminal-Wails)
* Rust Tauri版(画个饼，或许以后会做吧~)

### 服务器部署模型

验证码OCR识别系列项目今后将只会维护推理服务器(shmtu-cas-ocr-server)这一个项目。

[https://github.com/a645162/shmtu-cas-ocr-server](https://github.com/a645162/shmtu-cas-ocr-server)

注：这个项目为王老师的研究生课程《机器视觉》的课程设计项目，仅用作学习用途！！！

### Linux端口转发

```bash
sudo sysctl -w net.ipv4.ip_forward=1
sudo iptables -t nat -A PREROUTING -p tcp --dport 21601 -j DNAT --to-destination x:21601
```

### 统一认证登录流程(数字平台+微信平台)

* Kotlin版(方便移植Android)
  [https://github.com/a645162/shmtu-cas-kotlin](https://github.com/a645162/shmtu-cas-kotlin)
* Go版(为Wails桌面客户端做准备)
  [https://github.com/a645162/shmtu-cas-go](https://github.com/a645162/shmtu-cas-go)
* Rust版(未来想做Tauri桌面客户端可能会移植)
  ps.功能其实和Golang版本没啥区别，甚至可能实现地更费劲，Golang的移植已经让我比较抓狂了，虽然Rust我也是会的，但是或许不会做。。。

注：这个项目为王老师的研究生课程《机器视觉》的课程设计项目，仅用作学习用途！！！

### 模型训练

**神经网络图像分类模型训练**

使用PyTorch以及经典网络ResNet

[https://github.com/a645162/shmtu-cas-ocr-model](https://github.com/a645162/shmtu-cas-ocr-model)

**人工标注的数据集(2选1下载)**

* Hugging Face
  https://huggingface.co/datasets/a645162/shmtu_cas_validate_code
* Gitee AI(国内较快)
  https://ai.gitee.com/datasets/a645162/shmtu_cas_validate_code

训练代码中包含爬虫代码，以及自动测试识别结果代码。
您可以对其修改，对测试通过的图片进行标注，这样可以获得准确的标注。

注：这个项目为王老师的研究生课程《机器视觉》的课程设计项目，仅用作学习用途！！！

### 模型本地部署

* Windows客户端(包括VC Win32 GUI以及C# WPF)
  [https://github.com/a645162/shmtu-cas-ocr-demo-windows](https://github.com/a645162/shmtu-cas-ocr-demo-windows)
* Qt客户端(支持Windows/macOS/Linux)
  [https://github.com/a645162/shmtu-cas-ocr-demo-qt](https://github.com/a645162/shmtu-cas-ocr-demo-qt)
* Android客户端
  [https://github.com/a645162/shmtu-cas-demo-android](https://github.com/a645162/shmtu-cas-demo-android)

注：这3个项目为王老师的研究生课程《机器视觉》的课程设计项目，仅用作学习用途！！！

### 原型测试

Python+Selenium4自动化测试数字海大平台登录流程

[https://github.com/a645162/Digital-SHMTU-Tools](https://github.com/a645162/Digital-SHMTU-Tools)

注：本项目为付老师的研究生课程《Python程序设计与开发》的课程设计项目，仅用作学习用途！！！

## 免责声明

本(系列)项目仅供学习交流使用，不得用于商业用途，如有侵权请联系作者删除。

本(系列)项目为个人开发，与上海海事大学无关，仅供学习参考，请勿用于非法用途。

本(系列)项目为孔昊旻同学的**课程设计**项目，仅用作学习用途！！！

## JitPack

本项目现在可通过 JitPack 同时获取 JVM `jar` 和 Android `aar`。

产物坐标:

* JVM库
  `com.github.a645162.shmtu-cas-kotlin:shmtu-cas-jvm:<tag>`
* Android库
  `com.github.a645162.shmtu-cas-kotlin:shmtu-cas-android:<tag>`

其中 `<tag>` 可以是 Git tag、commit hash，或分支快照例如 `master-SNAPSHOT`。

### Gradle Kotlin DSL

`settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

`build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.a645162.shmtu-cas-kotlin:shmtu-cas-jvm:<tag>")
}
```

Android 项目:

```kotlin
dependencies {
    implementation("com.github.a645162.shmtu-cas-kotlin:shmtu-cas-android:<tag>")
}
```

### Gradle Groovy DSL

`settings.gradle`:

```gradle
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

`build.gradle`:

```gradle
dependencies {
    implementation 'com.github.a645162.shmtu-cas-kotlin:shmtu-cas-jvm:<tag>'
}
```

Android 项目:

```gradle
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
        <artifactId>shmtu-cas-jvm</artifactId>
        <version>&lt;tag&gt;</version>
    </dependency>
</dependencies>
```

Android AAR:

```xml
<dependency>
    <groupId>com.github.a645162.shmtu-cas-kotlin</groupId>
    <artifactId>shmtu-cas-android</artifactId>
    <version>&lt;tag&gt;</version>
</dependency>
```

### 说明

* `shmtu-cas-jvm` 是纯 JVM `jar`，适合 Java/Kotlin 后端、桌面端。
* `shmtu-cas-android` 是 Android `aar`，适合 Android App。
* 两个库保持同一套 `cn.edu.shmtu.cas.*` 包名和主要 API。
* Android 项目建议同时保留 `google()` 与 `mavenCentral()` 仓库配置。

### 示例

```kotlin
import cn.edu.shmtu.cas.auth.EpayAuth
import cn.edu.shmtu.cas.captcha.RemoteOcrHttpCaptchaResolver

val resolver = RemoteOcrHttpCaptchaResolver("http://127.0.0.1:5000")
val auth = EpayAuth(resolver)
```

可根据业务切换为:

```kotlin
implementation("com.github.a645162.shmtu-cas-kotlin:shmtu-cas-jvm:<tag>")
implementation("com.github.a645162.shmtu-cas-kotlin:shmtu-cas-android:<tag>")
```
