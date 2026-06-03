---
layout: home

hero:
  name: shmtu-cas-kotlin
  text: 开发者文档
  tagline: 上海海事大学 CAS 统一认证 + Epay 账单 + 微信平台热水的 Kotlin 多端组件库
  actions:
    - theme: brand
      text: 快速开始
      link: /guide/get-started
    - theme: alt
      text: API 总览
      link: /api/overview
    - theme: alt
      text: Android 集成
      link: /platforms/android

features:
  - title: CAS 统一认证
    details: 封装 OkHttp + Jsoup，复现 execution 提取、验证码挑战、登录提交、重定向跟踪、TGC 复用完整流程。
  - title: 验证码可插拔
    details: CaptchaResolver 接口统一表达「识别验证码」这一动作，提供 Manual / TCP OCR / HTTP OCR / 算式四种内置实现。
  - title: 增量账单同步
    details: BillStore + SyncOptions + incrementalSync 对接宿主存储（Room / SQLite / 内存），支持分页、BillType、早停与进度回调。
  - title: 多端产物
    details: 同源代码同时产出 JVM jar、CLI 可执行程序、Android aar，minSdk 21，coroutine 友好。
---

## 这套库做什么

`shmtu-cas-kotlin` 帮你完成三件事：

1. **登录** — 探测 CAS 会话、获取验证码、提交凭证、跟随重定向、持久化 Cookie。
2. **同步** — 逐页拉取 Epay 一卡通账单，把新条目通过 `BillStore` 交还宿主存储。
3. **识别** — TCP 远程 OCR、HTTP 远程 OCR、本地算式、Manual 四种验证码策略可热切换。

你只需要实现 `BillStore.contains` 和 `BillStore.merge`，再选定一个 `CaptchaResolver`，就能跑通整个同步链路。

## 最快的上手路径

```kotlin
// 1. 选一个验证码解析器
val resolver = RemoteOcrHttpCaptchaResolver("http://127.0.0.1:5000")

// 2. 创建 EpayAuth 并登录
val epay = EpayAuth(resolver)
epay.submitLogin("学号", "密码")                  // TGC 复用 + 自动重试

// 3. 拉取并同步账单
val store = object : BillStore {
    override fun contains(transactionNo: String) = db.exists(transactionNo)
    override fun merge(newBills: List<BillItem>) = db.insertAll(newBills)
}
incrementalSync(epay, store, SyncOptions(maxPages = 20)) { progress ->
    println("page ${progress.page} new=${progress.newCount}")
}
```

## 模块一览

| 模块 | 职责 |
|------|------|
| [`auth.common`](/api/cas-auth) | CAS 底层：`createClient` / `getExecution` / `casLogin` / `casRedirect` / `mergeCookies` |
| [`auth.EpayAuth`](/api/epay-auth) | 一卡通侧：探测登录、获取 challenge、提交登录、增量获取账单 |
| [`auth.WechatAuth`](/api/wechat-auth) | 微信后勤侧：wengine_new_ticket 跳转 + 热水查询 |
| [`captcha.Captcha`](/api/captcha) | 验证码图片下载、TCP OCR、算式提取、工具方法 |
| [`captcha.CaptchaResolver`](/api/captcha-resolver) | 抽象接口 + Manual / TCP / HTTP / Expr 四种实现 |
| [`parser.BillParser`](/api/bill-parser) | 账单 HTML → `BillItem` 强类型 |
| [`parser.HotWaterParser`](/api/hotwater-parser) | 热水 HTML → 楼栋温度/水位 |
| [`parser.CsvExporter`](/api/bill-parser#csv-导出) | `BillItem` → CSV 文件/字符串 |
| [`sync.incrementalSync`](/api/bill-sync) | `BillStore` + `SyncOptions` 增量同步主入口 |
| [`classifier`](/api/classifier) | `BillClassifier`、`PositionTranslator` 关键词规则 |

## 子项目成员

| 子项目 | 产物 | 用途 |
|--------|------|------|
| `cas_lib` | `shmtu-cas-jvm.jar` | 纯 JVM 库，Kotlin 2.2 / JDK 17 toolchain |
| `cas_android_lib` | `shmtu-cas-android.aar` | Android 库，namespace `cn.edu.shmtu.cas`，复用 cas_lib 源码 |
| `cas_cli` | 可执行 jar | 命令行调试：`bill` / `hot-water` / `captcha-test` / `parse` |

两个客户端产物共享同一套 `cn.edu.shmtu.cas.*` 包名与 API。
