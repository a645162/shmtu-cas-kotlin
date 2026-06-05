---
title: 模块与子项目
---

# 模块与子项目

## `cas_lib`

位置：`cas_lib/`

产物：`shmtu-cas-jvm`（通过 `maven-publish` 发布）

Kotlin/JVM 库，导出包 `cn.edu.shmtu.cas.*`，包含全部业务代码。是 Android 库的源代码源。

主要子包：

| 子包 | 内容 |
|------|------|
| `auth.common` | `CasAuth`、`CasAuthStatus`、`CookieManager` |
| `auth` | `EpayAuth`、`WechatAuth` |
| `captcha` | `Captcha`、`CaptchaResolver`、`CaptchaAnswer`、`CaptchaAnswerKind` 与 4 个内置实现 |
| `datatype` | `BillItem`、`BillType`、`BillItemStatus` |
| `parser` | `BillParser`、`BillParseResult`、`HotWaterParser`、`CsvExporter` |
| `sync` | `BillStore`、`SyncOptions`、`SyncResult`、`SyncProgress`、`incrementalSync` |
| `session` | `SessionProbe`、`LoginSubmitResult`、`LoginChallenge` |
| `classifier` | `BillClassifier`、`BillCategory`、`PositionTranslator`、`PositionInfo` |

## `cas_cli`

位置：`cas_cli/`

入口：`cn.edu.shmtu.cas.cli.MainKt`

可执行 jar，使用方式：

```bash
shmtu-cas <command> [options]
```

| 命令 | 说明 |
|------|------|
| `bill` | 登录 CAS 并打印第一页账单 |
| `hot-water` | 登录微信平台并打印热水信息 |
| `captcha-test` | 单次识别验证（开发调试） |
| `parse` | 解析本地 HTML 账单文件 |
| `help` | 打印帮助 |

通用参数（`bill` / `hot-water`）：

| 参数 | 环境变量 | 说明 |
|------|----------|------|
| `-u, --username` | `SHMTU_USER_ID` / `SHMTU_USERNAME` | 学号 |
| `-p, --password` | `SHMTU_PASSWORD` | 密码 |
| `-c, --captcha` | - | `ocr` 或 `manual` |
| `--ocr-host` | `SHMTU_OCR_HOST` | TCP OCR 地址 |
| `--ocr-port` | `SHMTU_OCR_PORT` | TCP OCR 端口 |
| `--ocr-server-type` | - | `tcp` 或 `http` |
| `--ocr-http-url` | `SHMTU_OCR_HTTP_URL` | HTTP OCR 基址 |

## `cas_android_lib`

位置：`cas_android_lib/`

产物：`shmtu-cas-android`（Android AAR，namespace `cn.edu.shmtu.cas`）

通过 `sourceSets.main.java.srcDirs += ['../cas_lib/src/main/kotlin']` 直接复用 `cas_lib` 源码，因此 Android 与 JVM 共用同一份 Kotlin 代码。

构建配置要点：

| 字段 | 值 |
|------|----|
| `namespace` | `cn.edu.shmtu.cas` |
| `compileSdk` | 37 |
| `minSdk` | 21 |
| `compileOptions.sourceCompatibility` | 17 |
| `coreLibraryDesugaring` | `com.android.tools:desugar_jdk_libs:2.1.5` |
| `kotlin.jvmToolchain` | 17 |

## `Documents`

位置：`Documents/`

承载 VitePress 开发者文档，对外是本套文档站。

## 互相关系

```
cas_lib ── 复用于 ──> cas_android_lib
cas_lib ── 依赖 ──>   cas_cli
Documents ── 文档 ──> {cas_lib, cas_android_lib, cas_cli}
```

- 任何 `cas_lib` 的 API 改动都会同步影响 `cas_android_lib`
- `cas_cli` 是 `cas_lib` 的「参考实现」，建议把它当作最佳实践模板
- 文档同时覆盖三个子项目，按场景导航
