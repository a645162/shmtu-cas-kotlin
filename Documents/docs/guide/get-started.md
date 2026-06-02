---
title: 快速开始
---

# 快速开始

## 环境要求

- **JDK 21+**：本项目使用 Kotlin JVM，需要 Java 21 或更高版本
- **Gradle**：项目已包含 Gradle Wrapper，无需单独安装
- **OCR 推理服务器**（可选）：如需验证码自动识别，需部署 [shmtu-cas-ocr-server](https://github.com/a645162/shmtu-cas-ocr-server)

## 克隆项目

```bash
git clone https://github.com/a645162/shmtu-cas-kotlin.git
cd shmtu-cas-kotlin
```

## 构建项目

项目使用 Gradle 构建，首次构建会自动下载依赖：

```bash
./gradlew build
```

主要依赖包括：

| 依赖 | 版本 | 用途 |
|------|------|------|
| Kotlin | 2.3.21 | 编程语言 |
| OkHttp | 5.3.2 | HTTP 客户端 |
| Jsoup | 1.22.2 | HTML 解析 |

## 配置 OCR 服务器

验证码识别依赖远程 OCR 推理服务器。默认连接 `127.0.0.1:21601`，可通过环境变量修改：

```bash
export SHMTU_OCR_HOST=127.0.0.1
export SHMTU_OCR_PORT=21601
```

也可以在代码中动态配置：

```kotlin
Captcha.setOcrServer("192.168.1.100", 21601)
```

## 运行示例

项目入口类为 `cn.edu.shmtu.cas.MainKt`，通过环境变量传入用户名和密码：

```bash
export SHMTU_USER_ID=你的学号
export SHMTU_PASSWORD=你的密码
./gradlew run
```

## 项目结构

```
src/main/kotlin/cn/edu/shmtu/cas/
├── Main.kt                  # 程序入口
├── HtmlCommon.kt            # HTML 工具类
├── auth/
│   ├── common/
│   │   ├── CasAuth.kt       # CAS 认证核心逻辑
│   │   └── CasAuthStatus.kt # 认证状态枚举
│   ├── EpayAuth.kt          # Epay 一卡通认证
│   └── WechatAuth.kt        # 微信平台认证
├── captcha/
│   └── Captcha.kt           # 验证码下载与 OCR 识别
├── parser/
│   ├── BillParser.kt        # 账单 HTML 解析器
│   └── HotWaterParser.kt    # 热水信息 HTML 解析器
└── demo/
    ├── BillDemo.kt          # 账单查询示例
    └── HotWaterDemo.kt      # 热水查询示例
```

## 基本使用

### Epay 账单查询

```kotlin
val epayAuth = EpayAuth()
val isSuccess = epayAuth.login(userId, password)
if (isSuccess) {
    val billResult = epayAuth.getBill(pageNo = "1")
    println(billResult.first)   // 状态码
    println(billResult.second)  // HTML 内容
}
```

### 微信平台热水查询

```kotlin
val wechatAuth = WechatAuth()
wechatAuth.login(userId, password)
val hotWaterResult = wechatAuth.getHotWater()
println(hotWaterResult.first)   // 状态码
println(hotWaterResult.second)  // 热水信息
```

详细使用方法请参阅后续章节。
