---
title: CLI 工具
---

# CLI 工具

`cas_cli` 是 `cas_lib` 的命令行参考实现，可作为开发期调试工具。

## 构建

```bash
./gradlew :cas_cli:installDist
# 产物在 cas_cli/build/install/cas_cli/bin/
```

或直接：

```bash
./gradlew :cas_cli:run --args="help"
```

## 用法

```text
shmtu-cas <command> [options]
```

| 命令 | 说明 |
|------|------|
| `bill` | 登录 CAS 并打印第一页账单 |
| `hot-water` | 登录微信平台并打印热水信息 |
| `captcha-test` | 单次识别验证 |
| `parse` | 解析本地 HTML 账单文件 |
| `help` | 打印帮助 |

### `bill`

```bash
./shmtu-cas bill -u 学号 -p 密码 --captcha ocr
```

参数：

| 参数 | 环境变量 | 默认 | 说明 |
|------|----------|------|------|
| `-u, --username` | `SHMTU_USER_ID` / `SHMTU_USERNAME` | - | 学号 |
| `-p, --password` | `SHMTU_PASSWORD` | - | 密码 |
| `-c, --captcha` | - | `ocr` | `ocr` 或 `manual` |
| `--ocr-host` | `SHMTU_OCR_HOST` | `127.0.0.1` | TCP OCR 地址 |
| `--ocr-port` | `SHMTU_OCR_PORT` | `21601` | TCP OCR 端口 |
| `--ocr-server-type` | - | `tcp` | `tcp` 或 `http` |
| `--ocr-http-url` | `SHMTU_OCR_HTTP_URL` | `http://127.0.0.1:5000` | HTTP OCR 基址 |

### `hot-water`

参数同 `bill`。

### `captcha-test`

仅测试 OCR：

```bash
./shmtu-cas captcha-test --ocr-server-type http --ocr-http-url http://127.0.0.1:5000
```

### `parse`

解析本地 HTML：

```bash
./shmtu-cas parse -i /tmp/bill.html
```

## 入口源码

`cn.edu.shmtu.cas.cli.MainKt` 是参考实现，可作为：

- 怎样把 `CaptchaResolver` 注入到 `EpayAuth` / `WechatAuth` 的范例
- 怎样用环境变量管理配置
- 怎样把 `Result<*>` 转成可读输出

## 配合 OCR 服务器

```bash
# 终端 1：启动 OCR 服务器
docker run -p 5000:5000 shmtu-ocr-server:cpu

# 终端 2：跑 CLI
export SHMTU_USER_ID=2024001
export SHMTU_PASSWORD=xxx
./shmtu-cas bill --ocr-server-type http
```
