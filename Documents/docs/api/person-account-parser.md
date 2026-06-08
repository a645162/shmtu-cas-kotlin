---
title: PersonAccountParser
---

# PersonAccountParser

`PersonAccountParser` 解析一卡通个人账户页 `/epay/personaccount/index` 的 HTML，
提取资金&安全信息、基本信息、头部标题以及 CSRF token。

`cn.edu.shmtu.cas.parser.PersonAccountParser`

## 构造函数

```kotlin
class PersonAccountParser
```

无参构造，调用 `parse(htmlCode)` 一次性返回完整 `PersonAccountInfo` 数据类。

## 方法

### parse

```kotlin
fun parse(htmlCode: String): PersonAccountInfo
```

一次性解析整个 HTML 页面，返回 `PersonAccountInfo`。

## 数据类 PersonAccountInfo

| 字段 | 类型 | 来源 |
|------|------|------|
| `realName` | `String` | `.panel-title` 中的 `姓名：xxx` |
| `realNameAuthStatus` | `String` | `.panel-title` 中的 `实名认证:xxx` |
| `cashBalance` | `Double` | `现金资金` 数值 |
| `cashBalanceRaw` | `String` | `现金资金` 原始字符串（去 "元"） |
| `securityQuestionStatus` | `String` | `安全保护问题` 值 |
| `registerDate` | `String` | `注册时间` 值 |
| `studentId` | `String` | `学工号` |
| `email` | `String` | `电子邮箱` |
| `nickname` | `String` | `昵称` |
| `gender` | `String` | `性别` |
| `className` | `String` | `班级` |
| `mobile` | `String` | `手机` |
| `fixedLine` | `String` | `固话` |
| `idType` | `String` | `证件类型` |
| `idNumber` | `String` | `证件号码` |
| `remark` | `String` | `备注` |
| `userType` | `String` | `用户类型` |
| `csrfToken` | `String` | `meta name="_csrf"` |
| `csrfHeader` | `String` | `meta name="_csrf_header"` (默认 `X-CSRF-TOKEN`) |

## 解析规则

### 头部标题 (panel-title)

正则提取：`姓名[:：](\S+)` 和 `实名认证[:：](\S+)`，自动把 `&nbsp;` 视为空白分界。

### 基本信息表 (#baseinfo tbody)

按行解析 `<tr><td>字段:值</td><td>value</td></tr>`。中英文冒号自动归一化。

### 资金&安全信息表 (#otherinfo)

`#otherinfo` 容器下包含两张 `<table>`（资金信息 + 安全信息），每张各一个 `<tbody>`。
**所有 tbody 合并**到一个 Map，再按 key 提取 — 这是一个关键的合并逻辑。

### 现金资金

`现金资金` 字段形如 `41.40 元` 或 `41.40元`，`cashBalanceRaw` 去除 "元" 后保留字符串，
`cashBalance` 是 `cashBalanceRaw.toDoubleOrNull() ?: 0.0`。

### CSRF

从 `<meta name="_csrf" content="...">` 与 `<meta name="_csrf_header" content="...">` 提取。
若 `_csrf_header` 不存在则回退到 `X-CSRF-TOKEN`。

## 使用示例

```kotlin
// 方式 1: 与 EpayAuth 配合
val auth = EpayAuth(resolver)
auth.submitLogin(username, password)
val html = auth.getPersonAccountHtml().getOrThrow()
val info = PersonAccountParser().parse(html)

println("${info.realName} (${info.realNameAuthStatus})")
println("现金: ${info.cashBalanceRaw} 元")
println("学工号: ${info.studentId}")
println("CSRF: ${info.csrfHeader} = ${info.csrfToken}")
```

## 对齐说明

该 Parser 与 Rust `shmtu_cas::parser::parse_person_account`、
Python `shmtu_cas.parser.parse_person_account` 解析结果完全等价，便于跨语言使用。
