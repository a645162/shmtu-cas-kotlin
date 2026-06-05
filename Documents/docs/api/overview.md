---
title: API 总览
---

# API 总览

`cas_lib` 的所有公开类型都集中在 `cn.edu.shmtu.cas.*` 包下。下表按职责分块，详细说明见各子页面。

## 包结构

```
cn.edu.shmtu.cas
├── auth
│   ├── EpayAuth
│   ├── WechatAuth
│   └── common
│       ├── CasAuth
│       ├── CasAuthStatus
│       └── CookieManager
├── captcha
│   ├── Captcha
│   ├── CaptchaAnswer
│   ├── CaptchaAnswerKind
│   ├── CaptchaResolver           # interface
│   ├── ManualCaptchaResolver
│   ├── RemoteOcrCaptchaResolver
│   ├── RemoteOcrHttpCaptchaResolver
│   └── ExprCaptchaResolver
├── classifier
│   ├── BillCategory              # enum
│   ├── BillClassifier
│   ├── PositionTranslator
│   └── PositionInfo
├── datatype
│   ├── BillItem
│   ├── BillType                  # enum
│   └── BillItemStatus            # enum
├── parser
│   ├── BillParser
│   ├── BillParseResult
│   ├── HotWaterParser
│   └── CsvExporter
├── session
│   ├── SessionProbe              # sealed
│   ├── LoginSubmitResult         # sealed
│   └── LoginChallenge
└── sync
    ├── BillStore                 # interface
    ├── SyncOptions
    ├── SyncResult
    ├── SyncProgress
    └── incrementalSync           # suspend fun
```

## 快速索引

| 想做什么 | 入口 | 文档 |
|---------|------|------|
| 探测/重定向/login | `CasAuth.createClient` / `getExecution` / `casLogin` / `casRedirect` | [CasAuth](/api/cas-auth) |
| 一卡通登录 + 账单 | `EpayAuth` | [EpayAuth](/api/epay-auth) |
| 微信平台登录 + 热水 | `WechatAuth` | [WechatAuth](/api/wechat-auth) |
| 下载/识别验证码 | `Captcha` | [Captcha](/api/captcha) |
| 抽象识别器 | `CaptchaResolver` + 4 个实现 | [CaptchaResolver](/api/captcha-resolver) |
| 解析账单 HTML | `BillParser`、`BillParseResult`、`CsvExporter` | [BillParser](/api/bill-parser) |
| 解析热水 HTML | `HotWaterParser` | [HotWaterParser](/api/hotwater-parser) |
| 增量同步 | `incrementalSync` + `BillStore` | [BillSync](/api/bill-sync) |
| 关键词分类/翻译 | `BillClassifier`、`PositionTranslator` | [Classifier](/api/classifier) |
| 核心数据模型 | `BillItem`、`BillType`、`BillItemStatus` | [数据类型](/api/datatype) |
| 会话枚举 | `SessionProbe`、`LoginSubmitResult`、`LoginChallenge` | [数据类型](/api/datatype) |
| Cookie 持久化 | `CookieManager` | [高级主题：会话](/advanced/session) |

## 推荐接入方式

```kotlin
val resolver = RemoteOcrHttpCaptchaResolver("http://127.0.0.1:21600")
val epay = EpayAuth(resolver)
val r = epay.submitLogin("学号", "密码").getOrThrow()
check(r is LoginSubmitResult.Success)

val store = object : BillStore {
    override fun contains(transactionNo: String) = false
    override fun merge(newBills: List<BillItem>) = newBills.forEach(::println)
}
val result = incrementalSync(epay, store, SyncOptions(maxPages = 20)).getOrThrow()
println("new=${result.newCount}, total=${result.totalFetched}")
```

## API 设计特点

- 以接口隔离宿主依赖（`BillStore`、`CaptchaResolver`）
- 以 `suspend` + `Result<*>` 表达异步与失败
- 以 `sealed class` 表达枚举化结果（`SessionProbe`、`LoginSubmitResult`）
- 以模块边界区分网络、解析、分类、同步
- 三阶段登录设计（probe → challenge → submit），每一步都可独立注入测试
